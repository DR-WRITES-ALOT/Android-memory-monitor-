package com.example.memorymonitor

import com.example.memorymonitor.domain.MemoryProcessor
import com.example.memorymonitor.domain.ProcessEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryProcessorTest {

    private val gb = 1024L * 1024L * 1024L

    @Test
    fun usedPercent_isComputedFromTotalAndAvailable() {
        assertEquals(75f, MemoryProcessor.usedPercent(8 * gb, 2 * gb), 0.01f)
        assertEquals(0f, MemoryProcessor.usedPercent(0, 0), 0.01f)
    }

    @Test
    fun lowMemory_flaggedBelow15PercentFree() {
        assertTrue(MemoryProcessor.isLowMemory(total = 100, available = 14))
        assertFalse(MemoryProcessor.isLowMemory(total = 100, available = 15))
        assertFalse(MemoryProcessor.isLowMemory(total = 100, available = 50))
    }

    @Test
    fun parseMemInfo_readsKbValuesIntoBytes() {
        val text = """
            MemTotal:        7999940 kB
            MemFree:          312000 kB
            MemAvailable:    2500000 kB
            Buffers:            1234 kB
            Cached:          1900000 kB
            SwapTotal:       2097148 kB
            SwapFree:        2000000 kB
            SomethingElse:   garbage
        """.trimIndent()
        val info = MemoryProcessor.parseMemInfo(text)
        assertEquals(7999940L * 1024, info.memTotal)
        assertEquals(2500000L * 1024, info.memAvailable)
        assertEquals(1234L * 1024, info.buffers)
        assertEquals(2000000L * 1024, info.swapFree)
    }

    @Test
    fun parseVmRss_extractsValueOrNull() {
        val status = "Name:\tmemorymonitor\nVmPeak:\t 100 kB\nVmRSS:\t   58000 kB\nThreads:\t12\n"
        assertEquals(58000L * 1024, MemoryProcessor.parseVmRss(status))
        assertNull(MemoryProcessor.parseVmRss("Name:\tfoo\n"))
    }

    @Test
    fun rank_sortsByPssDescending() {
        val list = listOf(
            ProcessEntry("a", "a", 10, 0),
            ProcessEntry("b", "b", 30, 0),
            ProcessEntry("c", "c", 20, 0),
        )
        assertEquals(listOf("b", "c", "a"), MemoryProcessor.rank(list).map { it.packageName })
    }
}
