# Pre-flight Checklist Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Put the team's 29-step pre-flight checklist in the app. Every step is ticked by a person and stamped with their initials and the time. Live ASCENT readings appear under steps 9.11, 9.14 and G.3, and everything is saved on the phone.

**Architecture:**
- **Steps:** the steps and the reading limits live in `ChecklistSteps.kt`.
- **Rules:** every decision is a pure function in `ChecklistRules.kt`, covered by unit tests: which board to read, the reading values, what a tap does, tick records, progress, the 12-hour rule, and saving as text.
- **View model:** a small `ChecklistViewModel` holds the ticks and the initials. It saves them through a `ChecklistStorage` that `AppModule` provides.
- **Screen:** `ChecklistScreen.kt` draws the screen and its dialogs, and `App.kt` switches between the scan screen and the checklist.
- **Readings:** worked out from `BluetoothDeviceViewModel` state, which gains each board's latest telemetry in Task 4.

**Tech Stack:**
- Kotlin 2.0.20, Compose Multiplatform 1.6.11 (Material3, material icons extended), moko-mvvm 0.16.1, BlueFalcon 2.2.4, kotlin.test.
- iOS: `NSUserDefaults` and `NSDateFormatter`.
- Android: `SharedPreferences`, `java.text.DateFormat` and `androidx.activity.compose.BackHandler`.

Spec: [`docs/superpowers/specs/2026-10-22-preflight-checklist-design.md`](../specs/2026-10-22-preflight-checklist-design.md) (revised 2026-10-26, commit `172372e`)

## Global Constraints

- **No new libraries and no Gradle changes.** Use only what `shared/build.gradle.kts` already has.
- **Where code goes:**
  - Checklist code: package `com.example.bluefalconcomposemultiplatform.checklist`.
  - Platform pieces: under `shared/src/iosMain` and `shared/src/androidMain`, with the same package as their common counterpart.
- **The checklist never sends anything to the board.** It only reads the Bluetooth view model's state.
- **Steps:**
  - The 29 step texts are exactly as in the spec's tables; Task 1 Step 1 has them in code.
  - Ids are `<section code>.<number>`: `8.1`…`8.8`, `9.1`…`9.15`, `G.1`…`G.6`.
  - Section labels: "8 · ONSITE PREFLIGHT READY", "9 · PRE-LAUNCH", "G · LAUNCH".
- **Readings:** under 9.11, Battery, Drogue continuity, Main continuity and GPS; under 9.14 and under G.3, GPS only. No other step has readings.
- **Limits:**
  - `MIN_BATTERY_VOLTS = 7.4`, `MIN_SATELLITES = 6`, `FRESH_TELEMETRY = 3 s`, `DROGUE_PYRO_CHANNEL = 1`, `MAIN_PYRO_CHANNEL = 2`.
  - Pyro channel *n* is bit *n − 1* of `pyroState`.
  - Battery volts are the raw value ÷ 2500.
- **Ticking:**
  - Every step is ticked by a person. A tick records the initials and the wall-clock time.
  - Initials: letters only, capitals, at most 4.
  - Ticking with a red reading asks first; grey readings never ask; unticking never asks.
  - Initials are asked for before the red-reading question.
- **Saving:**
  - Ticks are saved under the key `preflight-checklist` in the `v2` text format.
  - Initials are saved under `preflight-initials`.
  - Readings are never saved.
  - Unreadable text and the old `v1` format give an empty checklist.
- **Old checklist:** started more than 12 hours ago.
- **Wording:** all on-screen wording is copied from the spec. Use the strings in this plan exactly.
- **Colours:**

  | Use | Colour |
  |---|---|
  | Ticked | green `#3DDC84` |
  | Stamp and fine readings | `#7EE8AE` |
  | Problem readings | `#FF8A80` |
  | Empty ring | white at 35% |
  | Reading block | background black at 28%, border white at 7% |
  | Accents | gold `#FFC627` |
- **Platforms:** iOS is built and tested on this Mac. Android can't be built here (no SDK), so a teammate checks it.
- **Committing:** the user commits. Each task ends with `git add`/`git commit` commands for them. Never add `iosBlueFalconExampleMP/iosBlueFalconExampleMP.xcodeproj/project.pbxproj`; it holds the user's local signing.

---

## Before you start

1. Work on the branch `preflight-checklist`, which the user already created from `scan-screen-redesign`. It holds the scan-screen code the checklist builds on and the revised spec.
2. Run the unit tests once (command below). Expected: `tests="11" ... failures="0"` (the scan rules).

## Commands used in several tasks

Run everything from the repo root, `/Users/cliverafiq/dev/ascent-app`.

**Unit tests (iOS Simulator):**
```bash
./gradlew :shared:iosSimulatorArm64Test --console=plain
grep -ho 'tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' shared/build/test-results/iosSimulatorArm64Test/*.xml
```
The second line prints one summary per test class.

**iOS compile check (fast):**
```bash
./gradlew :shared:compileKotlinIosArm64 --console=plain
```

**Run in the iOS Simulator and take a screenshot.** The Simulator has no Bluetooth, which suits the checklist: reading blocks show "No board connected".
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
Then open `build/xcode-sim/screen.png` (`build/` is git-ignored).

**Build, install and launch on the iPhone** ("Boeuf"):
```bash
xcodebuild -project iosBlueFalconExampleMP/iosBlueFalconExampleMP.xcodeproj -scheme iosBlueFalconExampleMP \
  -configuration Debug -destination 'id=00008110-00063D160C20201E' -derivedDataPath build/xcode-device \
  -allowProvisioningUpdates -allowProvisioningDeviceRegistration \
  DEVELOPMENT_TEAM=LQN7334YGH PRODUCT_BUNDLE_IDENTIFIER=cliverafiq.goober1 build -quiet
xcrun devicectl device install app --device 00008110-00063D160C20201E \
  build/xcode-device/Build/Products/Debug-iphoneos/iosBlueFalconExampleMP.app
xcrun devicectl device process launch --device 00008110-00063D160C20201E --terminate-existing --console cliverafiq.goober1
```
Before running it, check these:
- **The phone is reachable.** It must be unlocked and either on a cable or on the same network. Campus Wi-Fi blocks this, so join the iPhone's Personal Hotspot from the Mac. `xcrun devicectl list devices` should show Boeuf as `connected` or `available (paired)`.
- **Xcode has the Apple account signed in.** If the build says "No Accounts", the user signs in again under Xcode → Settings → Accounts.
- **Free signing lasts 7 days.** The current one runs until 2026-10-29 19:13. If the app won't open, rebuild. If iOS then says the developer isn't trusted, the user trusts it again under Settings → General → VPN & Device Management.
- **`--console`** prints the app's output, including any `Uncaught Kotlin exception`. It blocks until the app exits.

## File structure

| File | New / changed | Responsibility |
|---|---|---|
| `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/checklist/ChecklistSteps.kt` | new | Sections, the 29 steps, which readings show under which step, the 5 limits. This is the file the team edits. |
| `.../checklist/ChecklistRules.kt` (commonMain) | new | Pure functions: board choice, a reading block's first line, reading values, the red readings under a step, what a tap does, tick records, cleaning initials, progress text, the 12-hour rule, saving as text, number formatting |
| `.../checklist/ChecklistStorage.kt` (commonMain) | new | `ChecklistStorage` (load/save text by key) and the two keys |
| `.../checklist/ClockTime.kt` (commonMain) | new | `expect` wall-clock time and time formatting |
| `.../checklist/ChecklistViewModel.kt` (commonMain) | new | Holds the ticks and the initials; saves every change |
| `.../checklist/ChecklistScreen.kt` (commonMain) | new | The checklist screen, its dialogs, and the scan-screen entry row |
| `shared/src/iosMain/.../checklist/IosChecklistStorage.kt` | new | `NSUserDefaults` storage |
| `shared/src/iosMain/.../checklist/ClockTime.ios.kt` | new | `NSDate` / `NSDateFormatter` clock |
| `shared/src/androidMain/.../checklist/AndroidChecklistStorage.kt` | new | `SharedPreferences` storage |
| `shared/src/androidMain/.../checklist/ClockTime.android.kt` | new | `System.currentTimeMillis` / `DateFormat` clock |
| `.../core/presentation/PlatformBackHandler.kt` (commonMain) + `.ios.kt` + `.android.kt` | new | Android Back button returns to the scan screen; iOS does nothing |
| `.../di/AppModule.kt` + `AppModule.ios.kt` + `AppModule.android.kt` | changed | Provide `checklistStorage` |
| `.../ui/theme/Color.kt` | changed | Checklist colours |
| `.../App.kt` | changed | Screen switching, entry row, checklist view model, board snapshots |
| `.../ble/presentation/EnhancedBluetoothPeripheral.kt` | changed (Task 4) | `lastTelemetry`, `lastTelemetryAt` |
| `.../ble/presentation/BluetoothDeviceViewModel.kt` | changed (Task 4) | Store each board's latest telemetry |
| `shared/src/commonTest/.../checklist/ChecklistRulesTest.kt` | new | Unit tests for the steps and rules |
| `shared/src/commonTest/.../checklist/ChecklistViewModelTest.kt` | new | Unit tests for loading and saving |

`...` stands for `kotlin/com/example/bluefalconcomposemultiplatform`.

---

