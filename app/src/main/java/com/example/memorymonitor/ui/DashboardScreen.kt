package com.example.memorymonitor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.memorymonitor.HISTORY_SIZE
import com.example.memorymonitor.POLL_INTERVAL_MS
import com.example.memorymonitor.UiState
import com.example.memorymonitor.domain.MemoryProcessor.formatMb
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(state: UiState) {
    val s = state.latest
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        if (s == null) {
            Text("Sampling..."); return@Column
        }

        Header("RAM  (ActivityManager.getMemoryInfo  <-  Binder ioctl)")
        Mono("Total      ${formatMb(s.system.totalBytes)}")
        Mono("Available  ${formatMb(s.system.availableBytes)}")
        Mono("Used       ${"%.1f".format(s.usedPercent)} %")
        Mono("Threshold  ${formatMb(s.system.thresholdBytes)}   lowMemory=${s.system.lowMemory}")
        if (s.lowMemoryFlag) {
            Text(
                "!! LOW MEMORY  (free < 15 %)",
                color = Color.Red, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        Gap()
        Header("RAM  (/proc/meminfo  <-  open/read/close)")
        val p = s.proc
        if (p == null) {
            Mono("could not read /proc/meminfo")
        } else {
            Mono("MemTotal      ${formatMb(p.memTotal)}")
            Mono("MemFree       ${formatMb(p.memFree)}")
            Mono("MemAvailable  ${formatMb(p.memAvailable)}")
            Mono("Buffers       ${formatMb(p.buffers)}")
            Mono("Cached        ${formatMb(p.cached)}")
            Mono("Swap          ${formatMb(p.swapFree)} free / ${formatMb(p.swapTotal)}")
        }

        Gap()
        Header("This process  (pid ${s.self.pid})")
        Mono("PSS          ${formatMb(s.self.pssBytes)}   (getProcessMemoryInfo)")
        Mono("RSS          ${formatMb(s.self.rssBytes)}   (/proc/self/status VmRSS)")
        Mono("ART heap     ${formatMb(s.self.artHeapUsedBytes)} / ${formatMb(s.self.artHeapMaxBytes)}")
        Mono("Native heap  ${formatMb(s.self.nativeHeapBytes)}")
        Small("RSS counts shared pages fully; PSS splits shared pages between the processes using them.")
        state.lastTrimLevel?.let { Mono("last onTrimMemory: $it") }

        Gap()
        Header("Used %  (last ${HISTORY_SIZE * POLL_INTERVAL_MS / 1000} s)")
        SimpleLineChart(state.history, HISTORY_SIZE)
        Small("guide lines at 50 % and 85 % used")

        Gap()
        val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(s.timestampMs))
        Small("last sample $time   interval ${POLL_INTERVAL_MS / 1000} s   samples ${state.history.size}")
        state.error?.let { Text(it, color = Color.Red) }
    }
}

@Composable fun Header(text: String) =
    Text(text, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)

@Composable fun Mono(text: String) =
    Text(text, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)

@Composable fun Small(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = Color.Gray)

@Composable fun Gap() = Spacer(Modifier.height(16.dp))
