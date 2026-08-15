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

    /**
     * Non-null when the PC refused to authenticate this phone. The stored
     * device key is deliberately still there — re-pairing is the user's call,
     * never an automatic reaction to a frame anyone on the LAN can forge
     * (spec §2.8 ops-F1).
     */
    val repairPrompt = syncManager.repairPrompt

    private val _manualIp = MutableStateFlow("")
    val manualIp = _manualIp.asStateFlow()

    /**
     * The single 33-glyph code shown on the PC. It carries the key id AND the
     * 128-bit secret, so there is exactly one thing to type. Never rendered
     * anywhere else, and never read back out of settings.
     */
    private val _pairingCode = MutableStateFlow("")
    val pairingCode = _pairingCode.asStateFlow()

    private val _isPaired = MutableStateFlow(false)
    val isPaired = _isPaired.asStateFlow()

    /** Set while the pairing form is open — after a first pair, or a re-pair. */
    private val _pairingFormOpen = MutableStateFlow(false)
    val pairingFormOpen = _pairingFormOpen.asStateFlow()

    /** Why the last "Pair" tap was refused, if it was. */
    private val _pairingError = MutableStateFlow<String?>(null)
    val pairingError = _pairingError.asStateFlow()

    init {
        viewModelScope.launch {
            val paired = syncManager.isPaired()
            _isPaired.value = paired
            // Nothing about an existing pairing is echoed back into the form:
            // the secret is write-only from the UI's point of view.
            _pairingFormOpen.value = !paired
        }
    }

    fun setManualIp(ip: String) {
        _manualIp.value = ip
    }

    fun setPairingCode(code: String) {
        // Uppercase as the user types so the field matches what the desktop
        // shows; dashes are kept because the desktop groups the code with them
        // (33 glyphs + 6 separators, with headroom for stray spaces).
        _pairingCode.value = code.uppercase().take(SyncCrypto.PAIRING_CODE_LENGTH * 2)
        _pairingError.value = null
    }

    /** Open the form. Nothing is discarded here — the old key stays until a new one lands. */
    fun beginPairing() {
        _pairingFormOpen.value = true
        _pairingError.value = null
    }

    fun cancelPairing() {
        _pairingFormOpen.value = false
        _pairingCode.value = ""
        _pairingError.value = null
    }

    fun dismissRepairPrompt() {
        syncManager.dismissRepairPrompt()
    }

    /**
     * Store the single code shown in the desktop app's sidebar. This is the
     * ONLY thing that replaces an existing device key, and it also resets the
     * sync cursor so the next run fully reconciles (spec §2.8 ops-F5).
     */
    fun savePairing() {
        val code = _pairingCode.value.trim()
        if (code.isEmpty()) {
            _pairingError.value = "Enter the pairing code shown on the PC."
            return
        }
        viewModelScope.launch {
            try {
                syncManager.savePairing(code)
                _isPaired.value = true
                _pairingFormOpen.value = false
                _pairingCode.value = ""
                _pairingError.value = null
            } catch (e: IllegalArgumentException) {
                // decodePairingCode's messages never quote the code itself.
                _pairingError.value = e.message ?: "That pairing code was not accepted."
            }
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
