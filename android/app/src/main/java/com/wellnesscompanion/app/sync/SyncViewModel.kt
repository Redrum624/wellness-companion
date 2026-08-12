package com.wellnesscompanion.app.sync

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.wellnesscompanion.app.data.local.WellnessDatabase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SyncViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: WellnessDatabase,
    private val gson: Gson
) : ViewModel() {

    private val syncManager = SyncManager(context, db, gson)

    val status = syncManager.status

    private val _manualIp = MutableStateFlow("")
    val manualIp = _manualIp.asStateFlow()

    fun setManualIp(ip: String) { _manualIp.value = ip }

    fun syncAuto() {
        viewModelScope.launch {
            syncManager.discoverAndSync()
        }
    }

    fun syncManual() {
        val ip = _manualIp.value.trim()
        if (ip.isNotEmpty()) {
            viewModelScope.launch {
                val parts = ip.split(":")
                val host = parts[0]
                val port = if (parts.size > 1) parts[1].toIntOrNull() ?: 9847 else 9847
                syncManager.syncWith(host, port)
            }
        }
    }
}
