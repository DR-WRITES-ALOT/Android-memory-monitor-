package com.example.memorymonitor.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.memorymonitor.MemoryViewModel

@Composable
fun MonitorApp(vm: MemoryViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Dashboard") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Processes") })
        }
        when (tab) {
            0 -> DashboardScreen(state)
            else -> ProcessListScreen(state, onRefreshPermission = vm::refreshPermission)
        }
    }
}
