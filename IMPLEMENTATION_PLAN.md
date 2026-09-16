# Android Memory Monitor — Implementation Plan

> Status: **IMPLEMENTED** (v1). Decisions in §10 were taken as: skip Room+Sync, Canvas chart, `com.example.memorymonitor`, tests included.
> Based on the DA1 Review-1 report (Section 5: Android Memory Monitor).

---

## 1. Goal (what we are building)

A deliberately **plain** native Android app that:

1. Shows **live RAM usage** (total / available / used %, low-memory flag), polled every 2 seconds.
2. Shows a **process / app list** ranked by memory usage.
3. Makes the **Framework → JNI → syscall** path from Section 4 of the report visible in the code and the UI.

No fancy UI. Plain text, a couple of `Text`/`LazyColumn` composables, one tiny line chart drawn with Compose `Canvas` (no third-party chart library — MPAndroidChart is a View-based lib and adds interop noise for no conceptual gain; can be swapped in later if the rubric demands it).

Optional modules from the report (**Room storage**, **Sync to laptop**) are **out of scope for v1** — listed in §8 as stretch goals.

---

## 2. Decisions already made (from your answers)

| Question | Decision |
|---|---|
| Language / UI | Kotlin + Jetpack Compose |
| Concept focus | OS concepts: memory, processes, polling, syscall path |
| Process list source | `UsageStatsManager` + `ActivityManager.getProcessMemoryInfo()` (needs `PACKAGE_USAGE_STATS` special permission the user grants in Settings) |
| Min SDK | 26 (Android 8.0) as per report |
| Target SDK | 35 |

---

## 3. OS concepts the app demonstrates (and where)

This is the part that matters for the course. Each concept maps to a specific file.

| OS concept | How the app shows it | File |
|---|---|---|
| **Framework API → Binder IPC → system_server** | `ActivityManager.getMemoryInfo()` is a Binder call (`ioctl(BINDER_WRITE_READ)`) into `ActivityManagerService` living in `system_server`. | `data/MemoryDataSource.kt` |
| **Direct syscalls: `open()/read()/close()`** | We *also* read `/proc/meminfo` ourselves with a `File.readLines()` → Bionic `open/read/close`. Both numbers are shown side by side so you can see they agree. | `data/ProcMemInfoReader.kt` |
| **procfs as kernel→user interface** | `/proc/meminfo` (system-wide) and `/proc/self/status` (`VmRSS` of *our own* process) are parsed as plain text. | `data/ProcMemInfoReader.kt` |
| **Process isolation / sandboxing** | On Android 8+ `/proc/<pid>` of other apps is hidden (`hidepid=2`) and `getRunningAppProcesses()` returns only your own process. The app displays a one-line note explaining *why* the list needs `UsageStatsManager` instead. | `data/ProcessDataSource.kt`, `ui/ProcessListScreen.kt` |
| **Per-process memory: PSS vs RSS** | `Debug.MemoryInfo.totalPss` from `getProcessMemoryInfo()`; short in-app glossary line: RSS counts shared pages fully, PSS splits them. | `domain/MemoryModels.kt` |
| **Low-memory management (LMK / OOM)** | `MemoryInfo.lowMemory` and `MemoryInfo.threshold` displayed; our own rule flags **< 15 % free** (report §5.3). `onTrimMemory()` callback is logged so you can trigger it from the emulator. | `domain/MemoryProcessor.kt`, `MainActivity.kt` |
| **Polling / scheduling in user space** | A coroutine `while(isActive) { sample(); delay(2000) }` on `Dispatchers.IO`, cancelled with the lifecycle. | `MemoryViewModel.kt` |
| **Permissions as a security boundary** | `PACKAGE_USAGE_STATS` cannot be granted via a runtime dialog; the app detects it with `AppOpsManager` and deep-links to `Settings.ACTION_USAGE_ACCESS_SETTINGS`. | `data/UsagePermission.kt` |
| **GC / managed heap vs native heap** | `Runtime.totalMemory()/freeMemory()` (ART heap) vs `Debug.getNativeHeapAllocatedSize()` shown for our own process. | `data/MemoryDataSource.kt` |

---

## 4. Architecture (kept minimal, mirrors the report's block diagram)

```
[ UI Layer — Compose ]            ui/DashboardScreen.kt, ui/ProcessListScreen.kt
          ▲  StateFlow<UiState>
          │
[ MemoryViewModel ]               polls every 2 s (coroutine), holds last 60 samples
          │
[ Processing Module ]             domain/MemoryProcessor.kt  → used %, free %, lowMem flag, ranking
          │
[ Data Collection Module ]        data/MemoryDataSource.kt   → ActivityManager.getMemoryInfo()  (Binder)
                                  data/ProcMemInfoReader.kt  → /proc/meminfo, /proc/self/status (syscalls)
                                  data/ProcessDataSource.kt  → UsageStatsManager + getProcessMemoryInfo()
```

One module, one Activity, no DI framework, no navigation library — two screens toggled by a simple tab row.

---

## 5. Project structure

