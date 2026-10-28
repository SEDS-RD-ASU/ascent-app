# Scan Screen Improvements and Launch Glow Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the pre-connection scan screen explain what's happening (Bluetooth status, searching, live signal, boards that have gone quiet) and restyle the app in the approved "Launch Glow" look, with Apogee/Mains removed.

**Architecture:** The existing `BluetoothDeviceViewModel` keeps owning the device list and gains a Bluetooth status loop, a monotonic clock and per-board signal/last-heard tracking fed by BlueFalcon's `didDiscoverDevice` callback. All decision rules (bars, "gone", which message) are pure functions in `ScanRules.kt` with unit tests. Platform differences are behind a small `PlatformBluetooth` interface (Android checks adapter/permissions; iOS opens Settings). The look is a Compose-drawn background, a new color scheme and a bundled logo.

**Tech Stack:** Kotlin 2.0.20, Compose Multiplatform 1.6.11 (Material 3, compose resources), BlueFalcon 2.2.4, moko-mvvm 0.16.1, kotlin.test, Xcode 26.6 / iOS.

**Spec:** `docs/superpowers/specs/2026-10-15-scan-screen-design.md`. Mockups and logo files: `docs/superpowers/specs/2026-10-15-scan-screen-assets/`.

## Global Constraints

- No version bumps and no new dependencies (Kotlin 2.0.20, CMP 1.6.11, AGP 8.3.0, BlueFalcon 2.2.4 stay).
- Work on branch `scan-screen-redesign`. **Never commit `iosBlueFalconExampleMP/iosBlueFalconExampleMP.xcodeproj/project.pbxproj`** (it holds a local signing change); always `git add` explicit paths.
- Never call `blueFalcon.scan()` before the Bluetooth status is `Ready` (crash fix `ecba402`), except the deliberate probe in `notReadyReason()`.
- Device name filter stays: only names containing "ASCENT" (case-insensitive).
- Numbers: gone after **10 s**; "No ASCENT found yet" after **15 s**; bars 4 ≥ −60, 3 ≥ −70, 2 ≥ −80, 1 ≥ −90, else 0 dBm; smoothing `0.7 × previous + 0.3 × reading` (first reading as is).
- Copy, verbatim: "Bluetooth is off. Turn it on to find your ASCENT." · "GOOBER Connect isn't allowed to use Bluetooth" · "Turn on Bluetooth for this app in Settings to find your ASCENT." · "Open Settings" · "This device doesn't support Bluetooth LE." · "Starting Bluetooth…" · "Searching for ASCENT…" · "No ASCENT found yet" · "Make sure the ASCENT is powered on" · "Bring your phone closer to the board" · "Still searching…" · "Not seen for N s" · "GOOBER Connect" · "GROUND STATION".
- Colors: sky gradient `#050714` → `#0A0C1E` (50%) → `#1B0E1E` (75%) → `#2A1012`; glow stops `#D9FFAA28` → `#8CFF6E1E` (25%) → `#668C1D40` (55%) → transparent (78%); text `#FFF7EC`; secondary `#C9B8A6`; ASU gold `#FFC627`; on gold `#1A1200`; card `#94080812`; card border `#1AFFFFFF`; signal track `#2EFFFFFF`; connected `#3DDC84`; commands red `#D32F2F`, purple `#8E24AA`, green `#388E3C`.
- Commands shown: TX Lock, AUX (row 1); Reboot, Wakeup (row 2). Apogee and Mains are not shown; their `GooberMsgType` entries stay in `goober.kt`.
- Android code can't be compiled on this Mac (no Android SDK). Write it carefully; it is verified by a teammate (Task 6).

## Commands used in several tasks

Run all commands from the repo root `/Users/cliverafiq/dev/ascent-app`.

**Unit tests (iOS Simulator):**
```bash
./gradlew :shared:iosSimulatorArm64Test --console=plain
```
Report: `shared/build/reports/tests/iosSimulatorArm64Test/index.html`.

**iOS compile check (fast):**
```bash
./gradlew :shared:compileKotlinIosArm64 --console=plain
```

