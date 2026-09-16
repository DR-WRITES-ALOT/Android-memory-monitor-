package com.example.memorymonitor.data

import android.app.ActivityManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import com.example.memorymonitor.domain.MemoryProcessor
import com.example.memorymonitor.domain.ProcessEntry

/**
 * Data Collection Module - per-app memory.
 *
 * Why not just list /proc/<pid>?  Since Android 8.0 the platform mounts /proc with
 * hidepid=2, and ActivityManager.getRunningAppProcesses() returns ONLY the caller's
 * own process. Each app runs as its own Linux UID in its own sandbox; it cannot see
 * its neighbours. That is process isolation working as designed.
 *
 * So we ask two system services instead:
 *   1. UsageStatsManager  - which packages were used recently (needs Usage Access)
 *   2. ActivityManager    - getProcessMemoryInfo(pids) for PSS of any pids we can find
 *
 * Both are Binder calls into system_server, which runs as the privileged "system" UID
 * and *can* read every /proc/<pid>/smaps.
 */
class ProcessDataSource(private val context: Context) {

    private val usageStats = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    private val pm: PackageManager = context.packageManager

    fun readProcesses(): List<ProcessEntry> {
        val now = System.currentTimeMillis()
        val dayAgo = now - 24L * 60 * 60 * 1000

        val recent = usageStats
            .queryUsageStats(UsageStatsManager.INTERVAL_DAILY, dayAgo, now)
            .filter { it.totalTimeInForeground > 0 || it.lastTimeUsed > dayAgo }
            .groupBy { it.packageName }
            .mapValues { (_, list) -> list.maxOf { it.lastTimeUsed } }

        // Map package -> pid for whatever processes are visible to us. On modern
        // Android this is usually just ourselves; on older/rooted devices it is more.
        val pidByPackage = HashMap<String, Int>()
        activityManager.runningAppProcesses?.forEach { p ->
            p.pkgList?.forEach { pkg -> pidByPackage[pkg] = p.pid }
        }

        // Running services can expose pids of other apps' service processes on older APIs.
        @Suppress("DEPRECATION")
        activityManager.getRunningServices(200)?.forEach { s ->
            if (s.pid > 0) pidByPackage.putIfAbsent(s.service.packageName, s.pid)
        }

        val pids = pidByPackage.values.distinct().toIntArray()
        val pssByPid = HashMap<Int, Long>()
        if (pids.isNotEmpty()) {
            val infos = activityManager.getProcessMemoryInfo(pids)
            pids.forEachIndexed { i, pid -> pssByPid[pid] = infos[i].totalPss * 1024L }
        }

        val entries = recent.map { (pkg, lastUsed) ->
            val pid = pidByPackage[pkg]
            ProcessEntry(
                label = labelOf(pkg),
                packageName = pkg,
                pssBytes = pid?.let { pssByPid[it] } ?: 0L,
                lastUsedEpochMs = lastUsed,
            )
        }
        return MemoryProcessor.rank(entries)
    }

    private fun labelOf(pkg: String): String = runCatching {
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg.substringAfterLast('.'))
}