### Task 1: Steps and rules, with unit tests

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/checklist/ChecklistSteps.kt`
- Create: `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/checklist/ChecklistRules.kt`
- Test: `shared/src/commonTest/kotlin/com/example/bluefalconcomposemultiplatform/checklist/ChecklistRulesTest.kt`

**Interfaces:**
- Consumes: `GooberPostTelemetryPayload` from `goober.kt`, package `com.example.bluefalconcomposemultiplatform`. Its fields: `timestamp: UInt`, `latitude: Int`, `longitude: Int`, `altitudeAgl: Float`, `verticalVelocity: Float`, `xAcc: Float`, `gyrX: Float`, `pyroState: UByte`, `sats: UByte`, `flightState: UByte`, `batteryVoltage: UShort`.
- Produces (all in package `...checklist`):
  - `enum class ChecklistSection(val code: String, val title: String) { OnsitePreflightReady, PreLaunch, Launch }` with `val label: String`
  - `enum class BoardReading(val label: String) { Battery, DrogueContinuity, MainContinuity, Gps }`
  - `data class ChecklistStep(section: ChecklistSection, number: Int, text: String, readings: List<BoardReading> = emptyList())` with `val id: String`
  - `val PREFLIGHT_STEPS: List<ChecklistStep>`
  - `MIN_BATTERY_VOLTS: Double`, `MIN_SATELLITES: Int`, `FRESH_TELEMETRY: Duration`, `DROGUE_PYRO_CHANNEL: Int`, `MAIN_PYRO_CHANNEL: Int`
  - `data class BoardSnapshot(name: String, connected: Boolean, telemetry: GooberPostTelemetryPayload?, telemetryAt: Duration?)`
  - `sealed interface BoardFocus { data object NoBoard; data object TooMany; data class One(name: String, telemetry: GooberPostTelemetryPayload?, telemetryAge: Duration?) }`
  - `fun boardFocus(boards: List<BoardSnapshot>, now: Duration): BoardFocus`
  - `data class BoardLine(name: String?, status: String, fresh: Boolean)` and `fun boardLine(focus: BoardFocus): BoardLine`
  - `sealed interface ReadingValue { data object NoData; data class Fine(text: String); data class Problem(text: String) }`
  - `fun batteryVolts(raw: UShort): Double`
  - `fun hasContinuity(pyroState: UByte, channel: Int): Boolean`
  - `fun readingValue(reading: BoardReading, focus: BoardFocus): ReadingValue`
  - `fun problemsUnder(step: ChecklistStep, focus: BoardFocus): List<String>`
  - `data class TickRecord(initials: String, atMillis: Long)`
  - `data class ChecklistTicks(startedAtMillis: Long? = null, ticks: Map<String, TickRecord> = emptyMap())`
  - `fun cleanInitials(typed: String): String`
  - `fun withTick(ticks: ChecklistTicks, stepId: String, initials: String, nowMillis: Long): ChecklistTicks`
  - `fun withoutTick(ticks: ChecklistTicks, stepId: String): ChecklistTicks`
  - `sealed interface TapAction { data object Untick; data object AskInitials; data class ConfirmProblems(problems: List<String>); data object Tick }`
  - `fun tapAction(step: ChecklistStep, ticks: ChecklistTicks, initials: String, focus: BoardFocus): TapAction`
  - `data class ChecklistProgress(done: Int, total: Int)` with `val allDone: Boolean`
  - `fun progress(steps: List<ChecklistStep>, ticks: ChecklistTicks): ChecklistProgress`
  - `fun progressHeadline(progress: ChecklistProgress): String`
  - `fun entryLine(progress: ChecklistProgress, startedAt: String?): String`
  - `val STALE_AFTER: Duration` and `fun staleHours(startedAtMillis: Long?, nowMillis: Long): Long?`
  - `fun encodeTicks(ticks: ChecklistTicks): String` and `fun decodeTicks(text: String?, knownIds: Set<String>): ChecklistTicks`
  - `fun formatFixed(value: Double, decimals: Int): String`

- [ ] **Step 1: Write the steps file**

Create `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/checklist/ChecklistSteps.kt`:

```kotlin
package com.example.bluefalconcomposemultiplatform.checklist

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// The team's pre-flight checklist, as on the paper form. Edit the texts below to change it.
// A step's id is its number on the form (e.g. "9.11") and ticks are saved by id, so adding or removing a
// step renumbers the ones after it: start a new checklist after changing the list.

// A section of the form; the screen shows "<code> · <title>" above its steps
enum class ChecklistSection(val code: String, val title: String) {
    OnsitePreflightReady("8", "ONSITE PREFLIGHT READY"),
    PreLaunch("9", "PRE-LAUNCH"),
    Launch("G", "LAUNCH");

    val label: String get() = "$code · $title"
}

// Values the app reads from the connected ASCENT's telemetry
enum class BoardReading(val label: String) {
    Battery("Battery"),
    DrogueContinuity("Drogue continuity"),
    MainContinuity("Main continuity"),
    Gps("GPS")
}

data class ChecklistStep(
    val section: ChecklistSection,
    // The step's number within its section, as on the form
    val number: Int,
    val text: String,
    // Live readings shown under the step
    val readings: List<BoardReading> = emptyList()
) {
    // e.g. "9.11": how the step is saved, and what the red-reading question calls it
    val id: String get() = "${section.code}.$number"
}

// Limits for the readings. Confirm these with the team (battery type, pyro wiring) before flying.
const val MIN_BATTERY_VOLTS = 7.4          // nominal voltage of a 2-cell LiPo
const val MIN_SATELLITES = 6               // a solid GPS fix (4 is the minimum for any fix)
val FRESH_TELEMETRY: Duration = 3.seconds  // how recent the last telemetry packet must be
const val DROGUE_PYRO_CHANNEL = 1          // pyro channel n is bit n - 1 of the telemetry's pyroState
const val MAIN_PYRO_CHANNEL = 2

// Which readings show under which steps
private val READINGS_BY_STEP: Map<String, List<BoardReading>> = mapOf(
    "9.11" to listOf(BoardReading.Battery, BoardReading.DrogueContinuity, BoardReading.MainContinuity, BoardReading.Gps),
    "9.14" to listOf(BoardReading.Gps),
    "G.3" to listOf(BoardReading.Gps)
)

// Numbers a section's steps 1, 2, 3, … in the order given
private fun numbered(section: ChecklistSection, vararg texts: String): List<ChecklistStep> =
    texts.mapIndexed { index, text -> ChecklistStep(section, index + 1, text) }

val PREFLIGHT_STEPS: List<ChecklistStep> = (
    numbered(
        ChecklistSection.OnsitePreflightReady,
        "Ensure that all batteries being used are fully charged the day of flight.",
        "Install and power on payload into nosecone 6 radial M3 screws",
        "Install nosecone onto upper airframe with 4 M6 radial bolts. Be sure to use loctite",
        "Install lower airframe onto the fin can. Be sure to use loctite",
        "Attach first quick-link of the drogue to the bottom of the avbay and take off tape from the Nomex",
        "Attach second quick link to the top of the avbay. Ensure the tape is removed from the Nomex on the mains",
        "Unshort e-matches, trim ends to length, and attach securely to pass-through bolts. If needed, tape ends with electrical tape.",
        "Stack all sections of the rocket. Insert four M3 nylon shear pins into the avbay through the upper airframe and four M3 nylon shear pins into the avbay through the lower airframe."
    ) + numbered(
        ChecklistSection.PreLaunch,
        "Drive the rocket to the launch pad.",
        "Once at the pad we will ensure a clear radius around the rocket so that exhaust gasses do not cause a fire",
        "Once the area is safe to launch from, we will lower the rail and insert the rocket.",
        "Then raise the rocket to be vertical.",
        "With the vehicle vertical, only the minimum personnel will approach the vehicle for further procedures. The rest of the group will remain back at a good standoff distance.",
        "Next the nitrous lines will be hooked up to the tank.",
        "Ensure that the nitrous pressure is at optimal levels, ideally 750 PSI",
        "Once given clearance, a squirt leak test will be performed. The remainder of the checklist is only to be continued if the leak test was nominal.",
        "(One person with a handheld radio) Once the rocket is vertical and in launch orientation, we will arm the flight computers one at a time, starting with the Ascent avionics system.",
        "Once Ascent is armed, the arming personnel is to observe the beep sequence of Ascent for any error beeps.",
        "After Ascent is booted up, the arming personnel is to radio ground control from pad to ensure good telemetry, continuity, battery voltage, and GPS.",
        "The Featherweight GPS will be armed for flight after we are assigned a frequency.",
        "Next, the Blue Raven is armed for flight.",
        "Arming personnel must listen for the beeps from the Raven, and ensure that we still have GPS lock with the Featherweight and Ascent.",
        "Once all controllers and trackers are confirmed armed, the final pad personnel will wire the ignitors to the launch controller."
    ) + numbered(
        ChecklistSection.Launch,
        "Complete pre-Launch checklist and return to the flight line. (V.F.9)",
        "Check to see if live video is still transmitting",
        "Ensure the Featherweight and Ascent is still tracking. Failure of one system results in a scrub, follow V.I.1.",
        "Once avionics is go for launch, and we are given permission by the LCO, the fill for the rocket can be started. A rough time estimate for fill must be provided to the LCO",
        "Once the rocket is confirmed full, the pad is armed and the fill valve is closed.",
        "Once we are given the go for launch, the launch button can be pressed. Go Solar Flare"
    )
).map { step -> step.copy(readings = READINGS_BY_STEP[step.id].orEmpty()) }
```

(`READINGS_BY_STEP` must stay above `PREFLIGHT_STEPS`, because top-level properties in a file are set up in order.)

- [ ] **Step 2: Write the failing tests**

Create `shared/src/commonTest/kotlin/com/example/bluefalconcomposemultiplatform/checklist/ChecklistRulesTest.kt`:

```kotlin
package com.example.bluefalconcomposemultiplatform.checklist