**Run in the iOS Simulator and take a screenshot** (Bluetooth isn't available in the Simulator, so this is for looks and status messages only; the first build takes several minutes):
```bash
xcodebuild -project iosBlueFalconExampleMP/iosBlueFalconExampleMP.xcodeproj -scheme iosBlueFalconExampleMP \
  -configuration Debug -destination 'platform=iOS Simulator,name=iPhone 17' \
  -derivedDataPath build/xcode-sim CODE_SIGNING_ALLOWED=NO build -quiet
APP=build/xcode-sim/Build/Products/Debug-iphonesimulator/iosBlueFalconExampleMP.app
BUNDLE=$(/usr/libexec/PlistBuddy -c 'Print :CFBundleIdentifier' "$APP/Info.plist")
xcrun simctl boot "iPhone 17" 2>/dev/null || true
xcrun simctl install "iPhone 17" "$APP"
xcrun simctl launch --terminate-running-process "iPhone 17" "$BUNDLE"
sleep 8
xcrun simctl io "iPhone 17" screenshot build/xcode-sim/screen.png
```
Open `build/xcode-sim/screen.png` to check. (`build/` is git-ignored.)

**Build, install and launch on the iPhone** ("Boeuf", plugged in and unlocked; Developer Mode on):
```bash
xcodebuild -project iosBlueFalconExampleMP/iosBlueFalconExampleMP.xcodeproj -scheme iosBlueFalconExampleMP \
  -configuration Debug -destination 'id=00008110-00063D160C20201E' -derivedDataPath build/xcode-device \
  -allowProvisioningUpdates -allowProvisioningDeviceRegistration \
  DEVELOPMENT_TEAM=LQN7334YGH PRODUCT_BUNDLE_IDENTIFIER=cliverafiq.goober1 build -quiet
xcrun devicectl device install app --device 00008110-00063D160C20201E \
  build/xcode-device/Build/Products/Debug-iphoneos/iosBlueFalconExampleMP.app
xcrun devicectl device process launch --device 00008110-00063D160C20201E --terminate-existing --console cliverafiq.goober1
```
`--console` prints the app's output (including any `Uncaught Kotlin exception`) and blocks until the app exits; stop it with Ctrl-C. Other developers use their own team ID and bundle ID.

## File structure

| File | Responsibility |
|---|---|
| `shared/src/commonMain/.../ble/data/BluetoothStatus.kt` (new) | `BluetoothStatus` enum |
| `shared/src/commonMain/.../ble/data/PlatformBluetooth.kt` (new) | Interface for platform checks and opening Settings |
| `shared/src/iosMain/.../ble/data/IosPlatformBluetooth.kt` (new) | iOS implementation |
| `shared/src/androidMain/.../ble/data/AndroidPlatformBluetooth.kt` (new) | Android implementation |
| `shared/src/commonMain/.../ble/presentation/ScanRules.kt` (new) | Pure rules + constants |
| `shared/src/commonTest/.../ble/presentation/ScanRulesTest.kt` (new) | Unit tests for the rules |
| `shared/src/commonMain/.../core/presentation/LaunchGlowBackground.kt` (new) | Drawn background |
| `shared/src/commonMain/.../core/presentation/AppHeader.kt` (new) | Logo + title |
| `shared/src/commonMain/.../ble/presentation/component/ScanStatusMessage.kt` (new) | Status line |
| `shared/src/commonMain/.../ble/presentation/component/SignalBars.kt` (new) | 4-bar signal graphic |
| `shared/src/commonMain/composeResources/drawable/seds_logo_dark.png` (new) | Logo |
| `ui/theme/Color.kt`, `ui/theme/Theme.kt` | Replaced by the Launch Glow palette/scheme |
| `core/presentation/BlueFalconTheme.{android,ios}.kt` | Always use Launch Glow |
| `App.kt`, `ble/presentation/component/DeviceScanView.kt`, `.../FoundDeviceCard.kt` | Screen |
| `ble/presentation/BluetoothDeviceViewModel.kt`, `BluetoothDeviceState.kt`, `EnhancedBluetoothPeripheral.kt` | State and logic |
| `ble/data/BleDelegate.kt`, `ble/data/DeviceEvent.kt` | Broadcast events |
| `di/AppModule.kt` + `.android.kt` + `.ios.kt` | Single BlueFalcon, `platformBluetooth` |
| `iosBlueFalconExampleMP/iosBlueFalconExampleMP/ContentView.swift`, `Info.plist` | Edge-to-edge, dark status bar, app name |
| `androidBlueFalconExampleMP/src/main/AndroidManifest.xml` | App name |
| `shared/build.gradle.kts` | Simulator used for iOS tests |

`...` = `kotlin/com/example/bluefalconcomposemultiplatform`.

---

### Task 1: Scan rules with unit tests

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/data/BluetoothStatus.kt`
- Create: `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/ScanRules.kt`
- Test: `shared/src/commonTest/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/ScanRulesTest.kt`
- Modify: `shared/build.gradle.kts` (append at the end)

**Interfaces:**
- Produces: `enum class BluetoothStatus { Ready, Off, NoPermission, Unsupported, Starting }` (package `...ble.data`); in package `...ble.presentation`: `enum class ScanMessage { BluetoothOff, NoPermission, Unsupported, StartingBluetooth, Searching, NotFound }`, `val GONE_AFTER: Duration`, `val NOT_FOUND_AFTER: Duration`, `fun signalBars(rssi: Float?): Int`, `fun smoothRssi(previous: Float?, reading: Float): Float`, `fun sinceLastSeen(now: Duration, lastSeenAt: Duration?): Duration?`, `fun isGone(sinceLastSeen: Duration?, connected: Boolean): Boolean`, `fun scanMessage(status: BluetoothStatus, audibleBoards: Int, scanningFor: Duration?): ScanMessage?`, `fun bluetoothStatusFor(error: Throwable): BluetoothStatus`.

- [ ] **Step 1: Point iOS tests at an installed simulator**

Xcode 26.6 only ships iPhone 17-era simulators. Append to the end of `shared/build.gradle.kts`:

```kotlin

// Run the iOS unit tests on a simulator that ships with the installed Xcode
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest>().configureEach {
    device.set("iPhone 17")
}
```

- [ ] **Step 2: Write the failing tests**

Create `shared/src/commonTest/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/ScanRulesTest.kt`:

```kotlin
package com.example.bluefalconcomposemultiplatform.ble.presentation

import com.example.bluefalconcomposemultiplatform.ble.data.BluetoothStatus
import dev.bluefalcon.BluetoothNotEnabledException
import dev.bluefalcon.BluetoothPermissionException
import dev.bluefalcon.BluetoothResettingException
import dev.bluefalcon.BluetoothUnknownException
import dev.bluefalcon.BluetoothUnsupportedException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class ScanRulesTest {

    @Test
    fun signalBarsFollowTheThresholds() {
        assertEquals(4, signalBars(-40f))
        assertEquals(4, signalBars(-60f))
        assertEquals(3, signalBars(-60.5f))
        assertEquals(3, signalBars(-70f))
        assertEquals(2, signalBars(-80f))
        assertEquals(1, signalBars(-90f))
        assertEquals(0, signalBars(-90.5f))
        assertEquals(0, signalBars(null))
    }

    @Test
    fun firstReadingIsUsedAsIs() {
        assertEquals(-70f, smoothRssi(previous = null, reading = -70f))
    }

    @Test
    fun laterReadingsAreSmoothed() {
        // 0.7 * -60 + 0.3 * -80 = -66
        assertEquals(-66f, smoothRssi(previous = -60f, reading = -80f), absoluteTolerance = 0.001f)
    }

    @Test
    fun timeSinceLastSeenNeverGoesNegative() {
        assertEquals(3.seconds, sinceLastSeen(now = 13.seconds, lastSeenAt = 10.seconds))
        assertEquals(Duration.ZERO, sinceLastSeen(now = 10.seconds, lastSeenAt = 11.seconds))
        assertNull(sinceLastSeen(now = 10.seconds, lastSeenAt = null))
    }

    @Test
    fun boardIsGoneAfterTenSecondsOfSilence() {
        assertFalse(isGone(sinceLastSeen = 9_999.milliseconds, connected = false))
        assertTrue(isGone(sinceLastSeen = 10.seconds, connected = false))
    }

    @Test
    fun connectedBoardIsNeverGone() {
        assertFalse(isGone(sinceLastSeen = 60.seconds, connected = true))
    }

    @Test
    fun boardNeverHeardIsNotGone() {
        assertFalse(isGone(sinceLastSeen = null, connected = false))
    }

    @Test
    fun bluetoothProblemsComeFirst() {
        assertEquals(ScanMessage.BluetoothOff, scanMessage(BluetoothStatus.Off, audibleBoards = 2, scanningFor = 30.seconds))
        assertEquals(ScanMessage.NoPermission, scanMessage(BluetoothStatus.NoPermission, audibleBoards = 0, scanningFor = null))
        assertEquals(ScanMessage.Unsupported, scanMessage(BluetoothStatus.Unsupported, audibleBoards = 0, scanningFor = null))
        assertEquals(ScanMessage.StartingBluetooth, scanMessage(BluetoothStatus.Starting, audibleBoards = 0, scanningFor = null))
    }

    @Test
    fun noMessageWhileABoardCanBeHeard() {
        assertNull(scanMessage(BluetoothStatus.Ready, audibleBoards = 1, scanningFor = 30.seconds))
    }

    @Test
    fun searchingTurnsIntoNotFoundAfterFifteenSeconds() {
        assertEquals(ScanMessage.Searching, scanMessage(BluetoothStatus.Ready, audibleBoards = 0, scanningFor = null))
        assertEquals(ScanMessage.Searching, scanMessage(BluetoothStatus.Ready, audibleBoards = 0, scanningFor = 14.seconds))
        assertEquals(ScanMessage.NotFound, scanMessage(BluetoothStatus.Ready, audibleBoards = 0, scanningFor = 15.seconds))
    }

    @Test
    fun blueFalconExceptionsMapToStatuses() {
        assertEquals(BluetoothStatus.Off, bluetoothStatusFor(BluetoothNotEnabledException()))
        assertEquals(BluetoothStatus.NoPermission, bluetoothStatusFor(BluetoothPermissionException()))
        assertEquals(BluetoothStatus.Unsupported, bluetoothStatusFor(BluetoothUnsupportedException()))
        assertEquals(BluetoothStatus.Starting, bluetoothStatusFor(BluetoothResettingException()))
        assertEquals(BluetoothStatus.Starting, bluetoothStatusFor(BluetoothUnknownException()))
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew :shared:iosSimulatorArm64Test --console=plain`
Expected: FAIL at compile time with `Unresolved reference 'signalBars'` (and similar for the other rules and `BluetoothStatus`).

- [ ] **Step 4: Add `BluetoothStatus`**

Create `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/data/BluetoothStatus.kt`:

```kotlin
package com.example.bluefalconcomposemultiplatform.ble.data

// Whether the app can scan, and if not, why
enum class BluetoothStatus { Ready, Off, NoPermission, Unsupported, Starting }
```

- [ ] **Step 5: Add the rules**

Create `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/ScanRules.kt`:

```kotlin
package com.example.bluefalconcomposemultiplatform.ble.presentation

import com.example.bluefalconcomposemultiplatform.ble.data.BluetoothStatus
import dev.bluefalcon.BluetoothNotEnabledException
import dev.bluefalcon.BluetoothPermissionException
import dev.bluefalcon.BluetoothUnsupportedException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// What the status line above the device list says
enum class ScanMessage { BluetoothOff, NoPermission, Unsupported, StartingBluetooth, Searching, NotFound }

// A board we're not connected to counts as gone after this long without a broadcast
val GONE_AFTER: Duration = 10.seconds

// Searching turns into "No ASCENT found yet" after this long without hearing a board
val NOT_FOUND_AFTER: Duration = 15.seconds

// Signal strength bars (0-4) for an RSSI reading in dBm
fun signalBars(rssi: Float?): Int = when {
    rssi == null -> 0
    rssi >= -60f -> 4
    rssi >= -70f -> 3
    rssi >= -80f -> 2
    rssi >= -90f -> 1
    else -> 0
}

// Smooth RSSI readings so the bars don't flicker; the first reading is used as is
fun smoothRssi(previous: Float?, reading: Float): Float =
    if (previous == null) reading else previous * 0.7f + reading * 0.3f

// How long ago a board was last heard, or null if it never has been
fun sinceLastSeen(now: Duration, lastSeenAt: Duration?): Duration? =
    lastSeenAt?.let { (now - it).coerceAtLeast(Duration.ZERO) }

// Connected boards stop broadcasting, so only boards we're not connected to can go quiet
fun isGone(sinceLastSeen: Duration?, connected: Boolean): Boolean =
    !connected && sinceLastSeen != null && sinceLastSeen >= GONE_AFTER

// Bluetooth problems first; then nothing while a board can be heard; then searching / not found
fun scanMessage(status: BluetoothStatus, audibleBoards: Int, scanningFor: Duration?): ScanMessage? =
    when (status) {
        BluetoothStatus.Off -> ScanMessage.BluetoothOff
        BluetoothStatus.NoPermission -> ScanMessage.NoPermission
        BluetoothStatus.Unsupported -> ScanMessage.Unsupported
        BluetoothStatus.Starting -> ScanMessage.StartingBluetooth
        BluetoothStatus.Ready -> when {
            audibleBoards > 0 -> null
            scanningFor != null && scanningFor >= NOT_FOUND_AFTER -> ScanMessage.NotFound
            else -> ScanMessage.Searching
        }
    }

// BlueFalcon's scan() throws one of these on iOS when Bluetooth isn't ready
fun bluetoothStatusFor(error: Throwable): BluetoothStatus = when (error) {
    is BluetoothNotEnabledException -> BluetoothStatus.Off
    is BluetoothPermissionException -> BluetoothStatus.NoPermission
    is BluetoothUnsupportedException -> BluetoothStatus.Unsupported
    else -> BluetoothStatus.Starting
}
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :shared:iosSimulatorArm64Test --console=plain`
Expected: `BUILD SUCCESSFUL`; the report lists 11 tests in `ScanRulesTest`, all passed. If the task fails with an invalid-device error, run `xcrun simctl list devices available` and put an installed iPhone's name in Step 1.

- [ ] **Step 7: Commit**

```bash
git add shared/build.gradle.kts \
  shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/data/BluetoothStatus.kt \
  shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/ScanRules.kt \
  shared/src/commonTest/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/ScanRulesTest.kt
git commit -m "add scan screen rules with unit tests"
```

---

### Task 2: Launch Glow look (theme, background, logo, header, edge to edge, app name)

**Files:**
- Modify (replace contents): `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ui/theme/Color.kt`
- Modify (replace contents): `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ui/theme/Theme.kt`
- Modify: `shared/src/iosMain/kotlin/com/example/bluefalconcomposemultiplatform/core/presentation/BlueFalconTheme.ios.kt`
- Modify: `shared/src/androidMain/kotlin/com/example/bluefalconcomposemultiplatform/core/presentation/BlueFalconTheme.android.kt`
- Create: `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/core/presentation/LaunchGlowBackground.kt`
- Create: `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/core/presentation/AppHeader.kt`
- Create: `shared/src/commonMain/composeResources/drawable/seds_logo_dark.png`
- Modify: `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/App.kt`
- Modify: `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/component/DeviceScanView.kt`
- Modify: `iosBlueFalconExampleMP/iosBlueFalconExampleMP/ContentView.swift`, `iosBlueFalconExampleMP/iosBlueFalconExampleMP/Info.plist`
- Modify: `androidBlueFalconExampleMP/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces (package `...ui.theme`): colors `GlowSkyTop`, `GlowSkyMid`, `GlowSkyLow`, `GlowHorizon`, `TextPrimary`, `TextSecondary`, `AsuGold`, `OnAsuGold`, `CardBackground`, `CardBorder`, `SignalTrack`, `ConnectedGreen`, `CommandRed`, `CommandPurple`, `CommandGreen`; `val LaunchGlowColorScheme: ColorScheme`. Package `...core.presentation`: `@Composable fun LaunchGlowBackground(modifier: Modifier = Modifier)`, `@Composable fun AppHeader(modifier: Modifier = Modifier)`.

- [ ] **Step 1: Replace the palette**

Check nothing else uses the old colors: `grep -rn "md_theme_\|LightColorScheme\|DarkColorScheme" shared/src` should list only `Color.kt`, `Theme.kt` and the two `BlueFalconTheme.*.kt` files.

Replace all of `ui/theme/Color.kt` with:

```kotlin
package com.example.bluefalconcomposemultiplatform.ui.theme

import androidx.compose.ui.graphics.Color

// Launch Glow: night sky warming into a launch glow at the bottom
val GlowSkyTop = Color(0xFF050714)
val GlowSkyMid = Color(0xFF0A0C1E)
val GlowSkyLow = Color(0xFF1B0E1E)
val GlowHorizon = Color(0xFF2A1012)

val TextPrimary = Color(0xFFFFF7EC)
val TextSecondary = Color(0xFFC9B8A6)
val AsuGold = Color(0xFFFFC627)
val OnAsuGold = Color(0xFF1A1200)

val CardBackground = Color(0x94080812)   // rgba(8, 8, 18, .58)
val CardBorder = Color(0x1AFFFFFF)       // white at 10%
val SignalTrack = Color(0x2EFFFFFF)      // white at 18%
val ConnectedGreen = Color(0xFF3DDC84)

// Command button colors (unchanged from before the redesign)
val CommandRed = Color(0xFFD32F2F)
val CommandPurple = Color(0xFF8E24AA)
val CommandGreen = Color(0xFF388E3C)
```

Replace all of `ui/theme/Theme.kt` with:

```kotlin
package com.example.bluefalconcomposemultiplatform.ui.theme

import androidx.compose.material3.darkColorScheme

// The app is always dark, whatever the phone's light/dark setting
val LaunchGlowColorScheme = darkColorScheme(
    primary = AsuGold,
    onPrimary = OnAsuGold,
    secondary = AsuGold,
    onSecondary = OnAsuGold,
    background = GlowSkyTop,
    onBackground = TextPrimary,
    surface = GlowSkyMid,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary,
    outline = CardBorder
)
```

- [ ] **Step 2: Use the scheme on both platforms**

Replace all of `BlueFalconTheme.ios.kt` with:

```kotlin
package com.example.bluefalconcomposemultiplatform.core.presentation

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import com.example.bluefalconcomposemultiplatform.ui.theme.LaunchGlowColorScheme
import com.example.bluefalconcomposemultiplatform.ui.theme.Typography

@Composable
actual fun BlueFalconTheme(
    darkTheme: Boolean,
    dynamicColor: Boolean,
    content: @Composable () -> Unit
) {
    // Launch Glow is always dark, so the phone's light/dark setting doesn't change the colors
    MaterialTheme(
        colorScheme = LaunchGlowColorScheme,
        typography = Typography,
        content = content
    )
}
```

Replace all of `BlueFalconTheme.android.kt` with:

```kotlin
package com.example.bluefalconcomposemultiplatform.core.presentation

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.bluefalconcomposemultiplatform.ui.theme.GlowHorizon
import com.example.bluefalconcomposemultiplatform.ui.theme.GlowSkyTop
import com.example.bluefalconcomposemultiplatform.ui.theme.LaunchGlowColorScheme
import com.example.bluefalconcomposemultiplatform.ui.theme.Typography

@Composable
actual fun BlueFalconTheme(
    darkTheme: Boolean,
    dynamicColor: Boolean,
    content: @Composable () -> Unit
) {
    // Launch Glow is always dark, so the phone's light/dark setting and dynamic color are ignored
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = GlowSkyTop.toArgb()
            window.navigationBarColor = GlowHorizon.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = LaunchGlowColorScheme,
        typography = Typography,
        content = content
    )
}
```

- [ ] **Step 3: Add the background**

Create `core/presentation/LaunchGlowBackground.kt`:

```kotlin
package com.example.bluefalconcomposemultiplatform.core.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import com.example.bluefalconcomposemultiplatform.ui.theme.GlowHorizon
import com.example.bluefalconcomposemultiplatform.ui.theme.GlowSkyLow
import com.example.bluefalconcomposemultiplatform.ui.theme.GlowSkyMid
import com.example.bluefalconcomposemultiplatform.ui.theme.GlowSkyTop

// Night sky with stars, warming into a glow at the bottom like a rocket lighting up on the pad
@Composable
fun LaunchGlowBackground(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxSize()) {
        drawRect(
            Brush.verticalGradient(
                0f to GlowSkyTop,
                0.5f to GlowSkyMid,
                0.75f to GlowSkyLow,
                1f to GlowHorizon
            )
        )

        stars.forEach { star ->
            drawCircle(
                color = Color.White.copy(alpha = star.alpha),
                radius = star.radiusDp.dp.toPx(),
                center = Offset(star.x * size.width, star.y * size.height)
            )
        }

        // An ellipse 85% of the width and 42% of the height, centred just below the bottom edge:
        // draw a circle with radius = the ellipse's width and squash it vertically
        val radiusX = size.width * 0.85f
        val radiusY = size.height * 0.42f
        val center = Offset(size.width / 2f, size.height * 1.04f)
        scale(scaleX = 1f, scaleY = radiusY / radiusX, pivot = center) {
            drawCircle(
                brush = Brush.radialGradient(
                    0f to Color(0xD9FFAA28),
                    0.25f to Color(0x8CFF6E1E),
                    0.55f to Color(0x668C1D40),
                    0.78f to Color.Transparent,
                    center = center,
                    radius = radiusX
                ),
                radius = radiusX,
                center = center
            )
        }
    }
}

private class Star(val x: Float, val y: Float, val radiusDp: Float, val alpha: Float)

// Fixed positions (fractions of the screen) so the sky looks the same every time
private val stars = listOf(
    Star(0.072f, 0.042f, 0.9f, 0.8f), Star(0.208f, 0.123f, 0.6f, 0.5f), Star(0.364f, 0.035f, 1.1f, 0.9f),
    Star(0.548f, 0.079f, 0.6f, 0.45f), Star(0.704f, 0.029f, 0.8f, 0.7f), Star(0.856f, 0.100f, 1.0f, 0.85f),
    Star(0.944f, 0.185f, 0.6f, 0.5f), Star(0.048f, 0.227f, 0.7f, 0.6f), Star(0.912f, 0.308f, 0.9f, 0.7f),
    Star(0.124f, 0.365f, 0.5f, 0.4f), Star(0.960f, 0.446f, 0.6f, 0.45f), Star(0.032f, 0.504f, 0.9f, 0.6f),
    Star(0.248f, 0.246f, 0.5f, 0.35f), Star(0.784f, 0.227f, 0.5f, 0.4f), Star(0.640f, 0.169f, 0.7f, 0.55f),
    Star(0.280f, 0.577f, 0.6f, 0.35f), Star(0.888f, 0.612f, 0.8f, 0.5f), Star(0.080f, 0.677f, 0.6f, 0.4f),
    Star(0.480f, 0.015f, 0.5f, 0.5f), Star(0.976f, 0.019f, 0.6f, 0.6f), Star(0.176f, 0.185f, 1.2f, 0.9f),
    Star(0.820f, 0.146f, 0.5f, 0.4f), Star(0.440f, 0.185f, 0.4f, 0.35f), Star(0.944f, 0.750f, 0.6f, 0.35f),
    Star(0.056f, 0.827f, 0.7f, 0.3f), Star(0.640f, 0.481f, 0.5f, 0.25f), Star(0.384f, 0.788f, 0.6f, 0.25f)
)
```

- [ ] **Step 4: Bundle the logo**

The source is only 216 × 139 px, so store a 3× copy (smoother when shown at ~180 dp on 3× screens):

```bash
mkdir -p shared/src/commonMain/composeResources/drawable
sips --resampleHeightWidth 417 648 docs/superpowers/specs/2026-10-15-scan-screen-assets/seds-logo-dark.png \
  --out shared/src/commonMain/composeResources/drawable/seds_logo_dark.png
sips -g pixelWidth -g pixelHeight -g hasAlpha shared/src/commonMain/composeResources/drawable/seds_logo_dark.png
```
Expected: `pixelWidth: 648`, `pixelHeight: 417`, `hasAlpha: yes`.

- [ ] **Step 5: Add the header**

Create `core/presentation/AppHeader.kt`:

```kotlin
package com.example.bluefalconcomposemultiplatform.core.presentation

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import bluefalconcomposemultiplatform.shared.generated.resources.Res
import bluefalconcomposemultiplatform.shared.generated.resources.seds_logo_dark
import com.example.bluefalconcomposemultiplatform.ui.theme.TextPrimary
import com.example.bluefalconcomposemultiplatform.ui.theme.TextSecondary
import org.jetbrains.compose.resources.painterResource

@Composable
fun AppHeader(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(Res.drawable.seds_logo_dark),
            contentDescription = "SEDS Rocketry Division at ASU",
            modifier = Modifier.width(180.dp)
        )
        Text(
            "GOOBER Connect",
            color = TextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(
            "GROUND STATION",
            color = TextSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 3.sp
        )
    }
}
```

- [ ] **Step 6: Put the screen together**

Replace all of `App.kt` with:

```kotlin
package com.example.bluefalconcomposemultiplatform

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.example.bluefalconcomposemultiplatform.ble.presentation.BluetoothDeviceViewModel
import com.example.bluefalconcomposemultiplatform.ble.presentation.component.DeviceScanView
import com.example.bluefalconcomposemultiplatform.core.presentation.AppHeader
import com.example.bluefalconcomposemultiplatform.core.presentation.BlueFalconTheme
import com.example.bluefalconcomposemultiplatform.core.presentation.LaunchGlowBackground
import com.example.bluefalconcomposemultiplatform.di.AppModule
import com.example.bluefalconcomposemultiplatform.ui.theme.TextPrimary
import dev.icerock.moko.mvvm.compose.getViewModel
import dev.icerock.moko.mvvm.compose.viewModelFactory

@Composable
fun App(
    darkTheme: Boolean,
    dynamicColor: Boolean,
    appModule: AppModule
) {
    BlueFalconTheme(
        darkTheme = darkTheme,
        dynamicColor = dynamicColor
    ) {
        val viewModel = getViewModel(
            key = "bluetooth-device-screen",
            factory = viewModelFactory {
                BluetoothDeviceViewModel(appModule.blueFalcon)
            }
        )

        val state by viewModel.deviceState.collectAsState()
        val parsedPackets by viewModel.parsedPackets.collectAsState()

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Transparent,
            contentColor = TextPrimary
        ) {
            Box(Modifier.fillMaxSize()) {
                // The background fills the whole screen; content stays clear of the notch and home indicator
                LaunchGlowBackground()
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    AppHeader()
                    DeviceScanView(
                        state = state,
                        parsedPackets = parsedPackets,
                        onEvent = viewModel::onEvent
                    )
                }
            }
        }
    }
}
```

Replace all of `DeviceScanView.kt` with (the purple box behind the list goes away; cards float on the background):

```kotlin
package com.example.bluefalconcomposemultiplatform.ble.presentation.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bluefalconcomposemultiplatform.GooberPacket
import com.example.bluefalconcomposemultiplatform.ble.presentation.BluetoothDeviceState
import com.example.bluefalconcomposemultiplatform.ble.presentation.UiEvent
import kotlin.OptIn

@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
@Composable
fun DeviceScanView(
    state: BluetoothDeviceState,
    parsedPackets: Map<String, GooberPacket>,
    onEvent: (UiEvent) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        items(state.devices.values.toList().sortedByDescending { it.peripheral.name }) { device ->
            FoundDeviceCard(
                deviceName = if (!device.peripheral.name.isNullOrBlank()) device.peripheral.name else "No Name",
                macId = device.peripheral.uuid,
                rssi = device.peripheral.rssi,
                services = device.peripheral.services.values.toList(),
                connected = device.connected,
                parsedPacket = parsedPackets[device.peripheral.uuid],
                onEvent = onEvent
            )
        }
    }
}
```

- [ ] **Step 7: iOS edge to edge, dark status bar, app name**

Replace the body in `iosBlueFalconExampleMP/iosBlueFalconExampleMP/ContentView.swift`:

```swift
struct ContentView: View {
    var body: some View {
        ComposeView()
            .ignoresSafeArea()           // Compose draws the background behind the status bar and home indicator
            .preferredColorScheme(.dark) // light status-bar text on the dark background
    }
}
```

In `iosBlueFalconExampleMP/iosBlueFalconExampleMP/Info.plist`, add right after `<dict>`:

```xml
	<key>CFBundleDisplayName</key>
	<string>GOOBER Connect</string>
```

In `androidBlueFalconExampleMP/src/main/AndroidManifest.xml`, add `android:label="GOOBER Connect"` to `<application>`:

```xml
    <application
        android:allowBackup="false"
        android:label="GOOBER Connect"
        android:supportsRtl="true"
        android:theme="@style/AppTheme">
```

- [ ] **Step 8: Check it in the Simulator**

Run the **Run in the iOS Simulator** commands. Expected: the app opens without crashing (a missing logo resource would crash with a `MissingResourceException`); the screenshot shows the navy-to-glow background reaching behind the status bar and home indicator, light status-bar text, stars, the logo with white text, "GOOBER Connect" and "GROUND STATION". Compare with `docs/superpowers/specs/2026-10-15-scan-screen-assets/mockup-launch-glow-scan.png`; if the logo looks much bigger or smaller than the mockup, adjust the `180.dp` in `AppHeader`. Also confirm the home-screen icon label reads "GOOBER Connect".

Also run `./gradlew :shared:iosSimulatorArm64Test --console=plain` → still passing.

- [ ] **Step 9: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ui/theme/Color.kt \
  shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ui/theme/Theme.kt \
  shared/src/iosMain/kotlin/com/example/bluefalconcomposemultiplatform/core/presentation/BlueFalconTheme.ios.kt \
  shared/src/androidMain/kotlin/com/example/bluefalconcomposemultiplatform/core/presentation/BlueFalconTheme.android.kt \
  shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/core/presentation/LaunchGlowBackground.kt \
  shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/core/presentation/AppHeader.kt \
  shared/src/commonMain/composeResources/drawable/seds_logo_dark.png \
  shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/App.kt \
  shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/component/DeviceScanView.kt \
  iosBlueFalconExampleMP/iosBlueFalconExampleMP/ContentView.swift \
  iosBlueFalconExampleMP/iosBlueFalconExampleMP/Info.plist \
  androidBlueFalconExampleMP/src/main/AndroidManifest.xml
git commit -m "add Launch Glow look: background, logo header, edge to edge, app name"
```

---

### Task 3: Bluetooth status and the status line

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/data/PlatformBluetooth.kt`
- Create: `shared/src/iosMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/data/IosPlatformBluetooth.kt`
- Create: `shared/src/androidMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/data/AndroidPlatformBluetooth.kt`
- Modify: `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/di/AppModule.kt` and both actuals
- Modify: `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/BluetoothDeviceState.kt`
- Modify: `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/BluetoothDeviceViewModel.kt`
- Create: `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/component/ScanStatusMessage.kt`
- Modify: `DeviceScanView.kt`, `App.kt`

**Interfaces:**
- Consumes: `BluetoothStatus`, `ScanMessage`, `scanMessage(...)`, `bluetoothStatusFor(...)` (Task 1); theme colors (Task 2).
- Produces: `interface PlatformBluetooth { fun problem(): BluetoothStatus?; fun openAppSettings() }`; `AppModule.platformBluetooth: PlatformBluetooth`; `BluetoothDeviceViewModel(blueFalcon: BlueFalcon, platformBluetooth: PlatformBluetooth, delegate: BleDelegate = BleDelegate())`; `BluetoothDeviceState.bluetoothStatus: BluetoothStatus`, `.scanStartedAt: Duration?`, `.now: Duration`; private `BluetoothDeviceViewModel.now(): Duration`; `@Composable fun ScanStatusMessage(message: ScanMessage, onOpenSettings: () -> Unit, modifier: Modifier = Modifier)`; `DeviceScanView(state, parsedPackets, onEvent, onOpenSettings: () -> Unit)`.

- [ ] **Step 1: Add the platform interface and implementations**

Create `ble/data/PlatformBluetooth.kt`:

```kotlin
package com.example.bluefalconcomposemultiplatform.ble.data

// Checks that BlueFalcon can't do for us: BlueFalcon 2.2.4 always reports Ready on Android,
// and it can't open the app's settings page on either platform
interface PlatformBluetooth {
    // A problem that stops scanning, or null if nothing is wrong as far as the platform can tell
    fun problem(): BluetoothStatus?

    fun openAppSettings()
}
```

Create `shared/src/iosMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/data/IosPlatformBluetooth.kt`:

```kotlin
package com.example.bluefalconcomposemultiplatform.ble.data

import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

class IosPlatformBluetooth : PlatformBluetooth {
    // On iOS, BlueFalcon's scan() exceptions already say why Bluetooth isn't ready
    override fun problem(): BluetoothStatus? = null

    override fun openAppSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
    }
}
```

Create `shared/src/androidMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/data/AndroidPlatformBluetooth.kt`:

```kotlin
package com.example.bluefalconcomposemultiplatform.ble.data

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat

class AndroidPlatformBluetooth(private val context: Context) : PlatformBluetooth {

    override fun problem(): BluetoothStatus? {
        val adapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
            ?: return BluetoothStatus.Unsupported
        if (!hasScanPermissions()) return BluetoothStatus.NoPermission
        if (!adapter.isEnabled) return BluetoothStatus.Off
        return null
    }

    override fun openAppSettings() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    // Matches what MainActivity asks for: scan + connect on Android 12+, fine location before that
    private fun hasScanPermissions(): Boolean {
        val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return needed.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    }
}
```

- [ ] **Step 2: AppModule: one BlueFalcon, plus `platformBluetooth`**

`AppModule.blueFalcon` is currently a getter that builds a new BlueFalcon on every read. Make it lazy so it's built once, on first use.

Replace all of `di/AppModule.kt` with:

```kotlin
package com.example.bluefalconcomposemultiplatform.di

import com.example.bluefalconcomposemultiplatform.ble.data.PlatformBluetooth
import dev.bluefalcon.BlueFalcon

expect class AppModule {
    val blueFalcon: BlueFalcon
    val platformBluetooth: PlatformBluetooth
}
```

Replace all of `shared/src/iosMain/.../di/AppModule.ios.kt` with:

```kotlin
package com.example.bluefalconcomposemultiplatform.di

import com.example.bluefalconcomposemultiplatform.ble.data.IosPlatformBluetooth
import com.example.bluefalconcomposemultiplatform.ble.data.PlatformBluetooth
import dev.bluefalcon.ApplicationContext
import dev.bluefalcon.BlueFalcon
import platform.UIKit.UIApplication

actual class AppModule {
    // Built once, on first use: each BlueFalcon creates its own Bluetooth manager
    actual val blueFalcon: BlueFalcon by lazy {
        BlueFalcon(
            log = null,
            context = UIApplication.sharedApplication
        )
    }

    actual val platformBluetooth: PlatformBluetooth = IosPlatformBluetooth()
}
```

Replace all of `shared/src/androidMain/.../di/AppModule.android.kt` with:

```kotlin
package com.example.bluefalconcomposemultiplatform.di

import android.content.Context
import com.example.bluefalconcomposemultiplatform.ble.data.AndroidPlatformBluetooth
import com.example.bluefalconcomposemultiplatform.ble.data.PlatformBluetooth
import dev.bluefalcon.ApplicationContext
import dev.bluefalcon.BlueFalcon

actual class AppModule(
    private val context: Context
) {
    // Built once, on first use: each BlueFalcon creates its own Bluetooth manager
    actual val blueFalcon: BlueFalcon by lazy {
        BlueFalcon(
            log = null,
            context as ApplicationContext,
        )
    }

    actual val platformBluetooth: PlatformBluetooth = AndroidPlatformBluetooth(context)
}
```

(`ApplicationContext` stays imported in the iOS file as before; it was already unused there.)

- [ ] **Step 3: State fields**

Replace all of `ble/presentation/BluetoothDeviceState.kt` with:

```kotlin
package com.example.bluefalconcomposemultiplatform.ble.presentation

import com.example.bluefalconcomposemultiplatform.ble.data.BluetoothStatus
import kotlin.time.Duration

data class BluetoothDeviceState(
    val devices: HashMap<String, EnhancedBluetoothPeripheral> = HashMap(),
    val updateVersion: Int = 0,
    val bluetoothStatus: BluetoothStatus = BluetoothStatus.Starting,
    // When the current scan started and when the state was last refreshed, on the view model's clock
    val scanStartedAt: Duration? = null,
    val now: Duration = Duration.ZERO
)
```

- [ ] **Step 4: View model: status loop and starting the scan**

In `BluetoothDeviceViewModel.kt`:

a) Add imports (next to the existing ones):

```kotlin
import com.example.bluefalconcomposemultiplatform.ble.data.BluetoothStatus
import com.example.bluefalconcomposemultiplatform.ble.data.PlatformBluetooth
import kotlin.time.Duration
import kotlin.time.TimeSource
```

b) Change the constructor to:

```kotlin
class BluetoothDeviceViewModel(
    private val blueFalcon: BlueFalcon,
    private val platformBluetooth: PlatformBluetooth,
    delegate: BleDelegate = BleDelegate()
): ViewModel() {
```

c) Right after `private val readingJobs: MutableMap<String, Job> = mutableMapOf()`, add:

