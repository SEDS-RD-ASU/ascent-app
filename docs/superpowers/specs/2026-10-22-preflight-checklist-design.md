# Pre-flight checklist

- **Date:** 2026-10-22
- **Status:** Design approved; implementation not started
- **Platforms:** iOS and Android (shared Compose Multiplatform code, plus small platform pieces)
- **Builds on:** the scan screen work on branch `scan-screen-redesign`
  ([2026-10-15-scan-screen-design.md](2026-10-15-scan-screen-design.md))

![Scan screen entry, checklist with no board, checklist checking a board](2026-10-22-preflight-checklist-assets/mockup-checklist.png)

Where the mockup and the text below differ, the text wins. For example, a step ticked by hand shows
the live reason ("Checked by hand · No board connected") rather than the mockup's "no data", and
times follow the phone's 12- or 24-hour setting.

## Why

Launch-day mistakes are usually small: a step skipped at the pad, a pyro channel without
continuity, a battery that's lower than it looked, no GPS fix. The board already sends battery
voltage, continuity for four pyro channels and the GPS satellite count, but today the app only
lists the raw numbers on the connected card.

This adds a checklist that walks through the launch steps, with the board-related steps ticked
automatically from live telemetry. It's a guide and a record: it never sends anything to the board,
and it doesn't unlock or block anything. It's the second of three planned improvements, after the
scan screen and before the home screen.

## What you'll see

### Opening it

- On the scan screen, a new row under the header (gold border): **Pre-flight checklist**, with a
  short progress line underneath: "Not started · 12 steps" when nothing is ticked, "5 of 12 done ·
  started 7:15 PM" part-way (no "by hand" count here, to keep it short), and "All 12 steps done"
  at the end. Tapping it opens the checklist full screen.
- The checklist has a back arrow and the title "Pre-flight checklist". The back arrow returns to the
  scan screen; on Android the system Back button does the same. iOS has no system back gesture here,
  so the arrow is the only way back. Going back doesn't disconnect the board.
- The top bar and the progress card stay put; the steps scroll underneath. **Start new checklist**
  is at the end of the list.

### The steps

All steps live in one file, `ChecklistSteps.kt`, which is the file the team edits. Each step has a
fixed id, so renaming or reordering steps doesn't upset saved ticks. On screen, the steps are
grouped under the phase labels "AT THE PREP TABLE" and "AT THE PAD".

| # | Id | Phase | Kind | Title |
|---|---|---|---|---|
| 1 | `recovery-packed` | At the prep table | Manual | Recovery packed: drogue, main, harness |
| 2 | `charges-installed` | At the prep table | Manual | Ejection charges / e-matches installed |
| 3 | `avbay-closed` | At the prep table | Manual | Avionics bay closed, shear pins in |
| 4 | `motor-installed` | At the prep table | Manual | Motor installed and retained |
| 5 | `on-rail` | At the pad | Manual | Rocket on the rail, angle set |
| 6 | `avionics-on` | At the pad | Manual | Avionics switched on |
| 7 | `board-talking` | At the pad | Board | Board is talking |
| 8 | `battery` | At the pad | Board | Battery at least 7.4 V |
| 9 | `drogue-continuity` | At the pad | Board | Drogue continuity (pyro 1) |
| 10 | `main-continuity` | At the pad | Board | Main continuity (pyro 2) |
| 11 | `gps-fix` | At the pad | Board | GPS fix: at least 6 satellites |
| 12 | `igniter` | At the pad | Manual | Igniter installed — last, once everyone is clear |

The settings sit in the same file:

| Setting | Default | Why this value |
|---|---|---|
| `MIN_BATTERY_VOLTS` | 7.4 | Nominal voltage of a 2-cell LiPo; to be confirmed for the battery the team flies |
| `MIN_SATELLITES` | 6 | A common bar for a solid fix (4 is the minimum for any fix) |
| `FRESH_TELEMETRY` | 3 s | How recent the last telemetry packet must be |
| `DROGUE_PYRO_CHANNEL` | 1 | A guess; which channel is which is an open firmware question |
| `MAIN_PYRO_CHANNEL` | 2 | Same |

Pyro channel *n* is bit *n − 1* of the telemetry's `pyroState`, the same mapping the connected card
already uses. Battery volts are the raw value ÷ 2500, also as on the card. The **flight state** byte
isn't used: nothing in the app says what its values mean.

### Step states

**Manual steps** are Not done (empty ring) or Done (green tick). Tapping toggles.

**Board steps** show one of four states, each with a detail line under the title and a small
**BOARD** tag on the right:

| State | Looks like | When |
|---|---|---|
| Waiting | dashed grey ring, grey text | the check can't be made (see reasons below) |
| OK | green tick, green detail | the latest fresh telemetry passes the check |
| Problem | red "!", red detail | the latest fresh telemetry fails the check |
| Checked by hand | orange tick, "Checked by hand · …" | a person ticked it and the board hasn't confirmed it |

