package com.example.memorymonitor.data

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import android.os.Process
import com.example.memorymonitor.domain.SelfProcessMemory
import com.example.memorymonitor.domain.SystemMemory

/**
 * Data Collection Module - FRAMEWORK path.
 *
 * ActivityManager is a client-side proxy. Calling getMemoryInfo() does:
 *   1. pack arguments into a Parcel
 *   2. libbinder issues  ioctl(fd, BINDER_WRITE_READ, ...)   <- the real system call
 *   3. kernel Binder driver copies the transaction into system_server
 *   4. ActivityManagerService fills MemoryInfo (it reads /proc/meminfo itself)
 *   5. reply comes back through another ioctl()
 */
class MemoryDataSource(context: Context) {

    private val activityManager =
        context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

    fun readSystemMemory(): SystemMemory {
        val info = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(info)          // Binder IPC into system_server
        return SystemMemory(
            totalBytes = info.totalMem,
            availableBytes = info.availMem,
            thresholdBytes = info.threshold,
            lowMemory = info.lowMemory,
        )
    }

    fun readSelfMemory(): SelfProcessMemory {
        val pid = Process.myPid()

        // Another Binder call; system_server reads /proc/<pid>/smaps for us and returns PSS.
        val pss = activityManager.getProcessMemoryInfo(intArrayOf(pid))
            .firstOrNull()?.totalPss?.toLong()?.times(1024L) ?: 0L

        // Managed (ART) heap - what the garbage collector controls.
        val rt = Runtime.getRuntime()
        val artUsed = rt.totalMemory() - rt.freeMemory()
        val artMax = rt.maxMemory()

        // Native heap - malloc() allocations made by C/C++ code in our process.
        val native = Debug.getNativeHeapAllocatedSize()

        return SelfProcessMemory(
            pid = pid,
            pssBytes = pss,
            rssBytes = ProcMemInfoReader.readSelfRss(),   // direct /proc read, no Binder
            artHeapUsedBytes = artUsed,
            artHeapMaxBytes = artMax,
            nativeHeapBytes = native,
        )
    }
}
