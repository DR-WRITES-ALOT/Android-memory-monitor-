package com.example.memorymonitor.domain

/**
 * System-wide RAM figures obtained through the FRAMEWORK path:
 * app -> ActivityManager.getMemoryInfo() -> Binder ioctl() -> system_server (ActivityManagerService).
 */
data class SystemMemory(
    val totalBytes: Long,
    val availableBytes: Long,
    /** Below this amount of free RAM the kernel's Low Memory Killer starts killing processes. */
    val thresholdBytes: Long,
    /** Set by the system when availableBytes < thresholdBytes. */
    val lowMemory: Boolean,
) {
    val usedBytes: Long get() = totalBytes - availableBytes
}

/**
 * System-wide RAM figures obtained through the DIRECT SYSCALL path:
 * app -> java.io.File -> Bionic libc open()/read()/close() on /proc/meminfo (procfs).
 * All values in bytes. Fields are nullable because /proc/meminfo keys vary by kernel version.
 */
data class ProcMemInfo(
    val memTotal: Long?,
    val memFree: Long?,
    val memAvailable: Long?,
    val buffers: Long?,
    val cached: Long?,
    val swapTotal: Long?,
    val swapFree: Long?,
)

/**
 * Memory of OUR OWN process, seen from three angles:
 *  - PSS / RSS from the kernel (via /proc/self/status and Debug.MemoryInfo)
 *  - ART managed heap (Runtime) - what the garbage collector manages
 *  - Native heap (malloc) - C/C++ allocations by Bionic/libs
 */
data class SelfProcessMemory(
    val pid: Int,
    val pssBytes: Long,
    val rssBytes: Long?,
    val artHeapUsedBytes: Long,
    val artHeapMaxBytes: Long,
    val nativeHeapBytes: Long,
)

/** One row in the process list. PSS may be 0 when the app is not currently resident in RAM. */
data class ProcessEntry(
    val label: String,
    val packageName: String,
    val pssBytes: Long,
    val lastUsedEpochMs: Long,
)

/** A processed snapshot ready for display. */
data class MemorySnapshot(
    val timestampMs: Long,
    val system: SystemMemory,
    val proc: ProcMemInfo?,
    val self: SelfProcessMemory,
    /** Derived by MemoryProcessor. */
    val usedPercent: Float,
    val freePercent: Float,
    /** Our own rule from the project report: free < 15 % => low memory. */
    val lowMemoryFlag: Boolean,
)