Detail wording:

| Check | OK | Problem |
|---|---|---|
| Board is talking | "Last packet 0.4 s ago" (one decimal) | never red: old data is Waiting |
| Battery | "7.92 V" | "7.10 V · needs 7.4 V" |
| Drogue / Main continuity | "Continuity" | "No continuity" |
| GPS fix | "9 satellites" | "4 satellites · needs 6" |

Waiting reasons, the same for every board step:

- No connected board: "No board connected"
- More than one connected board: "Connect only one board"
- Connected, but no telemetry packet yet: "No data yet"
- Last telemetry packet older than 3 s: "Last data 12 s ago" (whole seconds)

Rules for board steps:

- **OK wins.** If the board confirms the step, it shows green, whether or not it was also ticked by
  hand. A hand tick is kept underneath, so if the reading later turns bad the step shows Checked by
  hand with the red reading rather than dropping back to an unticked Problem.
- **Ticking by hand:** tapping a Waiting or Problem step ticks it by hand; tapping a Checked by hand
  step removes the hand tick; tapping a green step does nothing.
- A hand-ticked step keeps showing the live situation after "Checked by hand · ", e.g. "Checked by
  hand · No board connected". If the board reports a problem, that part stays red ("Checked by
  hand · 7.10 V · needs 7.4 V"), so a bad reading is never hidden.

Above the board steps, a header row names the board being checked:

- No connected board: "No board connected — board steps can be ticked by hand"
- One connected board: green dot + "Checking **ASCENT R3 SN4**"
- More than one: "More than one board connected — disconnect the others"

Naming the board matters: on 2026-10-15 a nearby board was mistaken for a test device.

### Progress, finishing and starting again

- The progress card reads "5 of 12 done · 1 by hand · started 7:15 PM", with a gold bar. The "by
  hand" part only appears when at least one board step is Checked by hand; the "started" part only
  once a start time exists.
- A step counts as done if it's a Done manual step, an OK board step, or a Checked by hand board
  step.
- When everything is done the card says "All 12 steps done" (plus "· 2 by hand" if any) and the bar
  turns green.
- **Start time** is set by the first tick on an empty checklist: the first time it's used, and after
  each "Start new checklist". It's shown in the phone's time format ("7:15 PM" or "19:15").
- **Old checklist:** if the start time is more than 12 hours ago, a banner at the top says "Started
  14 h ago. Start a new checklist?" with a **Start new** button.
