package com.example.memorymonitor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.memorymonitor.UiState
import com.example.memorymonitor.data.UsagePermission
import com.example.memorymonitor.domain.MemoryProcessor.formatMb
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProcessListScreen(state: UiState, onRefreshPermission: () -> Unit) {
    val context = LocalContext.current

    if (!state.usageAccessGranted) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            Header("Usage access required")
            Small(
                "Since Android 8, /proc is mounted with hidepid=2 and getRunningAppProcesses() " +
                "returns only this app. Each app is its own Linux UID in its own sandbox and " +
                "cannot see other processes. To list other apps we ask system_server through " +
                "UsageStatsManager, which is gated behind the PACKAGE_USAGE_STATS app-op. " +
                "It cannot be granted by a dialog; flip the switch in Settings, then press Back."
            )
            Gap()
            Button(onClick = { context.startActivity(UsagePermission.settingsIntent()) }) {
                Text("Grant usage access")
            }
            Button(onClick = onRefreshPermission) { Text("Re-check") }
        }
        return
    }

    val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("App", Modifier.weight(1f), fontFamily = FontFamily.Monospace)
            Text("PSS", Modifier.weight(0.4f), fontFamily = FontFamily.Monospace)
            Text("Last used", Modifier.weight(0.4f), fontFamily = FontFamily.Monospace)
        }
        HorizontalDivider()
        LazyColumn(Modifier.weight(1f)) {
            items(state.processes, key = { it.packageName }) { p ->
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        Text(p.label, Modifier.weight(1f))
                        Text(if (p.pssBytes > 0) formatMb(p.pssBytes) else "-", Modifier.weight(0.4f),
                            fontFamily = FontFamily.Monospace)
                        Text(fmt.format(Date(p.lastUsedEpochMs)), Modifier.weight(0.4f),
                            fontFamily = FontFamily.Monospace)
                    }
                    Small(p.packageName)
                }
                HorizontalDivider()
            }
        }
        Small(
            "${state.processes.size} packages used in last 24 h (UsageStatsManager). " +
            "PSS comes from ActivityManager.getProcessMemoryInfo and is '-' for processes " +
            "whose pid is hidden from this app or that are not resident right now.",
        )
    }
}
