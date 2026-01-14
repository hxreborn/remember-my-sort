# Remember My Sort

An Xposed module that forces the Android file picker to remember your sorting preferences.

![Android CI](https://github.com/hxreborn/remember-my-sort/actions/workflows/android.yml/badge.svg)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1.21-7F52FF?style=flat&logo=kotlin&logoColor=white)
![Android](https://img.shields.io/badge/API-30%2B-3DDC84?logo=android&logoColor=white)

<p>
  <a href="https://f-droid.org/en/packages/eu.hxreborn.remembermysort/"><img src=".github/assets/badge_fdroid.png" height="80" alt="Get it on F-Droid" /></a>
  <a href="https://apt.izzysoft.de/packages/eu.hxreborn.remembermysort"><img src=".github/assets/badge_izzyondroid.png" height="80" alt="Get it on IzzyOnDroid" /></a>
  <a href="../../releases"><img src=".github/assets/badge_github.png" height="80" alt="Get it on GitHub" /></a>
  <a href="http://apps.obtainium.imranr.dev/redirect.html?r=obtainium://app/%7B%22id%22%3A%22eu.hxreborn.remembermysort%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fhxreborn%2Fremember-my-sort%22%2C%22author%22%3A%22rafareborn%22%2C%22name%22%3A%22Remember%20My%20Sort%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Afalse%7D%22%7D"><img src=".github/assets/badge_obtainium.png" height="80" alt="Get it on Obtainium" /></a>
</p>

## Overview

Starting with Android 11's [scoped storage](https://developer.android.com/about/versions/11/privacy/storage#scoped-storage), all apps that need file access must use the system file picker. This picker fails to persist sort preferences across directories. Sort order is kept in the root view, but navigating into any subdirectory resets it to filename A-Z. Users must manually change the sort every time they browse into a folder, regardless of how many times they've set it before.

Users have [reported the issue since 2021](https://xdaforums.com/t/google-files-default-sort.4309799/) with no fix from Google.

## Usage

From the file picker's sort menu:

- Normal tap: Updates the global sort for all folders.
- Long-press: Saves a custom sort for the current folder only. Clear it by changing sort without long-press.

> [!NOTE]
> Per-folder sorting quirks:
> 1. Recent view can't have per-folder sort. Long-pressing there updates the global sort instead.
> 2. Sort settings are stored per content provider. The same folder reached through different entry points may be treated separately (e.g., Downloads via quick access vs. via root storage).

## Requirements

Requires [LSPosed](https://github.com/JingMatrix/LSPosed) and Android 11+. Works on Pixel and AOSP-based ROMs. OEM-modified ROMs are untested.

## Installation

1. Install APK and enable in LSPosed Manager
2. Add recommended DocumentsUI packages to scope
   - Pixel: `com.google.android.documentsui`
   - AOSP: `com.android.documentsui`
3. Force stop DocumentsUI

## Build

1. Install JDK 21, Android SDK

2. Configure SDK path in `local.properties`

   ```properties
   sdk.dir=/path/to/android/sdk
   ```

3. Build APK

   ```bash
   ./gradlew assembleRelease
   ```

## License

<a href="LICENSE"><img src=".github/assets/gplv3.svg" height="90" alt="GPLv3"></a>

This project is licensed under the GNU General Public License v3.0 – see the [LICENSE](LICENSE) file for details.