- **Start new checklist** (bottom of the list, or the banner's button) asks first: "Start a new
  checklist?" / "This clears every tick, including the ones checked by hand." with **Cancel** and
  **Start new**. Starting new clears all ticks and the start time.

### Saving

- The start time, the manual ticks and the hand ticks are saved on the phone straight after every
  change, so they survive the app being closed or the phone restarting.
- Board OK states are never saved: they're worked out from live data every time.
- If the saved data can't be read, the app starts with an empty checklist instead of crashing.
  Ticks for step ids that no longer exist are ignored; new steps start unticked.

### Colours

Same Launch Glow theme as the scan screen, plus:

- Done / OK: green `#3DDC84` (detail text `#7EE8AE`)
- Checked by hand: orange `#FF9F1C` (detail text `#FFB45C`), kept distinct from the gold accent
- Problem: red `#FF5A4E` (detail text `#FF8A80`)
- Not done: empty ring `rgba(255,255,255,.35)`; Waiting: dashed ring `rgba(255,255,255,.28)`
- Gold `#FFC627` for the phase labels, the progress bar, the back arrow and the entry row's border

## How it works

### New files

In `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/checklist/`:

- **`ChecklistSteps.kt`:** `ChecklistPhase` (prep table, pad), `BoardCheck` (talking, battery,
  drogue continuity, main continuity, GPS fix), `ChecklistStep(id, phase, title, boardCheck?)`, the
  list of 12 steps and the five settings.
- **`ChecklistRules.kt`:** small functions with no UI and no platform code, all unit-tested:
  - battery volts and pyro-channel continuity from a telemetry payload;
  - which board to check from the Bluetooth view model's devices (none, one, or too many) and how
    old its last telemetry is;
  - each board check's result (Waiting with a reason, OK or Problem with its detail);
  - each step's state from the ticks and the check result (OK wins, then hand tick);
  - progress (done, total, by hand, all done);
  - the 12-hour rule;
  - saving and loading the ticks as text.
- **`ChecklistStorage.kt`:** `interface ChecklistStorage { fun load(): String?; fun save(text: String) }`.
  Implementations: `IosChecklistStorage` (`NSUserDefaults`) in `iosMain` and
  `AndroidChecklistStorage(context)` (`SharedPreferences`) in `androidMain`, both under the key
  `preflight-checklist`. `AppModule` provides it as `checklistStorage`, the same way it provides
  `platformBluetooth`.
- **`ClockTime.kt`:** `expect fun currentEpochMillis(): Long` and
  `expect fun formatClockTime(epochMillis: Long): String`, with small `iosMain` and `androidMain`
  versions. Kotlin 2.0.20's standard library has no wall clock, and this avoids adding a library.
- **`ChecklistViewModel.kt`:** holds the saved part (start time, manual ticks, hand ticks) in a
  `StateFlow`. It loads on creation and saves on every change; its actions are tap a step and start
  new.
- **`ChecklistScreen.kt`:** the checklist screen, plus the entry row used on the scan screen.
- **`PlatformBackHandler`:** an `expect` composable. Android uses `BackHandler` from
  `androidx.activity.compose` (already a dependency); iOS does nothing.

The saved text is one line, e.g.
`v1;started=1792721854000;manual=recovery-packed,on-rail;hand=battery`. Anything that doesn't parse
gives an empty checklist.

### Changes to existing code

- **`EnhancedBluetoothPeripheral`** gets `lastTelemetry` (the latest telemetry payload) and
  `lastTelemetryAt` (when it arrived, on the view model's clock).
- **`BluetoothDeviceViewModel`:** when a read gives a telemetry packet, it sets those two fields and
  refreshes the state's `now`, so the packet's age is never negative. Other packet types don't touch
  them. The existing `parsedPackets` and the connected card are unchanged.
- **`App.kt`:** keeps which screen is showing (scan or checklist) and draws it over the Launch Glow
  background. The scan screen shows the header, the entry row and the device list. The checklist
  view model is created next to the Bluetooth one.
- **`AppModule`** (common and both platforms) gains `checklistStorage`.

### Data flow

```
Bluetooth view model ─(connected boards, latest telemetry, its age)─► rules ─► board step states ─┐
                                                                                                    ├─► ChecklistScreen
Checklist view model ─(start time, manual ticks, hand ticks)◄──► storage on the phone ─────────────┘
```

The checklist never talks to Bluetooth directly; it only reads what the Bluetooth view model
already knows. Board step states are recalculated whenever that state changes. Its `now` refreshes
at least once a second, so a board that goes quiet turns its steps to Waiting about 1 s after its
last packet passes 3 s.

## Testing

- **Unit tests** in `shared/src/commonTest/.../checklist/ChecklistRulesTest.kt`, run on the iOS
  Simulator with `./gradlew :shared:iosSimulatorArm64Test`:
  - Battery: raw 18500 (7.40 V) is OK, raw 18475 (7.39 V) is a Problem; detail wording.
  - Continuity: channel 1 and 2 read the right bits, set and clear; other bits don't matter.
  - GPS: 6 satellites OK, 5 a Problem.
  - Freshness: exactly 3 s old is still fresh; older is Waiting with "Last data N s ago"; no
    telemetry is "No data yet".
  - Choosing the board: 0, 1 and 2 connected boards; boards that aren't connected are ignored.
  - Step state: OK beats a hand tick; a hand tick beats Waiting and Problem.
  - Progress: counts including hand ticks; all done.
  - 12-hour rule: exactly 12 h isn't old, 12 h 1 min is; no start time is never old.
  - Saving: round trip; unreadable text gives an empty checklist; unknown ids are dropped.
- **On the iPhone, no board needed:**
  1. The scan screen shows "Not started · 12 steps"; tapping opens the checklist; the back arrow
     returns.
  2. Tick manual steps: they turn green, progress updates, and the start time appears after the
     first tick.
  3. Tap a board step: orange "Checked by hand · No board connected". Tap again: back to Waiting.
  4. Swipe the app away and reopen it: the ticks and start time are still there, and the scan
     screen row shows the progress.
  5. Start new checklist: Cancel keeps everything; Start new clears it.
  6. The screens match the mockup.
- **With a real board** (needs telemetry working first, see below):
  1. Before connecting, check the card shows the board name you expect.
  2. Connect: the header says "Checking" and the name; board steps turn green or red with real
     readings.
  3. Switch the board off: its steps go back to Waiting within about 4 s, with "Last data N s ago".
- **On Android** (needs someone with an Android phone): the same lists, plus the system Back button
  returns to the scan screen.

## Not included / open questions

- **Telemetry isn't confirmed yet.** On 2026-10-15 the connected card showed "No data received" from
  ASCENT R3 SN4. Until that's solved, board steps can only be ticked by hand. Questions for the
  firmware owner: does the board answer reads on `0x0001` with telemetry, or push it as
  notifications? Does it need **Wakeup** before it sends data?
- **Values to confirm with the team:** the battery type behind 7.4 V, which pyro channels are
  drogue and main, and the satellite bar.
- **Flight state:** could become a step ("board in pad/idle state") once the firmware owner says
  what its values mean.
- Not included: editing steps in the app, a history or export of finished checklists, more than one
  checklist at a time.
- The 12-hour rule uses the phone's clock, so changing the clock or time zone affects it.