import com.example.bluefalconcomposemultiplatform.GooberPostTelemetryPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ChecklistRulesTest {

    private val allIds = PREFLIGHT_STEPS.map { it.id }.toSet()

    private fun step(id: String) = PREFLIGHT_STEPS.first { it.id == id }

    // A telemetry packet whose readings are all fine unless a test changes them
    private fun packet(
        batteryRaw: UShort = 19750u,   // 7.90 V
        pyroState: UByte = 0b0011u,    // channels 1 and 2 have continuity
        sats: UByte = 9u
    ) = GooberPostTelemetryPayload(
        timestamp = 0u, latitude = 0, longitude = 0,
        altitudeAgl = 0f, verticalVelocity = 0f, xAcc = 0f, gyrX = 0f,
        pyroState = pyroState, sats = sats, flightState = 0u, batteryVoltage = batteryRaw
    )

    // One connected board whose telemetry arrived `age` ago
    private fun oneBoard(telemetry: GooberPostTelemetryPayload? = packet(), age: Duration = 400.milliseconds) =
        boardFocus(
            listOf(
                BoardSnapshot(
                    name = "ASCENT R3 SN4",
                    connected = true,
                    telemetry = telemetry,
                    telemetryAt = if (telemetry == null) null else 10.seconds
                )
            ),
            now = 10.seconds + age
        )

    @Test
    fun theTeamsTwentyNineStepsHaveUniqueIds() {
        assertEquals(29, PREFLIGHT_STEPS.size)
        assertEquals(29, allIds.size)
        assertEquals(listOf(8, 15, 6), ChecklistSection.entries.map { section -> PREFLIGHT_STEPS.count { it.section == section } })
        assertEquals("8.1", PREFLIGHT_STEPS.first().id)
        assertEquals("G.6", PREFLIGHT_STEPS.last().id)
        assertEquals("Drive the rocket to the launch pad.", step("9.1").text)
    }

    @Test
    fun readingsSitUnderOnlyThreeSteps() {
        assertEquals(
            listOf(BoardReading.Battery, BoardReading.DrogueContinuity, BoardReading.MainContinuity, BoardReading.Gps),
            step("9.11").readings
        )
        assertEquals(listOf(BoardReading.Gps), step("9.14").readings)
        assertEquals(listOf(BoardReading.Gps), step("G.3").readings)
        assertEquals(setOf("9.11", "9.14", "G.3"), PREFLIGHT_STEPS.filter { it.readings.isNotEmpty() }.map { it.id }.toSet())
    }

    @Test
    fun sectionLabels() {
        assertEquals("8 · ONSITE PREFLIGHT READY", ChecklistSection.OnsitePreflightReady.label)
        assertEquals("9 · PRE-LAUNCH", ChecklistSection.PreLaunch.label)
        assertEquals("G · LAUNCH", ChecklistSection.Launch.label)
    }

    @Test
    fun batteryIsFineFromSevenPointFourVolts() {
        assertEquals(ReadingValue.Fine("7.40 V"), readingValue(BoardReading.Battery, oneBoard(packet(batteryRaw = 18500u))))
        assertEquals(
            ReadingValue.Problem("7.39 V · needs 7.4 V"),
            readingValue(BoardReading.Battery, oneBoard(packet(batteryRaw = 18475u)))
        )
    }

    @Test
    fun continuityReadsTheRightBits() {
        // Only channel 1 (bit 0)
        val drogueOnly = oneBoard(packet(pyroState = 0b0001u))
        assertEquals(ReadingValue.Fine("Continuity"), readingValue(BoardReading.DrogueContinuity, drogueOnly))
        assertEquals(ReadingValue.Problem("No continuity"), readingValue(BoardReading.MainContinuity, drogueOnly))
        // Channels 3 and 4 don't count for drogue or main
        val otherChannels = oneBoard(packet(pyroState = 0b1100u))
        assertEquals(ReadingValue.Problem("No continuity"), readingValue(BoardReading.DrogueContinuity, otherChannels))
        assertEquals(ReadingValue.Problem("No continuity"), readingValue(BoardReading.MainContinuity, otherChannels))
    }

    @Test
    fun gpsNeedsSixSatellites() {
        assertEquals(ReadingValue.Fine("6 satellites"), readingValue(BoardReading.Gps, oneBoard(packet(sats = 6u))))
        assertEquals(
            ReadingValue.Problem("5 satellites · needs 6"),
            readingValue(BoardReading.Gps, oneBoard(packet(sats = 5u)))
        )
    }

    @Test
    fun theFirstLineShowsTheBoardAndHowFreshItsDataIs() {
        assertEquals(BoardLine("ASCENT R3 SN4", "last packet 0.4 s ago", fresh = true), boardLine(oneBoard(age = 400.milliseconds)))
        // Exactly 3 s old still counts as fresh
        assertEquals(BoardLine("ASCENT R3 SN4", "last packet 3.0 s ago", fresh = true), boardLine(oneBoard(age = 3.seconds)))
        assertEquals(
            BoardLine("ASCENT R3 SN4", "last data 3 s ago", fresh = false),
            boardLine(oneBoard(age = 3.seconds + 500.milliseconds))
        )
        assertEquals(BoardLine("ASCENT R3 SN4", "no data yet", fresh = false), boardLine(oneBoard(telemetry = null)))
        assertEquals(BoardLine(null, "No board connected", fresh = false), boardLine(BoardFocus.NoBoard))
        assertEquals(BoardLine(null, "Connect only one board", fresh = false), boardLine(BoardFocus.TooMany))
    }

    @Test
    fun oldOrMissingDataIsNeverJudged() {
        val stale = oneBoard(packet(batteryRaw = 17750u), age = 3.seconds + 500.milliseconds)
        BoardReading.entries.forEach { reading ->
            assertEquals(ReadingValue.NoData, readingValue(reading, stale))
        }
        assertEquals(ReadingValue.NoData, readingValue(BoardReading.Gps, oneBoard(telemetry = null)))
        assertEquals(ReadingValue.NoData, readingValue(BoardReading.Gps, BoardFocus.NoBoard))
    }

    @Test
    fun telemetryAgeIsNeverNegative() {
        // The view model's clock can trail a packet's arrival time by a moment
        val focus = boardFocus(listOf(BoardSnapshot("ASCENT", true, packet(), telemetryAt = 11.seconds)), now = 10.seconds)
        assertEquals(BoardLine("ASCENT", "last packet 0.0 s ago", fresh = true), boardLine(focus))
    }

    @Test
    fun onlyOneConnectedBoardIsRead() {
        val idle = BoardSnapshot("ASCENT A", connected = false, telemetry = null, telemetryAt = null)
        val linked = BoardSnapshot("ASCENT B", connected = true, telemetry = null, telemetryAt = null)
        assertEquals(BoardFocus.NoBoard, boardFocus(listOf(idle), now = 0.seconds))
        assertEquals(
            BoardFocus.One("ASCENT B", telemetry = null, telemetryAge = null),
            boardFocus(listOf(idle, linked), now = 0.seconds)
        )
        assertEquals(BoardFocus.TooMany, boardFocus(listOf(linked, linked.copy(name = "ASCENT C")), now = 0.seconds))
    }

    @Test
    fun problemsUnderAStepAreListedForTheQuestion() {
        val focus = oneBoard(packet(batteryRaw = 17750u, pyroState = 0b0001u))
        assertEquals(
            listOf("Battery: 7.10 V · needs 7.4 V", "Main continuity: No continuity"),
            problemsUnder(step("9.11"), focus)
        )
        assertEquals(emptyList<String>(), problemsUnder(step("9.14"), focus))   // GPS is fine
        assertEquals(emptyList<String>(), problemsUnder(step("8.1"), focus))    // no readings
    }

    @Test
    fun tappingATickedStepUnticksIt() {
        val ticks = ChecklistTicks(startedAtMillis = 1, ticks = mapOf("8.1" to TickRecord("CR", 1)))
        assertEquals(TapAction.Untick, tapAction(step("8.1"), ticks, initials = "", focus = BoardFocus.NoBoard))
    }

    @Test
    fun initialsComeFirst() {
        assertEquals(
            TapAction.AskInitials,
            tapAction(step("9.11"), ChecklistTicks(), initials = "", focus = oneBoard(packet(batteryRaw = 17750u)))
        )
    }

    @Test
    fun aRedReadingAsksFirstButGreyReadingsDoNot() {
        assertEquals(
            TapAction.ConfirmProblems(listOf("Battery: 7.10 V · needs 7.4 V")),
            tapAction(step("9.11"), ChecklistTicks(), "CR", oneBoard(packet(batteryRaw = 17750u)))
        )
        assertEquals(TapAction.Tick, tapAction(step("9.11"), ChecklistTicks(), "CR", BoardFocus.NoBoard))
        assertEquals(TapAction.Tick, tapAction(step("9.11"), ChecklistTicks(), "CR", oneBoard()))
        assertEquals(TapAction.Tick, tapAction(step("8.1"), ChecklistTicks(), "CR", BoardFocus.NoBoard))
    }

    @Test
    fun aTickStampsInitialsAndTimeAndTheFirstOneStartsTheChecklist() {
        val first = withTick(ChecklistTicks(), "8.1", "CR", nowMillis = 5_000)
        assertEquals(ChecklistTicks(startedAtMillis = 5_000, ticks = mapOf("8.1" to TickRecord("CR", 5_000))), first)
        val second = withTick(first, "8.2", "JM", nowMillis = 9_000)
        assertEquals(5_000L, second.startedAtMillis)
        assertEquals(TickRecord("JM", 9_000), second.ticks["8.2"])
        // Unticking keeps the start time
        assertEquals(
            ChecklistTicks(startedAtMillis = 5_000, ticks = mapOf("8.2" to TickRecord("JM", 9_000))),
            withoutTick(second, "8.1")
        )
    }

    @Test
    fun typedInitialsAreCleanedUp() {
        assertEquals("CR", cleanInitials(" c.r "))
        assertEquals("ABCD", cleanInitials("abcdef"))
        assertEquals("", cleanInitials("12;,"))
    }

    @Test
    fun progressAndTheScanScreenLine() {
        val ticks = ChecklistTicks(startedAtMillis = 1, ticks = mapOf("8.1" to TickRecord("CR", 1), "8.2" to TickRecord("CR", 2)))
        val progress = progress(PREFLIGHT_STEPS, ticks)
        assertEquals(ChecklistProgress(done = 2, total = 29), progress)
        assertEquals("2 of 29 done", progressHeadline(progress))
        assertEquals("2 of 29 done · started 7:15 PM", entryLine(progress, "7:15 PM"))
        assertEquals("Not started · 29 steps", entryLine(ChecklistProgress(done = 0, total = 29), startedAt = null))
    }

    @Test
    fun everythingDone() {
        val all = ChecklistTicks(startedAtMillis = 1, ticks = PREFLIGHT_STEPS.associate { it.id to TickRecord("CR", 1) })
        val done = progress(PREFLIGHT_STEPS, all)
        assertTrue(done.allDone)
        assertEquals("All 29 steps done", progressHeadline(done))
        assertEquals("All 29 steps done", entryLine(done, "7:15 PM"))
    }

    @Test
    fun aChecklistIsOldAfterTwelveHours() {
        val started = 1_000_000L
        assertNull(staleHours(started, started + 12.hours.inWholeMilliseconds))
        assertEquals(12L, staleHours(started, started + (12.hours + 1.minutes).inWholeMilliseconds))
        assertEquals(14L, staleHours(started, started + (14.hours + 20.minutes).inWholeMilliseconds))
        assertNull(staleHours(null, started))
    }

    @Test
    fun ticksSurviveSavingAndLoading() {
        val ticks = ChecklistTicks(
            startedAtMillis = 1_792_721_854_000,
            ticks = mapOf("8.2" to TickRecord("JM", 1_792_722_214_000), "8.1" to TickRecord("CR", 1_792_721_854_000))
        )
        val text = encodeTicks(ticks)
        assertEquals("v2;started=1792721854000;tick=8.1,CR,1792721854000;tick=8.2,JM,1792722214000", text)
        assertEquals(ticks, decodeTicks(text, allIds))
        assertEquals(ChecklistTicks(), decodeTicks(encodeTicks(ChecklistTicks()), allIds))
    }

    @Test
    fun unreadableOldOrUnknownSavedDataIsIgnored() {
        assertEquals(ChecklistTicks(), decodeTicks(null, allIds))
        assertEquals(ChecklistTicks(), decodeTicks("not a checklist", allIds))
        // The first draft's format
        assertEquals(ChecklistTicks(), decodeTicks("v1;started=5;manual=on-rail;hand=battery", allIds))
        // A removed step, a missing time, missing initials and a bad time are all skipped
        assertEquals(
            ChecklistTicks(startedAtMillis = 5, ticks = mapOf("8.1" to TickRecord("CR", 6))),
            decodeTicks("v2;started=5;tick=8.1,CR,6;tick=removed-step,CR,7;tick=8.2,CR;tick=8.3,,8;tick=8.4,CR,soon", allIds)
        )
    }

    @Test
    fun numbersAreFormattedWithFixedDecimals() {
        assertEquals("7.10", formatFixed(7.1, 2))
        assertEquals("0.4", formatFixed(0.44, 1))
        assertEquals("12", formatFixed(12.2, 0))
        assertEquals("-1.5", formatFixed(-1.5, 1))
    }
}
```

- [ ] **Step 3: Run the tests to check they fail**

Run: `./gradlew :shared:iosSimulatorArm64Test --console=plain`
Expected: the build fails with errors like `Unresolved reference 'boardFocus'`, `'readingValue'` and `'ChecklistTicks'`.

- [ ] **Step 4: Write the rules**

Create `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/checklist/ChecklistRules.kt`:

```kotlin
package com.example.bluefalconcomposemultiplatform.checklist

import com.example.bluefalconcomposemultiplatform.GooberPostTelemetryPayload
import kotlin.math.abs
import kotlin.math.round
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

// ---- Which board the checklist is reading ----

// What the checklist needs to know about one board, taken from the Bluetooth view model's state
data class BoardSnapshot(
    val name: String,
    val connected: Boolean,
    val telemetry: GooberPostTelemetryPayload?,
    // When that telemetry arrived, on the Bluetooth view model's clock
    val telemetryAt: Duration?
)

sealed interface BoardFocus {
    data object NoBoard : BoardFocus
    data object TooMany : BoardFocus
    data class One(
        val name: String,
        val telemetry: GooberPostTelemetryPayload?,
        // How long ago the telemetry arrived (never negative), or null if none has
        val telemetryAge: Duration?
    ) : BoardFocus
}