```kotlin

    // Clock for scan timing and "last heard" times, measured from when the view model starts
    private val clockStart = TimeSource.Monotonic.markNow()
    private fun now(): Duration = clockStart.elapsedNow()
```

d) In `init`, replace the first block (the `managerState` collector from the crash fix):

```kotlin
        // Start scanning automatically once Bluetooth is ready. On iOS, scan() throws
        // if called before CoreBluetooth reports powered on (e.g. right at app launch).
        CoroutineScope(Dispatchers.Main).launch {
            blueFalcon.managerState.collect { state ->
                if (state == BluetoothManagerState.Ready) {
                    blueFalcon.scan()
                }
            }
        }
```

with:

```kotlin
        // Work out the Bluetooth status whenever BlueFalcon's state changes, and once a second for
        // changes BlueFalcon doesn't report and to keep the timers ticking. The scan starts each
        // time the status becomes Ready; scan() throws if called before that.
        CoroutineScope(Dispatchers.Main).launch {
            blueFalcon.managerState.collect { refreshBluetoothStatus() }
        }
        CoroutineScope(Dispatchers.Main).launch {
            while (true) {
                delay(1000)
                refreshBluetoothStatus()
            }
        }
```

e) Add these functions right above the `/** Start continuous reading ...` doc comment of `startContinuousReading`:

```kotlin
    private fun refreshBluetoothStatus() {
        val status = platformBluetooth.problem()
            ?: if (blueFalcon.managerState.value == BluetoothManagerState.Ready) BluetoothStatus.Ready
            else notReadyReason()
        val wasReady = _deviceState.value.bluetoothStatus == BluetoothStatus.Ready
        _deviceState.update { it.copy(bluetoothStatus = status, now = now()) }
        if (status == BluetoothStatus.Ready && !wasReady) {
            startScan()
        }
    }

    // BlueFalcon only reports Ready/NotReady, but on iOS scan() throws a different exception for
    // each not-ready Bluetooth state, so trying it tells us why Bluetooth isn't ready
    private fun notReadyReason(): BluetoothStatus = try {
        blueFalcon.scan()
        BluetoothStatus.Ready
    } catch (e: Exception) {
        bluetoothStatusFor(e)
    }

    private fun startScan() {
        try {
            blueFalcon.scan()
            _deviceState.update { it.copy(scanStartedAt = now()) }
        } catch (e: Exception) {
            _deviceState.update { it.copy(bluetoothStatus = bluetoothStatusFor(e)) }
        }
    }

```

- [ ] **Step 5: The status line**

Create `ble/presentation/component/ScanStatusMessage.kt`:

```kotlin
package com.example.bluefalconcomposemultiplatform.ble.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluefalconcomposemultiplatform.ble.presentation.ScanMessage
import com.example.bluefalconcomposemultiplatform.ui.theme.AsuGold
import com.example.bluefalconcomposemultiplatform.ui.theme.CardBackground
import com.example.bluefalconcomposemultiplatform.ui.theme.CardBorder
import com.example.bluefalconcomposemultiplatform.ui.theme.OnAsuGold
import com.example.bluefalconcomposemultiplatform.ui.theme.SignalTrack
import com.example.bluefalconcomposemultiplatform.ui.theme.TextPrimary
import com.example.bluefalconcomposemultiplatform.ui.theme.TextSecondary

@Composable
fun ScanStatusMessage(
    message: ScanMessage,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Problems the user has to fix get a gold border and a "!" instead of a spinner
    val problem = message == ScanMessage.BluetoothOff ||
        message == ScanMessage.NoPermission ||
        message == ScanMessage.Unsupported
    val shape = RoundedCornerShape(14.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CardBackground)
            .border(1.dp, if (problem) AsuGold.copy(alpha = 0.45f) else CardBorder, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (problem) {
                // An icon rather than Text("!"): the theme's 24sp line height pushes a small "!" out of an 18dp circle
                Icon(Icons.Filled.Error, contentDescription = null, tint = AsuGold, modifier = Modifier.size(20.dp))
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = AsuGold,
                    trackColor = SignalTrack,
                    strokeWidth = 2.dp
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(title(message), color = TextPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }

        when (message) {
            ScanMessage.NoPermission -> {
                Text(
                    "Turn on Bluetooth for this app in Settings to find your ASCENT.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Button(
                    onClick = onOpenSettings,
                    colors = ButtonDefaults.buttonColors(containerColor = AsuGold, contentColor = OnAsuGold),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    modifier = Modifier.padding(top = 10.dp)
                ) {
                    Text("Open Settings", fontWeight = FontWeight.Bold)
                }
            }
            ScanMessage.NotFound -> {
                Column(Modifier.padding(top = 6.dp)) {
                    Text("•  Make sure the ASCENT is powered on", color = TextSecondary, fontSize = 13.sp)
                    Text("•  Bring your phone closer to the board", color = TextSecondary, fontSize = 13.sp)
                    Text(
                        "Still searching…",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
            else -> {}
        }
    }
}

private fun title(message: ScanMessage): String = when (message) {
    ScanMessage.BluetoothOff -> "Bluetooth is off. Turn it on to find your ASCENT."
    ScanMessage.NoPermission -> "GOOBER Connect isn't allowed to use Bluetooth"
    ScanMessage.Unsupported -> "This device doesn't support Bluetooth LE."
    ScanMessage.StartingBluetooth -> "Starting Bluetooth…"
    ScanMessage.Searching -> "Searching for ASCENT…"
    ScanMessage.NotFound -> "No ASCENT found yet"
}
```

