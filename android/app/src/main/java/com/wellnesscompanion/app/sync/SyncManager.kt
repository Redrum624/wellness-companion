package com.wellnesscompanion.app.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import com.google.gson.Gson
import com.wellnesscompanion.app.data.local.WellnessDatabase
import com.wellnesscompanion.app.util.nowMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

class SyncManager(
    private val context: Context,
    private val db: WellnessDatabase,
    private val gson: Gson
) {
    companion object {
        private const val TAG = "SyncManager"
        private const val SERVICE_TYPE = "_http._tcp."
        private const val SERVICE_NAME = "wellness-companion-sync"
        private const val SYNC_PORT = 9847
    }

    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status = _status.asStateFlow()

    private val client = OkHttpClient.Builder()
        .readTimeout(30, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    /** Try to discover the Windows app via mDNS, then sync */
    suspend fun discoverAndSync() {
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

    /** Sync with a known host:port (manual fallback) */
    suspend fun syncWith(host: String, port: Int) {
        _status.value = SyncStatus.Connecting
        try {
            val result = performFullSync(host, port)
            _status.value = SyncStatus.Done(result)
        } catch (e: Exception) {
            Log.e(TAG, "Sync error", e)
            _status.value = SyncStatus.Error(e.message ?: "Sync failed")
        }
    }

    private suspend fun discoverHost(): Pair<String, Int>? = withContext(Dispatchers.IO) {
        suspendCancellableCoroutine { cont ->
            val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
            var resolved = false
            var discoveryListener: NsdManager.DiscoveryListener? = null

            discoveryListener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(serviceType: String) {
                    Log.d(TAG, "NSD discovery started")
                }

                override fun onServiceFound(info: NsdServiceInfo) {
                    if (info.serviceName.contains(SERVICE_NAME) || info.serviceName.contains("wellness-companion")) {
                        nsdManager.resolveService(info, object : NsdManager.ResolveListener {
                            override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                                Log.e(TAG, "NSD resolve failed: $errorCode")
                            }

                            override fun onServiceResolved(info: NsdServiceInfo) {
                                if (!resolved) {
                                    resolved = true
                                    try { nsdManager.stopServiceDiscovery(discoveryListener) } catch (_: Exception) {}
                                    cont.resume(Pair(info.host.hostAddress ?: "127.0.0.1", info.port))
                                }
                            }
                        })
                    }
                }

                override fun onServiceLost(info: NsdServiceInfo) {}
                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    if (!resolved) cont.resume(null)
                }
                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            }

            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)

            // Timeout after 5 seconds
            cont.invokeOnCancellation {
                try { nsdManager.stopServiceDiscovery(discoveryListener) } catch (_: Exception) {}
            }

            // Manual timeout
            Thread {
                Thread.sleep(5000)
                if (!resolved) {
                    try { nsdManager.stopServiceDiscovery(discoveryListener) } catch (_: Exception) {}
                    cont.resume(null)
                }
            }.start()
        }
    }

    private suspend fun performFullSync(host: String, port: Int): String = withContext(Dispatchers.IO) {
        _status.value = SyncStatus.Syncing

        // Gather our data
        val entryDao = db.entryDao()
        val allEntries = entryDao.getAllEntriesSync()
        val hobbies = db.hobbyDao().getAllSync()
        val people = db.personDao().getAllSync()
        val choreTemplates = db.choreTemplateDao().getAllSync()

        val pushData = JSONObject().apply {
            put("type", "full_sync")
            put("entries", JSONArray(allEntries.map { e ->
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
                    put("id", h.id); put("name", h.name); put("color", h.color); put("created_at", h.createdAt)
                }
            }))
            put("people", JSONArray(people.map { p ->
                JSONObject().apply {
                    put("id", p.id); put("name", p.name); put("created_at", p.createdAt)
                }
            }))
            put("chore_templates", JSONArray(choreTemplates.map { t ->
                JSONObject().apply {
                    put("id", t.id); put("name", t.name); put("category", t.category); put("recurrence", t.recurrence); put("created_at", t.createdAt)
                }
            }))
        }

        // Connect via WebSocket
        suspendCancellableCoroutine { cont ->
            val request = Request.Builder().url("ws://$host:$port").build()
            var done = false

            client.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(pushData.toString())
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    try {
                        val msg = JSONObject(text)
                        when (msg.optString("type")) {
                            "hello" -> { /* Server greeted us, we already sent data */ }
                            "full_sync_response" -> {
                                // Process their entries
                                val theirEntries = msg.optJSONArray("entries") ?: JSONArray()
                                var inserted = 0
                                for (i in 0 until theirEntries.length()) {
                                    val e = theirEntries.getJSONObject(i)
                                    val id = e.getString("id")
                                    val existing = entryDao.getByIdSync(id)
                                    if (existing == null) {
                                        entryDao.insertSync(
                                            com.wellnesscompanion.app.data.local.entity.EntryEntity(
                                                id = id,
                                                category = e.getString("category"),
                                                timestamp = e.getLong("timestamp"),
                                                date = e.getString("date"),
                                                data = e.getString("data"),
                                                version = e.getInt("version"),
                                                modifiedAt = e.getLong("modified_at"),
                                                synced = 1
                                            )
                                        )
                                        inserted++
                                    } else if (e.getLong("modified_at") > existing.modifiedAt) {
                                        entryDao.updateSync(existing.copy(
                                            data = e.getString("data"),
                                            version = e.getInt("version"),
                                            modifiedAt = e.getLong("modified_at"),
                                            synced = 1
                                        ))
                                    }
                                }

                                val received = msg.optJSONObject("received")
                                val theyInserted = received?.optInt("inserted") ?: 0
                                val theyUpdated = received?.optInt("updated") ?: 0

                                webSocket.close(1000, "Sync complete")
                                if (!done) {
                                    done = true
                                    cont.resume("Synced: sent ${allEntries.size}, received $inserted new, PC got +$theyInserted")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing sync response", e)
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    if (!done) { done = true; cont.resume("Error: ${t.message}") }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    if (!done) { done = true; cont.resume("Connection closed") }
                }
            })
        }
    }
}

sealed class SyncStatus {
    data object Idle : SyncStatus()
    data object Discovering : SyncStatus()
    data object Connecting : SyncStatus()
    data object Syncing : SyncStatus()
    data class Done(val message: String) : SyncStatus()
    data class Error(val message: String) : SyncStatus()
}