// The readings come from exactly one connected board
fun boardFocus(boards: List<BoardSnapshot>, now: Duration): BoardFocus {
    val connected = boards.filter { it.connected }
    if (connected.isEmpty()) return BoardFocus.NoBoard
    if (connected.size > 1) return BoardFocus.TooMany
    val board = connected.single()
    return BoardFocus.One(
        name = board.name,
        telemetry = board.telemetry,
        telemetryAge = board.telemetryAt?.let { (now - it).coerceAtLeast(Duration.ZERO) }
    )
}

// ---- Reading blocks ----

// The first line of a reading block, e.g. "ASCENT R3 SN4 · last packet 0.4 s ago"
data class BoardLine(
    // Shown in bold before the status, or null when there's no single board
    val name: String?,
    val status: String,
    // Fresh telemetry: the line gets a green dot and the readings are judged
    val fresh: Boolean
)

fun boardLine(focus: BoardFocus): BoardLine = when (focus) {
    BoardFocus.NoBoard -> BoardLine(null, "No board connected", fresh = false)
    BoardFocus.TooMany -> BoardLine(null, "Connect only one board", fresh = false)
    is BoardFocus.One -> {
        val age = focus.telemetryAge
        when {
            focus.telemetry == null || age == null -> BoardLine(focus.name, "no data yet", fresh = false)
            age > FRESH_TELEMETRY -> BoardLine(focus.name, "last data ${age.inWholeSeconds} s ago", fresh = false)
            else -> BoardLine(
                focus.name,
                "last packet ${formatFixed(age.toDouble(DurationUnit.SECONDS), 1)} s ago",
                fresh = true
            )
        }
    }
}

sealed interface ReadingValue {
    // Shown as a grey "—": no single board, or no fresh telemetry
    data object NoData : ReadingValue
    data class Fine(val text: String) : ReadingValue
    data class Problem(val text: String) : ReadingValue
}

// Same conversion as the Battery row on the connected card
fun batteryVolts(raw: UShort): Double = raw.toInt() / 2500.0

// Pyro channel n is bit n - 1 of pyroState, as on the connected card
fun hasContinuity(pyroState: UByte, channel: Int): Boolean =
    (pyroState.toInt() shr (channel - 1)) and 1 == 1

// A reading's value, judged only on fresh telemetry from exactly one board
fun readingValue(reading: BoardReading, focus: BoardFocus): ReadingValue {
    val board = focus as? BoardFocus.One ?: return ReadingValue.NoData
    val telemetry = board.telemetry ?: return ReadingValue.NoData
    val age = board.telemetryAge ?: return ReadingValue.NoData
    // Never judge the board on old data
    if (age > FRESH_TELEMETRY) return ReadingValue.NoData

    return when (reading) {
        BoardReading.Battery -> {
            val volts = batteryVolts(telemetry.batteryVoltage)
            val text = "${formatFixed(volts, 2)} V"
            if (volts >= MIN_BATTERY_VOLTS) ReadingValue.Fine(text)
            else ReadingValue.Problem("$text · needs $MIN_BATTERY_VOLTS V")
        }
        BoardReading.DrogueContinuity -> continuity(telemetry.pyroState, DROGUE_PYRO_CHANNEL)
        BoardReading.MainContinuity -> continuity(telemetry.pyroState, MAIN_PYRO_CHANNEL)
        BoardReading.Gps -> {
            val sats = telemetry.sats.toInt()
            val text = if (sats == 1) "1 satellite" else "$sats satellites"
            if (sats >= MIN_SATELLITES) ReadingValue.Fine(text)
            else ReadingValue.Problem("$text · needs $MIN_SATELLITES")
        }
    }
}

private fun continuity(pyroState: UByte, channel: Int): ReadingValue =
    if (hasContinuity(pyroState, channel)) ReadingValue.Fine("Continuity") else ReadingValue.Problem("No continuity")

// The red readings under a step, as the red-reading question lists them, e.g. "Battery: 7.10 V · needs 7.4 V"
fun problemsUnder(step: ChecklistStep, focus: BoardFocus): List<String> =
    step.readings.mapNotNull { reading ->
        (readingValue(reading, focus) as? ReadingValue.Problem)?.let { "${reading.label}: ${it.text}" }
    }

// ---- Ticks ----

// Who ticked a step, and when (wall-clock milliseconds since 1970)
data class TickRecord(val initials: String, val atMillis: Long)

// The saved part of the checklist
data class ChecklistTicks(
    // When the first tick on this checklist was made
    val startedAtMillis: Long? = null,
    // Step id -> its tick
    val ticks: Map<String, TickRecord> = emptyMap()
)

// Letters only, in capitals, at most four, e.g. " c.r " -> "CR"
fun cleanInitials(typed: String): String = typed.filter { it.isLetter() }.uppercase().take(4)

// Ticks a step; the first tick on an empty checklist sets the start time
fun withTick(ticks: ChecklistTicks, stepId: String, initials: String, nowMillis: Long): ChecklistTicks =
    ChecklistTicks(
        startedAtMillis = ticks.startedAtMillis ?: nowMillis,
        ticks = ticks.ticks + (stepId to TickRecord(initials, nowMillis))
    )

// Unticking keeps the start time
fun withoutTick(ticks: ChecklistTicks, stepId: String): ChecklistTicks = ticks.copy(ticks = ticks.ticks - stepId)

sealed interface TapAction {
    data object Untick : TapAction
    data object AskInitials : TapAction
    // A red reading under the step: ask before ticking
    data class ConfirmProblems(val problems: List<String>) : TapAction
    data object Tick : TapAction
}

// What tapping a step should do: untick it, or tick it after asking for initials and then about red readings
fun tapAction(step: ChecklistStep, ticks: ChecklistTicks, initials: String, focus: BoardFocus): TapAction {
    if (step.id in ticks.ticks) return TapAction.Untick
    if (initials.isEmpty()) return TapAction.AskInitials
    val problems = problemsUnder(step, focus)
    return if (problems.isEmpty()) TapAction.Tick else TapAction.ConfirmProblems(problems)
}

// ---- Progress ----

data class ChecklistProgress(val done: Int, val total: Int) {
    val allDone: Boolean get() = total > 0 && done == total
}

fun progress(steps: List<ChecklistStep>, ticks: ChecklistTicks): ChecklistProgress =
    ChecklistProgress(done = steps.count { it.id in ticks.ticks }, total = steps.size)

// The bold line on the progress card
fun progressHeadline(progress: ChecklistProgress): String =
    if (progress.allDone) "All ${progress.total} steps done" else "${progress.done} of ${progress.total} done"

// The line under "Pre-flight checklist" on the scan screen
fun entryLine(progress: ChecklistProgress, startedAt: String?): String = when {
    progress.allDone -> "All ${progress.total} steps done"
    progress.done == 0 && startedAt == null -> "Not started · ${progress.total} steps"
    startedAt == null -> "${progress.done} of ${progress.total} done"
    else -> "${progress.done} of ${progress.total} done · started $startedAt"
}

// A checklist started more than this long ago gets a banner suggesting a new one
val STALE_AFTER: Duration = 12.hours

// Whole hours since the checklist started, once it's old enough for the banner; otherwise null
fun staleHours(startedAtMillis: Long?, nowMillis: Long): Long? {
    if (startedAtMillis == null) return null
    val age = (nowMillis - startedAtMillis).milliseconds
    return if (age > STALE_AFTER) age.inWholeHours else null
}

// ---- Saving ----

private const val TICKS_FORMAT = "v2"

// One line of text, e.g. "v2;started=1792721854000;tick=8.1,CR,1792721854000;tick=8.2,CR,1792722214000"
fun encodeTicks(ticks: ChecklistTicks): String = buildList {
    add(TICKS_FORMAT)
    add("started=${ticks.startedAtMillis ?: ""}")
    ticks.ticks.entries.sortedBy { it.key }.forEach { (id, tick) -> add("tick=$id,${tick.initials},${tick.atMillis}") }
}.joinToString(";")

// Anything unreadable (including the first draft's "v1" format) gives an empty checklist;
// ticks for unknown steps, or with missing parts, are dropped
fun decodeTicks(text: String?, knownIds: Set<String>): ChecklistTicks {
    val parts = text?.split(";") ?: return ChecklistTicks()
    if (parts.first() != TICKS_FORMAT) return ChecklistTicks()
    var startedAt: Long? = null
    val ticks = mutableMapOf<String, TickRecord>()
    for (part in parts.drop(1)) {
        when {
            part.startsWith("started=") -> startedAt = part.removePrefix("started=").toLongOrNull()
            part.startsWith("tick=") -> {
                val fields = part.removePrefix("tick=").split(",")
                if (fields.size != 3) continue
                val (id, typedInitials, at) = fields
                val initials = cleanInitials(typedInitials)
                val atMillis = at.toLongOrNull()
                if (id in knownIds && initials.isNotEmpty() && atMillis != null) {
                    ticks[id] = TickRecord(initials, atMillis)
                }
            }
        }
    }
    return ChecklistTicks(startedAt, ticks)
}

// ---- Formatting ----

// A number with a fixed number of decimals, e.g. formatFixed(7.1, 2) = "7.10" (common code has no String.format)
fun formatFixed(value: Double, decimals: Int): String {
    var factor = 1L
    repeat(decimals) { factor *= 10 }
    val scaled = round(abs(value) * factor).toLong()
    val sign = if (value < 0 && scaled != 0L) "-" else ""
    val whole = scaled / factor
    if (decimals == 0) return "$sign$whole"
    val fraction = (scaled % factor).toString().padStart(decimals, '0')
    return "$sign$whole.$fraction"
}
```

- [ ] **Step 5: Run the tests to check they pass**

Run the **Unit tests** commands.
Expected: `BUILD SUCCESSFUL`, `tests="22" skipped="0" failures="0" errors="0"` for `ChecklistRulesTest`, and `tests="11" ... failures="0"` for `ScanRulesTest`.

- [ ] **Step 6: Commit (the user runs this)**

```bash
git add shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/checklist/ChecklistSteps.kt \
  shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/checklist/ChecklistRules.kt \
  shared/src/commonTest/kotlin/com/example/bluefalconcomposemultiplatform/checklist/ChecklistRulesTest.kt