- [ ] **Step 6: Show it above the list**

Replace all of `DeviceScanView.kt` with:

```kotlin
package com.example.bluefalconcomposemultiplatform.ble.presentation.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bluefalconcomposemultiplatform.GooberPacket
import com.example.bluefalconcomposemultiplatform.ble.presentation.BluetoothDeviceState
import com.example.bluefalconcomposemultiplatform.ble.presentation.UiEvent
import com.example.bluefalconcomposemultiplatform.ble.presentation.scanMessage
import kotlin.OptIn

@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
@Composable
fun DeviceScanView(
    state: BluetoothDeviceState,
    parsedPackets: Map<String, GooberPacket>,
    onEvent: (UiEvent) -> Unit,
    onOpenSettings: () -> Unit
) {
    val message = scanMessage(
        status = state.bluetoothStatus,
        audibleBoards = state.devices.size,
        scanningFor = state.scanStartedAt?.let { state.now - it }
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        message?.let {
            item { ScanStatusMessage(it, onOpenSettings, Modifier.padding(bottom = 10.dp)) }
        }
        items(state.devices.values.toList().sortedByDescending { it.peripheral.name }) { device ->
            FoundDeviceCard(
                deviceName = if (!device.peripheral.name.isNullOrBlank()) device.peripheral.name else "No Name",
                macId = device.peripheral.uuid,
                rssi = device.peripheral.rssi,
                services = device.peripheral.services.values.toList(),
                connected = device.connected,
                parsedPacket = parsedPackets[device.peripheral.uuid],
                onEvent = onEvent
            )
        }
    }
}
```

- [ ] **Step 7: Wire it up in `App.kt`**

In `App.kt`, change the view model factory line to:

```kotlin
                BluetoothDeviceViewModel(appModule.blueFalcon, appModule.platformBluetooth)
```

and the `DeviceScanView(...)` call to:

```kotlin
                    DeviceScanView(
                        state = state,
                        parsedPackets = parsedPackets,
                        onEvent = viewModel::onEvent,
                        onOpenSettings = appModule.platformBluetooth::openAppSettings
                    )
```

- [ ] **Step 8: Verify**

1. `./gradlew :shared:iosSimulatorArm64Test --console=plain` → passing.
2. Simulator run → a status line appears (the Simulator has no Bluetooth, so expect "This device doesn't support Bluetooth LE." or "Starting Bluetooth…"); no crash.
3. iPhone run (with `--console`):
   - With Bluetooth on: "Searching for ASCENT…", then "No ASCENT found yet" with tips after 15 s (board off).
   - Settings → Bluetooth off → within about a second "Bluetooth is off. Turn it on to find your ASCENT."; on again → "Searching…".
   - Settings → GOOBER Connect → Bluetooth off → "GOOBER Connect isn't allowed to use Bluetooth"; **Open Settings** opens the app's settings page; switching it back on returns to "Searching…".
   - No `Uncaught Kotlin exception` in the console.

- [ ] **Step 9: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/data/PlatformBluetooth.kt \
  shared/src/iosMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/data/IosPlatformBluetooth.kt \
  shared/src/androidMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/data/AndroidPlatformBluetooth.kt \
  shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/di/AppModule.kt \
  shared/src/iosMain/kotlin/com/example/bluefalconcomposemultiplatform/di/AppModule.ios.kt \
  shared/src/androidMain/kotlin/com/example/bluefalconcomposemultiplatform/di/AppModule.android.kt \
  shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/BluetoothDeviceState.kt \
  shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/BluetoothDeviceViewModel.kt \
  shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/component/ScanStatusMessage.kt \
  shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/component/DeviceScanView.kt \
  shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/App.kt
git commit -m "show Bluetooth status above the device list and scan whenever Bluetooth becomes ready"
```

---

### Task 4: Live signal and boards that go quiet

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/data/DeviceEvent.kt`
- Modify: `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/data/BleDelegate.kt`
- Modify: `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/EnhancedBluetoothPeripheral.kt`
- Modify: `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/BluetoothDeviceViewModel.kt`
- Create: `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/component/SignalBars.kt`
- Modify: `DeviceScanView.kt`, `FoundDeviceCard.kt`

**Interfaces:**
- Consumes: `smoothRssi`, `sinceLastSeen`, `isGone`, `signalBars`, `scanMessage` (Task 1); `now()` and the `BluetoothDeviceState` fields (Task 3); theme colors (Task 2).
- Produces: `DeviceEvent.OnAdvertisement(macId: String, peripheral: BluetoothPeripheral, rssi: Float?)`; `EnhancedBluetoothPeripheral.rssi: Float?`, `.lastSeenAt: Duration?`; `@Composable fun SignalBars(bars: Int, modifier: Modifier = Modifier)`; `FoundDeviceCard(deviceName, macId, rssi: Float?, goneFor: Duration?, services, connected, parsedPacket, onEvent)`.

- [ ] **Step 1: A broadcast event**

In `DeviceEvent.kt`, add inside the `DeviceEvent` interface (after `OnDeviceDisconnected`):

```kotlin
    // A broadcast from a board we're not connected to; BlueFalcon reports every one while scanning
    data class OnAdvertisement(val macId: String, val peripheral: BluetoothPeripheral, val rssi: Float?): DeviceEvent
```

In `BleDelegate.kt`, add after `setListener`:

```kotlin
    override fun didDiscoverDevice(
        bluetoothPeripheral: BluetoothPeripheral,
        advertisementData: Map<AdvertisementDataRetrievalKeys, Any>
    ) {
        onDeviceEvent?.let {
            it(DeviceEvent.OnAdvertisement(bluetoothPeripheral.uuid, bluetoothPeripheral, bluetoothPeripheral.rssi))
        }
    }
```

(`AdvertisementDataRetrievalKeys` is already imported.)

- [ ] **Step 2: Per-board signal and last-heard time**

Replace all of `EnhancedBluetoothPeripheral.kt` with:

```kotlin
package com.example.bluefalconcomposemultiplatform.ble.presentation

import dev.bluefalcon.BluetoothPeripheral
import kotlin.time.Duration

data class EnhancedBluetoothPeripheral(
    val connected: Boolean,
    val peripheral: BluetoothPeripheral,
    // Smoothed signal strength from the board's broadcasts, in dBm
    val rssi: Float? = null,
    // When the board was last heard, on the view model's clock
    val lastSeenAt: Duration? = null
)
```

- [ ] **Step 3: View model: boards come from broadcasts**

In `BluetoothDeviceViewModel.kt`:

a) In the `delegate.setListener` `when`, add a branch (e.g. first):

```kotlin
                is DeviceEvent.OnAdvertisement -> onAdvertisement(event)

```

b) In the `OnDeviceDisconnected` branch, replace `updateDevices[event.macId] = it.copy(connected = false)` with:

```kotlin
                            // Count the moment of disconnecting as "heard", so the card doesn't show
                            // as gone before the board's broadcasts come back
                            updateDevices[event.macId] = it.copy(connected = false, lastSeenAt = now())
```

c) In the `OnServicesDiscovered` branch, replace

```kotlin
                            updateDevices[event.peripheral.uuid] = EnhancedBluetoothPeripheral(
                                connected = peripheralState.connected,
                                peripheral = event.peripheral
                            )
```

with (keeps signal and last-heard time):

```kotlin
                            updateDevices[event.peripheral.uuid] = peripheralState.copy(peripheral = event.peripheral)
```

d) Delete the `blueFalcon.peripherals` collector at the end of `init`: the whole `CoroutineScope(Dispatchers.IO).launch { blueFalcon.peripherals.collect { peripherals -> ... } }` block that follows `blueFalcon.delegates.add(delegate)`. Boards are now added by `onAdvertisement`.

e) Add this function above `refreshBluetoothStatus()`:

```kotlin
    private fun onAdvertisement(event: DeviceEvent.OnAdvertisement) {
        if (event.peripheral.name?.contains("ASCENT", ignoreCase = true) != true) return
        val heardAt = now()
        _deviceState.update { state ->
            val devices = HashMap(state.devices)
            val existing = devices[event.macId]
            devices[event.macId] = if (existing == null) {
                EnhancedBluetoothPeripheral(
                    connected = false,
                    peripheral = event.peripheral,
                    rssi = event.rssi,
                    lastSeenAt = heardAt
                )
            } else {
                // Keep the existing peripheral: after connecting it holds the discovered services
                existing.copy(
                    rssi = event.rssi?.let { smoothRssi(existing.rssi, it) } ?: existing.rssi,
                    lastSeenAt = heardAt
                )
            }
            state.copy(devices = devices, now = heardAt, updateVersion = state.updateVersion + 1)
        }
    }

```

