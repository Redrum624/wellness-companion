package com.wellnesscompanion.app.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.google.gson.Gson
import com.wellnesscompanion.app.data.local.WellnessDatabase
import com.wellnesscompanion.app.data.local.entity.EntryEntity
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
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Talks to the desktop app over the LAN.
 *
 * The desktop requires a pairing code before it will accept or return anything;
 * the code is shown in its sidebar and entered here once, then kept in settings.
 * Until [PAIRING_TOKEN_KEY] is set, sync fails closed with [SyncStatus.NeedsPairing]
 * rather than sending the health database to whatever answered the mDNS query.
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

        const val PAIRING_TOKEN_KEY = "sync.pairing_token"
        private const val LAST_SYNC_KEY = "sync.last_synced_at"

        private const val DISCOVERY_TIMEOUT_MS = 5_000L
        private const val SYNC_TIMEOUT_MS = 60_000L
        /** Cap per run so a long history is sent over several syncs, not one giant frame. */
        private const val MAX_ENTRIES_PER_SYNC = 2_000
    }

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status = _status.asStateFlow()

    suspend fun getPairingToken(): String? = db.settingsDao().getSetting(PAIRING_TOKEN_KEY)

    suspend fun setPairingToken(token: String) {
        db.settingsDao().setSetting(
            SettingEntity(key = PAIRING_TOKEN_KEY, value = token.trim().uppercase().replace("-", ""))
        )
    }

    /** Try to discover the Windows app via mDNS, then sync. */
    suspend fun discoverAndSync() {
        if (getPairingToken().isNullOrBlank()) {
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
        val token = getPairingToken()
        if (token.isNullOrBlank()) {
            _status.value = SyncStatus.NeedsPairing
            return
        }
        _status.value = SyncStatus.Connecting
        try {
            val result = withTimeoutOrNull(SYNC_TIMEOUT_MS) { performFullSync(host, port, token) }
            _status.value =
                if (result == null) SyncStatus.Error("Sync timed out") else SyncStatus.Done(result)
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

    private suspend fun performFullSync(host: String, port: Int, token: String): String =
        withContext(Dispatchers.IO) {
            _status.value = SyncStatus.Syncing

            val entryDao = db.entryDao()
            val settingsDao = db.settingsDao()
            val since = settingsDao.getSetting(LAST_SYNC_KEY)?.toLongOrNull() ?: 0L

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
                    }
                }))
                put("chore_templates", JSONArray(choreTemplates.map { t ->
                    JSONObject().apply {
                        put("id", t.id); put("name", t.name); put("category", t.category)
                        put("recurrence", t.recurrence); put("created_at", t.createdAt)
                    }
                }))
            }

            val authFrame = JSONObject().apply {
                put("type", "auth")
                put("token", token)
            }

            val syncStartedAt = nowMillis()

            suspendCancellableCoroutine { cont ->
                val request = Request.Builder().url("ws://$host:$port").build()
                val settled = AtomicBoolean(false)

                fun finish(socket: WebSocket?, message: String) {
                    if (settled.compareAndSet(false, true)) {
                        try {
                            socket?.close(1000, "Sync complete")
                        } catch (_: Exception) {
                        }
                        cont.resume(message)
                    }
                }

                val socket = client.newWebSocket(request, object : WebSocketListener() {
                    // Nothing is sent until the server accepts the pairing code.
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        webSocket.send(authFrame.toString())
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        try {
                            val msg = JSONObject(text)
                            when (msg.optString("type")) {
                                "hello" -> { /* wait for auth_ok before sending anything */ }

                                "auth_ok" -> webSocket.send(pushData.toString())

                                "auth_failed", "auth_required" ->
                                    finish(webSocket, "Pairing code rejected by the PC")

                                "full_sync_response" -> {
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
                                            entryDao.updateSync(
                                                existing.copy(
                                                    data = e.getString("data"),
                                                    version = e.optInt("version", 1),
                                                    modifiedAt = e.getLong("modified_at"),
                                                    synced = 1
                                                )
                                            )
                                        }
                                    }

                                    val received = msg.optJSONObject("received")
                                    val theyInserted = received?.optInt("inserted") ?: 0
                                    val remaining = (pending - outgoing.size).coerceAtLeast(0)

                                    val summary = buildString {
                                        append("Synced: sent ${outgoing.size}")
                                        append(", received $inserted new")
                                        append(", PC got +$theyInserted")
                                        if (remaining > 0) append(" · $remaining left, sync again")
                                    }
                                    finish(webSocket, summary)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing sync response", e)
                            finish(webSocket, "Error: ${e.message}")
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        finish(null, "Error: ${t.message}")
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        finish(null, if (code == 4002) "Too many failed pairing attempts" else "Connection closed")
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
                if (result.startsWith("Synced")) {
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
    /** No pairing code stored yet — the user must enter the one shown on the PC. */
    data object NeedsPairing : SyncStatus()
    data class Done(val message: String) : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}