git commit -m "add the team's pre-flight checklist steps and rules with unit tests"
```

---

### Task 2: Saving: storage, clock and the checklist view model

**Files:**
- Create: `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/checklist/ChecklistStorage.kt`
- Create: `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/checklist/ClockTime.kt`
- Create: `shared/src/iosMain/kotlin/com/example/bluefalconcomposemultiplatform/checklist/IosChecklistStorage.kt`
- Create: `shared/src/iosMain/kotlin/com/example/bluefalconcomposemultiplatform/checklist/ClockTime.ios.kt`
- Create: `shared/src/androidMain/kotlin/com/example/bluefalconcomposemultiplatform/checklist/AndroidChecklistStorage.kt`
- Create: `shared/src/androidMain/kotlin/com/example/bluefalconcomposemultiplatform/checklist/ClockTime.android.kt`
- Create: `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/checklist/ChecklistViewModel.kt`
- Modify: `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/di/AppModule.kt`, `shared/src/iosMain/kotlin/com/example/bluefalconcomposemultiplatform/di/AppModule.ios.kt`, `shared/src/androidMain/kotlin/com/example/bluefalconcomposemultiplatform/di/AppModule.android.kt`
- Test: `shared/src/commonTest/kotlin/com/example/bluefalconcomposemultiplatform/checklist/ChecklistViewModelTest.kt`

**Interfaces:**
- Consumes (Task 1): `ChecklistStep`, `ChecklistTicks`, `TickRecord`, `PREFLIGHT_STEPS`, `withTick`, `withoutTick`, `cleanInitials`, `encodeTicks`, `decodeTicks`.
- Produces:
  - `interface ChecklistStorage { fun load(key: String): String?; fun save(key: String, text: String) }`
  - `const val TICKS_KEY = "preflight-checklist"` and `const val INITIALS_KEY = "preflight-initials"`
  - `expect fun currentEpochMillis(): Long`
  - `expect fun formatClockTime(epochMillis: Long): String`
  - `class ChecklistViewModel(storage: ChecklistStorage, clock: () -> Long = ::currentEpochMillis)`, with:
    - `val ticks: StateFlow<ChecklistTicks>`
    - `val initials: StateFlow<String>`
    - `fun tick(step: ChecklistStep)`
    - `fun untick(step: ChecklistStep)`
    - `fun setInitials(typed: String)`
    - `fun startNew()`
  - `AppModule.checklistStorage: ChecklistStorage` (common `expect` plus both actuals)

- [ ] **Step 1: Add the storage interface, the clock and their platform versions**

Create `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/checklist/ChecklistStorage.kt`:

```kotlin
package com.example.bluefalconcomposemultiplatform.checklist

// Where the checklist is saved on the phone: UserDefaults on iOS, SharedPreferences on Android
interface ChecklistStorage {
    fun load(key: String): String?
    fun save(key: String, text: String)
}

// The keys it's saved under on both platforms
const val TICKS_KEY = "preflight-checklist"
const val INITIALS_KEY = "preflight-initials"
```

Create `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/checklist/ClockTime.kt`:

```kotlin
package com.example.bluefalconcomposemultiplatform.checklist

// The phone's wall clock, in milliseconds since 1970 (Kotlin 2.0's standard library has no wall clock)
expect fun currentEpochMillis(): Long

// A wall-clock time the way the phone shows times, e.g. "7:15 PM" or "19:15"
expect fun formatClockTime(epochMillis: Long): String
```

Create `shared/src/iosMain/kotlin/com/example/bluefalconcomposemultiplatform/checklist/IosChecklistStorage.kt`:

```kotlin
package com.example.bluefalconcomposemultiplatform.checklist

import platform.Foundation.NSUserDefaults

class IosChecklistStorage : ChecklistStorage {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun load(key: String): String? = defaults.stringForKey(key)

    override fun save(key: String, text: String) {
        defaults.setObject(text, forKey = key)
    }
}
```

Create `shared/src/iosMain/kotlin/com/example/bluefalconcomposemultiplatform/checklist/ClockTime.ios.kt`:

```kotlin
package com.example.bluefalconcomposemultiplatform.checklist

import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterNoStyle
import platform.Foundation.NSDateFormatterShortStyle
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.Foundation.timeIntervalSince1970

actual fun currentEpochMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()

// The short time style follows the phone's region and its 24-Hour Time setting
actual fun formatClockTime(epochMillis: Long): String {
    val formatter = NSDateFormatter()
    formatter.dateStyle = NSDateFormatterNoStyle
    formatter.timeStyle = NSDateFormatterShortStyle
    return formatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(epochMillis / 1000.0))
}
```

(`timeIntervalSince1970` and `dateWithTimeIntervalSince1970` are Kotlin/Native extensions from Objective-C categories, so they need their own imports, as shown.)

Create `shared/src/androidMain/kotlin/com/example/bluefalconcomposemultiplatform/checklist/AndroidChecklistStorage.kt`:

```kotlin
package com.example.bluefalconcomposemultiplatform.checklist

import android.content.Context

class AndroidChecklistStorage(context: Context) : ChecklistStorage {
    private val preferences = context.getSharedPreferences("goober-connect", Context.MODE_PRIVATE)

    override fun load(key: String): String? = preferences.getString(key, null)

    override fun save(key: String, text: String) {
        preferences.edit().putString(key, text).apply()
    }
}
```

Create `shared/src/androidMain/kotlin/com/example/bluefalconcomposemultiplatform/checklist/ClockTime.android.kt`:

```kotlin
package com.example.bluefalconcomposemultiplatform.checklist

import java.text.DateFormat
import java.util.Date

actual fun currentEpochMillis(): Long = System.currentTimeMillis()

// The short time format for the phone's language, e.g. "7:15 PM". Android's separate 24-hour switch needs
// a Context, which this function doesn't have, so it isn't followed.
actual fun formatClockTime(epochMillis: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(epochMillis))
```

- [ ] **Step 2: Write the failing view model tests**

Create `shared/src/commonTest/kotlin/com/example/bluefalconcomposemultiplatform/checklist/ChecklistViewModelTest.kt`:

```kotlin
package com.example.bluefalconcomposemultiplatform.checklist

import kotlin.test.Test
import kotlin.test.assertEquals

class ChecklistViewModelTest {

    private class FakeStorage(vararg saved: Pair<String, String>) : ChecklistStorage {
        val values = mutableMapOf(*saved)
        var saves = 0
        override fun load(key: String): String? = values[key]
        override fun save(key: String, text: String) {
            values[key] = text
            saves++
        }
    }

    private val allIds = PREFLIGHT_STEPS.map { it.id }.toSet()
    private val firstStep = PREFLIGHT_STEPS.first()   // 8.1

    @Test
    fun loadsWhatWasSavedBefore() {
        val storage = FakeStorage(TICKS_KEY to "v2;started=1000;tick=8.1,CR,1000", INITIALS_KEY to "cr")
        val viewModel = ChecklistViewModel(storage, clock = { 5_000L })
        assertEquals(
            ChecklistTicks(startedAtMillis = 1_000, ticks = mapOf("8.1" to TickRecord("CR", 1_000))),
            viewModel.ticks.value
        )
        assertEquals("CR", viewModel.initials.value)
    }

    @Test
    fun aTickIsStampedAndSavedStraightAway() {
        val storage = FakeStorage()
        val viewModel = ChecklistViewModel(storage, clock = { 5_000L })
        viewModel.setInitials("cr")
        viewModel.tick(firstStep)
        val expected = ChecklistTicks(startedAtMillis = 5_000, ticks = mapOf("8.1" to TickRecord("CR", 5_000)))
        assertEquals(expected, viewModel.ticks.value)
        assertEquals(expected, decodeTicks(storage.values[TICKS_KEY], allIds))
        assertEquals("CR", storage.values[INITIALS_KEY])
    }

    @Test
    fun withoutInitialsATickDoesNothing() {
        val storage = FakeStorage()
        val viewModel = ChecklistViewModel(storage, clock = { 5_000L })
        viewModel.tick(firstStep)
        assertEquals(ChecklistTicks(), viewModel.ticks.value)
        assertEquals(0, storage.saves)
    }

    @Test
    fun untickingKeepsTheStartTime() {
        val storage = FakeStorage(TICKS_KEY to "v2;started=1000;tick=8.1,CR,1000", INITIALS_KEY to "CR")
        val viewModel = ChecklistViewModel(storage, clock = { 5_000L })
        viewModel.untick(firstStep)
        assertEquals(ChecklistTicks(startedAtMillis = 1_000), viewModel.ticks.value)
        assertEquals(ChecklistTicks(startedAtMillis = 1_000), decodeTicks(storage.values[TICKS_KEY], allIds))
    }

    @Test
    fun startNewClearsTheTicksButKeepsTheInitials() {
        val storage = FakeStorage(TICKS_KEY to "v2;started=1000;tick=8.1,CR,1000", INITIALS_KEY to "CR")
        val viewModel = ChecklistViewModel(storage, clock = { 5_000L })
        viewModel.startNew()
        assertEquals(ChecklistTicks(), viewModel.ticks.value)
        assertEquals(ChecklistTicks(), decodeTicks(storage.values[TICKS_KEY], allIds))
        assertEquals("CR", viewModel.initials.value)
    }

    @Test
    fun emptyInitialsAreIgnoredAndNothingIsSavedWhenNothingChanges() {
        val storage = FakeStorage()
        val viewModel = ChecklistViewModel(storage, clock = { 5_000L })
        viewModel.setInitials("12")   // no letters
        viewModel.startNew()          // already empty
        assertEquals("", viewModel.initials.value)
        assertEquals(0, storage.saves)
    }
}
```

- [ ] **Step 3: Run the tests to check they fail**

Run: `./gradlew :shared:iosSimulatorArm64Test --console=plain`
Expected: the build fails with `Unresolved reference 'ChecklistViewModel'`.

- [ ] **Step 4: Write the view model**

Create `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/checklist/ChecklistViewModel.kt`:

```kotlin
package com.example.bluefalconcomposemultiplatform.checklist

import dev.icerock.moko.mvvm.viewmodel.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Holds the checklist's ticks and the initials, and saves every change straight away.
// The readings aren't here: App.kt works them out from the live Bluetooth state.
class ChecklistViewModel(
    private val storage: ChecklistStorage,
    private val clock: () -> Long = ::currentEpochMillis
) : ViewModel() {

    private val _ticks = MutableStateFlow(decodeTicks(storage.load(TICKS_KEY), PREFLIGHT_STEPS.map { it.id }.toSet()))
    val ticks: StateFlow<ChecklistTicks> get() = _ticks

    private val _initials = MutableStateFlow(cleanInitials(storage.load(INITIALS_KEY).orEmpty()))
    val initials: StateFlow<String> get() = _initials

    // Ticks a step with the current initials and time. The screen asks for initials first, so without
    // any this does nothing.
    fun tick(step: ChecklistStep) {
        val initials = _initials.value
        if (initials.isEmpty()) return
        changeTicks { withTick(it, step.id, initials, clock()) }
    }

    fun untick(step: ChecklistStep) {
        changeTicks { withoutTick(it, step.id) }
    }

    fun setInitials(typed: String) {
        val initials = cleanInitials(typed)
        if (initials.isEmpty() || initials == _initials.value) return
        _initials.value = initials
        storage.save(INITIALS_KEY, initials)
    }

    // Clears every tick and the start time; the initials stay
    fun startNew() {
        changeTicks { ChecklistTicks() }
    }

    private fun changeTicks(update: (ChecklistTicks) -> ChecklistTicks) {
        val next = update(_ticks.value)
        if (next == _ticks.value) return
        _ticks.value = next
        storage.save(TICKS_KEY, encodeTicks(next))
    }
}
```

- [ ] **Step 5: Provide the storage from `AppModule`**

Replace all of `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/di/AppModule.kt` with:

```kotlin
package com.example.bluefalconcomposemultiplatform.di

import com.example.bluefalconcomposemultiplatform.ble.data.PlatformBluetooth
import com.example.bluefalconcomposemultiplatform.checklist.ChecklistStorage
import dev.bluefalcon.BlueFalcon

expect class AppModule {
    val blueFalcon: BlueFalcon
    val platformBluetooth: PlatformBluetooth
    val checklistStorage: ChecklistStorage
}
```

Replace all of `shared/src/iosMain/kotlin/com/example/bluefalconcomposemultiplatform/di/AppModule.ios.kt` with:

```kotlin
package com.example.bluefalconcomposemultiplatform.di