- [ ] **Step 4: Signal bars**

Create `ble/presentation/component/SignalBars.kt`:

```kotlin
package com.example.bluefalconcomposemultiplatform.ble.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bluefalconcomposemultiplatform.ui.theme.AsuGold
import com.example.bluefalconcomposemultiplatform.ui.theme.SignalTrack

// Four bars of increasing height; the first `bars` are lit
@Composable
fun SignalBars(bars: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.height(14.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        listOf(5.dp, 8.dp, 11.dp, 14.dp).forEachIndexed { index, barHeight ->
            Box(
                Modifier
                    .width(3.5.dp)
                    .height(barHeight)
                    .background(if (index < bars) AsuGold else SignalTrack, RoundedCornerShape(1.dp))
            )
        }
    }
}
```

- [ ] **Step 5: Card header: signal, gone state, restyle**

In `FoundDeviceCard.kt`:

a) Add these imports (keep the existing ones):

```kotlin
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import com.example.bluefalconcomposemultiplatform.ble.presentation.signalBars
import com.example.bluefalconcomposemultiplatform.ui.theme.AsuGold
import com.example.bluefalconcomposemultiplatform.ui.theme.CardBackground
import com.example.bluefalconcomposemultiplatform.ui.theme.CardBorder
import com.example.bluefalconcomposemultiplatform.ui.theme.ConnectedGreen
import com.example.bluefalconcomposemultiplatform.ui.theme.OnAsuGold
import com.example.bluefalconcomposemultiplatform.ui.theme.SignalTrack
import com.example.bluefalconcomposemultiplatform.ui.theme.TextPrimary
import com.example.bluefalconcomposemultiplatform.ui.theme.TextSecondary
import kotlin.math.roundToInt
import kotlin.time.Duration
```

b) Replace everything from `@OptIn(ExperimentalUuidApi::class)` (just above `fun FoundDeviceCard(`) down to, but **not including**, the line `        // Display parsed Goober packet if connected` with:

```kotlin
@OptIn(ExperimentalUuidApi::class)
@Composable
fun FoundDeviceCard(
    deviceName: String?,
    macId: String,
    rssi: Float?,
    goneFor: Duration?,
    services: List<BluetoothService>,
    connected: Boolean,
    parsedPacket: GooberPacket?,
    onEvent: (UiEvent) -> Unit
) {
    // Helper function to normalize UUID for comparison (handles short and full formats)
    fun normalizeUuidForComparison(uuid: String): String {
        val cleaned = uuid.replace("-", "").lowercase()
        return if (cleaned.length <= 8) {
            cleaned.takeLast(4).padStart(4, '0')
        } else {
            cleaned.take(8).takeLast(4).padStart(4, '0')
        }
    }

    // Target UUID: 00000001-0000-1000-8000-00805f9b34fb
    // Also handle short UUID formats (0001, 00000001)
    val targetUuidStrings = listOf(
        "00000001-0000-1000-8000-00805f9b34fb",  // Full UUID
        "0001",                                   // Short 16-bit
        "00000001"                                // Alternative short format
    )

    // Find the characteristic with UUID 00000001 from all services
    val targetCharacteristic = services.flatMap { it.characteristics }
        .firstOrNull { characteristic ->
            val normalizedCharUuid = normalizeUuidForComparison(characteristic.uuid.toString())
            targetUuidStrings.any { target ->
                normalizedCharUuid == normalizeUuidForComparison(target)
            }
        }

    // A board that has gone quiet is dimmed and can't be connected to until it's heard again
    val gone = goneFor != null
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .padding(vertical = 5.dp)
            .fillMaxWidth()
            .alpha(if (gone) 0.42f else 1f)
            .clip(shape)
            .background(CardBackground)
            .border(1.dp, CardBorder, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "$deviceName",
                    color = TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    when {
                        connected -> {
                            Box(Modifier.size(8.dp).background(ConnectedGreen, CircleShape))
                            Spacer(Modifier.width(6.dp))
                            Text("Connected", color = TextSecondary, fontSize = 13.sp)
                        }
                        goneFor != null -> Text(
                            "Not seen for ${goneFor.inWholeSeconds} s",
                            color = TextSecondary,
                            fontSize = 13.sp
                        )
                        else -> {
                            SignalBars(signalBars(rssi))
                            rssi?.let {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "${it.roundToInt()} dBm",
                                    color = TextSecondary,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                        }
                    }
                }
            }
            if (connected) {
                OutlinedButton(
                    onClick = { onEvent(UiEvent.OnDisconnectClick(macId)) },
                    border = BorderStroke(1.dp, TextPrimary.copy(alpha = 0.28f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("Disconnect", fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = { onEvent(UiEvent.OnConnectClick(macId)) },
                    enabled = !gone,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AsuGold,
                        contentColor = OnAsuGold,
                        disabledContainerColor = SignalTrack,
                        disabledContentColor = TextPrimary
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text("Connect", fontWeight = FontWeight.Bold)
                }
            }
        }

```

The connected section below (from `// Display parsed Goober packet if connected` to the end of the file) is unchanged in this task; Task 5 replaces it.

- [ ] **Step 6: Feed the cards and count only boards that can be heard**

Replace all of `DeviceScanView.kt` with:

```kotlin
package com.example.bluefalconcomposemultiplatform.ble.presentation.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bluefalconcomposemultiplatform.GooberPacket
import com.example.bluefalconcomposemultiplatform.ble.presentation.BluetoothDeviceState
import com.example.bluefalconcomposemultiplatform.ble.presentation.UiEvent
import com.example.bluefalconcomposemultiplatform.ble.presentation.isGone
import com.example.bluefalconcomposemultiplatform.ble.presentation.scanMessage
import com.example.bluefalconcomposemultiplatform.ble.presentation.sinceLastSeen
import kotlin.OptIn

@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
@Composable
fun DeviceScanView(
    state: BluetoothDeviceState,
    parsedPackets: Map<String, GooberPacket>,
    onEvent: (UiEvent) -> Unit,
    onOpenSettings: () -> Unit
) {
    // Same order as before (by name), so cards don't jump around as signals change
    val devices = state.devices.values.toList().sortedByDescending { it.peripheral.name }
    // How long each board has been quiet, for boards that count as gone
    val goneFor = devices.associate { device ->
        device.peripheral.uuid to sinceLastSeen(state.now, device.lastSeenAt)
            ?.takeIf { isGone(it, device.connected) }
    }
    val message = scanMessage(
        status = state.bluetoothStatus,
        audibleBoards = devices.count { goneFor[it.peripheral.uuid] == null },
        scanningFor = state.scanStartedAt?.let { state.now - it }
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        message?.let {
            item { ScanStatusMessage(it, onOpenSettings, Modifier.padding(bottom = 10.dp)) }
        }
        items(devices) { device ->
            FoundDeviceCard(
                deviceName = if (!device.peripheral.name.isNullOrBlank()) device.peripheral.name else "No Name",
                macId = device.peripheral.uuid,
                rssi = device.rssi,
                goneFor = goneFor[device.peripheral.uuid],
                services = device.peripheral.services.values.toList(),
                connected = device.connected,
                parsedPacket = parsedPackets[device.peripheral.uuid],
                onEvent = onEvent
            )
        }
    }
}
```

- [ ] **Step 7: Verify**

1. `./gradlew :shared:iosSimulatorArm64Test --console=plain` → passing.
2. iPhone run (with `--console`), with the ASCENT board:
   - Board on → its card appears with bars and e.g. "-67 dBm"; the "Searching…" line disappears.
   - Walk away / come back → the bars and number change within a few seconds, without flicker.
   - Switch the board off → after 10 s the card dims, shows "Not seen for 10 s" counting up, Connect is disabled, and "Searching for ASCENT…" returns above it; switch it on → the card lights up again.
   - Connect → card shows the green "Connected" dot and Disconnect; telemetry still appears (old styling until Task 5); disconnect → the card does not flash "Not seen".

- [ ] **Step 8: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/data/DeviceEvent.kt \
  shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/data/BleDelegate.kt \
  shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/EnhancedBluetoothPeripheral.kt \
  shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/BluetoothDeviceViewModel.kt \
  shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/component/SignalBars.kt \
  shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/component/DeviceScanView.kt \
  shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/component/FoundDeviceCard.kt
git commit -m "show live signal strength and dim boards that stop broadcasting"
```

---

### Task 5: Connected board: restyled telemetry, four commands and a color key

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/component/FoundDeviceCard.kt`

**Interfaces:**
- Consumes: `formatFloat`, `formatDouble`, `formatPyroState` (top of `FoundDeviceCard.kt`, unchanged); `GooberMsgType`, `GooberPayload` (`goober.kt`); `UiEvent.OnSendSingleByteCommand(macId: String, msgType: UByte)`; theme colors (Task 2).
- Produces: nothing used elsewhere (all new composables are `private`).

- [ ] **Step 1: Final imports**

Replace the whole import block of `FoundDeviceCard.kt` (everything between the `package` line and the first `// Multiplatform-compatible number formatting helper` comment) with:

