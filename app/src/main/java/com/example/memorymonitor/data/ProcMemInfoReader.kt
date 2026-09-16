package com.example.memorymonitor.data

import com.example.memorymonitor.domain.MemoryProcessor
import com.example.memorymonitor.domain.ProcMemInfo
import java.io.File

/**
 * Data Collection Module - DIRECT SYSCALL path.
 *
 * /proc is procfs: a virtual filesystem the kernel exposes so user space can read
 * kernel state as plain text. Reading it needs no Binder and no system service:
 *
 *   File.readText()  ->  ART  ->  JNI  ->  Bionic libc  ->  open() / read() / close()  ->  kernel
 *
 * /proc/meminfo is world-readable on every Android version.
 * /proc/self/ always points at our own process, so it is readable even though
 * Android 8+ mounts /proc with hidepid=2 (other apps' /proc/<pid> dirs are invisible).
 */
object ProcMemInfoReader {

    private const val MEMINFO = "/proc/meminfo"
    private const val SELF_STATUS = "/proc/self/status"

    fun readMemInfo(): ProcMemInfo? = runCatching {
        MemoryProcessor.parseMemInfo(File(MEMINFO).readText())
    }.getOrNull()

    fun readSelfRss(): Long? = runCatching {
        MemoryProcessor.parseVmRss(File(SELF_STATUS).readText())
    }.getOrNull()
}