import com.example.bluefalconcomposemultiplatform.ble.data.IosPlatformBluetooth
import com.example.bluefalconcomposemultiplatform.ble.data.PlatformBluetooth
import com.example.bluefalconcomposemultiplatform.checklist.ChecklistStorage
import com.example.bluefalconcomposemultiplatform.checklist.IosChecklistStorage
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

    actual val checklistStorage: ChecklistStorage = IosChecklistStorage()
}
```

Replace all of `shared/src/androidMain/kotlin/com/example/bluefalconcomposemultiplatform/di/AppModule.android.kt` with:

```kotlin
package com.example.bluefalconcomposemultiplatform.di

import android.content.Context
import com.example.bluefalconcomposemultiplatform.ble.data.AndroidPlatformBluetooth
import com.example.bluefalconcomposemultiplatform.ble.data.PlatformBluetooth
import com.example.bluefalconcomposemultiplatform.checklist.AndroidChecklistStorage
import com.example.bluefalconcomposemultiplatform.checklist.ChecklistStorage
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

    actual val checklistStorage: ChecklistStorage = AndroidChecklistStorage(context)
}
```

(`MainActivity` already passes `applicationContext`, which suits `SharedPreferences`.)

- [ ] **Step 6: Run the tests and the iOS compile check**

Run the **Unit tests** commands, then the **iOS compile check**.
Expected:
- Tests: `ChecklistViewModelTest` `tests="6" ... failures="0"`, `ChecklistRulesTest` `tests="22"`, `ScanRulesTest` `tests="11"`, all passing.
- Compile: `BUILD SUCCESSFUL` with no `e:` lines. The only `w:` lines should be the existing expect/actual "Beta" warnings.

- [ ] **Step 7: Commit (the user runs this)**

```bash
git add shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/checklist/ChecklistStorage.kt \
  shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/checklist/ClockTime.kt \
  shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/checklist/ChecklistViewModel.kt \
  shared/src/iosMain/kotlin/com/example/bluefalconcomposemultiplatform/checklist/IosChecklistStorage.kt \
  shared/src/iosMain/kotlin/com/example/bluefalconcomposemultiplatform/checklist/ClockTime.ios.kt \
  shared/src/androidMain/kotlin/com/example/bluefalconcomposemultiplatform/checklist/AndroidChecklistStorage.kt \
  shared/src/androidMain/kotlin/com/example/bluefalconcomposemultiplatform/checklist/ClockTime.android.kt \
  shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/di/AppModule.kt \
  shared/src/iosMain/kotlin/com/example/bluefalconcomposemultiplatform/di/AppModule.ios.kt \
  shared/src/androidMain/kotlin/com/example/bluefalconcomposemultiplatform/di/AppModule.android.kt \
  shared/src/commonTest/kotlin/com/example/bluefalconcomposemultiplatform/checklist/ChecklistViewModelTest.kt
git commit -m "save pre-flight checklist ticks and initials on the phone"
```

---

### Task 3: The checklist screen and the way in

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ui/theme/Color.kt`
- Create: `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/core/presentation/PlatformBackHandler.kt`
- Create: `shared/src/iosMain/kotlin/com/example/bluefalconcomposemultiplatform/core/presentation/PlatformBackHandler.ios.kt`
- Create: `shared/src/androidMain/kotlin/com/example/bluefalconcomposemultiplatform/core/presentation/PlatformBackHandler.android.kt`
- Create: `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/checklist/ChecklistScreen.kt`
- Modify: `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/App.kt` (replace all)

**Interfaces:**
- Consumes:
  - From Task 1: `ChecklistSection`, `ChecklistStep`, `BoardReading`, `PREFLIGHT_STEPS`, `BoardSnapshot`, `BoardFocus`, `ReadingValue`, `TapAction`, `TickRecord`, `ChecklistTicks`, `ChecklistProgress`, and the functions `boardFocus`, `boardLine`, `readingValue`, `tapAction`, `cleanInitials`, `progress`, `progressHeadline`, `entryLine`, `staleHours`.
  - From Task 2: `ChecklistViewModel` (`ticks`, `initials`, `tick`, `untick`, `setInitials`, `startNew`), `currentEpochMillis`, `formatClockTime`, `AppModule.checklistStorage`.
  - Theme colours (`Color.kt`).
- Produces:
  - `@Composable fun ChecklistEntryRow(summary: String, onClick: () -> Unit, modifier: Modifier = Modifier)`
  - `@Composable fun ChecklistScreen(ticks: ChecklistTicks, initials: String, focus: BoardFocus, staleHours: Long?, onTick: (ChecklistStep) -> Unit, onUntick: (ChecklistStep) -> Unit, onSetInitials: (String) -> Unit, onStartNew: () -> Unit, onBack: () -> Unit)`
  - `@Composable expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)`
  - In `App.kt`: `private fun EnhancedBluetoothPeripheral.toBoardSnapshot(): BoardSnapshot`, which Task 4 changes.

- [ ] **Step 1: Add the checklist colours**

Append to the end of `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ui/theme/Color.kt`:

```kotlin

// Pre-flight checklist (the tick itself is ConnectedGreen, #3DDC84)
val CheckGreenText = Color(0xFF7EE8AE)          // stamps and fine readings
val OnCheckGreen = Color(0xFF062012)            // the tick mark inside the green circle
val ProblemRedText = Color(0xFFFF8A80)          // readings with a problem
val RingEmpty = Color(0x59FFFFFF)               // white at 35%: a step not ticked yet
val RowDivider = Color(0x0FFFFFFF)              // white at 6%
val ReadingBlockBackground = Color(0x47000000)  // black at 28%
val ReadingBlockBorder = Color(0x12FFFFFF)      // white at 7%
```

- [ ] **Step 2: Add the platform back handler**

Create `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/core/presentation/PlatformBackHandler.kt`:

```kotlin
package com.example.bluefalconcomposemultiplatform.core.presentation

import androidx.compose.runtime.Composable

// Calls onBack when the platform's own Back control is used (Android's Back button or gesture)
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)
```

Create `shared/src/androidMain/kotlin/com/example/bluefalconcomposemultiplatform/core/presentation/PlatformBackHandler.android.kt`:

```kotlin
package com.example.bluefalconcomposemultiplatform.core.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}
```

Create `shared/src/iosMain/kotlin/com/example/bluefalconcomposemultiplatform/core/presentation/PlatformBackHandler.ios.kt`:

```kotlin
package com.example.bluefalconcomposemultiplatform.core.presentation

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // iOS has no system Back button here; the screen's back arrow is the way back
}
```

- [ ] **Step 3: Write the checklist screen**

Create `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/checklist/ChecklistScreen.kt`:

