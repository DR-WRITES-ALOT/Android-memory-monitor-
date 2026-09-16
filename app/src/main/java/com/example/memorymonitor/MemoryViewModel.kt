package com.example.memorymonitor

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.memorymonitor.data.MemoryDataSource
import com.example.memorymonitor.data.ProcMemInfoReader
import com.example.memorymonitor.data.ProcessDataSource
import com.example.memorymonitor.data.UsagePermission
import com.example.memorymonitor.domain.MemoryProcessor
import com.example.memorymonitor.domain.MemorySnapshot
import com.example.memorymonitor.domain.ProcessEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

const val TAG = "MemoryMonitor"
const val POLL_INTERVAL_MS = 2_000L
const val HISTORY_SIZE = 60          // 60 samples x 2 s = last 2 minutes

data class UiState(
    val latest: MemorySnapshot? = null,
    /** Used-% history, oldest first, for the chart. */
    val history: List<Float> = emptyList(),
    val processes: List<ProcessEntry> = emptyList(),
    val usageAccessGranted: Boolean = false,
    val lastTrimLevel: String? = null,
    val error: String? = null,
)

/**
 * Polling in user space. A coroutine on Dispatchers.IO loops:
 *   sample()  ->  delay(2000)  ->  sample() ...
 * delay() suspends the coroutine (no thread blocked, no busy-wait); the underlying
 * thread pool is free while we wait. The Job is tied to viewModelScope, so rotation
 * survives it and finishing the Activity cancels it.
 */
class MemoryViewModel(app: Application) : AndroidViewModel(app) {

    private val memory = MemoryDataSource(app)
    private val processes = ProcessDataSource(app)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var pollJob: Job? = null

    fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                sample()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    fun refreshPermission() {
        _state.update { it.copy(usageAccessGranted = UsagePermission.isGranted(getApplication())) }
    }

    fun onTrimMemory(level: Int) {
        val name = when (level) {
            5 -> "RUNNING_MODERATE"
            10 -> "RUNNING_LOW"
            15 -> "RUNNING_CRITICAL"
            20 -> "UI_HIDDEN"
            40 -> "BACKGROUND"
            60 -> "MODERATE"
            80 -> "COMPLETE"
            else -> "LEVEL_$level"
        }
        Log.w(TAG, "onTrimMemory($name) - system asks us to release memory")
        _state.update { it.copy(lastTrimLevel = name) }
    }

    private fun sample() {
        try {
            val sys = memory.readSystemMemory()          // Binder -> system_server
            val proc = ProcMemInfoReader.readMemInfo()   // open/read/close on /proc/meminfo
            val self = memory.readSelfMemory()

            val snapshot = MemorySnapshot(
                timestampMs = System.currentTimeMillis(),
                system = sys,
                proc = proc,
                self = self,
                usedPercent = MemoryProcessor.usedPercent(sys.totalBytes, sys.availableBytes),
                freePercent = MemoryProcessor.freePercent(sys.totalBytes, sys.availableBytes),
                lowMemoryFlag = MemoryProcessor.isLowMemory(sys.totalBytes, sys.availableBytes),
            )

            val granted = UsagePermission.isGranted(getApplication())
            val procList = if (granted) processes.readProcesses() else emptyList()

            Log.d(TAG, "used=%.1f%% avail=%s low=%b".format(
                snapshot.usedPercent, MemoryProcessor.formatMb(sys.availableBytes), snapshot.lowMemoryFlag))

            _state.update { s ->
                s.copy(
                    latest = snapshot,
                    history = (s.history + snapshot.usedPercent).takeLast(HISTORY_SIZE),
                    processes = procList,
                    usageAccessGranted = granted,
                    error = null,
                )
            }
        } catch (t: Throwable) {
            Log.e(TAG, "sample failed", t)
            _state.update { it.copy(error = t.toString()) }
        }
    }
}
