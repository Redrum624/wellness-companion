package com.wellnesscompanion.app.sync

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SyncViewModel @Inject constructor(
    // Injected as a @Singleton rather than constructed here: a per-ViewModel
    // SyncManager brought its own OkHttp thread pool and connection pool along
    // with it, and nothing ever released them.
    private val syncManager: SyncManager
) : ViewModel() {

    val status = syncManager.status

    private val _manualIp = MutableStateFlow("")
    val manualIp = _manualIp.asStateFlow()

    private val _pairingCode = MutableStateFlow("")
    val pairingCode = _pairingCode.asStateFlow()

    private val _isPaired = MutableStateFlow(false)
    val isPaired = _isPaired.asStateFlow()

    init {
        viewModelScope.launch {
            val stored = syncManager.getPairingToken()
            _isPaired.value = !stored.isNullOrBlank()
            _pairingCode.value = stored.orEmpty()
        }
    }

    fun setManualIp(ip: String) {
        _manualIp.value = ip
    }

    fun setPairingCode(code: String) {
        // Uppercase and strip separators as the user types so the field matches
        // what the desktop displays regardless of how they enter it.
        _pairingCode.value = code.uppercase().replace("-", "").take(8)
    }

    /** Store the code shown in the desktop app's sidebar. */
    fun savePairingCode() {
        val code = _pairingCode.value.trim()
        if (code.isEmpty()) return
        viewModelScope.launch {
            syncManager.setPairingToken(code)
            _isPaired.value = true
        }
    }

    fun syncAuto() {
        viewModelScope.launch { syncManager.discoverAndSync() }
    }

    fun syncManual() {
        val ip = _manualIp.value.trim()
        if (ip.isEmpty()) return
        viewModelScope.launch {
            val parts = ip.split(":")
            val host = parts[0]
            val port = if (parts.size > 1) parts[1].toIntOrNull() ?: 9847 else 9847
            syncManager.syncWith(host, port)
        }
    }
}