```kotlin
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluefalconcomposemultiplatform.GooberMsgType
import com.example.bluefalconcomposemultiplatform.GooberPacket
import com.example.bluefalconcomposemultiplatform.GooberPayload
import com.example.bluefalconcomposemultiplatform.ble.presentation.UiEvent
import com.example.bluefalconcomposemultiplatform.ble.presentation.signalBars
import com.example.bluefalconcomposemultiplatform.ui.theme.AsuGold
import com.example.bluefalconcomposemultiplatform.ui.theme.CardBackground
import com.example.bluefalconcomposemultiplatform.ui.theme.CardBorder
import com.example.bluefalconcomposemultiplatform.ui.theme.CommandGreen
import com.example.bluefalconcomposemultiplatform.ui.theme.CommandPurple
import com.example.bluefalconcomposemultiplatform.ui.theme.CommandRed
import com.example.bluefalconcomposemultiplatform.ui.theme.ConnectedGreen
import com.example.bluefalconcomposemultiplatform.ui.theme.OnAsuGold
import com.example.bluefalconcomposemultiplatform.ui.theme.SignalTrack
import com.example.bluefalconcomposemultiplatform.ui.theme.TextPrimary
import com.example.bluefalconcomposemultiplatform.ui.theme.TextSecondary
import dev.bluefalcon.BluetoothService
import kotlin.OptIn
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.uuid.ExperimentalUuidApi
```

- [ ] **Step 2: Replace the connected section and the commands**

Replace everything from the line `        // Display parsed Goober packet if connected` to the **end of the file** with:

```kotlin
        // Telemetry and commands for a connected board
        if (connected) {
            PacketPanel(parsedPacket, Modifier.padding(top = 12.dp))
            // Commands need the target characteristic, which appears once services are discovered
            if (targetCharacteristic != null) {
                CommandPanel(macId, onEvent, Modifier.padding(top = 10.dp))
            }
        }
    }
}

@Composable
private fun PacketPanel(packet: GooberPacket?, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.35f))
            .border(1.dp, Color.White.copy(alpha = 0.07f), shape)
            .padding(12.dp)
    ) {
        Text(
            "LATEST PACKET",
            color = AsuGold,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )
        if (packet == null) {
            Text(
                "No data received",
                color = TextSecondary,
                fontSize = 13.sp,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(top = 6.dp)
            )
            return@Column
        }

        // Packet header on one line, e.g. "DEV 0x01 · MODE 0x02 · SEQ 0x2A · POST_TELEM · 33 B"
        Text(
            "DEV ${hex(packet.devId)} · MODE ${hex(packet.devMode)} · SEQ ${hex(packet.seqId)} · " +
                "${packet.msgCls.name.removePrefix("MSG_TYPE_")} · ${packet.payloadSize} B",
            color = TextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 6.dp, bottom = 8.dp)
        )

        val telemetry = (packet.payload as? GooberPayload.Telemetry)?.payload ?: return@Column
        TelemetryRow("Altitude AGL", "${formatFloat(telemetry.altitudeAgl)} m")
        TelemetryRow("Vertical velocity", "${formatFloat(telemetry.verticalVelocity)} m/s")
        TelemetryRow("X acceleration", "${formatFloat(telemetry.xAcc)} m/s²")
        TelemetryRow("Gyro X", formatFloat(telemetry.gyrX))
        TelemetryRow("Pyro state", formatPyroState(telemetry.pyroState))
        TelemetryRow("Satellites", "${telemetry.sats}")
        TelemetryRow("Flight state", hex(telemetry.flightState))
        TelemetryRow("Battery", "${formatDouble(telemetry.batteryVoltage.toDouble() / 2500.0)} V")
        TelemetryRow("Timestamp", "${telemetry.timestamp}")
    }
}

@Composable
private fun TelemetryRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = TextPrimary, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
    }
}

private fun hex(value: UByte): String = "0x${value.toString(16).padStart(2, '0').uppercase()}"

// What each command button's color means; shown in the key under the buttons
private enum class CommandKind(val color: Color, val title: String, val description: String) {
    SYSTEM(CommandRed, "System", "Locks the radio or restarts the board"),
    AUXILIARY(CommandPurple, "Auxiliary", "Switches on the AUX output"),
    WAKE(CommandGreen, "Wake", "Wakes the board up")
}

// Commands the app can send. Apogee and Mains (MSG_TYPE_REQ_POP_*) are deliberately left out:
// Bluetooth can't reach the rocket in flight and the protocol doesn't confirm they were received.
private enum class BoardCommand(
    val label: String,
    val kind: CommandKind,
    val msgType: GooberMsgType,
    val confirmTitle: String,
    val confirmMessage: String
) {
    TX_LOCK("TX Lock", CommandKind.SYSTEM, GooberMsgType.MSG_TYPE_REQ_TXLOCK_ACTIVATE,
        "Confirm TX Lock", "Are you sure you want to activate TX Lock?"),
    AUX("AUX", CommandKind.AUXILIARY, GooberMsgType.MSG_TYPE_REQ_AUX_ACTIVATE,
        "Confirm AUX", "Are you sure you want to activate AUX?"),
    REBOOT("Reboot", CommandKind.SYSTEM, GooberMsgType.MSG_TYPE_REQ_REBOOT,
        "Confirm Reboot", "Are you sure you want to reboot the device?"),
    WAKEUP("Wakeup", CommandKind.WAKE, GooberMsgType.MSG_TYPE_REQ_WAKEUP,
        "Confirm Wakeup", "Are you sure you want to wake up the device?")
}

@Composable
private fun CommandPanel(macId: String, onEvent: (UiEvent) -> Unit, modifier: Modifier = Modifier) {
    // The command waiting for confirmation, if any
    var pendingCommand by remember { mutableStateOf<BoardCommand?>(null) }

    Column(modifier.fillMaxWidth()) {
        listOf(
            listOf(BoardCommand.TX_LOCK, BoardCommand.AUX),
            listOf(BoardCommand.REBOOT, BoardCommand.WAKEUP)
        ).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { command ->
                    Button(
                        onClick = { pendingCommand = command },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = command.kind.color,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Text(command.label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        CommandLegend(Modifier.padding(top = 6.dp))
    }

    pendingCommand?.let { command ->
        ConfirmationDialog(
            command = command,
            onConfirm = {
                onEvent(UiEvent.OnSendSingleByteCommand(macId, command.msgType.value))
                pendingCommand = null
            },
            onDismiss = { pendingCommand = null }
        )
    }
}

@Composable
private fun CommandLegend(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        HorizontalDivider(color = CardBorder)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CommandKind.entries.forEach { kind ->
                Row(Modifier.weight(1f)) {
                    Box(
                        Modifier
                            .padding(top = 3.dp)
                            .size(10.dp)
                            .background(kind.color, RoundedCornerShape(3.dp))
                    )
                    Spacer(Modifier.width(6.dp))
                    Column {
                        Text(kind.title, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(kind.description, color = TextSecondary, fontSize = 11.sp, lineHeight = 14.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmationDialog(
    command: BoardCommand,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(command.confirmTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = { Text(command.confirmMessage, fontSize = 14.sp) },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Confirm") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
```

- [ ] **Step 3: Check nothing still sends Apogee/Mains**

Run: `grep -rn "POP_APOGEE\|POP_MAINS" shared/src`
Expected: only the two enum entries in `goober.kt`.

- [ ] **Step 4: Verify**

1. `./gradlew :shared:iosSimulatorArm64Test --console=plain` → passing.
2. iPhone run with the board, connect → compare with `docs/superpowers/specs/2026-10-15-scan-screen-assets/mockup-launch-glow-states.png`: "LATEST PACKET" panel with the one-line header and two-column values; TX Lock and AUX, then Reboot and Wakeup; the three-color key underneath; no Apogee or Mains.
3. Tap each command → confirmation dialog with the same wording as before; Confirm sends it (console shows `DEBUG WRITE: Sending command ...` with the right MSG_TYPE: 0x14, 0x0A, 0x1E, 0x6B); Cancel sends nothing.

- [ ] **Step 5: Commit**

```bash
git add shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/component/FoundDeviceCard.kt
git commit -m "restyle connected board, add command color key, remove Apogee and Mains buttons"
```

---

### Task 6: Full check on the iPhone and hand-off for Android

**Files:**
- Modify: `docs/superpowers/specs/2026-10-15-scan-screen-design.md` (status line only)

- [ ] **Step 1: Run the full spec checklist on the iPhone**

Fresh install (delete the app first so the Bluetooth prompt appears), then work through the spec's "Testing → On an iPhone" list 1–7. Also check with the phone in **light** mode (Settings → Display & Brightness): the app must still be dark with light status-bar text. Note anything that fails and fix it before continuing.

- [ ] **Step 2: Android (if an Android SDK is available on any machine)**

```bash
./gradlew :androidBlueFalconExampleMP:assembleDebug --console=plain
```
Expected: `BUILD SUCCESSFUL`. Then install on an Android 12+ phone and run the same checklist, including a fresh install: the permission prompt appears, the app doesn't crash before you answer it, and scanning starts once you allow it. If no Android SDK is available, leave this for the teammate and say so in the PR.

- [ ] **Step 3: Update the spec status**

In `docs/superpowers/specs/2026-10-15-scan-screen-design.md`, change the status line to:

```markdown
- **Status:** Implemented on branch `scan-screen-redesign`; checked on iPhone; Android not yet checked
```

(Write "Android checked" instead if Step 2 was done.)

- [ ] **Step 4: Commit**

```bash
git add docs/superpowers/specs/2026-10-15-scan-screen-design.md
git commit -m "mark scan screen design as implemented"
```
