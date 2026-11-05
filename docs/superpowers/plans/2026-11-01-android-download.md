# Android Download Link Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Teammates install GOOBER Connect on their Android phones from one link that always gets the newest build. GitHub builds and signs the app on every merge to `main`.

**Architecture:**
- **The app:** the Android module gets its real package ID and takes its version from Gradle properties. It signs release builds with a key named by environment variables, and asks for the right Bluetooth permission on each Android version.
- **The workflow:** a GitHub Actions workflow checks pull requests by building the app. On `main`, it builds the signed app and publishes it as the latest GitHub release, named `goober-connect.apk`.
- **The README:** tells teammates how to install it.

**Tech Stack:**
- Android Gradle Plugin 8.3.0, Gradle 8.5, Kotlin 2.0.20, JDK 17.
- GitHub Actions: `actions/checkout@v4`, `actions/setup-java@v4`, `gradle/actions/setup-gradle@v4`, and the `gh` command.
- Java's `keytool`; the Android SDK's `aapt2` and `apksigner`.

Spec: [`docs/superpowers/specs/2026-11-01-android-download-design.md`](../specs/2026-11-01-android-download-design.md) (commit `ff71249`)

## Global Constraints

- **No new libraries.** Only the files listed below change.
- **Package ID:** `io.github.sedsrdasu.gooberconnect`. The code's `namespace` stays `com.example.bluefalconcomposemultiplatform.android`.
- **Version:** from the Gradle properties `versionCode` and `versionName`, defaulting to 1 and "1.0". The workflow passes `github.run_number` as N, giving version code N and version name `1.0.N`.
- **Signing environment variables:** `GOOBER_KEYSTORE_FILE`, `GOOBER_KEYSTORE_PASSWORD` and `GOOBER_KEY_ALIAS`. The key password is the keystore password (PKCS12).
- **Repository secrets:** `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD` and `ANDROID_KEY_ALIAS`.
- **Release:**
  - The asset is named `goober-connect.apk`, with tag `build-N` and title "Build N", marked as the latest release.
  - The link is `https://github.com/SEDS-RD-ASU/ascent-app/releases/latest/download/goober-connect.apk`.
