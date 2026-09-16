package com.example.memorymonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.example.memorymonitor.ui.MonitorApp

class MainActivity : ComponentActivity() {

    private val vm: MemoryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface {
                    MonitorApp(vm)
                }
            }
        }
    }

    // Poll only while visible: no point burning battery for a screen nobody sees.
    override fun onStart() {
        super.onStart()
        vm.refreshPermission()   // user may have just come back from Settings
        vm.startPolling()
    }

    override fun onStop() {
        super.onStop()
        vm.stopPolling()
    }

    /**
     * The framework calls this when the system is under memory pressure.
     * Try it from a terminal:
     *   adb shell am send-trim-memory com.example.memorymonitor RUNNING_CRITICAL
     */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        vm.onTrimMemory(level)
    }
}
