package com.example.memorymonitor.domain

/**
 * Processing Module (report section 5.3): pure functions, no Android dependencies,
 * so they are unit-testable on the JVM.
 */
object MemoryProcessor {

    /** Threshold from the project report: flag low memory when free RAM is below 15 %. */
    const val LOW_MEMORY_FREE_PERCENT = 15f

    fun usedPercent(total: Long, available: Long): Float {
        if (total <= 0L) return 0f
        return ((total - available).toFloat() / total.toFloat() * 100f).coerceIn(0f, 100f)
    }

    fun freePercent(total: Long, available: Long): Float = 100f - usedPercent(total, available)

    fun isLowMemory(total: Long, available: Long): Boolean =
        freePercent(total, available) < LOW_MEMORY_FREE_PERCENT

    /** Ranks processes by PSS, highest first. */
    fun rank(entries: List<ProcessEntry>): List<ProcessEntry> =
        entries.sortedWith(compareByDescending<ProcessEntry> { it.pssBytes }.thenByDescending { it.lastUsedEpochMs })

    /**
     * Parses the text of /proc/meminfo. Lines look like:
     *   MemTotal:        7999940 kB
     * The kernel always reports these values in kB.
     */
    fun parseMemInfo(text: String): ProcMemInfo {
        val map = HashMap<String, Long>()
        for (line in text.lineSequence()) {
            val colon = line.indexOf(':')
            if (colon <= 0) continue
            val key = line.substring(0, colon).trim()
            val digits = line.substring(colon + 1).trim().takeWhile { it.isDigit() }
            val kb = digits.toLongOrNull() ?: continue
            map[key] = kb * 1024L
        }
        return ProcMemInfo(
            memTotal = map["MemTotal"],
            memFree = map["MemFree"],
            memAvailable = map["MemAvailable"],
            buffers = map["Buffers"],
            cached = map["Cached"],
            swapTotal = map["SwapTotal"],
            swapFree = map["SwapFree"],
        )
    }

    /** Extracts VmRSS (in bytes) from the text of /proc/self/status, or null if absent. */
    fun parseVmRss(statusText: String): Long? {
        val line = statusText.lineSequence().firstOrNull { it.startsWith("VmRSS:") } ?: return null
        val kb = line.substringAfter(':').trim().takeWhile { it.isDigit() }.toLongOrNull() ?: return null
        return kb * 1024L
    }

    fun formatMb(bytes: Long?): String =
        if (bytes == null) "n/a" else "%,d MB".format(bytes / (1024L * 1024L))
}
