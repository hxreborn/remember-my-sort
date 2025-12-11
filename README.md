# Remember My Sort

LSPosed module that sets DocumentsUI file picker default sort to date descending on Android 12+.

![Android API](https://img.shields.io/badge/API-31%2B-brightgreen)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0.0-blue)

## Problem

Android file picker resets to alphabetical sort every time. Pick "sort by date", close the picker, open again, back to A-Z.

**What's broken:**
- Sort preference IS stored in SharedPreferences (`sortModel-sortType`)
- Preference resets when picker closes
- Affects all apps using Storage Access Framework picker
- Broken since at least Android 12, reported [continuously since 2021](https://xdaforums.com/t/google-files-default-sort.4309799/)
- Affects Pixel, Samsung, Xiaomi devices

**Why it matters:**
Downloads folder grows chronologically. Users want newest files first. Forcing alphabetical sort on every picker launch adds friction to file selection workflow.

**Google response:** No acknowledgment or fix. Still broken 4 years later.

## Solution

Sets default to date descending. Respects manual sort if you pick one.

## Requirements

- LSPosed framework installed
- Android 12 or newer

Tested on Android 13-16 QPR2. Target package: `com.google.android.documentsui`

## Installation

1. Install [LSPosed](https://github.com/JingMatrix/LSPosed)
2. Install module APK
3. Enable module in LSPosed Manager
4. Add `com.google.android.documentsui` to scope
5. Force stop DocumentsUI or reboot

## Build

```bash
./gradlew assembleDebug
```

Requirements: JDK 21, Gradle 8.10+

## How it works

Hooks DocumentsUI sort logic. Applies date descending when user hasn't picked a sort. Detects date field dynamically instead of hardcoding values.

## License

[MIT](LICENSE)