- **Safety line,** in the release notes and the README: "Before sending any command, check the board name on the card."
- **Never commit:**
  - the signing key or its password;
  - `local.properties` (it's git-ignored);
  - `iosBlueFalconExampleMP/iosBlueFalconExampleMP.xcodeproj/project.pbxproj`, which holds the user's local signing.
- **Committing:** the user commits and pushes. Each task ends with `git add`/`git commit` commands for them. Only the user, or the org owner, adds the repository secrets.

---

## Before you start

1. Work on the branch `android-download`. The user created it from `main`, and the spec is committed there (`ff71249`).
2. The Android SDK is already installed. Google's command-line tools came from Homebrew at `/opt/homebrew/share/android-commandlinetools`, with the Android 14 platform, build tools 34.0.0 and the platform tools. `local.properties` holds `sdk.dir=/opt/homebrew/share/android-commandlinetools`, and is git-ignored.
3. The app already built once, unchanged: `./gradlew :androidBlueFalconExampleMP:assembleDebug` gave `BUILD SUCCESSFUL`.

## Commands used in several tasks

Run everything from the repo root, `/Users/cliverafiq/dev/ascent-app`.

**Android debug build:**
```bash
./gradlew :androidBlueFalconExampleMP:assembleDebug --console=plain
```

**Inspecting an APK:**
```bash
BT=/opt/homebrew/share/android-commandlinetools/build-tools/34.0.0
$BT/aapt2 dump badging <file.apk> | grep -E "^package:|^application-label:"
$BT/apksigner verify --print-certs <file.apk> | grep "Signer #1 certificate DN"
```

**iOS unit tests:**
```bash
./gradlew :shared:iosSimulatorArm64Test --console=plain
grep -ho 'testsuite name="[^"]*" tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' shared/build/test-results/iosSimulatorArm64Test/*.xml
```

## File structure

| File | New / changed | Responsibility |
|---|---|---|
| `androidBlueFalconExampleMP/build.gradle.kts` | changed | Package ID, version from properties, release signing from the environment |
| `androidBlueFalconExampleMP/src/main/java/com/example/bluefalconcomposemultiplatform/android/MainActivity.kt` | changed | Asks for the Bluetooth permissions each Android version needs |
| `.github/workflows/android.yml` | new | Checks pull requests; builds, signs and publishes on `main` |
| `README.md` | new | How teammates install the app |
| `docs/superpowers/specs/2026-11-01-android-download-design.md` | changed (Task 4) | Status line |

---

### Task 1: Package ID, version and release signing

**Files:**
- Modify: `androidBlueFalconExampleMP/build.gradle.kts`

**Interfaces:**
- Produces:
  - Gradle properties `versionCode` (a number) and `versionName` (text) for `:androidBlueFalconExampleMP`.
  - Environment variables `GOOBER_KEYSTORE_FILE` (an absolute path), `GOOBER_KEYSTORE_PASSWORD` and `GOOBER_KEY_ALIAS`. When `GOOBER_KEYSTORE_FILE` is set, `assembleRelease` signs `androidBlueFalconExampleMP/build/outputs/apk/release/androidBlueFalconExampleMP-release.apk`. Otherwise the output is `…-release-unsigned.apk`.

This code was tried before the plan was written. It built a release signed with a throwaway key, package `io.github.sedsrdasu.gooberconnect`, version 7 / "1.0.7", and `apksigner` verified it.

- [ ] **Step 1: Change the build file**

In `androidBlueFalconExampleMP/build.gradle.kts`, replace

```kotlin
        applicationId = "com.example.bluefalconcomposemultiplatform.android"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }
```

with

```kotlin
        // What phones recognise the app by; once people have installed it, keep it
        applicationId = "io.github.sedsrdasu.gooberconnect"
        minSdk = 24
        targetSdk = 34
        // GitHub's workflow passes its run number, so each build counts as an update. Builds on a Mac stay 1 / "1.0"
        versionCode = (project.findProperty("versionCode") as String?)?.toInt() ?: 1
        versionName = (project.findProperty("versionName") as String?) ?: "1.0"
    }
    // The release signing key, from the environment: GitHub's workflow sets these from the repo's secrets.
    // Without them, release builds stay unsigned, as before.
    val keystore = System.getenv("GOOBER_KEYSTORE_FILE")
    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = project.file(keystore)
                storePassword = System.getenv("GOOBER_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("GOOBER_KEY_ALIAS")
                // A PKCS12 key file has one password for the file and the key
                keyPassword = System.getenv("GOOBER_KEYSTORE_PASSWORD")
            }
        }
    }
```

and replace

```kotlin
        getByName("release") {
            isMinifyEnabled = false
        }
```

with

```kotlin
        getByName("release") {
            isMinifyEnabled = false
            if (keystore != null) signingConfig = signingConfigs.getByName("release")
        }
```

- [ ] **Step 2: Debug build: the new package ID**

Run the **Android debug build**, then inspect `androidBlueFalconExampleMP/build/outputs/apk/debug/androidBlueFalconExampleMP-debug.apk`.
Expected: `BUILD SUCCESSFUL`, and `package: name='io.github.sedsrdasu.gooberconnect' versionCode='1' versionName='1.0'` with `application-label:'GOOBER Connect'`.

- [ ] **Step 3: Signed release build with a throwaway key**

```bash
SP=/private/tmp/claude-501/-Users-cliverafiq-dev-ascent-app/a758ef3b-a2ec-4841-90f0-f0f60fc48eb7/scratchpad
rm -f $SP/test.jks
keytool -genkeypair -keystore $SP/test.jks -storetype PKCS12 -alias test -keyalg RSA -keysize 2048 -validity 2 \
  -dname "CN=throwaway test key" -storepass testpass123 -keypass testpass123
rm -rf androidBlueFalconExampleMP/build/outputs/apk/release
GOOBER_KEYSTORE_FILE=$SP/test.jks GOOBER_KEYSTORE_PASSWORD=testpass123 GOOBER_KEY_ALIAS=test \
  ./gradlew :androidBlueFalconExampleMP:assembleRelease -PversionCode=7 -PversionName=1.0.7 --console=plain
```
Then inspect `androidBlueFalconExampleMP/build/outputs/apk/release/androidBlueFalconExampleMP-release.apk`.
Expected:
- `package: name='io.github.sedsrdasu.gooberconnect' versionCode='7' versionName='1.0.7'`;
- `Signer #1 certificate DN: CN=throwaway test key`;
- `apksigner verify` on its own prints no errors.

- [ ] **Step 4: Release build without the key**

```bash
rm -rf androidBlueFalconExampleMP/build/outputs/apk/release
./gradlew :androidBlueFalconExampleMP:assembleRelease --console=plain
ls androidBlueFalconExampleMP/build/outputs/apk/release/
rm -f /private/tmp/claude-501/-Users-cliverafiq-dev-ascent-app/a758ef3b-a2ec-4841-90f0-f0f60fc48eb7/scratchpad/test.jks
```
Expected: `BUILD SUCCESSFUL`, and the folder holds `androidBlueFalconExampleMP-release-unsigned.apk`.

- [ ] **Step 5: Commit (the user runs this)**

```bash
git add androidBlueFalconExampleMP/build.gradle.kts
git commit -m "give the android app its package id, a version from the build, and release signing"
```

---

### Task 2: Ask for the right Bluetooth permission on every Android version

**Files:**
- Modify: `androidBlueFalconExampleMP/src/main/java/com/example/bluefalconcomposemultiplatform/android/MainActivity.kt` (whole file)

**Interfaces:**
- Consumes: what the scan screen checks in `shared/src/androidMain/.../ble/data/AndroidPlatformBluetooth.kt`. That's `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` on Android 12 (API 31, `Build.VERSION_CODES.S`) and newer, and `ACCESS_FINE_LOCATION` before that. The scan screen re-checks every second, so scanning starts as soon as permission is granted.

- [ ] **Step 1: Replace `MainActivity.kt`**

Replace the whole file with:

```kotlin
package com.example.bluefalconcomposemultiplatform.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.bluefalconcomposemultiplatform.App
import com.example.bluefalconcomposemultiplatform.di.AppModule

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ask for whatever Bluetooth scanning still needs. The scan screen checks again every second, so
        // scanning starts as soon as it's allowed.
        val missing = missingPermissions()
        if (missing.isNotEmpty()) {
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
                if (results.values.any { granted -> !granted }) {
                    Toast.makeText(this, "Cannot scan without permission.", Toast.LENGTH_LONG).show()
                }
            }.launch(missing.toTypedArray())
        }

        setContent {
            App(
                darkTheme = isSystemInDarkTheme(),
                dynamicColor = false,
                appModule = AppModule(LocalContext.current.applicationContext)
            )
        }
    }

    // What Bluetooth scanning needs, as AndroidPlatformBluetooth checks it: nearby devices (scan and connect)
    // on Android 12 and newer, precise location before that
    private fun missingPermissions(): List<String> {
        val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return needed.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
    }
}
```

- [ ] **Step 2: Build**

Run the **Android debug build**.
Expected: `BUILD SUCCESSFUL`, with no `e:` lines and no new `w:` lines for `MainActivity.kt`.

There's no Android phone or emulator here. The permission prompts get checked on a teammate's phone in Task 6.

- [ ] **Step 3: Commit (the user runs this)**

```bash
git add androidBlueFalconExampleMP/src/main/java/com/example/bluefalconcomposemultiplatform/android/MainActivity.kt
git commit -m "ask for the bluetooth permission each android version needs"
```

---

### Task 3: The GitHub workflow

**Files:**
- Create: `.github/workflows/android.yml`

**Interfaces:**
- Consumes: the Gradle properties and environment variables from Task 1, and the repository secrets from Task 5.
- Produces:
  - Pull requests, and "Run workflow" on any other branch: a debug build as a check.
  - Pushes to `main`, and "Run workflow" on `main`: release `build-N` with `goober-connect.apk`, marked as the latest.

- [ ] **Step 1: Write the workflow**

Create `.github/workflows/android.yml`:

```yaml
# Builds the Android app. Pull requests (and runs on other branches) only check that it builds. On main, it
# builds the signed app and publishes it as the latest release, so this link always gets the newest build:
# https://github.com/SEDS-RD-ASU/ascent-app/releases/latest/download/goober-connect.apk
name: Android

on:
  pull_request:
  push:
    branches: [main]
  workflow_dispatch:

permissions:
  contents: write

jobs:
  android:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17

      - uses: gradle/actions/setup-gradle@v4

      - name: Build the app (check only)
        if: github.ref != 'refs/heads/main'
        run: ./gradlew :androidBlueFalconExampleMP:assembleDebug --console=plain

      - name: Unpack the signing key
        if: github.ref == 'refs/heads/main'
        env:
          KEYSTORE_BASE64: ${{ secrets.ANDROID_KEYSTORE_BASE64 }}
          KEYSTORE_PASSWORD: ${{ secrets.ANDROID_KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.ANDROID_KEY_ALIAS }}
        run: |
          if [ -z "$KEYSTORE_BASE64" ] || [ -z "$KEYSTORE_PASSWORD" ] || [ -z "$KEY_ALIAS" ]; then
            echo "::error::signing key not set up: add ANDROID_KEYSTORE_BASE64, ANDROID_KEYSTORE_PASSWORD and ANDROID_KEY_ALIAS under Settings > Secrets and variables > Actions"
            exit 1
          fi
          echo "$KEYSTORE_BASE64" | base64 --decode > "$RUNNER_TEMP/goober-connect.jks"

      - name: Build the signed app
        if: github.ref == 'refs/heads/main'
        env:
          GOOBER_KEYSTORE_FILE: ${{ runner.temp }}/goober-connect.jks
          GOOBER_KEYSTORE_PASSWORD: ${{ secrets.ANDROID_KEYSTORE_PASSWORD }}
          GOOBER_KEY_ALIAS: ${{ secrets.ANDROID_KEY_ALIAS }}
        run: >-
          ./gradlew :androidBlueFalconExampleMP:assembleRelease --console=plain
          -PversionCode=${{ github.run_number }}
          -PversionName=1.0.${{ github.run_number }}

      - name: Publish it as the latest release
        if: github.ref == 'refs/heads/main'
        env:
          GH_TOKEN: ${{ github.token }}
          BUILD: ${{ github.run_number }}
        run: |
          cp androidBlueFalconExampleMP/build/outputs/apk/release/androidBlueFalconExampleMP-release.apk goober-connect.apk
          {
            echo "Built from $(git rev-parse --short HEAD) on ${GITHUB_REF_NAME}: $(git log -1 --format=%s)"
            echo
            echo "Install on an Android phone: open https://github.com/SEDS-RD-ASU/ascent-app/releases/latest/download/goober-connect.apk, tap the file and allow the install."
            echo
            echo "Before sending any command, check the board name on the card."
          } > notes.md
          gh release create "build-$BUILD" goober-connect.apk \
            --target "$GITHUB_SHA" --title "Build $BUILD" --notes-file notes.md --latest
```

- [ ] **Step 2: Check it parses, and the names match**

```bash
ruby -ryaml -e 'w = YAML.load_file(".github/workflows/android.yml"); puts w["jobs"]["android"]["steps"].map { |s| s["name"] || s["uses"] }'
grep -c "GOOBER_KEYSTORE_FILE\|GOOBER_KEYSTORE_PASSWORD\|GOOBER_KEY_ALIAS" .github/workflows/android.yml
grep -o "secrets\.[A-Z0-9_]*" .github/workflows/android.yml | sort -u
```
Expected:
- the steps, in order: `actions/checkout@v4`, `actions/setup-java@v4`, `gradle/actions/setup-gradle@v4`, "Build the app (check only)", "Unpack the signing key", "Build the signed app", "Publish it as the latest release";
- `3`;
- `secrets.ANDROID_KEYSTORE_BASE64`, `secrets.ANDROID_KEYSTORE_PASSWORD` and `secrets.ANDROID_KEY_ALIAS`.

GitHub itself runs the workflow in Task 6.

- [ ] **Step 3: Commit (the user runs this)**

```bash
git add .github/workflows/android.yml
git commit -m "build the android app on github and publish it as the latest release"
```

---

### Task 4: README and spec status

**Files:**
- Create: `README.md`
- Modify: `docs/superpowers/specs/2026-11-01-android-download-design.md` (the status line)

- [ ] **Step 1: Write the README**

Create `README.md`:

````markdown
# GOOBER Connect

The ground-station app for SEDS-RD-ASU's ASCENT flight computer. It finds ASCENT boards over Bluetooth,
shows their telemetry, runs the team's pre-flight checklist, and sends board commands.

## Install on Android

1. On your Android phone, open this link. It always gets the newest build:
   **https://github.com/SEDS-RD-ASU/ascent-app/releases/latest/download/goober-connect.apk**
2. Tap the downloaded file. The first time, Android asks you to allow installs from your browser: allow
   it, then tap **Install**.
3. Open GOOBER Connect and allow Bluetooth when it asks.

To update, open the same link again. The new build installs over the old one and keeps your checklist.

Older builds are on the [Releases page](https://github.com/SEDS-RD-ASU/ascent-app/releases). To go back to
one, uninstall the app first: Android won't install an older version over a newer one.

## Install on iPhone

There's no download for iPhones yet. That needs a paid Apple Developer account, and then TestFlight.
Until then, build it from a Mac with Xcode:

1. Open `iosBlueFalconExampleMP/iosBlueFalconExampleMP.xcodeproj` in Xcode.
2. Under **Signing & Capabilities**, pick your own team (your Apple ID), and change the bundle identifier
   to something unique, such as `yourname.goober`.
3. Plug in your iPhone, pick it as the destination, and press **Run**.

The first time, trust yourself as a developer on the phone: Settings → General → VPN & Device Management.
The app stops opening after 7 days, and then needs building again.

## Safety

Before sending any command, check the board name on the card. Pop Apogee, Pop Mains and Erase Flash can't
be undone.
````

- [ ] **Step 2: Update the spec status**

In `docs/superpowers/specs/2026-11-01-android-download-design.md`, replace

```markdown
- **Status:** Design approved; not implemented yet
```

with

```markdown
- **Status:** Implemented on branch `android-download`; the Android app builds and signs on the Mac; the first GitHub release and an install on an Android phone not yet checked
```

- [ ] **Step 3: Final local check**

Run the **Android debug build**, then the **iOS unit tests**.
Expected:
- `BUILD SUCCESSFUL` for both.
- The iOS summaries show `CommandRulesTest` 12, `GooberTest` 6, `ChecklistRulesTest` 22, `ChecklistViewModelTest` 6 and `ScanRulesTest` 11 tests, all with `failures="0" errors="0"`.
- `git status --short` lists only `project.pbxproj` and this task's two files. `local.properties` and `build/` don't appear, because they're git-ignored.

- [ ] **Step 4: Commit (the user runs this)**

```bash
git add README.md docs/superpowers/specs/2026-11-01-android-download-design.md
git commit -m "add install instructions to the readme"
```

---

### Task 5: The signing key (the user, once)

Nothing to commit. **Never put the key file or its password in the repo folder or a commit.**

- [ ] **Step 1: Check admin access**

On https://github.com/SEDS-RD-ASU/ascent-app, the user looks for the **Settings** tab.
- **If it's there:** they have admin access, so carry on.
- **If it isn't:** the org owner does Steps 2–4 instead.

- [ ] **Step 2: Make the key, outside the repo**

```bash
cd ~/Documents
keytool -genkeypair -keystore goober-connect.jks -storetype PKCS12 -alias goober-connect \
  -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=GOOBER Connect, O=SEDS-RD-ASU"
```
It asks for a password twice. Choose a strong one, at least 6 characters.
Expected: a line starting `Generating 2,048 bit RSA key pair` and ending `for: CN=GOOBER Connect, O=SEDS-RD-ASU`.

- [ ] **Step 3: Add the three secrets**

```bash
base64 -i ~/Documents/goober-connect.jks | pbcopy
```
That copies the key file as text. Then, under Settings → Secrets and variables → Actions → **New repository secret**, add:

| Name | Secret |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | Paste (⌘V) |
| `ANDROID_KEYSTORE_PASSWORD` | The password from Step 2 |
| `ANDROID_KEY_ALIAS` | `goober-connect` |

Expected: the page lists the three names. GitHub never shows the values again.

- [ ] **Step 4: Back it up**

1. Put `goober-connect.jks` and its password somewhere safe for the team, such as a shared drive folder only the leads can open, or a password manager.
2. Then delete `~/Documents/goober-connect.jks` from the Mac, if the backup is elsewhere.

If the key is ever lost, the next build gets a new key, and everyone uninstalls and reinstalls once.

---

### Task 6: First run on GitHub, and a real install

- [ ] **Step 1: Push and open a pull request (the user)**

```bash
git push -u origin android-download
```
Then open https://github.com/SEDS-RD-ASU/ascent-app/compare/main...android-download?expand=1 and create the pull request against `main`.

- [ ] **Step 2: The pull request's check**

On the pull request, the **Android / android** check runs: just "Build the app (check only)", taking a few minutes.
Expected: a green tick. If it fails, open the check's log, find the first `e:` or `What went wrong` line, fix it on the branch, and push again.

- [ ] **Step 3: Merge, then the first release**

The user merges the pull request with **Create a merge commit**. The workflow then runs on `main` (Actions tab).
Expected:
- **The run:** all steps green.
- **The Releases page:** "Build N" is marked Latest, with `goober-connect.apk` attached. The notes give the commit, the install link and the safety line.
- **If "Unpack the signing key" fails** with "signing key not set up": Task 5 isn't finished. Add the secrets, then press "Run workflow" on `main` (Actions → Android → Run workflow).

- [ ] **Step 4: Check the link and the file**

```bash
curl -sIL https://github.com/SEDS-RD-ASU/ascent-app/releases/latest/download/goober-connect.apk | grep -iE "^HTTP/|^location:" | head -8
SP=/private/tmp/claude-501/-Users-cliverafiq-dev-ascent-app/a758ef3b-a2ec-4841-90f0-f0f60fc48eb7/scratchpad
mkdir -p $SP/release-check && curl -sL -o $SP/release-check/goober-connect.apk https://github.com/SEDS-RD-ASU/ascent-app/releases/latest/download/goober-connect.apk
BT=/opt/homebrew/share/android-commandlinetools/build-tools/34.0.0
$BT/aapt2 dump badging $SP/release-check/goober-connect.apk | grep -E "^package:|^application-label:"
$BT/apksigner verify --print-certs $SP/release-check/goober-connect.apk | grep "Signer #1 certificate DN"
```
Expected:
- one or more `302` redirects, the first to `…/releases/download/build-N/goober-connect.apk`, then `HTTP/2 200`;
- `package: name='io.github.sedsrdasu.gooberconnect' versionCode='N' versionName='1.0.N'`, where N is the build number;
- `application-label:'GOOBER Connect'`;
- `Signer #1 certificate DN: CN=GOOBER Connect, O=SEDS-RD-ASU`.

- [ ] **Step 5: A teammate's Android phone**

Send a teammate the link, or point them to the README.
Expected:
1. The install works as the README says.
2. On Android 12 or newer the app asks for "Nearby devices"; on Android 7–11, for location. It doesn't show "Cannot scan without permission." once they allow it.
3. The scan screen appears, and with a board around, the board's card. **Check the board name before sending anything.**
4. After the next merge to `main`, opening the link again installs over the old app, and their checklist ticks are still there.

When these pass, the spec's status line can say so in the next change that touches it.