```kotlin
package com.example.bluefalconcomposemultiplatform.checklist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluefalconcomposemultiplatform.ui.theme.AsuGold
import com.example.bluefalconcomposemultiplatform.ui.theme.CardBackground
import com.example.bluefalconcomposemultiplatform.ui.theme.CardBorder
import com.example.bluefalconcomposemultiplatform.ui.theme.CheckGreenText
import com.example.bluefalconcomposemultiplatform.ui.theme.ConnectedGreen
import com.example.bluefalconcomposemultiplatform.ui.theme.OnAsuGold
import com.example.bluefalconcomposemultiplatform.ui.theme.OnCheckGreen
import com.example.bluefalconcomposemultiplatform.ui.theme.ProblemRedText
import com.example.bluefalconcomposemultiplatform.ui.theme.ReadingBlockBackground
import com.example.bluefalconcomposemultiplatform.ui.theme.ReadingBlockBorder
import com.example.bluefalconcomposemultiplatform.ui.theme.RingEmpty
import com.example.bluefalconcomposemultiplatform.ui.theme.RowDivider
import com.example.bluefalconcomposemultiplatform.ui.theme.SignalTrack
import com.example.bluefalconcomposemultiplatform.ui.theme.TextPrimary
import com.example.bluefalconcomposemultiplatform.ui.theme.TextSecondary

private val CardShape = RoundedCornerShape(14.dp)

// The row under the header on the scan screen that opens the checklist
@Composable
fun ChecklistEntryRow(summary: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(CardBackground)
            .border(1.dp, AsuGold.copy(alpha = 0.45f), CardShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(28.dp).background(AsuGold, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = OnAsuGold, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Pre-flight checklist",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 19.sp
            )
            Text(summary, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = AsuGold)
    }
}

// The whole checklist: progress and initials at the top, the steps by section, and a way to start again
@Composable
fun ChecklistScreen(
    ticks: ChecklistTicks,
    initials: String,
    focus: BoardFocus,
    // Hours since the start, once the checklist is old enough to suggest a new one
    staleHours: Long?,
    onTick: (ChecklistStep) -> Unit,
    onUntick: (ChecklistStep) -> Unit,
    onSetInitials: (String) -> Unit,
    onStartNew: () -> Unit,
    onBack: () -> Unit
) {
    // Open dialogs. When the initials dialog was opened by tapping a step, that step carries on afterwards.
    var editingInitials by remember { mutableStateOf(false) }
    var stepAfterInitials by remember { mutableStateOf<ChecklistStep?>(null) }
    var confirming by remember { mutableStateOf<Pair<ChecklistStep, List<String>>?>(null) }
    var confirmingStartNew by remember { mutableStateOf(false) }

    // Initials first, then the red-reading question, then the tick
    fun handleTap(step: ChecklistStep, initialsNow: String) {
        when (val action = tapAction(step, ticks, initialsNow, focus)) {
            TapAction.Untick -> onUntick(step)
            TapAction.AskInitials -> {
                stepAfterInitials = step
                editingInitials = true
            }
            is TapAction.ConfirmProblems -> confirming = step to action.problems
            TapAction.Tick -> onTick(step)
        }
    }

    val stepProgress = progress(PREFLIGHT_STEPS, ticks)
    val startedAt = ticks.startedAtMillis?.let(::formatClockTime)

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Back",
                    tint = AsuGold,
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                "Pre-flight checklist",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 24.sp
            )
        }
        ProgressCard(
            progress = stepProgress,
            startedAt = startedAt,
            initials = initials,
            onInitialsClick = {
                stepAfterInitials = null
                editingInitials = true
            },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp)
        ) {
            if (staleHours != null) {
                item { StaleBanner(staleHours, onStartNew = { confirmingStartNew = true }) }
            }
            ChecklistSection.entries.forEach { section ->
                val sectionSteps = PREFLIGHT_STEPS.filter { it.section == section }
                if (sectionSteps.isNotEmpty()) {
                    item { SectionLabel(section.label) }
                    item { SectionCard(sectionSteps, ticks, focus, onStepTap = { step -> handleTap(step, initials) }) }
                }
            }
            item {
                OutlinedButton(
                    onClick = { confirmingStartNew = true },
                    border = BorderStroke(1.dp, TextPrimary.copy(alpha = 0.28f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
                ) {
                    Text("Start new checklist", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (editingInitials) {
        InitialsDialog(
            current = initials,
            onSave = { typed ->
                editingInitials = false
                onSetInitials(typed)
                // Carry on with the step that asked for initials, now that there are some
                stepAfterInitials?.let { step -> handleTap(step, cleanInitials(typed)) }
                stepAfterInitials = null
            },
            onDismiss = {
                editingInitials = false
                stepAfterInitials = null
            }
        )
    }

    confirming?.let { (step, problems) ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text("Tick ${step.id} anyway?", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = { Text(problems.joinToString("\n"), fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = null
                    onTick(step)
                }) { Text("Tick anyway") }
            },
            dismissButton = {
                TextButton(onClick = { confirming = null }) { Text("Cancel") }
            }
        )
    }

    if (confirmingStartNew) {
        AlertDialog(
            onDismissRequest = { confirmingStartNew = false },
            title = { Text("Start a new checklist?", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = { Text("This clears every tick, with its initials and time. Your initials stay set.", fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingStartNew = false
                    onStartNew()
                }) { Text("Start new") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingStartNew = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ProgressCard(
    progress: ChecklistProgress,
    startedAt: String?,
    initials: String,
    onInitialsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(CardBackground)
            .border(1.dp, CardBorder, CardShape)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    progressHeadline(progress),
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 18.sp
                )
                if (startedAt != null) {
                    Text("started $startedAt", color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
            InitialsChip(initials, onInitialsClick)
        }
        // The bar turns green once every step is done
        val fraction = if (progress.total == 0) 0f else progress.done.toFloat() / progress.total
        Box(
            Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(SignalTrack)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(if (progress.allDone) ConnectedGreen else AsuGold)
            )
        }
    }
}

@Composable
private fun InitialsChip(initials: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(9.dp)
    Column(
        modifier = Modifier
            .clip(shape)
            .border(1.dp, AsuGold.copy(alpha = 0.55f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "INITIALS",
            color = TextSecondary,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            lineHeight = 10.sp
        )
        Text(
            initials.ifEmpty { "—" },
            color = AsuGold,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun StaleBanner(hours: Long, onStartNew: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(CardShape)
            .background(CardBackground)
            .border(1.dp, AsuGold.copy(alpha = 0.45f), CardShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Started $hours h ago. Start a new checklist?",
            color = TextPrimary,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = onStartNew,
            colors = ButtonDefaults.buttonColors(containerColor = AsuGold, contentColor = OnAsuGold),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text("Start new", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(
        label,
        color = AsuGold,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.5.sp,
        lineHeight = 14.sp,
        modifier = Modifier.padding(start = 2.dp, top = 14.dp, bottom = 6.dp)
    )
}

// One section's steps in a card
@Composable
private fun SectionCard(
    steps: List<ChecklistStep>,
    ticks: ChecklistTicks,
    focus: BoardFocus,
    onStepTap: (ChecklistStep) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(CardBackground)
            .border(1.dp, CardBorder, CardShape)
    ) {
        steps.forEachIndexed { index, step ->
            if (index > 0) HorizontalDivider(color = RowDivider)
            StepRow(step, ticks.ticks[step.id], focus, onClick = { onStepTap(step) })
        }
    }
}

@Composable
private fun StepRow(step: ChecklistStep, tick: TickRecord?, focus: BoardFocus, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.Top
    ) {
        StepMark(ticked = tick != null)
        Spacer(Modifier.width(8.dp))
        Text(
            "${step.number}.",
            color = TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            lineHeight = 18.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.width(24.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(step.text, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp)
            if (tick != null) {
                Text(
                    "${tick.initials} · ${formatClockTime(tick.atMillis)}",
                    color = CheckGreenText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            if (step.readings.isNotEmpty()) {
                ReadingBlock(step.readings, focus, Modifier.padding(top = 8.dp))
            }
        }
    }
}

// The circle on the left of each step. An icon rather than a text glyph: the theme's 24 sp line height
// pushes small text out of a small circle.
@Composable
private fun StepMark(ticked: Boolean) {
    if (ticked) {
        Box(Modifier.size(20.dp).background(ConnectedGreen, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, contentDescription = "Ticked", tint = OnCheckGreen, modifier = Modifier.size(14.dp))
        }
    } else {
        Box(Modifier.size(20.dp).border(2.dp, RingEmpty, CircleShape))
    }
}

// Live readings under a step: which board and how fresh its data is, then one row per reading
@Composable
private fun ReadingBlock(readings: List<BoardReading>, focus: BoardFocus, modifier: Modifier = Modifier) {
    val line = boardLine(focus)
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ReadingBlockBackground)
            .border(1.dp, ReadingBlockBorder, shape)
            .padding(horizontal = 9.dp, vertical = 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
            if (line.fresh) {
                Box(Modifier.size(7.dp).background(ConnectedGreen, CircleShape))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                buildAnnotatedString {
                    line.name?.let { name ->
                        withStyle(SpanStyle(color = TextPrimary, fontWeight = FontWeight.Bold)) { append(name) }
                        append(" · ")
                    }
                    append(line.status)
                },
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
        HorizontalDivider(color = RowDivider)
        readings.forEach { reading ->
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(
                    reading.label,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                when (val value = readingValue(reading, focus)) {
                    ReadingValue.NoData ->
                        Text("—", color = TextSecondary.copy(alpha = 0.7f), fontSize = 12.sp, lineHeight = 16.sp)
                    is ReadingValue.Fine ->
                        Text(value.text, color = CheckGreenText, fontSize = 12.sp, lineHeight = 16.sp)
                    is ReadingValue.Problem ->
                        Text(
                            value.text,
                            color = ProblemRedText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 16.sp
                        )
                }
            }
        }
    }
}

@Composable
private fun InitialsDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var typed by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your initials", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Saved with each step you tick.", fontSize = 14.sp)
                OutlinedTextField(
                    value = typed,
                    // Letters only, in capitals, at most four, as they're typed
                    onValueChange = { typed = cleanInitials(it) },
                    singleLine = true,
                    placeholder = { Text("e.g. CR") },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrect = false
                    ),
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(typed) }, enabled = typed.isNotEmpty()) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
```

- [ ] **Step 4: Switch screens in `App.kt`**

Replace all of `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/App.kt` with:

```kotlin
package com.example.bluefalconcomposemultiplatform

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.bluefalconcomposemultiplatform.ble.presentation.BluetoothDeviceViewModel
import com.example.bluefalconcomposemultiplatform.ble.presentation.EnhancedBluetoothPeripheral
import com.example.bluefalconcomposemultiplatform.ble.presentation.component.DeviceScanView
import com.example.bluefalconcomposemultiplatform.checklist.BoardSnapshot
import com.example.bluefalconcomposemultiplatform.checklist.ChecklistEntryRow
import com.example.bluefalconcomposemultiplatform.checklist.ChecklistScreen
import com.example.bluefalconcomposemultiplatform.checklist.ChecklistViewModel
import com.example.bluefalconcomposemultiplatform.checklist.PREFLIGHT_STEPS
import com.example.bluefalconcomposemultiplatform.checklist.boardFocus
import com.example.bluefalconcomposemultiplatform.checklist.currentEpochMillis
import com.example.bluefalconcomposemultiplatform.checklist.entryLine
import com.example.bluefalconcomposemultiplatform.checklist.formatClockTime
import com.example.bluefalconcomposemultiplatform.checklist.progress
import com.example.bluefalconcomposemultiplatform.checklist.staleHours
import com.example.bluefalconcomposemultiplatform.core.presentation.AppHeader
import com.example.bluefalconcomposemultiplatform.core.presentation.BlueFalconTheme
import com.example.bluefalconcomposemultiplatform.core.presentation.LaunchGlowBackground
import com.example.bluefalconcomposemultiplatform.core.presentation.PlatformBackHandler
import com.example.bluefalconcomposemultiplatform.di.AppModule
import com.example.bluefalconcomposemultiplatform.ui.theme.TextPrimary
import dev.icerock.moko.mvvm.compose.getViewModel
import dev.icerock.moko.mvvm.compose.viewModelFactory

// The two screens: the scan screen (the home screen for now) and the pre-flight checklist
private enum class Screen { Scan, Checklist }

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
                BluetoothDeviceViewModel(appModule.blueFalcon, appModule.platformBluetooth)
            }
        )
        val checklistViewModel = getViewModel(
            key = "preflight-checklist",
            factory = viewModelFactory {
                ChecklistViewModel(appModule.checklistStorage)
            }
        )

        val state by viewModel.deviceState.collectAsState()
        val parsedPackets by viewModel.parsedPackets.collectAsState()
        val ticks by checklistViewModel.ticks.collectAsState()
        val initials by checklistViewModel.initials.collectAsState()
        var screen by rememberSaveable { mutableStateOf(Screen.Scan) }

        // Readings are worked out from the live Bluetooth state on every update; they're never saved
        val focus = boardFocus(state.devices.values.map { it.toBoardSnapshot() }, state.now)

        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.Transparent,
            contentColor = TextPrimary
        ) {
            Box(Modifier.fillMaxSize()) {
                // The background fills the whole screen; content stays clear of the notch and home indicator
                LaunchGlowBackground()
                Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                    when (screen) {
                        Screen.Scan -> {
                            AppHeader()
                            ChecklistEntryRow(
                                summary = entryLine(
                                    progress(PREFLIGHT_STEPS, ticks),
                                    ticks.startedAtMillis?.let(::formatClockTime)
                                ),
                                onClick = { screen = Screen.Checklist },
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
                            )
                            DeviceScanView(
                                state = state,
                                parsedPackets = parsedPackets,
                                onEvent = viewModel::onEvent,
                                onOpenSettings = appModule.platformBluetooth::openAppSettings
                            )
                        }
                        Screen.Checklist -> {
                            PlatformBackHandler { screen = Screen.Scan }
                            ChecklistScreen(
                                ticks = ticks,
                                initials = initials,
                                focus = focus,
                                staleHours = staleHours(ticks.startedAtMillis, currentEpochMillis()),
                                onTick = checklistViewModel::tick,
                                onUntick = checklistViewModel::untick,
                                onSetInitials = checklistViewModel::setInitials,
                                onStartNew = checklistViewModel::startNew,
                                onBack = { screen = Screen.Scan }
                            )
                        }
                    }
                }
            }
        }
    }
}

// What the checklist needs to know about a board. Telemetry isn't kept per board yet, so for now the
// readings say "no data yet".
private fun EnhancedBluetoothPeripheral.toBoardSnapshot() = BoardSnapshot(
    name = peripheral.name?.takeIf { it.isNotBlank() } ?: "No Name",
    connected = connected,
    telemetry = null,
    telemetryAt = null
)
```

- [ ] **Step 5: Compile and run the tests**

Run the **iOS compile check**, then the **Unit tests** commands.
Expected:
- Compile: `BUILD SUCCESSFUL` with no `e:` lines. A deprecation warning for `autoCorrect` is acceptable, if one appears.
- Tests: all pass (22 + 6 + 11).

- [ ] **Step 6: Check it in the Simulator**

Run the **Run in the iOS Simulator** commands.
Expected:
- The app opens without crashing.
- Under "GOOBER Connect / GROUND STATION" there's a gold-bordered row: **Pre-flight checklist** with "Not started · 29 steps" and a gold `›`.
- The Bluetooth status line and the list sit underneath.

