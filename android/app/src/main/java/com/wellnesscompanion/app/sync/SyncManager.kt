package com.wellnesscompanion.app.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import com.google.gson.Gson
import com.wellnesscompanion.app.data.local.WellnessDatabase
import com.wellnesscompanion.app.data.local.entity.ChoreTemplateEntity
import com.wellnesscompanion.app.data.local.entity.EntryEntity
import com.wellnesscompanion.app.data.local.entity.HobbyEntity
import com.wellnesscompanion.app.data.local.entity.PersonEntity
import com.wellnesscompanion.app.data.local.entity.SettingEntity
import com.wellnesscompanion.app.util.nowMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Talks to the desktop app over the LAN using `wc-sync/4` (`crypto:'x1'`).
 *
 * The phone is the CLIENT half of the handshake in
 * the internal security-hardening spec §2.1–2.9: it waits
 * for the desktop's `hello`, sends `hs1`, verifies `mac_s`, sends `hs3`, and
 * from then on every application frame travels as an AES-256-GCM record inside
 * a BINARY WebSocket frame. There is no plaintext data path: the only
 * `send(String)` calls in this file are `hs1` and `hs3`, both before the
 * channel exists.
 *
 * Fail-closed rules that a reviewer checks first:
 *  - nothing at all is sent until a `hello` proves the peer speaks version 4
 *    with `x1`; a v3 desktop gets no `hs1`, only "update the desktop app";
 *  - a fresh ephemeral P-256 keypair per connection (with
 *    `retryOnConnectionFailure(false)` on the client) so a silent re-dial can
 *    never restart the record counters under a key that was already used;
 *  - an unauthenticated plaintext error NEVER destroys the stored device key —
 *    it only raises a user-gated re-pair prompt (spec §2.8 ops-F1).
 */
