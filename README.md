# Remember My Sort

An Xposed module that forces the Android file picker to remember your sorting preferences.

![Android CI](https://github.com/hxreborn/remember-my-sort/actions/workflows/android.yml/badge.svg)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1.21-7F52FF?style=flat&logo=kotlin&logoColor=white)
![Android](https://img.shields.io/badge/API-30%2B-3DDC84?logo=android&logoColor=white)

<p>
  <a href="https://f-droid.org/en/packages/eu.hxreborn.remembermysort/"><img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" height="80" alt="Get it on F-Droid" /></a>
  <a href="https://apt.izzysoft.de/packages/eu.hxreborn.remembermysort"><img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" height="80" alt="Get it on IzzyOnDroid" /></a>
  <a href="../../releases"><img src=".github/assets/badge_github.png" height="82" alt="Get it on GitHub" /></a>
</p>

## Overview

Starting with Android 11's [scoped storage](https://developer.android.com/about/versions/11/privacy/storage#scoped-storage), all apps that need file access must use the system file picker. This picker fails to persist sort preferences across directories. Sort order is kept in the root view, but navigating into any subdirectory resets it to filename A-Z. Users must manually change the sort every time they browse into a folder, regardless of how many times they've set it before.

Users have [reported the issue since 2021](https://xdaforums.com/t/google-files-default-sort.4309799/) with no fix from Google.

## How it Works

Hooks into DocumentsUI sort logic. Manual sort changes are persisted to storage and restored on subsequent picker launches. Defaults to date descending on first run.

## Requirements

- LSPosed framework (API 100)
- Android 11+ (API 30+)

## Compatibility

Works on AOSP-based ROMs and Pixel devices. OEM-modified ROMs are untested.

## Installation

1. Install [LSPosed](https://github.com/JingMatrix/LSPosed) (JingMatrix fork recommended)
2. Download latest APK from [releases](../../releases) or [IzzyOnDroid](https://apt.izzysoft.de/packages/eu.hxreborn.remembermysort)
3. Install APK and enable module in LSPosed Manager
4. Add your DocumentsUI package to module scope:
   - Google/Pixel: `com.google.android.documentsui`
   - AOSP: `com.android.documentsui`
3. Force stop DocumentsUI
4. Open a file from any app to trigger DocumentsUI

## Build

```bash
./gradlew assembleDebug
```

Requires JDK 21 and Gradle 8.13.

## License

<a href="LICENSE"><img src=".github/assets/gplv3.svg" height="90" alt="GPLv3"></a>

This project is licensed under the GNU General Public License v3.0 – see the [LICENSE](LICENSE) file for details.