Then seed a part-done checklist and look again. This exercises loading, the time format and the summary line without tapping:
```bash
NOW=$(( $(date +%s) * 1000 ))
xcrun simctl terminate "iPhone 17" cliverafiq.goober1 2>/dev/null || true
xcrun simctl spawn "iPhone 17" defaults write cliverafiq.goober1 preflight-checklist -string \
  "v2;started=$NOW;tick=8.1,CR,$NOW;tick=8.2,CR,$NOW;tick=8.3,CR,$NOW;tick=8.4,CR,$NOW;tick=8.5,CR,$NOW"
xcrun simctl spawn "iPhone 17" defaults write cliverafiq.goober1 preflight-initials -string "CR"
xcrun simctl launch "iPhone 17" cliverafiq.goober1
sleep 8
xcrun simctl io "iPhone 17" screenshot build/xcode-sim/screen-seeded.png
xcrun simctl spawn "iPhone 17" defaults delete cliverafiq.goober1 preflight-checklist
xcrun simctl spawn "iPhone 17" defaults delete cliverafiq.goober1 preflight-initials
```
Expected: the row reads "5 of 29 done · started *now*", with *now* in the Simulator's time format. If it still says "Not started · 29 steps", the Simulator didn't pass the seeded value to the app. Note that and rely on the iPhone check in Step 7.

- [ ] **Step 7: Check it on the iPhone (the user does the tapping)**

Run the **Build, install and launch on the iPhone** commands. You don't need to connect to any board for this check. The checklist never sends anything to a board.

Have the user check:
1. **Scan screen:** the gold **Pre-flight checklist** row shows "Not started · 29 steps", or the progress saved from earlier. Tapping it opens the checklist, and the back arrow returns.
2. **The checklist:**
   - The title and back arrow.
   - The progress card says "0 of 29 done", with the initials chip showing "—".
   - The sections "8 · ONSITE PREFLIGHT READY" (8 steps), "9 · PRE-LAUNCH" (15) and "G · LAUNCH" (6), numbered as on the paper.
3. **First tick:** tap 8.1.
   - The initials dialog appears. Type "cr" and it shows "CR". Save.
   - 8.1 shows a green tick and "CR · *time*".
   - The card says "1 of 29 done", with "started *time*" under it and **CR** on the chip.
4. **Untick:** tap 8.1 again. The tick and stamp clear; the start time stays.
5. **Change initials:** tap the chip, change them to e.g. "JM", and tick 8.2.
   - 8.2 shows "JM · *time*".
   - Re-tick 8.1: it shows "JM" too. Earlier stamps don't change.
6. **Readings without a board:** scroll to 9.11.
   - Its block says "No board connected" with grey "—" for Battery, Drogue continuity, Main continuity and GPS.
   - Ticking 9.11 asks nothing.
   - 9.14 and G.3 show a GPS block.
7. **Saving:** swipe the app away and reopen it.
   - The scan-screen row shows the progress.
   - The ticks, stamps, start time and initials are all still there.
8. **Start new:**
   - Tap **Start new checklist**, then **Cancel**: nothing changes.
   - Tap it again, then **Start new**: all ticks clear, the initials stay, and the scan-screen row says "Not started · 29 steps".
9. **Console:** no `Uncaught Kotlin exception`.

- [ ] **Step 8: Commit (the user runs this)**

```bash
git add shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ui/theme/Color.kt \
  shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/core/presentation/PlatformBackHandler.kt \
  shared/src/iosMain/kotlin/com/example/bluefalconcomposemultiplatform/core/presentation/PlatformBackHandler.ios.kt \
  shared/src/androidMain/kotlin/com/example/bluefalconcomposemultiplatform/core/presentation/PlatformBackHandler.android.kt \
  shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/checklist/ChecklistScreen.kt \
  shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/App.kt
git commit -m "add the pre-flight checklist screen and its entry on the scan screen"
```

---

### Task 4: Readings follow live telemetry

**Files:**
- Modify: `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/EnhancedBluetoothPeripheral.kt` (replace all)
- Modify: `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/BluetoothDeviceViewModel.kt` (imports; the `OnCharacteristicReadComplete` branch)
- Modify: `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/App.kt` (`toBoardSnapshot` only)

**Interfaces:**
- Consumes:
  - From Task 1: `BoardSnapshot(name, connected, telemetry, telemetryAt)`.
  - From Task 3: `App.kt`'s `toBoardSnapshot()`.
  - From `goober.kt`: `GooberPayload.Telemetry(val payload: GooberPostTelemetryPayload)`.
  - From the view model: `now()`.
- Produces: `EnhancedBluetoothPeripheral.lastTelemetry: GooberPostTelemetryPayload?` and `.lastTelemetryAt: Duration?`.

- [ ] **Step 1: Keep the latest telemetry per board**

Replace all of `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/EnhancedBluetoothPeripheral.kt` with:

```kotlin
package com.example.bluefalconcomposemultiplatform.ble.presentation

import com.example.bluefalconcomposemultiplatform.GooberPostTelemetryPayload
import dev.bluefalcon.BluetoothPeripheral
import kotlin.time.Duration

data class EnhancedBluetoothPeripheral(
    val connected: Boolean,
    val peripheral: BluetoothPeripheral,
    // Smoothed signal strength from the board's broadcasts, in dBm
    val rssi: Float? = null,
    // When the board was last heard, on the view model's clock
    val lastSeenAt: Duration? = null,
    // The board's latest telemetry packet and when it arrived, on the view model's clock
    val lastTelemetry: GooberPostTelemetryPayload? = null,
    val lastTelemetryAt: Duration? = null
)
```

- [ ] **Step 2: Store it when a telemetry packet is read**

In `BluetoothDeviceViewModel.kt`:

a) Add this import next to `import com.example.bluefalconcomposemultiplatform.GooberPacket`:

```kotlin
import com.example.bluefalconcomposemultiplatform.GooberPayload
```

b) In the `is DeviceEvent.OnCharacteristicReadComplete` branch, find this block inside `if (parsedPacket != null) {`:

```kotlin
                                // Update parsed packets StateFlow
                                _parsedPackets.update { current ->
                                    current.toMutableMap().apply {
                                        put(event.macId, parsedPacket)
                                    }
                                }
```

and add right after it, still inside `if (parsedPacket != null) {`:

```kotlin

                                // Keep the latest telemetry per board for the pre-flight checklist. Moving
                                // `now` along with it keeps the packet's age from going negative.
                                (parsedPacket.payload as? GooberPayload.Telemetry)?.let { telemetry ->
                                    val receivedAt = now()
                                    _deviceState.update { state ->
                                        val device = state.devices[event.macId] ?: return@update state
                                        val devices = HashMap(state.devices)
                                        devices[event.macId] = device.copy(
                                            lastTelemetry = telemetry.payload,
                                            lastTelemetryAt = receivedAt
                                        )
                                        state.copy(devices = devices, now = receivedAt)
                                    }
                                }
```

`update` is an inline function, so `return@update state` returns the state unchanged for a board that isn't in the list. Other packet types don't touch the telemetry fields. The existing `parsedPackets` and the connected card are unchanged.

- [ ] **Step 3: Feed it to the checklist**

In `App.kt`, replace the `toBoardSnapshot` function at the bottom (its comment included) with:

```kotlin
// What the checklist needs to know about a board, including its latest telemetry
private fun EnhancedBluetoothPeripheral.toBoardSnapshot() = BoardSnapshot(
    name = peripheral.name?.takeIf { it.isNotBlank() } ?: "No Name",
    connected = connected,
    telemetry = lastTelemetry,
    telemetryAt = lastTelemetryAt
)
```

- [ ] **Step 4: Compile and run the tests**

Run the **iOS compile check**, then the **Unit tests** commands.
Expected: `BUILD SUCCESSFUL`, no `e:` lines, and all tests pass (22 + 6 + 11).

- [ ] **Step 5: Check nothing changed without a board**

Run the **Build, install and launch on the iPhone** commands.
Expected:
- The scan screen and the checklist behave as at the end of Task 3.
- The reading blocks still say "No board connected".
- No `Uncaught Kotlin exception` in the console.

- [ ] **Step 6: Check with a real board (when one is available)**

This needs an ASCENT that actually sends telemetry. The 2026-10-15 "No data received" problem must be solved first; see the spec's open questions. If no board is available, do Step 7 now and do this check in Task 5.

1. On the scan screen, **check the card shows the board name you expect** before tapping Connect. A board in range on 2026-10-15 turned out to be someone else's.
2. Connect, then open the checklist.
   - 9.11's block says "● *name* · last packet … s ago".
   - Its readings turn green or red with real values, e.g. "7.92 V", "Continuity", "9 satellites".
   - 9.14's and G.3's GPS rows show the same GPS value.
3. If any 9.11 reading is red, tap 9.11.
   - It asks "Tick 9.11 anyway?" and lists the red readings.
   - **Cancel** leaves it unticked; **Tick anyway** ticks it.
4. Switch the board off.
   - Within about 4 s the blocks say "*name* · last data N s ago", with grey "—" values.
   - Disconnect: they say "No board connected".
5. Console: no `Uncaught Kotlin exception`.

- [ ] **Step 7: Commit (the user runs this)**

```bash
git add shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/EnhancedBluetoothPeripheral.kt \
  shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/BluetoothDeviceViewModel.kt \
  shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/App.kt
git commit -m "show live ASCENT readings in the pre-flight checklist"
```

---

### Task 5: Full check and the spec status

**Files:**
- Modify: `docs/superpowers/specs/2026-10-22-preflight-checklist-design.md` (the status line)

- [ ] **Step 1: Fresh install on the iPhone**

1. The user deletes GOOBER Connect from the phone: long-press the icon → Remove App → Delete App.
2. Run the **Build, install and launch on the iPhone** commands, and allow Bluetooth when asked.
3. Then run through Task 3 Step 7, items 1–9, again. The first item must show "Not started · 29 steps", and the initials chip must show "—".

- [ ] **Step 2: Light mode**

The user switches the phone to Light under Settings → Display & Brightness.
Expected:
- The scan screen and the checklist stay dark, with light status-bar text.
- The section labels, progress bar, initials and back arrow stay gold.

Switch back afterwards if the user prefers.

- [ ] **Step 3: With a real board (when one is available)**

Do Task 4 Step 6 if it hasn't been done. If a reading disagrees with the board's real state (battery, continuity or satellites), stop and compare with the connected card's telemetry rows before changing any limits.

- [ ] **Step 4: Android (needs a teammate with the Android SDK and a phone)**

```bash
./gradlew :androidBlueFalconExampleMP:assembleDebug --console=plain
```
Expected: `BUILD SUCCESSFUL`. Then install the app and check:
- Task 3 Step 7, items 1–9.
- The system **Back** button on the checklist returns to the scan screen.
- Ticks and initials survive closing the app.

If no Android SDK is available, leave this for the teammate and say so in the pull request.

- [ ] **Step 5: Update the spec status**

In `docs/superpowers/specs/2026-10-22-preflight-checklist-design.md`, replace the line

```markdown
- **Status:** Design approved; implementation not started
```

with the variant that matches what was checked:

- Readings and Android checks still to do:
  ```markdown
  - **Status:** Implemented on branch `preflight-checklist`; checked on iPhone without a board; live readings and Android not yet checked
  ```
- Live readings checked on the iPhone (Step 3 done):
  ```markdown
  - **Status:** Implemented on branch `preflight-checklist`; checked on iPhone, including live readings; Android not yet checked
  ```

- [ ] **Step 6: Commit (the user runs this)**

```bash
git add docs/superpowers/specs/2026-10-22-preflight-checklist-design.md
git commit -m "mark pre-flight checklist design as implemented"
```