@Singleton
class SyncManager @Inject constructor(
    private val context: Context,
    private val db: WellnessDatabase,
    private val gson: Gson,
    private val client: OkHttpClient
) {
    companion object {
        private const val TAG = "SyncManager"
        private const val SERVICE_TYPE = "_http._tcp."
        private const val SERVICE_NAME = "wellness-companion-sync"
        private const val SYNC_PORT = 9847

        /** Stable per-install identity; the desktop keys its device slot on it. */
        const val DEVICE_ID_KEY = "sync.device_id"
        /** The desktop-side id of the pairing this phone holds. */
        const val PAIRING_KEY_ID_KEY = "sync.key_id"
        /** The 128-bit pairing secret, base64. It IS the long-term device key. */
        const val DEVICE_KEY_KEY = "sync.device_key"
        /**
         * The v3 ~40-bit code. The protocol no longer has one, and the desktop
         * treats a legacy `auth` frame as a tombstone, so this row is dead
         * weight; it is blanked the first time a v4 pairing is stored.
         */
        private const val LEGACY_PAIRING_TOKEN_KEY = "sync.pairing_token"
        private const val LAST_SYNC_KEY = "sync.last_synced_at"

        private const val DISCOVERY_TIMEOUT_MS = 5_000L
        private const val SYNC_TIMEOUT_MS = 60_000L
        /** Cap per run so a long history is sent over several syncs, not one giant frame. */
        private const val MAX_ENTRIES_PER_SYNC = 2_000
        private const val NONCE_BYTES = 32
        private const val MAX_DEVICE_NAME = 64

        // ── Phone-side copy per failure mode (spec §2.8 ops-F7) ────────────
        // Every close code the desktop can emit maps to one of these; none of
        // them is the old generic "Connection closed".
        const val MSG_UPDATE_DESKTOP =
            "Update the Wellness Companion app on your PC — it speaks an older sync protocol."
        const val MSG_PC_NOT_RECOGNISED =
            "The PC no longer recognises this phone. If you reset pairing on the PC, tap Re-pair " +
                "and enter the new code."
        const val MSG_PAIRING_REMOVED =
            "The PC has forgotten this phone. Tap Re-pair and enter the new code shown on the PC."
        private const val MSG_TOO_MANY_CONNECTIONS =
            "The PC is busy syncing other devices. Try again in a moment."
        private const val MSG_LOCKED_OUT_GENERIC =
            "The PC paused pairing after repeated failures. Your pairing is still saved — try again later."
        private const val MSG_PROTOCOL_VIOLATION =
            "Sync failed: the PC broke the encrypted protocol. Try again."
        private const val MSG_RECORD_REJECTED =
            "Sync failed: the encrypted channel was corrupted. Try again."
        private const val MSG_GENERIC_FAILURE = "Sync failed — try again."
        private const val MSG_CLOSED_EARLY = "The PC closed the connection before the sync finished."
    }

    /** What the phone holds after pairing. The secret is never logged. */
    class Pairing(val keyId: String, val secret: ByteArray)

    /** The outcome of one sync run; drives both the status and the cursor write. */
    private class SyncResult(
        val message: String,
        val succeeded: Boolean,
        val needsRepair: Boolean = false
    )

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status = _status.asStateFlow()

    /**
     * Non-null when the desktop could not (or would not) authenticate this
     * phone. The stored device key is KEPT: only the user completing a fresh
     * pairing discards it (spec §2.8 ops-F1 / protocol-F3).
     */
    private val _repairPrompt = MutableStateFlow<String?>(null)
    val repairPrompt = _repairPrompt.asStateFlow()

    fun dismissRepairPrompt() {
        _repairPrompt.value = null
    }

    // ── Pairing state ──────────────────────────────────────────────────────

    suspend fun getPairing(): Pairing? {
        val settings = db.settingsDao()
        val keyId = settings.getSetting(PAIRING_KEY_ID_KEY)?.trim()
        val secretB64 = settings.getSetting(DEVICE_KEY_KEY)?.trim()
        if (keyId.isNullOrBlank() || secretB64.isNullOrBlank()) return null
        val secret = runCatching { Base64.getDecoder().decode(secretB64) }.getOrNull()
        if (secret == null || secret.size != SyncCrypto.PAIRING_SECRET_BYTES) return null
        return Pairing(keyId, secret)
    }

    suspend fun isPaired(): Boolean = getPairing() != null

    /**
     * Store a pairing typed off the desktop's "Pair a device" panel: ONE
     * 33-glyph code carrying `keyId(4) ‖ secret(16)`. The user never
     * transcribes a lookup handle separately.
     *
     * Throws [IllegalArgumentException] with a user-safe message (never
     * quoting the code) if the code is malformed — and it throws BEFORE
     * anything is written, so a typo cannot destroy an existing pairing.
     *
     * On success the sync cursor is reset to 0 so the next run is one full
     * reconciling sync — this is the ops-F5 fix: without it, re-pairing against
     * a desktop whose database was lost or rolled back left the two sides
     * silently and permanently out of step.
     */
    suspend fun savePairing(code: String) {
        val parts = SyncCrypto.decodePairingCode(code)

        val settings = db.settingsDao()
        settings.setSetting(SettingEntity(key = PAIRING_KEY_ID_KEY, value = parts.keyId))
        settings.setSetting(
            SettingEntity(
                key = DEVICE_KEY_KEY,
                value = Base64.getEncoder().encodeToString(parts.secret)
            )
        )
        settings.setSetting(SettingEntity(key = LAST_SYNC_KEY, value = "0"))
        // Retire the dead v3 credential rather than leaving it in the database.
        if (!settings.getSetting(LEGACY_PAIRING_TOKEN_KEY).isNullOrEmpty()) {
            settings.setSetting(SettingEntity(key = LEGACY_PAIRING_TOKEN_KEY, value = ""))
        }
        _repairPrompt.value = null
    }

    /** A UUID generated once and kept forever; the desktop's slots are keyed by it. */
    suspend fun deviceId(): String {
        val settings = db.settingsDao()
        val existing = settings.getSetting(DEVICE_ID_KEY)?.trim()
        if (!existing.isNullOrBlank()) return existing
        val fresh = UUID.randomUUID().toString()
        settings.setSetting(SettingEntity(key = DEVICE_ID_KEY, value = fresh))
        return fresh
    }

    /** Shown in the desktop's paired-devices list. Printable ASCII-ish, bounded. */
    private fun deviceLabel(): String {
        val model = (Build.MODEL ?: "").trim()
        val printable = model.filter { it.code in 0x20..0x7E || it.code > 0xA0 }
        return printable.take(MAX_DEVICE_NAME).ifBlank { "Phone" }
    }

    // ── Entry points ───────────────────────────────────────────────────────

    /** Try to discover the Windows app via mDNS, then sync. */
    suspend fun discoverAndSync() {
        val pairing = getPairing()
        if (pairing == null) {
            _status.value = SyncStatus.NeedsPairing
            return
        }
        _status.value = SyncStatus.Discovering
        try {
            val host = discoverHost()
            if (host != null) {
                syncWith(host.first, host.second)
            } else {
                _status.value = SyncStatus.Error("PC not found on network")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            _status.value = SyncStatus.Error(e.message ?: "Sync failed")
        }
    }

    /** Sync with a known host:port (manual fallback). */
    suspend fun syncWith(host: String, port: Int) {
        val pairing = getPairing()
        if (pairing == null) {
            _status.value = SyncStatus.NeedsPairing
            return
        }
        _status.value = SyncStatus.Connecting
        try {
            val result = withTimeoutOrNull(SYNC_TIMEOUT_MS) { performFullSync(host, port, pairing) }
            _status.value = when {
                result == null -> SyncStatus.Error("Sync timed out")
                result.needsRepair -> {
                    _repairPrompt.value = result.message
                    SyncStatus.NeedsRepair(result.message)
                }
                result.succeeded -> SyncStatus.Done(result.message)
                else -> SyncStatus.Error(result.message)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync error", e)
            _status.value = SyncStatus.Error(e.message ?: "Sync failed")
        }
    }

    private suspend fun discoverHost(): Pair<String, Int>? = withContext(Dispatchers.IO) {
        // withTimeoutOrNull replaces a raw Thread { sleep(5000) } that was never
        // interrupted or joined and outlived coroutine cancellation.
        withTimeoutOrNull(DISCOVERY_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
                // Resumed from the NSD callback thread and from cancellation, so
                // the guard has to be atomic — a lost race double-resumed the
                // continuation and crashed on a callback thread.
                val settled = AtomicBoolean(false)
                var discoveryListener: NsdManager.DiscoveryListener? = null

                fun stopDiscovery() {
                    try {
                        discoveryListener?.let { nsdManager.stopServiceDiscovery(it) }
                    } catch (_: Exception) {
                    }
                }

                discoveryListener = object : NsdManager.DiscoveryListener {
                    override fun onDiscoveryStarted(serviceType: String) {
                        Log.d(TAG, "NSD discovery started")
                    }

                    override fun onServiceFound(info: NsdServiceInfo) {
                        if (!info.serviceName.contains(SERVICE_NAME) &&
                            !info.serviceName.contains("wellness-companion")
                        ) return

                        nsdManager.resolveService(info, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                                Log.e(TAG, "NSD resolve failed: $errorCode")
                            }

                            override fun onServiceResolved(info: NsdServiceInfo) {
                                if (settled.compareAndSet(false, true)) {
                                    stopDiscovery()
                                    cont.resume(
                                        Pair(info.host?.hostAddress ?: "127.0.0.1", info.port)
                                    )
                                }
                            }
                        })
                    }

                    override fun onServiceLost(info: NsdServiceInfo) {}
                    override fun onDiscoveryStopped(serviceType: String) {}
                    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                        if (settled.compareAndSet(false, true)) {
                            stopDiscovery()
                            cont.resume(null)
                        }
                    }

                    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
                }

                nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
                cont.invokeOnCancellation { stopDiscovery() }
            }
        }
    }

    /**
     * Store the hobby / person / chore-template lists the desktop sends back.
     * Room's REPLACE conflict strategy makes this idempotent, and each row is
     * skipped rather than aborting the batch if it is malformed.
     */
    private fun ingestAux(msg: JSONObject) {
        msg.optJSONArray("hobbies")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id")
                if (id.isBlank()) continue
                runCatching {
                    db.hobbyDao().insertSync(
                        HobbyEntity(
                            id = id,
                            name = o.optString("name"),
                            color = o.optString("color", "#F0997B"),
                            createdAt = o.optLong("created_at", nowMillis())
                        )
                    )
                }.onFailure { Log.e(TAG, "hobby ingest failed", it) }
            }
        }

        msg.optJSONArray("people")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id")
                if (id.isBlank()) continue
                runCatching {
                    val deletedAt = if (o.isNull("deleted_at")) null
                                    else o.optLong("deleted_at").takeIf { it > 0 }
                    val existing = db.personDao().getByIdSync(id)
                    if (existing == null) {
                        db.personDao().insertSync(
                            PersonEntity(
                                id = id,
                                name = o.optString("name"),
                                createdAt = o.optLong("created_at", nowMillis()),
                                deletedAt = deletedAt
                            )
                        )
                    } else if (deletedAt != null && existing.deletedAt == null) {
                        // Tombstones are grow-only: never resurrect, never clear.
                        db.personDao().markDeletedSync(id, deletedAt)
                    }
                }.onFailure { Log.e(TAG, "person ingest failed", it) }
            }
        }

        msg.optJSONArray("chore_templates")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id")
                if (id.isBlank()) continue
                runCatching {
                    db.choreTemplateDao().insertSync(
                        ChoreTemplateEntity(
                            id = id,
                            name = o.optString("name"),
                            category = o.optString("category").takeIf { it.isNotBlank() },
                            recurrence = o.optString("recurrence").takeIf { it.isNotBlank() },
                            createdAt = o.optLong("created_at", nowMillis())
                        )
                    )
                }.onFailure { Log.e(TAG, "chore template ingest failed", it) }
            }
        }
    }

    private suspend fun performFullSync(host: String, port: Int, pairing: Pairing): SyncResult =
        withContext(Dispatchers.IO) {
            _status.value = SyncStatus.Syncing

            val entryDao = db.entryDao()
            val settingsDao = db.settingsDao()
            val since = settingsDao.getSetting(LAST_SYNC_KEY)?.toLongOrNull() ?: 0L
            val deviceId = deviceId()

            val outgoing = entryDao.getModifiedSinceSync(since, MAX_ENTRIES_PER_SYNC)
            val pending = entryDao.countModifiedSinceSync(since)
            val hobbies = db.hobbyDao().getAllSync()
            val people = db.personDao().getAllSync()
            val choreTemplates = db.choreTemplateDao().getAllSync()

            val pushData = JSONObject().apply {
                put("type", "full_sync")
                put("since", since)
                put("entries", JSONArray(outgoing.map { e ->
                    JSONObject().apply {
                        put("id", e.id)
                        put("category", e.category)
                        put("timestamp", e.timestamp)
                        put("date", e.date)
                        put("data", e.data)
                        put("version", e.version)
                        put("modified_at", e.modifiedAt)
                        put("synced", e.synced)
                    }
                }))
                put("hobbies", JSONArray(hobbies.map { h ->
                    JSONObject().apply {
                        put("id", h.id); put("name", h.name)
                        put("color", h.color); put("created_at", h.createdAt)
                    }
                }))
                put("people", JSONArray(people.map { p ->
                    JSONObject().apply {
                        put("id", p.id); put("name", p.name); put("created_at", p.createdAt)
                        p.deletedAt?.let { put("deleted_at", it) }
                    }
                }))
                put("chore_templates", JSONArray(choreTemplates.map { t ->
                    JSONObject().apply {
                        put("id", t.id); put("name", t.name); put("category", t.category)
                        put("recurrence", t.recurrence); put("created_at", t.createdAt)
                    }
                }))
            }.toString()

            val psk = SyncCrypto.pskFromSecret(pairing.secret)
            val deviceName = deviceLabel()
            val syncStartedAt = nowMillis()

            suspendCancellableCoroutine { cont ->
                val request = Request.Builder().url("ws://$host:$port").build()
                val settled = AtomicBoolean(false)

                // ── Per-connection session state ──────────────────────────
                // OkHttp delivers every listener callback on one reader thread,
                // so these are only ever touched serially. The ephemeral key is
                // generated HERE, per connection: reusing one across a re-dial
                // would restart the record counters at 0 under a key that had
                // already sealed records, which is the GCM nonce-reuse
                // catastrophe (spec §2.5, I-2).
                var ephemeral: SyncCrypto.EphemeralKey? = SyncCrypto.generateEphemeral()
                var helloBytes: ByteArray? = null
                var hs1Bytes: ByteArray? = null
                var keyC2S: ByteArray? = null
                var keyS2C: ByteArray? = null
                var established = false
                var counterOut = 0L
                var counterIn = 0L
                // A pre-channel plaintext error is ADVISORY ONLY: it is
                // remembered so the close that follows can be described
                // accurately, and it never acts on its own.
                var advisory: SyncResult? = null

                fun finish(socket: WebSocket?, result: SyncResult) {
                    if (settled.compareAndSet(false, true)) {
                        // Drop every key this connection derived.
                        ephemeral = null
                        keyC2S = null
                        keyS2C = null
                        established = false
                        try {
                            socket?.close(1000, "Sync complete")
                        } catch (_: Exception) {
                        }
                        cont.resume(result)
                    }
                }

                fun finishOk(socket: WebSocket?, message: String) =
                    finish(socket, SyncResult(message, succeeded = true))

                fun finishFail(socket: WebSocket?, message: String) =
                    finish(socket, SyncResult(message, succeeded = false))

                /** Recoverable: the user is asked to re-pair; the key is kept. */
                fun finishRepair(socket: WebSocket?, message: String) =
                    finish(socket, SyncResult(message, succeeded = false, needsRepair = true))

                /** The ONLY data-plane emitter: a GCM record in a BINARY frame. */
                fun sendEncrypted(webSocket: WebSocket, json: String) {
                    val key = keyC2S ?: throw IllegalStateException("no channel")
                    val frame = SyncCrypto.sealRecord(
                        key,
                        counterOut,
                        SyncCrypto.DIR_C2S,
                        json.toByteArray(Charsets.UTF_8)
                    )
                    counterOut++
                    webSocket.send(frame.toByteString())
                }

                fun handleHello(webSocket: WebSocket, raw: ByteArray, msg: JSONObject) {
                    if (helloBytes != null) {
                        webSocket.close(1002, "duplicate hello")
                        return finishFail(webSocket, MSG_PROTOCOL_VIOLATION)
                    }
                    val cryptoList = msg.optJSONArray("crypto")
                    val speaksX1 = (0 until (cryptoList?.length() ?: 0))
                        .any { cryptoList?.optString(it) == SyncCrypto.CRYPTO }
                    if (msg.optInt("version", 0) != SyncCrypto.PROTOCOL_VERSION || !speaksX1) {
                        // A v3 desktop, or one that stripped `crypto`. Nothing at
                        // all has been sent yet and nothing will be: no hs1, no
                        // health data, no key id.
                        webSocket.close(1000, "update required")
                        return finishFail(webSocket, MSG_UPDATE_DESKTOP)
                    }

                    val ephemeralKey = ephemeral
                        ?: return finishFail(webSocket, MSG_GENERIC_FAILURE)
                    val nonceC = ByteArray(NONCE_BYTES).also { SecureRandom().nextBytes(it) }
                    val hs1 = JSONObject().apply {
                        put("type", "hs1")
                        put("proto", SyncCrypto.CRYPTO)
                        put("keyId", pairing.keyId)
                        put("deviceId", deviceId)
                        put("pub_c", ephemeralKey.publicSpkiB64)
                        put("nonce_c", Base64.getEncoder().encodeToString(nonceC))
                        // Additive: the desktop labels its paired-devices row with it.
                        put("deviceName", deviceName)
                    }.toString()

                    helloBytes = raw
                    // The transcript hashes the bytes that actually go on the
                    // wire, so hs1Bytes is taken from the string being sent.
                    hs1Bytes = hs1.toByteArray(Charsets.UTF_8)
                    webSocket.send(hs1)
                }

                fun handleHs2(webSocket: WebSocket, msg: JSONObject) {
                    val hello = helloBytes
                    val hs1 = hs1Bytes
                    val ephemeralKey = ephemeral
                    if (hello == null || hs1 == null || ephemeralKey == null || established) {
                        webSocket.close(1002, "unexpected hs2")
                        return finishFail(webSocket, MSG_PROTOCOL_VIOLATION)
                    }
                    val pubS = msg.optString("pub_s")
                    val macS = msg.optString("mac_s")
                    if (pubS.isEmpty() || macS.isEmpty()) {
                        webSocket.close(1002, "malformed hs2")
                        return finishFail(webSocket, MSG_PROTOCOL_VIOLATION)
                    }

                    // Throws on an off-curve / malformed / wrong-curve point.
                    val ss = SyncCrypto.ecdhSharedSecret(ephemeralKey.priv, pubS)
                    // th covers the exact wire bytes of hello and hs1 — so
                    // version, minVersion, crypto[], both nonces and pub_c are
                    // all bound — plus pub_s as base64 TEXT, never decoded DER.
                    val th = SyncCrypto.transcriptHash(
                        hello,
                        hs1,
                        pubS.toByteArray(Charsets.US_ASCII)
                    )
                    val keys = SyncCrypto.deriveSession(ss, psk, th)
                    val presented = runCatching { Base64.getDecoder().decode(macS) }
                        .getOrDefault(ByteArray(0))
                    val expected = SyncCrypto.macTag(keys.km, SyncCrypto.ROLE_SERVER, th)
                    if (!SyncCrypto.constantTimeEqual(presented, expected)) {
                        // Either the PC holds a different key for this pairing,
                        // or something on the LAN is impersonating it. Nothing
                        // sensitive left the phone (hs1 is an ephemeral public
                        // key and a nonce), and the device key is KEPT — the
                        // user decides whether to re-pair.
                        webSocket.close(1002, "server authentication failed")
                        return finishRepair(webSocket, MSG_PC_NOT_RECOGNISED)
                    }

                    // hs3 is the LAST text frame this connection ever sends.
                    webSocket.send(
                        JSONObject().apply {
                            put("type", "hs3")
                            put(
                                "mac_c",
                                Base64.getEncoder().encodeToString(
                                    SyncCrypto.macTag(keys.km, SyncCrypto.ROLE_CLIENT, th)
                                )
                            )
                        }.toString()
                    )
                    keyC2S = keys.kC2S
                    keyS2C = keys.kS2C
                    established = true
                }

                /**
                 * A plaintext error before the channel exists. Display-only:
                 * it is never allowed to delete the device key, because anyone
                 * on the LAN can forge one (spec §2.8, protocol-F3).
                 */
                fun handlePlaintextError(msg: JSONObject) {
                    advisory = when (msg.optString("code")) {
                        "update_required" -> SyncResult(MSG_UPDATE_DESKTOP, succeeded = false)
                        "locked_out" -> {
                            val retryMs = msg.optLong("retryAfterMs", 0L)
                            val minutes = ((retryMs + 59_999L) / 60_000L).toInt()
                            SyncResult(
                                if (minutes > 0) {
                                    "The PC paused pairing after repeated failures. Your pairing is " +
                                        "still saved — try again in about $minutes min."
                                } else {
                                    MSG_LOCKED_OUT_GENERIC
                                },
                                succeeded = false
                            )
                        }
                        // Unknown key id, wrong key bytes, or a device the PC
                        // forgot: one recoverable, user-gated state.
                        "repair_required", "unknown_device", "pairing_changed" ->
                            SyncResult(MSG_PC_NOT_RECOGNISED, succeeded = false, needsRepair = true)
                        else -> SyncResult(MSG_GENERIC_FAILURE, succeeded = false)
                    }
                }

                fun handleText(webSocket: WebSocket, text: String) {
                    if (established) {
                        // There is no plaintext channel once records start.
                        webSocket.close(1002, "text frame on an encrypted channel")
                        return finishFail(webSocket, MSG_PROTOCOL_VIOLATION)
                    }
                    val raw = text.toByteArray(Charsets.UTF_8)
                    // Size cap BEFORE any parsing (handshake-junk DoS, impl-9).
                    if (raw.size > SyncCrypto.MAX_HANDSHAKE_FRAME_BYTES) {
                        webSocket.close(1009, "handshake frame too large")
                        return finishFail(webSocket, MSG_PROTOCOL_VIOLATION)
                    }
                    val msg = JSONObject(text)
                    when (msg.optString("type")) {
                        "hello" -> handleHello(webSocket, raw, msg)
                        "hs2" -> handleHs2(webSocket, msg)
                        "error" -> handlePlaintextError(msg)
                        // A v3 desktop's `auth_required`, or anything else: the
                        // phone never replies in plaintext to an unknown frame.
                        else -> {
                            webSocket.close(1002, "unexpected handshake frame")
                            finishFail(webSocket, MSG_UPDATE_DESKTOP)
                        }
                    }
                }

                fun applyFullSyncResponse(webSocket: WebSocket, msg: JSONObject) {
                    val theirEntries = msg.optJSONArray("entries") ?: JSONArray()
                    var inserted = 0
                    for (i in 0 until theirEntries.length()) {
                        val e = theirEntries.getJSONObject(i)
                        val id = e.getString("id")
                        val existing = entryDao.getByIdSync(id)
                        if (existing == null) {
                            entryDao.insertSync(
                                EntryEntity(
                                    id = id,
                                    category = e.getString("category"),
                                    timestamp = e.getLong("timestamp"),
                                    date = e.getString("date"),
                                    data = e.getString("data"),
                                    version = e.optInt("version", 1),
                                    modifiedAt = e.getLong("modified_at"),
                                    synced = 1
                                )
                            )
                            inserted++
                        } else if (e.getLong("modified_at") > existing.modifiedAt) {
                            // LWW is whole-row — pinning `date` to the first-seen value silently broke any feature that legitimately re-dates an entry.
                            entryDao.updateSync(
                                existing.copy(
                                    date = e.getString("date"),
                                    data = e.getString("data"),
                                    version = e.optInt("version", 1),
                                    modifiedAt = e.getLong("modified_at"),
                                    synced = 1
                                )
                            )
                        }
                    }

                    // Auxiliary tables travelled phone -> desktop only: the
                    // desktop sends these back, but they used to be dropped on
                    // the floor here, so a freshly installed phone showed
                    // hobby/chore ENTRIES with an empty hobby and template list.
                    ingestAux(msg)

                    val received = msg.optJSONObject("received")
                    val theyInserted = received?.optInt("inserted") ?: 0
                    val remaining = (pending - outgoing.size).coerceAtLeast(0)

                    finishOk(
                        webSocket,
                        buildString {
                            append("Synced: sent ${outgoing.size}")
                            append(", received $inserted new")
                            append(", PC got +$theyInserted")
                            if (remaining > 0) append(" · $remaining left, sync again")
                        }
                    )
                }

                fun handleRecord(webSocket: WebSocket, frame: ByteArray) {
                    val key = keyS2C
                    if (!established || key == null) {
                        webSocket.close(1002, "binary frame before the handshake")
                        return finishFail(webSocket, MSG_PROTOCOL_VIOLATION)
                    }
                    val plaintext = try {
                        // One-shot: the counter is checked first and the
                        // plaintext only exists after the tag verifies.
                        SyncCrypto.openRecord(key, counterIn, SyncCrypto.DIR_S2C, frame)
                    } catch (e: Exception) {
                        // No reply — any reply here would have to be plaintext.
                        webSocket.close(1008, "record rejected")
                        return finishFail(webSocket, MSG_RECORD_REJECTED)
                    }
                    counterIn++

                    val msg = JSONObject(String(plaintext, Charsets.UTF_8))
                    when (msg.optString("type")) {
                        "auth_ok" -> sendEncrypted(webSocket, pushData)
                        "full_sync_response" -> applyFullSyncResponse(webSocket, msg)
                        "error" -> finishFail(
                            webSocket,
                            msg.optString("message").ifBlank { MSG_GENERIC_FAILURE }
                        )
                        else -> Log.w(TAG, "ignoring unknown record type")
                    }
                }

                /**
                 * Every close code the desktop emits gets its own actionable
                 * phone string — none of them is the old generic "Connection
                 * closed". A plaintext advisory that arrived just before the
                 * close is more specific, so it wins.
                 */
                fun handleClose(webSocket: WebSocket?, code: Int) {
                    advisory?.let { return finish(webSocket, it) }
                    when (code) {
                        SyncCrypto.CLOSE_PAIRING_CHANGED ->
                            finishRepair(webSocket, MSG_PAIRING_REMOVED)
                        SyncCrypto.CLOSE_LOCKED_OUT ->
                            finishFail(webSocket, MSG_LOCKED_OUT_GENERIC)
                        SyncCrypto.CLOSE_TOO_MANY_CONNECTIONS ->
                            finishFail(webSocket, MSG_TOO_MANY_CONNECTIONS)
                        SyncCrypto.CLOSE_UPDATE_REQUIRED ->
                            finishFail(webSocket, MSG_UPDATE_DESKTOP)
                        SyncCrypto.CLOSE_REPAIR_REQUIRED ->
                            finishRepair(webSocket, MSG_PC_NOT_RECOGNISED)
                        1000 -> finishFail(webSocket, MSG_CLOSED_EARLY)
                        else -> finishFail(webSocket, MSG_GENERIC_FAILURE)
                    }
                }

                val socket = client.newWebSocket(request, object : WebSocketListener() {
                    /**
                     * Deliberately empty: the phone speaks second. Firing before
                     * `hello` is what let a v3 (or hostile) peer receive a frame
                     * from a v4 phone at all.
                     */
                    override fun onOpen(webSocket: WebSocket, response: Response) {}

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        try {
                            handleText(webSocket, text)
                        } catch (e: Exception) {
                            // Type only, never the message or the frame: every
                            // handshake failure is computed over key material.
                            Log.e(TAG, "handshake frame rejected (${e.javaClass.simpleName})")
                            try {
                                webSocket.close(1002, "handshake failed")
                            } catch (_: Exception) {
                            }
                            finishFail(webSocket, MSG_PROTOCOL_VIOLATION)
                        }
                    }

                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                        try {
                            handleRecord(webSocket, bytes.toByteArray())
                        } catch (e: Exception) {
                            Log.e(TAG, "record rejected (${e.javaClass.simpleName})")
                            try {
                                webSocket.close(1008, "record rejected")
                            } catch (_: Exception) {
                            }
                            finishFail(webSocket, MSG_RECORD_REJECTED)
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        finishFail(null, "Could not reach the PC: ${t.message ?: "connection failed"}")
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        // The peer closed first; finish() completes the
                        // handshake so the socket does not linger half-closed.
                        handleClose(webSocket, code)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        handleClose(null, code)
                    }
                })

                // Leaving the screen used to cancel the coroutine while the socket,
                // its reader thread and this listener stayed alive indefinitely.
                cont.invokeOnCancellation {
                    try {
                        socket.cancel()
                    } catch (_: Exception) {
                    }
                }
            }.also { result ->
                if (result.succeeded) {
                    settingsDao.setSetting(
                        SettingEntity(key = LAST_SYNC_KEY, value = syncStartedAt.toString())
                    )
                }
            }
        }
}

sealed class SyncStatus {
    data object Idle : SyncStatus()
    data object Discovering : SyncStatus()
    data object Connecting : SyncStatus()
    data object Syncing : SyncStatus()
    /** No pairing stored yet — the user must pair with the PC first. */
    data object NeedsPairing : SyncStatus()
    /**
     * The PC would not authenticate this phone. Recoverable and user-gated: the
     * stored device key is kept until the user completes a fresh pairing.
     */
    data class NeedsRepair(val message: String) : SyncStatus()
    data class Done(val message: String) : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}