```
Android-memory-monitor-/
├── IMPLEMENTATION_PLAN.md          ← this file
├── README.md                       ← how to build/run + concept summary
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/libs.versions.toml
├── gradle/wrapper/                 ← wrapper jar + properties (so Android Studio just opens it)
└── app/
    ├── build.gradle.kts
    └── src/main/
        ├── AndroidManifest.xml     ← PACKAGE_USAGE_STATS declared
        ├── java/com/example/memorymonitor/
        │   ├── MainActivity.kt
        │   ├── MemoryViewModel.kt
        │   ├── data/
        │   │   ├── MemoryDataSource.kt
        │   │   ├── ProcMemInfoReader.kt
        │   │   ├── ProcessDataSource.kt
        │   │   └── UsagePermission.kt
        │   ├── domain/
        │   │   ├── MemoryModels.kt
        │   │   └── MemoryProcessor.kt
        │   └── ui/
        │       ├── DashboardScreen.kt
        │       ├── ProcessListScreen.kt
        │       └── SimpleLineChart.kt   (Compose Canvas, ~40 lines)
        └── res/values/strings.xml, themes.xml
```

---

## 6. Screens (plain, text-heavy on purpose)

**Tab 1 — Dashboard**
```
RAM (ActivityManager.getMemoryInfo  ← Binder ioctl)
  Total      7 856 MB
  Available  2 310 MB
  Used       70.6 %
  Threshold    566 MB   lowMemory = false
  ⚠ LOW MEMORY  (only when free < 15 %)

RAM (/proc/meminfo  ← open/read/close)
  MemTotal 7 856 MB  MemAvailable 2 312 MB  Cached 1 900 MB  SwapFree ...

This process (pid 12345)
  PSS 41 MB   RSS 58 MB (/proc/self/status)
  ART heap 12 / 24 MB   Native heap 9 MB

[ tiny 60-point used-% line chart ]
Last sample: 14:02:31   interval 2 s
```

**Tab 2 — Processes**
- If permission missing → explanation text + button "Grant usage access" → opens Settings.
- Else → `LazyColumn` of rows: `app label | package | PSS MB | last used`, sorted by PSS desc.
- Footer note: *"Android 8+ hides other apps' /proc entries; this list comes from UsageStatsManager (last 24 h) + ActivityManager.getProcessMemoryInfo(). PSS may be 0 for apps not currently resident."*

---

## 7. Implementation steps (what I will do once you approve)

1. **Scaffold** Gradle project (Kotlin DSL, AGP 8.x, Compose BOM, wrapper included) — buildable with `./gradlew assembleDebug`.
2. **Data collection**: `MemoryDataSource`, `ProcMemInfoReader` (with unit-testable parser), `ProcessDataSource`, `UsagePermission`.
3. **Processing**: `MemoryProcessor` — pure functions, one small JUnit test for the 15 % rule and the `/proc/meminfo` parser.
4. **ViewModel** with 2 s polling coroutine and a ring buffer of 60 samples.
5. **UI**: two Compose screens + Canvas chart.
6. **Manifest / permissions**: declare `PACKAGE_USAGE_STATS` (with `tools:ignore="ProtectedPermissions"`), handle `onTrimMemory`.
7. **README** with run steps (below) and a "concept map" table like §3, so the report's Section 5 can be lifted from it.
8. Commit to branch `arena/01a0a886-android-memory-monitor`.

Estimated size: ~600 lines of Kotlin total.

---

## 8. Out of scope for v1 (stretch, only if you want them later)

- Room DB history log (Storage Module)
- Sync to laptop over Wi-Fi/USB (Sync Module) — could be a trivial `adb logcat` tag or a 20-line HTTP server
- MPAndroidChart instead of Canvas chart
- Foreground `Service` so polling continues when app is backgrounded

---

## 9. How you will run it (Android Studio already installed)

1. **Get the code**
   `git clone https://github.com/DR-WRITES-ALOT/Android-memory-monitor-.git`
   `cd Android-memory-monitor- && git checkout arena/01a0a886-android-memory-monitor`
2. **Open** Android Studio → *File ▸ Open* → select the repo folder → wait for Gradle sync (first sync downloads dependencies, 2–5 min).
   If prompted to upgrade the Gradle/AGP version, click **Don't remind me** — the pinned versions are known-good.
3. **Create an emulator** (if none): *Tools ▸ Device Manager ▸ Create Device* → Pixel 6 → system image **API 34 (Android 14)** with Google APIs → Finish.
   *Or* plug in a physical phone with USB debugging enabled (Settings ▸ About ▸ tap Build number 7× ▸ Developer options ▸ USB debugging).
4. **Run**: pick the device in the toolbar dropdown → press the green ▶ (Shift+F10).
5. **Grant usage access**: open the *Processes* tab → tap **Grant usage access** → toggle on *Memory Monitor* → press Back. The list populates on the next 2 s tick.
6. **Demo low-memory** on the emulator: Android Studio *Logcat* filter `MemoryMonitor`; in the emulator's extended controls you can also run
   `adb shell am send-trim-memory com.example.memorymonitor RUNNING_CRITICAL`
   to see the `onTrimMemory` log line.
7. **Screenshots for report §6**: take them from the emulator toolbar camera icon.

Command-line alternative (no IDE): `./gradlew installDebug` with a device attached.

---

## 10. Things I want you to confirm

1. OK to **skip Room + Sync** for v1?
2. OK to use a **Compose Canvas chart instead of MPAndroidChart**? (The report names MPAndroidChart; I can use it if the reviewer will check.)
3. Package name `com.example.memorymonitor` fine, or do you want your own (e.g. `com.<yourname>.memorymonitor`)?
4. Should I include the small **JUnit tests** (parser + 15 % rule)? Adds ~40 lines, nice to show in review.
