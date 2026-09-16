# Android Memory Monitor

A deliberately plain Android app (Kotlin + Jetpack Compose, API 26+) that shows live RAM usage and a
ranked process list, built to demonstrate the **Framework → JNI → syscall** path described in the
DA1 report. No fancy UI: the concepts are the point.

See [`IMPLEMENTATION_PLAN.md`](IMPLEMENTATION_PLAN.md) for the design and the concept → file map.

## Run it (Android Studio already installed)

1. Clone and check out the branch
   ```
   git clone https://github.com/DR-WRITES-ALOT/Android-memory-monitor-.git
   cd Android-memory-monitor-
   git checkout arena/01a0a886-android-memory-monitor
   ```
2. **Android Studio → File ▸ Open** → pick the repo folder → wait for Gradle sync (2–5 min the first time).
   - If Studio says the *Gradle wrapper jar is missing*, accept its offer to generate it. If it doesn't offer:
     **File ▸ Settings ▸ Build, Execution, Deployment ▸ Build Tools ▸ Gradle** → *Distribution: Wrapper* → OK, then
     **File ▸ Sync Project with Gradle Files**. (The repo ships `gradle-wrapper.properties` pinned to Gradle 8.9,
     but the binary jar is intentionally not committed.)
   - If prompted to upgrade AGP/Gradle: choose *Don't remind me*. Pinned versions: AGP 8.5.2, Kotlin 2.0.20, Gradle 8.9, JDK 17 (bundled with Studio).
3. **Device**: *Tools ▸ Device Manager ▸ Create Device* → Pixel 6 → system image **API 34** (Google APIs) → Finish.
   Or a real phone: *Settings ▸ About ▸ tap Build number 7×* → *Developer options ▸ USB debugging*.
4. Select the device in the toolbar and press **▶ Run** (Shift+F10).
5. **Processes tab** → *Grant usage access* → toggle **Memory Monitor** on → Back. The list fills on the next 2 s tick.
6. **Low-memory demo**: in Studio's *Logcat* filter `MemoryMonitor`, then from *Terminal*:
   ```
   adb shell am send-trim-memory com.example.memorymonitor RUNNING_CRITICAL
   ```
   The dashboard shows `last onTrimMemory: RUNNING_CRITICAL` and Logcat logs the callback.
7. Unit tests: right-click `app/src/test` → *Run tests* (or `./gradlew test`).

## What's where

```
app/src/main/java/com/example/memorymonitor/
├── MainActivity.kt          lifecycle-bound polling, onTrimMemory()
├── MemoryViewModel.kt       coroutine polling every 2 s, 60-sample ring buffer, StateFlow
├── data/
│   ├── MemoryDataSource.kt  ActivityManager.getMemoryInfo() → Binder ioctl → system_server
│   ├── ProcMemInfoReader.kt /proc/meminfo, /proc/self/status → open()/read()/close()
│   ├── ProcessDataSource.kt UsageStatsManager + getProcessMemoryInfo() (why: hidepid=2)
│   └── UsagePermission.kt   AppOpsManager check + deep-link to Settings
├── domain/
│   ├── MemoryModels.kt      data classes
│   └── MemoryProcessor.kt   used/free %, 15 % low-mem rule, ranking, /proc parsers (pure, tested)
└── ui/
    ├── MonitorApp.kt        two tabs
    ├── DashboardScreen.kt   RAM numbers from both paths, self-process PSS/RSS/heaps, chart
    ├── ProcessListScreen.kt ranked list or permission explainer
    └── SimpleLineChart.kt   Compose Canvas, no library
```

## OS concepts demonstrated

| Concept | Where |
|---|---|
| Framework API → Binder IPC (`ioctl(BINDER_WRITE_READ)`) → `system_server` | `MemoryDataSource.kt` |
| Direct syscalls `open/read/close` on procfs | `ProcMemInfoReader.kt` |
| Both paths shown side-by-side so the numbers can be compared | Dashboard |
| Process isolation: `hidepid=2`, per-app UID, why other pids are invisible | `ProcessDataSource.kt`, Processes tab text |
| PSS vs RSS; ART (GC) heap vs native (malloc) heap | Dashboard "This process" |
| Low Memory Killer threshold, `lowMemory` flag, own 15 % rule, `onTrimMemory` | `MemoryProcessor.kt`, `MainActivity.kt` |
| User-space polling with coroutines (`delay` suspends, no busy-wait), cancelled with lifecycle | `MemoryViewModel.kt` |
| App-op permission as a security boundary (no dialog, Settings only) | `UsagePermission.kt` |
