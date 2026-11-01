# Board Commands Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the connected card the web configurator's seven buttons with the firmware's real codes: TX Lock, Reboot, AUX On, AUX Off, Erase Flash, Pop Apogee and Pop Mains. The pops sit behind an arm switch and a press-and-hold, and Erase Flash behind a press-and-hold. The card also decodes the telemetry current firmware sends, and says when a board's firmware sends none.

**Architecture:**
- **Protocol:** `goober.kt` gets the firmware's message classes (`AscentMessageClass`) and decodes class 21 (TELEMETRY).
- **Rules:** every command decision is a pure function in `CommandRules.kt`, covered by unit tests: the command table, the packet bytes, the arming window, whether a command may be sent, the countdown text and pop continuity.
- **View model:** `BluetoothDeviceViewModel` keeps each board's arming time and whether its reads are single bytes. It sends commands as raw bytes and refuses a pop for a board that isn't armed.
- **Screen:** `CommandPanel.kt` (the Commands group and the dialogs), `GroundTestPanel.kt` and `HoldToConfirmButton.kt` replace the old command code in `FoundDeviceCard.kt`.
- **Testing without a board:** a Swift script on the Mac (`tools/fake-ascent.swift`) pretends to be a board and prints every byte the app writes.

**Tech Stack:** Kotlin 2.0.20, Compose Multiplatform 1.6.11 (Material3, material icons), moko-mvvm 0.16.1, BlueFalcon 2.2.4, kotlin.test. Swift and CoreBluetooth for the fake board.

Spec: [`docs/superpowers/specs/2026-11-01-board-commands-design.md`](../specs/2026-11-01-board-commands-design.md) (commit `9f6875f`)

## Global Constraints

- **No new libraries and no Gradle changes.** Use only what `shared/build.gradle.kts` already has.
- **Where code goes:**
  - Protocol: `goober.kt`, package `com.example.bluefalconcomposemultiplatform`.
  - Rules: `ble/presentation/CommandRules.kt`, package `com.example.bluefalconcomposemultiplatform.ble.presentation`.
  - Screen pieces: `ble/presentation/component`.
  - The fake board: `tools/fake-ascent.swift`.
- **Firmware codes** (the firmware's `ascent_message_classes_t`): REBOOT 4, PYRO1 5, PYRO2 6, AUX_ON 9, AUX_OFF 10, WAKE_UP 12, TXLOCK_ON 13, TELEMETRY 21, ERASE_FLASH 22. There is no Wakeup button.
- **The packet:** every command is the five bytes `[code, 0x21, 0x00, code, 0x00]`, written with `blueFalcon.writeCharacteristicWithoutEncoding(peripheral, characteristic, packet, null)`. Never use the String or ByteArray `writeCharacteristic`: they re-encode the bytes as text.
- **Telemetry:**
  - Class 21 with a payload of at least 34 bytes is decoded as telemetry.
  - The layout is unchanged. The fields `xAcc` and `gyrX` become `yAcc` and `gyrY`.
  - Battery volts are the raw value ÷ 2500.
- **Confirmations:** AUX On and AUX Off send on tap. TX Lock and Reboot ask with a tap. Erase Flash needs a 2-second hold. The pops need the board armed, then a 2-second hold.
- **Arming:**
  - Per board, for 2 minutes, on the wall clock (`currentEpochMillis()`).
  - It ends when the switch is turned off, the board disconnects, a pop is sent, or the time is up.
  - A clock that reads earlier than the arming time counts as not armed.
  - At the moment of sending, the view model refuses a pop for a board that isn't armed.
- **Continuity under the pops:**
  - Apogee is channel 1 and Mains channel 2, read from `pyroState`.
  - It comes only from telemetry at most 3 s old (`FRESH_TELEMETRY`). Otherwise the line says "No telemetry".
- **Wording:** all on-screen wording is copied from the spec. Use the strings in this plan exactly.
- **Colours:**

  | Use | Colour | Name in `Color.kt` |
  |---|---|---|
  | Plain command buttons | white at 11% | `PlainButton` (new) |
  | Erase Flash outline and text | `#FF8A80` | `ProblemRedText` (exists) |
  | Pop buttons, the arm switch's track | `#D32F2F` | `CommandRed` (exists) |
  | Ground-test box | background `#D32F2F` at 10%, border `#FF8A80` at 35% | `GroundTestBackground`, `GroundTestBorder` (new) |
  | Hold button | track `#7A1E1E`, fill `#E53935` | `HoldTrack`, `HoldFill` (new) |
  | Continuity / No continuity / No telemetry | `#7EE8AE` / `#FF8A80` / `#C9B8A6` | `CheckGreenText` / `ProblemRedText` / `TextSecondary` (exist) |
- **Safety:**
  - Before any phone test that sends commands, the card must show the board you expect: "ASCENT-MAC" for the fake board.
  - Any other ASCENT card is a real board. Don't connect to it or tap its buttons. On 2026-10-15 a real board, "ASCENT R3 SN4", was in range unnoticed.
  - The pops go to a real board only in a team ground test with a test load.
- **Platforms:** iOS is built and tested on this Mac. Android can't be built here (no SDK), so a teammate checks it.
- **Committing:** the user commits. Each task ends with `git add`/`git commit` commands for them. Never add `iosBlueFalconExampleMP/iosBlueFalconExampleMP.xcodeproj/project.pbxproj`; it holds the user's local signing.

---

## Before you start

1. Work on the branch `board-commands`. The user created it from `preflight-checklist`, and the spec is already committed there (`9f6875f`).
2. Run the unit tests once (command below). Expected: `ScanRulesTest` 11, `ChecklistRulesTest` 22 and `ChecklistViewModelTest` 6 tests, all passing.

## Commands used in several tasks

Run everything from the repo root, `/Users/cliverafiq/dev/ascent-app`.

**Unit tests (iOS Simulator):**
```bash
./gradlew :shared:iosSimulatorArm64Test --console=plain
grep -ho 'testsuite name="[^"]*" tests="[0-9]*" skipped="[0-9]*" failures="[0-9]*" errors="[0-9]*"' shared/build/test-results/iosSimulatorArm64Test/*.xml
```
The second line prints one summary per test class.

**iOS compile check (fast):**
```bash
./gradlew :shared:compileKotlinIosArm64 --console=plain
```

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
- **Free signing lasts 7 days.** The current one runs until 2026-11-05 19:42. If the app won't open, rebuild. If iOS then says the developer isn't trusted, the user trusts it again under Settings → General → VPN & Device Management.
- **`--console`** prints the app's output, including any `Uncaught Kotlin exception`. It blocks until the app exits.

**The fake board** (from Task 2 on):
```bash
mkdir -p build && swiftc -O tools/fake-ascent.swift -o build/fake-ascent
build/fake-ascent telemetry 900
```
- The first word sets what reads answer with: `telemetry` for packet firmware, or `battery` for first-byte firmware. The number is how many seconds it runs.
- Run it in its own terminal, or in the background with `build/fake-ascent telemetry 900 > build/fake-ascent.log 2>&1 &`.
- Every write the app makes shows up as a `WRITE` line. Stop it with Ctrl-C or `pkill -f build/fake-ascent`.

## File structure

| File | New / changed | Responsibility |
|---|---|---|
| `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/goober.kt` | changed | `AscentMessageClass`; decoding class-21 telemetry; `GooberPacket.msgCls` as the raw byte; `yAcc`/`gyrY`; old code removed |
| `.../ble/presentation/CommandRules.kt` | new | `Confirmation`, `BoardCommand`, `commandPacket`, `PYRO_ARM_DURATION`, `HOLD_TO_CONFIRM`, `armedTimeLeft`, `canSend`, `formatTimeLeft`, `PopContinuity`, `popContinuity` |
| `.../ble/presentation/component/HoldToConfirmButton.kt` | new | The press-and-hold button |
| `.../ble/presentation/component/CommandPanel.kt` | new | The Commands group, the ground-test box's place, and the confirmation dialogs |
| `.../ble/presentation/component/GroundTestPanel.kt` | new | The arm switch, its countdown, the two pops and their continuity |
| `.../ble/presentation/component/FoundDeviceCard.kt` | changed | Packet panel wording; the old command code goes; passes state to `CommandPanel` |
| `.../ble/presentation/component/DeviceScanView.kt` | changed | Works out each card's battery-only flag, time left and continuity |
| `.../ble/presentation/BluetoothDeviceViewModel.kt` | changed | Battery-only reads, `sendCommand`, arming |
| `.../ble/presentation/EnhancedBluetoothPeripheral.kt` | changed | `batteryOnlyReads`, `pyroArmedAtMillis` |
| `.../ble/presentation/BluetoothDeviceState.kt` | changed | `nowMillis` |
| `.../ble/presentation/UiEvent.kt` | changed | `OnSendCommand`, `OnArmPyro`, `OnDisarmPyro` |
| `.../ui/theme/Color.kt` | changed | New colours; `CommandGreen` and `CommandPurple` go |
| `shared/src/commonTest/kotlin/com/example/bluefalconcomposemultiplatform/GooberTest.kt` | new | Message classes and decoding |
| `shared/src/commonTest/.../ble/presentation/CommandRulesTest.kt` | new | The command rules |
| `shared/src/commonTest/.../checklist/ChecklistRulesTest.kt` | changed | Renamed telemetry fields |
| `tools/fake-ascent.swift` | new | The Mac pretending to be a board |
| `docs/superpowers/specs/2026-11-01-board-commands-design.md` | changed (Task 6) | Status line |

`...` stands for `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform` (or `commonTest` where it says so).

---

### Task 1: Telemetry from current firmware

**Files:**
- Create: `shared/src/commonTest/kotlin/com/example/bluefalconcomposemultiplatform/GooberTest.kt`
- Modify: `.../goober.kt` (whole file)
- Modify: `.../ble/presentation/EnhancedBluetoothPeripheral.kt`
- Modify: `.../ble/presentation/BluetoothDeviceViewModel.kt`
- Modify: `.../ble/presentation/component/FoundDeviceCard.kt`
- Modify: `.../ble/presentation/component/DeviceScanView.kt`
- Modify: `shared/src/commonTest/kotlin/com/example/bluefalconcomposemultiplatform/checklist/ChecklistRulesTest.kt`

**Interfaces:**
- Consumes: nothing new.
- Produces:
  - `enum class AscentMessageClass(val code: UByte)` with entries PING (0) … ERASE_FLASH (22), and `AscentMessageClass.fromCode(code: UByte): AscentMessageClass?`.
  - `GooberPacket.msgCls: UByte`.
  - `GooberPostTelemetryPayload` fields `yAcc: Float` and `gyrY: Float`, replacing `xAcc` and `gyrX`.
  - `const val TELEMETRY_PAYLOAD_SIZE = 34`.
  - `EnhancedBluetoothPeripheral.batteryOnlyReads: Boolean = false`.
  - `FoundDeviceCard(..., parsedPacket: GooberPacket?, batteryOnlyReads: Boolean, onEvent: (UiEvent) -> Unit)`.

- [ ] **Step 1: Write the failing tests**

Create `shared/src/commonTest/kotlin/com/example/bluefalconcomposemultiplatform/GooberTest.kt`:

```kotlin
package com.example.bluefalconcomposemultiplatform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class GooberTest {

    @Test
    fun messageClassesMatchTheFirmwareNumbering() {
        // The firmware's list is a C enum, so each class's code is its position in the list
        AscentMessageClass.entries.forEachIndexed { index, messageClass ->
            assertEquals(index, messageClass.code.toInt(), messageClass.name)
        }
        assertEquals(4, AscentMessageClass.REBOOT.code.toInt())
        assertEquals(5, AscentMessageClass.PYRO1.code.toInt())
        assertEquals(6, AscentMessageClass.PYRO2.code.toInt())
        assertEquals(9, AscentMessageClass.AUX_ON.code.toInt())
        assertEquals(10, AscentMessageClass.AUX_OFF.code.toInt())
        assertEquals(13, AscentMessageClass.TXLOCK_ON.code.toInt())
        assertEquals(21, AscentMessageClass.TELEMETRY.code.toInt())
        assertEquals(22, AscentMessageClass.ERASE_FLASH.code.toInt())
    }

    @Test
    fun fromCodeKnowsOnlyTheFirmwareClasses() {
        assertEquals(AscentMessageClass.TELEMETRY, AscentMessageClass.fromCode(21u))
        // 0xCA was the telemetry class of an older protocol; no firmware branch sends it
        assertNull(AscentMessageClass.fromCode(0xCAu))
    }

    // A telemetry packet as packet firmware sends it: the 5-byte header, then the payload. A shorter
    // payloadLength cuts the payload and says so in the header.
    private fun telemetryPacket(payloadLength: Int = 34): ByteArray {
        val bytes = mutableListOf<Byte>()
        fun u8(value: Int) { bytes.add(value.toByte()) }
        fun i32(value: Int) { repeat(4) { i -> u8((value shr (8 * i)) and 0xFF) } }
        fun f32(value: Float) = i32(value.toRawBits())
        fun u16(value: Int) { u8(value and 0xFF); u8((value shr 8) and 0xFF) }

        // DEV_ID 130, DEV_MODE 0x0D, SEQ_ID 7, MSG_CLS 21 (TELEMETRY), payload length
        u8(130); u8(0x0D); u8(7); u8(21); u8(payloadLength)
        i32(123_456)            // timestamp
        i32(334_215_000)        // latitude
        i32(-1_119_340_000)     // longitude
        f32(12.5f)              // altitude AGL
        f32(-3.25f)             // vertical velocity
        f32(9.75f)              // Y acceleration
        f32(0.25f)              // Y gyro
        u8(0b01)                // pyro state: continuity on channel 1 only
        u8(7)                   // satellites
        u8(2)                   // flight state
        u8(0)                   // padding
        u16(20_625)             // battery, 8.25 V
        return bytes.take(5 + payloadLength).toByteArray()
    }

    @Test
    fun telemetryIsDecodedFieldByField() {
        val bytes = telemetryPacket()
        val packet = assertNotNull(gooberParse(bytes, bytes.size))
        assertEquals(AscentMessageClass.TELEMETRY.code, packet.msgCls)
        val telemetry = assertIs<GooberPayload.Telemetry>(packet.payload).payload
        assertEquals(123_456u, telemetry.timestamp)
        assertEquals(334_215_000, telemetry.latitude)
        assertEquals(-1_119_340_000, telemetry.longitude)
        assertEquals(12.5f, telemetry.altitudeAgl)
        assertEquals(-3.25f, telemetry.verticalVelocity)
        assertEquals(9.75f, telemetry.yAcc)
        assertEquals(0.25f, telemetry.gyrY)
        assertEquals(0b01.toUByte(), telemetry.pyroState)
        assertEquals(7.toUByte(), telemetry.sats)
        assertEquals(2.toUByte(), telemetry.flightState)
        assertEquals(20_625.toUShort(), telemetry.batteryVoltage)
    }

    @Test
    fun aPayloadShorterThan34BytesIsNotTelemetry() {
        val bytes = telemetryPacket(payloadLength = 33)
        val packet = assertNotNull(gooberParse(bytes, bytes.size))
        assertIs<GooberPayload.Raw>(packet.payload)
    }

    @Test
    fun anUnknownClassKeepsItsRawByte() {
        val bytes = byteArrayOf(0x01, 0x02, 0x03, 0xCA.toByte(), 0x00)
        val packet = assertNotNull(gooberParse(bytes, bytes.size))
        assertEquals(0xCA.toUByte(), packet.msgCls)
        assertNull(AscentMessageClass.fromCode(packet.msgCls))
    }

    @Test
    fun aSingleBatteryByteIsNotAPacket() {
        // What first-byte firmware answers a read with
        assertNull(gooberParse(byteArrayOf(200.toByte()), 1))
    }
}
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `./gradlew :shared:iosSimulatorArm64Test --console=plain`
Expected: the build fails with errors like `Unresolved reference 'AscentMessageClass'`, `'yAcc'` and `'gyrY'`.

- [ ] **Step 3: Replace `goober.kt`**

Replace the whole of `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/goober.kt` with:

```kotlin
package com.example.bluefalconcomposemultiplatform

// The ASCENT firmware's message classes, in the firmware's order: enum ascent_message_classes_t in
// main/command/command.h of github.com/SEDS-RD-ASU/ascent-r3-firmware. Every branch numbers them the same
// way. Commands to the board and packets from it both use them.
enum class AscentMessageClass(val code: UByte) {
    PING(0u),
    PONG(1u),
    ACK(2u),
    NAK(3u),
    REBOOT(4u),
    PYRO1(5u),          // pyro channel 1: apogee
    PYRO2(6u),          // pyro channel 2: mains
    PYRO3(7u),
    PYRO4(8u),
    AUX_ON(9u),
    AUX_OFF(10u),
    SLEEP(11u),
    WAKE_UP(12u),
    TXLOCK_ON(13u),
    TXLOCK_OFF(14u),
    DUMP_CONFIG(15u),
    EDIT_CONFIG(16u),
    DUMP_RADIO(17u),
    EDIT_RADIO(18u),
    SIMULATOR_ON(19u),
    VOLTAGE(20u),
    TELEMETRY(21u),     // the class of the board's telemetry packets
    ERASE_FLASH(22u);

    companion object {
        fun fromCode(code: UByte): AscentMessageClass? = entries.find { it.code == code }
    }
}

// Codes from an older protocol, still sent by the card's old command buttons. They go when those
// buttons are replaced.
enum class GooberMsgType(val value: UByte) {
    // POST message types
    MSG_TYPE_POST_PINGPONG(0xFFu),
    MSG_TYPE_POST_TXLOCK_ACTIVATE(0x83u),
    MSG_TYPE_POST_TELEM(0xCAu),
    MSG_TYPE_POST_LOCATE(0xE3u),
    
    // REQ message types
    MSG_TYPE_REQ_PINGPONG(0x01u),
    MSG_TYPE_REQ_TELEM(0x02u),
    MSG_TYPE_REQ_AUX_ACTIVATE(0x0Au),
    MSG_TYPE_REQ_AUX_DEACTIVATE(0x0Bu),
    MSG_TYPE_REQ_TXLOCK_ACTIVATE(0x14u),
    MSG_TYPE_REQ_REBOOT(0x1Eu),
    MSG_TYPE_REQ_POP_APOGEE(0x69u),
    MSG_TYPE_REQ_POP_MAINS(0x6Au),
    MSG_TYPE_REQ_WAKEUP(0x6Bu);
    
    companion object {
        fun fromByte(byte: UByte): GooberMsgType? {
            return values().find { it.value == byte }
        }
    }
}

// The telemetry payload (ascent_telemetry_t in the firmware): 34 little-endian bytes laid out like the
// C struct, with one padding byte before the battery
data class GooberPostTelemetryPayload(
    val timestamp: UInt,         // 4 bytes
    val latitude: Int,           // 4 bytes
    val longitude: Int,          // 4 bytes
    val altitudeAgl: Float,      // 4 bytes
    val verticalVelocity: Float, // 4 bytes
    val yAcc: Float,             // 4 bytes: Y acceleration (the firmware's y_acc)
    val gyrY: Float,             // 4 bytes: Y gyro (the firmware's gyr_y)
    val pyroState: UByte,        // 1 byte: bit n - 1 is continuity on pyro channel n
    val sats: UByte,             // 1 byte
    val flightState: UByte,      // 1 byte
    val batteryVoltage: UShort   // 2 bytes, after the padding byte: volts x 2500
)

// The telemetry payload's size, padding included
const val TELEMETRY_PAYLOAD_SIZE = 34

// Locator payload structure
data class GooberPostLocatorPayload(
    val timestamp: Long,        // 8 bytes
    val latitude: Int,          // 4 bytes
    val longitude: Int          // 4 bytes
) // total: 16 bytes

// Single byte payload structure
data class GooberPostSingleBytePayload(
    val singleBytePayload: UByte // 1 byte payload
)

// Union of payloads (using sealed class to represent union)
sealed class GooberPayload {
    data class Telemetry(val payload: GooberPostTelemetryPayload) : GooberPayload()
    data class Locate(val payload: GooberPostLocatorPayload) : GooberPayload()
    data class SingleByte(val payload: GooberPostSingleBytePayload) : GooberPayload()
    data class Raw(val data: ByteArray) : GooberPayload() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Raw) return false
            return data.contentEquals(other.data)
        }
        
        override fun hashCode(): Int {
            return data.contentHashCode()
        }
    }
}

// GOOBER Message Structure
data class GooberPacket(
    var devId: UByte,           // 1 byte (DEV_ID)
    var devMode: UByte,         // 1 byte (DEV_MODE)
    var seqId: UByte,           // 1 byte (SEQ_ID)
    var msgCls: UByte,          // 1 byte (MSG_CLS); AscentMessageClass.fromCode gives its name
    var payloadSize: UByte,     // 1 byte (PAYLOAD_SIZE)
    var payload: GooberPayload  // N bytes
)

fun gooberParse(rxBuffer: ByteArray, rxBufferSize: Int, debug: Boolean = false): GooberPacket? {
    // Need at least 5 bytes for header
    if (rxBufferSize < 5 || rxBuffer.size < rxBufferSize) {
        return null
    }
    
    val devId = rxBuffer[0].toUByte()
    val devMode = rxBuffer[1].toUByte()
    val seqId = rxBuffer[2].toUByte()
    val msgCls = rxBuffer[3].toUByte()
    val payloadSize = rxBuffer[4].toUByte()
    
    // Always read PAYLOAD_SIZE number of bytes, but ensure we don't read beyond the available buffer
    val availableBytes = (rxBufferSize - 5).toUByte() // 5 bytes for header
    val actualPayloadSize = if (payloadSize > availableBytes) availableBytes else payloadSize
    
    // Extract payload bytes
    val payloadBytes = ByteArray(actualPayloadSize.toInt())
    rxBuffer.copyInto(
        payloadBytes,
        startIndex = 5,
        endIndex = minOf(5 + actualPayloadSize.toInt(), rxBufferSize)
    )
    
    // Parse payload based on message class
    val payload = if (msgCls == AscentMessageClass.TELEMETRY.code) {
        // Parse the telemetry payload (34 bytes, padding included)
        if (actualPayloadSize.toInt() >= TELEMETRY_PAYLOAD_SIZE) {
            try {
                // Helper functions to read bytes in little-endian (C-like) format
                fun ByteArray.readUInt(offset: Int): UInt {
                    return (this[offset].toUByte().toInt() or
                            (this[offset + 1].toUByte().toInt() shl 8) or
                            (this[offset + 2].toUByte().toInt() shl 16) or
                            (this[offset + 3].toUByte().toInt() shl 24)).toUInt()
                }
                
                fun ByteArray.readInt(offset: Int): Int {
                    return (this[offset].toInt() and 0xFF) or
                           ((this[offset + 1].toInt() and 0xFF) shl 8) or
                           ((this[offset + 2].toInt() and 0xFF) shl 16) or
                           ((this[offset + 3].toInt() shl 24))
                }
                
                fun ByteArray.readFloat(offset: Int): Float {
                    val bits = readUInt(offset).toInt()
                    // Use multiplatform-compatible Float conversion
                    return Float.fromBits(bits)
                }
                
                fun ByteArray.readUShort(offset: Int): UShort {
                    // Read little-endian uint16_t: low byte first, then high byte
                    // Ensure proper unsigned conversion without sign extension
                    val byte0 = this[offset].toInt() and 0xFF
                    val byte1 = this[offset + 1].toInt() and 0xFF
                    return ((byte0) or (byte1 shl 8)).toUShort()
                }
                
                val telemetry = GooberPostTelemetryPayload(
                    timestamp = payloadBytes.readUInt(0),
                    latitude = payloadBytes.readInt(4),
                    longitude = payloadBytes.readInt(8),
                    altitudeAgl = payloadBytes.readFloat(12),
                    verticalVelocity = payloadBytes.readFloat(16),
                    yAcc = payloadBytes.readFloat(20),
                    gyrY = payloadBytes.readFloat(24),
                    pyroState = payloadBytes[28].toUByte(),
                    sats = payloadBytes[29].toUByte(),
                    flightState = payloadBytes[30].toUByte(),
                    batteryVoltage = payloadBytes.readUShort(32)
                )
                GooberPayload.Telemetry(telemetry)
            } catch (e: Exception) {
                // If parsing fails, fall back to raw
                GooberPayload.Raw(payloadBytes)
            }
        } else {
            // Not enough bytes for telemetry, use raw
            GooberPayload.Raw(payloadBytes)
        }
    } else {
        // For other message types or single byte payloads, use raw
        if (actualPayloadSize.toInt() == 1) {
            GooberPayload.SingleByte(GooberPostSingleBytePayload(payloadBytes[0].toUByte()))
        } else {
            GooberPayload.Raw(payloadBytes)
        }
    }
    
    val receivedPacket = GooberPacket(
        devId = devId,
        devMode = devMode,
        seqId = seqId,
        msgCls = msgCls,
        payloadSize = payloadSize,
        payload = payload
    )
    
    if (false) {
        println("[GOOBER_PARSE] DEV_ID: 0x${devId.toString(16).padStart(2, '0').uppercase()}")
        println("[GOOBER_PARSE] DEV_MODE: 0x${devMode.toString(16).padStart(2, '0').uppercase()}")
        println("[GOOBER_PARSE] SEQ_ID: 0x${seqId.toString(16).padStart(2, '0').uppercase()}")
        println("[GOOBER_PARSE] MSG_CLS: 0x${msgCls.toString(16).padStart(2, '0').uppercase()}")
        println("[GOOBER_PARSE] PAYLOAD_SIZE: $payloadSize (actual: $actualPayloadSize)")
        print("[GOOBER_PARSE] Raw payload: ")
        payloadBytes.forEach { byte ->
            print("${byte.toUByte().toString(16).padStart(2, '0').uppercase()} ")
        }
        println("\n")
    }
    
    return receivedPacket
}
```

This removes the old constants at the top of the file, `GooberSeqCounter`, `gooberCreatePacket` and `GooberPayload.MAX_PAYLOAD_SIZE`. Nothing else uses them. `GooberMsgType` stays until Task 4, because the old buttons still use it.

- [ ] **Step 4: Rename the fields in the checklist test**

In `shared/src/commonTest/kotlin/com/example/bluefalconcomposemultiplatform/checklist/ChecklistRulesTest.kt`, in the `packet` helper, replace

```kotlin
        altitudeAgl = 0f, verticalVelocity = 0f, xAcc = 0f, gyrX = 0f,
```

with

```kotlin
        altitudeAgl = 0f, verticalVelocity = 0f, yAcc = 0f, gyrY = 0f,
```

- [ ] **Step 5: Remember boards whose reads are single bytes**

In `.../ble/presentation/EnhancedBluetoothPeripheral.kt`, replace

```kotlin
    val lastTelemetryAt: Duration? = null
)
```

with

```kotlin
    val lastTelemetryAt: Duration? = null,
    // The board's reads come back as single bytes: first-byte firmware (flight, dev), which sends its
    // battery but no telemetry over Bluetooth
    val batteryOnlyReads: Boolean = false
)
```

In `.../ble/presentation/BluetoothDeviceViewModel.kt`, make three changes.

1. In the `DeviceEvent.OnCharacteristicReadComplete` branch, right after the line `val parsedPacket = gooberParse(bytes, bytes.size, debug = true)`, insert:

```kotlin

                            // First-byte firmware (flight, dev) answers reads with a single battery byte
                            // and sends no telemetry over Bluetooth; packet firmware answers with a packet
                            when {
                                bytes.size == 1 -> setBatteryOnlyReads(event.macId, true)
                                parsedPacket != null -> setBatteryOnlyReads(event.macId, false)
                            }
```

2. In the `DeviceEvent.OnDeviceDisconnected` branch, replace

```kotlin
                            updateDevices[event.macId] = it.copy(
                                connected = false,
                                lastSeenAt = now(),
                                lastTelemetry = null,
                                lastTelemetryAt = null
                            )
```

with

```kotlin
                            updateDevices[event.macId] = it.copy(
                                connected = false,
                                lastSeenAt = now(),
                                lastTelemetry = null,
                                lastTelemetryAt = null,
                                batteryOnlyReads = false
                            )
```

3. Add this function right after `onAdvertisement` (before `private fun refreshBluetoothStatus()`):

```kotlin
    // Records whether a board's reads are single bytes. Reads arrive every few milliseconds, so the state
    // only changes when the answer does.
    private fun setBatteryOnlyReads(macId: String, batteryOnly: Boolean) {
        if (_deviceState.value.devices[macId]?.batteryOnlyReads == batteryOnly) return
        _deviceState.update { state ->
            val device = state.devices[macId] ?: return@update state
            val devices = HashMap(state.devices)
            devices[macId] = device.copy(batteryOnlyReads = batteryOnly)
            state.copy(devices = devices)
        }
    }
```

- [ ] **Step 6: Show it on the card**

In `.../ble/presentation/component/FoundDeviceCard.kt`:

1. Add this import above `import com.example.bluefalconcomposemultiplatform.GooberMsgType`:

```kotlin
import com.example.bluefalconcomposemultiplatform.AscentMessageClass
```

2. In `FoundDeviceCard`'s parameters, replace

```kotlin
    parsedPacket: GooberPacket?,
    onEvent: (UiEvent) -> Unit
) {
```

with

```kotlin
    parsedPacket: GooberPacket?,
    // The board's reads are single bytes: its firmware sends no telemetry over Bluetooth
    batteryOnlyReads: Boolean,
    onEvent: (UiEvent) -> Unit
) {
```

3. Replace `PacketPanel(parsedPacket, Modifier.padding(top = 12.dp))` with

```kotlin
            PacketPanel(parsedPacket, batteryOnlyReads, Modifier.padding(top = 12.dp))
```

4. Replace `private fun PacketPanel(packet: GooberPacket?, modifier: Modifier = Modifier) {` with

```kotlin
private fun PacketPanel(packet: GooberPacket?, batteryOnlyReads: Boolean, modifier: Modifier = Modifier) {
```

5. Replace

```kotlin
        if (packet == null) {
            Text(
                "No data received",
```

with

```kotlin
        if (packet == null) {
            Text(
                // First-byte firmware answers reads with one battery byte, which isn't a packet
                if (batteryOnlyReads) "This board's firmware doesn't send telemetry over Bluetooth" else "No data received",
```

6. Replace

```kotlin
        // Packet header on one line, e.g. "DEV 0x01 · MODE 0x02 · SEQ 0x2A · POST_TELEM · 33 B"
        Text(
            "DEV ${hex(packet.devId)} · MODE ${hex(packet.devMode)} · SEQ ${hex(packet.seqId)} · " +
                "${packet.msgCls.name.removePrefix("MSG_TYPE_")} · ${packet.payloadSize} B",
```

with

```kotlin
        // Packet header on one line, e.g. "DEV 0x82 · MODE 0x0D · SEQ 0x2A · TELEMETRY · 34 B"
        Text(
            "DEV ${hex(packet.devId)} · MODE ${hex(packet.devMode)} · SEQ ${hex(packet.seqId)} · " +
                "${messageClassName(packet.msgCls)} · ${packet.payloadSize} B",
```

7. Replace

```kotlin
        TelemetryRow("X acceleration", "${formatFloat(telemetry.xAcc)} m/s²")
        TelemetryRow("Gyro X", formatFloat(telemetry.gyrX))
```

with

```kotlin
        TelemetryRow("Y acceleration", "${formatFloat(telemetry.yAcc)} m/s²")
        TelemetryRow("Gyro Y", formatFloat(telemetry.gyrY))
```

8. Right after the line `private fun hex(value: UByte): String = "0x${value.toString(16).padStart(2, '0').uppercase()}"`, add:

```kotlin

// A packet's class by its firmware name, e.g. "TELEMETRY", or as hex if the app doesn't know it
private fun messageClassName(code: UByte): String = AscentMessageClass.fromCode(code)?.name ?: hex(code)
```

In `.../ble/presentation/component/DeviceScanView.kt`, replace

```kotlin
                parsedPacket = parsedPackets[device.peripheral.uuid],
                onEvent = onEvent
```

with

```kotlin
                parsedPacket = parsedPackets[device.peripheral.uuid],
                batteryOnlyReads = device.batteryOnlyReads,
                onEvent = onEvent
```

- [ ] **Step 7: Compile and run the tests**

Run the **iOS compile check**, then the **Unit tests** commands.
Expected: `BUILD SUCCESSFUL` with no `e:` lines. The summaries show `GooberTest` 6, `ChecklistRulesTest` 22, `ChecklistViewModelTest` 6 and `ScanRulesTest` 11 tests, with `failures="0" errors="0"` everywhere.

The phone check of this task comes in Task 2, with the fake board.

- [ ] **Step 8: Commit (the user runs this)**

```bash
git add shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/goober.kt \
  shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/EnhancedBluetoothPeripheral.kt \
  shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/BluetoothDeviceViewModel.kt \
  shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/component/FoundDeviceCard.kt \
  shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/component/DeviceScanView.kt \
  shared/src/commonTest/kotlin/com/example/bluefalconcomposemultiplatform/GooberTest.kt \
  shared/src/commonTest/kotlin/com/example/bluefalconcomposemultiplatform/checklist/ChecklistRulesTest.kt
git commit -m "decode telemetry from current firmware and say when a board sends none"
```

---

### Task 2: A fake board on the Mac

**Files:**
- Create: `tools/fake-ascent.swift`

**Interfaces:**
- Consumes: the card from Task 1.
- Produces: `build/fake-ascent`, a fake board named "ASCENT-MAC". Tasks 4 and 5 check their bytes with it.

- [ ] **Step 1: Write the script**

Create `tools/fake-ascent.swift`:

```swift
// A fake ASCENT board on this Mac, for checking the app's connected card without a real board.
//
// It advertises as "ASCENT-MAC", offers characteristic 0x0001 (the one the app reads and writes),
// prints every write in hex with what each kind of firmware would do with it, and answers reads
// like one of the two kinds of ASCENT firmware:
//   telemetry  packet firmware (irec-dev, fsm-unique-counters-dev, ...): a GOOBER packet with class 21
//              (TELEMETRY) and fixed readings
//   battery    first-byte firmware (flight, dev, ...): one byte, 25 x the battery's whole volts
//
// Build and run from the repo root:
//   mkdir -p build && swiftc -O tools/fake-ascent.swift -o build/fake-ascent
//   build/fake-ascent telemetry 900     (what reads answer with, then how many seconds to run)
//
// The app only lists boards whose name contains "ASCENT". If the iPhone shows this Mac under the Mac's
// own name instead, rename the Mac to "ASCENT-MAC" (System Settings > General > About) for the test.
import CoreBluetooth
import Foundation

setbuf(stdout, nil)

// The firmware's message classes the app can send (main/command/command.h in ascent-r3-firmware)
let commandNames: [UInt8: String] = [
    4: "REBOOT", 5: "PYRO1 (Pop Apogee)", 6: "PYRO2 (Pop Mains)", 9: "AUX_ON", 10: "AUX_OFF",
    12: "WAKE_UP", 13: "TXLOCK_ON", 22: "ERASE_FLASH",
]

func hexString(_ data: Data) -> String { data.map { String(format: "0x%02X", $0) }.joined(separator: " ") }

// What each kind of firmware would do with a write
func meaning(_ data: Data) -> String {
    let bytes = [UInt8](data)
    guard let first = bytes.first else { return "empty write" }
    // First-byte firmware runs the first byte
    let firstByte = commandNames[first] ?? "rejected (\(first))"
    // Packet firmware decodes a GOOBER packet, whose length must match its header, and runs MSG_CLS
    let packet = bytes.count >= 5 && bytes.count == 5 + Int(bytes[4])
        ? commandNames[bytes[3]] ?? "rejected (\(bytes[3]))"
        : "rejected (not a GOOBER packet)"
    return "first-byte firmware: \(firstByte) | packet firmware: \(packet)"
}

enum ReadReply: String { case telemetry, battery }

final class FakeBoard: NSObject, CBPeripheralManagerDelegate {
    private var manager: CBPeripheralManager!
    private let name = "ASCENT-MAC"
    private let reply: ReadReply
    // Any service works: the app looks for characteristic 0x0001 in every service
    private let serviceUUID = CBUUID(string: "0000FFE0-0000-1000-8000-00805F9B34FB")
    private let characteristicUUID = CBUUID(string: "00000001-0000-1000-8000-00805F9B34FB")
    private let start = Date()
    private var seq: UInt8 = 0
    private var reads = 0

    init(reply: ReadReply) {
        self.reply = reply
        super.init()
        manager = CBPeripheralManager(delegate: self, queue: nil)
    }

    func peripheralManagerDidUpdateState(_ peripheral: CBPeripheralManager) {
        print("Bluetooth state: \(peripheral.state.rawValue) (5 = on, 3 = not allowed)")
        guard peripheral.state == .poweredOn else { return }
        let characteristic = CBMutableCharacteristic(
            type: characteristicUUID, properties: [.read, .write], value: nil, permissions: [.readable, .writeable]
        )
        let service = CBMutableService(type: serviceUUID, primary: true)
        service.characteristics = [characteristic]
        peripheral.add(service)
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, didAdd service: CBService, error: Error?) {
        if let error { print("adding the service failed: \(error)"); return }
        // The name only: a service UUID in the advertisement pushes the name out
        peripheral.startAdvertising([CBAdvertisementDataLocalNameKey: name])
    }

    func peripheralManagerDidStartAdvertising(_ peripheral: CBPeripheralManager, error: Error?) {
        if let error { print("advertising failed: \(error)") }
        else { print("advertising as \(name); reads answer with \(reply.rawValue)") }
    }

    // DEV_ID 130, DEV_MODE 0x0D, SEQ_ID, MSG_CLS 21 (TELEMETRY), payload length 34, then the payload
    private func telemetryPacket() -> Data {
        seq &+= 1
        var d = Data([130, 0x0D, seq, 21, 34])
        func put<T: FixedWidthInteger>(_ v: T) { withUnsafeBytes(of: v.littleEndian) { d.append(contentsOf: $0) } }
        func putF(_ v: Float) { put(v.bitPattern) }
        put(UInt32(Date().timeIntervalSince(start) * 1000))   // timestamp, ms
        put(Int32(334_215_000))                                // latitude
        put(Int32(-1_119_340_000))                             // longitude
        putF(1.5)                                              // altitude AGL, m
        putF(0)                                                // vertical velocity, m/s
        putF(9.75)                                             // Y acceleration
        putF(0.25)                                             // Y gyro
        d.append(0b01)                                         // pyro state: continuity on channel 1 only
        d.append(9)                                            // satellites
        d.append(0)                                            // flight state
        d.append(0)                                            // padding
        put(UInt16(20_625))                                    // battery, 8.25 V
        return d
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, didReceiveRead request: CBATTRequest) {
        // Battery mode: one byte, 25 x 8 whole volts
        let value = reply == .telemetry ? telemetryPacket() : Data([200])
        guard request.offset <= value.count else {
            peripheral.respond(to: request, withResult: .invalidOffset)
            return
        }
        request.value = value.subdata(in: request.offset..<value.count)
        peripheral.respond(to: request, withResult: .success)
        reads += 1
        if reads == 1 || reads % 500 == 0 { print("served \(reads) reads") }
    }

    func peripheralManager(_ peripheral: CBPeripheralManager, didReceiveWrite requests: [CBATTRequest]) {
        let stamp = DateFormatter.localizedString(from: Date(), dateStyle: .none, timeStyle: .medium)
        for request in requests {
            let data = request.value ?? Data()
            print("[\(stamp)] WRITE \(hexString(data)) -> \(meaning(data))")
        }
        peripheral.respond(to: requests[0], withResult: .success)
    }
}

let arguments = CommandLine.arguments
let reply = arguments.count > 1 ? ReadReply(rawValue: arguments[1]) ?? .telemetry : .telemetry
let seconds = arguments.count > 2 ? Double(arguments[2]) ?? 900 : 900
let board = FakeBoard(reply: reply)
RunLoop.main.run(until: Date().addingTimeInterval(seconds))
print("stopped")
```

- [ ] **Step 2: Compile it**

Run: `mkdir -p build && swiftc -O tools/fake-ascent.swift -o build/fake-ascent`
Expected: no errors, and `build/fake-ascent` exists. `build/` is git-ignored.

- [ ] **Step 3: Start it in telemetry mode**

Run `build/fake-ascent telemetry 900` in its own terminal, or in the background with its output in `build/fake-ascent.log`.
Expected:
```
Bluetooth state: 5 (5 = on, 3 = not allowed)
advertising as ASCENT-MAC; reads answer with telemetry
```
If the state is 3, the terminal app isn't allowed to use Bluetooth. Allow it under System Settings → Privacy & Security → Bluetooth, then start the script again.

- [ ] **Step 4: Find the fake board on the phone**

Run the **Build, install and launch on the iPhone** commands. This also checks Task 1 on the phone.
1. Wait up to 15 s for a card named **ASCENT-MAC**. **Only connect to that card.** Any other ASCENT card is a real board: leave it alone.
2. If no ASCENT-MAC card appears, or it turns into the Mac's own name (e.g. "dinde") after connecting:
   - The user renames the Mac to "ASCENT-MAC" under System Settings → General → About → Name.
   - `scutil --get ComputerName` should now print `ASCENT-MAC`.
   - Turn the Mac's Bluetooth off and on, restart the script, and look again.
3. If it still doesn't show up as ASCENT-MAC, write down what the phone shows. The phone checks in Tasks 4 and 5 then wait for a real board (Task 6, Step 2), and the unit tests cover the bytes meanwhile. Go on to Step 7.

- [ ] **Step 5: Check the readings (telemetry mode)**

Connect to ASCENT-MAC. Don't tap the old command buttons; Task 4 replaces them.
Expected on the card:
- The header line reads `DEV 0x82 · MODE 0x0D · SEQ 0x… · TELEMETRY · 34 B`, and SEQ keeps changing.
- The rows read:

  | Row | Value |
  |---|---|
  | Altitude AGL | 1.50 m |
  | Vertical velocity | 0.00 m/s |
  | Y acceleration | 9.75 m/s² |
  | Gyro Y | 0.25 |
  | Pyro state | T F F F |
  | Satellites | 9 |
  | Flight state | 0x00 |
  | Battery | 8.25 V |

  The Timestamp keeps rising.

Then open the checklist. Step 9.11's block shows "● ASCENT-MAC · last packet … s ago", with:
- Battery 8.25 V (green);
- Drogue continuity: Continuity (green);
- Main continuity: No continuity (red);
- GPS: 9 satellites (green).

- [ ] **Step 6: Check a battery-only board (battery mode)**

1. Stop the script, then start `build/fake-ascent battery 900`. The card disconnects.
2. Reconnect to ASCENT-MAC.

Expected: the card's packet panel says "This board's firmware doesn't send telemetry over Bluetooth". The checklist's blocks say "ASCENT-MAC · no data yet".

Stop the script afterwards. Keep the Mac's new name until Task 5 is done; Task 6 renames it back. The console must show no `Uncaught Kotlin exception`.

- [ ] **Step 7: Commit (the user runs this)**

```bash
git add tools/fake-ascent.swift
git commit -m "add a fake ASCENT board for testing on the Mac"
```

---

### Task 3: Command rules, with unit tests

**Files:**
- Create: `.../ble/presentation/CommandRules.kt`
- Test: `shared/src/commonTest/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/CommandRulesTest.kt`

**Interfaces:**
- Consumes:
  - `AscentMessageClass` (Task 1).
  - `GooberPostTelemetryPayload` with `yAcc`/`gyrY` (Task 1).
  - From `com.example.bluefalconcomposemultiplatform.checklist`: `DROGUE_PYRO_CHANNEL` (1), `MAIN_PYRO_CHANNEL` (2), `FRESH_TELEMETRY` (3 s) and `hasContinuity(pyroState: UByte, channel: Int): Boolean`.
- Produces (package `com.example.bluefalconcomposemultiplatform.ble.presentation`):
  - `enum class Confirmation { None, Tap, Hold, ArmAndHold }`.
  - `enum class BoardCommand(val messageClass: AscentMessageClass, val label: String, val confirmation: Confirmation, val pyroChannel: Int?)` with entries TX_LOCK, REBOOT, AUX_ON, AUX_OFF, ERASE_FLASH, POP_APOGEE and POP_MAINS.
  - `fun commandPacket(command: BoardCommand): ByteArray`.
  - `val PYRO_ARM_DURATION: Duration` (2 min) and `val HOLD_TO_CONFIRM: Duration` (2 s).
  - `fun armedTimeLeft(armedAtMillis: Long?, nowMillis: Long): Duration?`.
  - `fun canSend(command: BoardCommand, armedTimeLeft: Duration?): Boolean`.
  - `fun formatTimeLeft(timeLeft: Duration): String`.
  - `enum class PopContinuity { Yes, No, NoTelemetry }`.
  - `fun popContinuity(channel: Int, telemetry: GooberPostTelemetryPayload?, telemetryAt: Duration?, now: Duration): PopContinuity`.

- [ ] **Step 1: Write the failing tests**

Create `shared/src/commonTest/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/CommandRulesTest.kt`:

```kotlin
package com.example.bluefalconcomposemultiplatform.ble.presentation

import com.example.bluefalconcomposemultiplatform.GooberPostTelemetryPayload
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class CommandRulesTest {

    @Test
    fun commandsUseTheFirmwareCodes() {
        assertEquals(13, BoardCommand.TX_LOCK.messageClass.code.toInt())
        assertEquals(4, BoardCommand.REBOOT.messageClass.code.toInt())
        assertEquals(9, BoardCommand.AUX_ON.messageClass.code.toInt())
        assertEquals(10, BoardCommand.AUX_OFF.messageClass.code.toInt())
        assertEquals(22, BoardCommand.ERASE_FLASH.messageClass.code.toInt())
        assertEquals(5, BoardCommand.POP_APOGEE.messageClass.code.toInt())
        assertEquals(6, BoardCommand.POP_MAINS.messageClass.code.toInt())
    }

    @Test
    fun popMainsPacket() {
        assertContentEquals(byteArrayOf(6, 0x21, 0x00, 6, 0x00), commandPacket(BoardCommand.POP_MAINS))
    }

    @Test
    fun everyPacketCarriesItsCodeInBytesZeroAndThree() {
        BoardCommand.entries.forEach { command ->
            val code = command.messageClass.code.toByte()
            assertContentEquals(byteArrayOf(code, 0x21, 0x00, code, 0x00), commandPacket(command), command.name)
        }
    }

    @Test
    fun confirmations() {
        assertEquals(Confirmation.ArmAndHold, BoardCommand.POP_APOGEE.confirmation)
        assertEquals(Confirmation.ArmAndHold, BoardCommand.POP_MAINS.confirmation)
        assertEquals(Confirmation.Hold, BoardCommand.ERASE_FLASH.confirmation)
        assertEquals(Confirmation.Tap, BoardCommand.TX_LOCK.confirmation)
        assertEquals(Confirmation.Tap, BoardCommand.REBOOT.confirmation)
        assertEquals(Confirmation.None, BoardCommand.AUX_ON.confirmation)
        assertEquals(Confirmation.None, BoardCommand.AUX_OFF.confirmation)
    }

    @Test
    fun popsFireChannelsOneAndTwo() {
        assertEquals(1, BoardCommand.POP_APOGEE.pyroChannel)
        assertEquals(2, BoardCommand.POP_MAINS.pyroChannel)
        assertNull(BoardCommand.ERASE_FLASH.pyroChannel)
    }

    // Any wall-clock time, in milliseconds since 1970
    private val armedAt = 1_793_570_000_000L

    @Test
    fun notArmedWithoutAnArmingTime() {
        assertNull(armedTimeLeft(armedAtMillis = null, nowMillis = armedAt))
    }

    @Test
    fun armingLastsTwoMinutes() {
        assertEquals(2.minutes, armedTimeLeft(armedAt, armedAt))
        assertEquals(1.seconds, armedTimeLeft(armedAt, armedAt + (1.minutes + 59.seconds).inWholeMilliseconds))
        assertNull(armedTimeLeft(armedAt, armedAt + 2.minutes.inWholeMilliseconds))
        assertNull(armedTimeLeft(armedAt, armedAt + 10.minutes.inWholeMilliseconds))
    }

    @Test
    fun aClockThatWentBackwardsIsNotArmed() {
        assertNull(armedTimeLeft(armedAt, armedAt - 1))
    }

    @Test
    fun onlyPopsNeedArming() {
        assertFalse(canSend(BoardCommand.POP_APOGEE, armedTimeLeft = null))
        assertFalse(canSend(BoardCommand.POP_MAINS, armedTimeLeft = null))
        assertTrue(canSend(BoardCommand.POP_MAINS, armedTimeLeft = 30.seconds))
        assertTrue(canSend(BoardCommand.ERASE_FLASH, armedTimeLeft = null))
        assertTrue(canSend(BoardCommand.REBOOT, armedTimeLeft = null))
    }

    @Test
    fun timeLeftReadsInMinutesAndSecondsRoundedUp() {
        assertEquals("2:00", formatTimeLeft(2.minutes))
        assertEquals("1:47", formatTimeLeft(1.minutes + 46.seconds + 200.milliseconds))
        assertEquals("0:01", formatTimeLeft(200.milliseconds))
    }

    private fun telemetry(pyroState: UByte) = GooberPostTelemetryPayload(
        timestamp = 0u, latitude = 0, longitude = 0,
        altitudeAgl = 0f, verticalVelocity = 0f, yAcc = 0f, gyrY = 0f,
        pyroState = pyroState, sats = 0u, flightState = 0u, batteryVoltage = 0u
    )

    @Test
    fun continuityComesFromThePyroStateBits() {
        val onlyChannelOne = telemetry(pyroState = 0b01u)
        assertEquals(PopContinuity.Yes, popContinuity(1, onlyChannelOne, telemetryAt = 10.seconds, now = 11.seconds))
        assertEquals(PopContinuity.No, popContinuity(2, onlyChannelOne, telemetryAt = 10.seconds, now = 11.seconds))
    }

    @Test
    fun continuityNeedsFreshTelemetry() {
        val both = telemetry(pyroState = 0b11u)
        assertEquals(PopContinuity.NoTelemetry, popContinuity(1, telemetry = null, telemetryAt = null, now = 11.seconds))
        assertEquals(PopContinuity.Yes, popContinuity(1, both, telemetryAt = 10.seconds, now = 13.seconds))
        assertEquals(
            PopContinuity.NoTelemetry,
            popContinuity(1, both, telemetryAt = 10.seconds, now = 13.seconds + 1.milliseconds)
        )
    }
}
```

- [ ] **Step 2: Run the tests to see them fail**

Run: `./gradlew :shared:iosSimulatorArm64Test --console=plain`
Expected: the build fails with errors like `Unresolved reference 'BoardCommand'`, `'commandPacket'` and `'armedTimeLeft'`.

- [ ] **Step 3: Write the rules**

Create `shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/CommandRules.kt`:

```kotlin
package com.example.bluefalconcomposemultiplatform.ble.presentation

import com.example.bluefalconcomposemultiplatform.AscentMessageClass
import com.example.bluefalconcomposemultiplatform.GooberPostTelemetryPayload
import com.example.bluefalconcomposemultiplatform.checklist.DROGUE_PYRO_CHANNEL
import com.example.bluefalconcomposemultiplatform.checklist.FRESH_TELEMETRY
import com.example.bluefalconcomposemultiplatform.checklist.MAIN_PYRO_CHANNEL
import com.example.bluefalconcomposemultiplatform.checklist.hasContinuity
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

// How a command is confirmed before it's sent
enum class Confirmation {
    None,       // sent on tap: AUX on and off are easy to undo
    Tap,        // a dialog with a button
    Hold,       // a dialog with a button held for HOLD_TO_CONFIRM
    ArmAndHold  // only while the board is armed, then a held button
}

// The commands on the connected card, with the firmware's codes (see the board commands spec)
enum class BoardCommand(
    val messageClass: AscentMessageClass,
    val label: String,
    val confirmation: Confirmation,
    // The pyro channel a pop fires; null for the other commands
    val pyroChannel: Int? = null
) {
    TX_LOCK(AscentMessageClass.TXLOCK_ON, "TX Lock", Confirmation.Tap),
    REBOOT(AscentMessageClass.REBOOT, "Reboot", Confirmation.Tap),
    AUX_ON(AscentMessageClass.AUX_ON, "AUX On", Confirmation.None),
    AUX_OFF(AscentMessageClass.AUX_OFF, "AUX Off", Confirmation.None),
    ERASE_FLASH(AscentMessageClass.ERASE_FLASH, "Erase Flash", Confirmation.Hold),
    POP_APOGEE(AscentMessageClass.PYRO1, "Pop Apogee", Confirmation.ArmAndHold, DROGUE_PYRO_CHANNEL),
    POP_MAINS(AscentMessageClass.PYRO2, "Pop Mains", Confirmation.ArmAndHold, MAIN_PYRO_CHANNEL)
}

// The bytes written for a command: [code, 0x21, 0x00, code, 0x00]. Both kinds of ASCENT firmware run it:
// - packet firmware (irec-dev, fsm-unique-counters-dev, ...) decodes a GOOBER packet and runs its MSG_CLS,
//   the 4th byte. DEV_ID = code is never checked, DEV_MODE 0x21 is simplex + command-only (as on the
//   website), SEQ_ID 0, no payload.
// - first-byte firmware (flight, dev, ...) runs the first byte.
fun commandPacket(command: BoardCommand): ByteArray {
    val code = command.messageClass.code.toByte()
    return byteArrayOf(code, 0x21, 0x00, code, 0x00)
}

// How long arming lasts, and how long the Fire and Erase buttons must be held
val PYRO_ARM_DURATION: Duration = 2.minutes
val HOLD_TO_CONFIRM: Duration = 2.seconds

// Time left before a board's arming runs out, on the wall clock (milliseconds since 1970), or null when
// it isn't armed. A clock that reads earlier than the arming time counts as not armed.
fun armedTimeLeft(armedAtMillis: Long?, nowMillis: Long): Duration? {
    if (armedAtMillis == null || nowMillis < armedAtMillis) return null
    val left = PYRO_ARM_DURATION - (nowMillis - armedAtMillis).milliseconds
    return left.takeIf { it > Duration.ZERO }
}

// Pops go only to an armed board; the other commands don't need arming
fun canSend(command: BoardCommand, armedTimeLeft: Duration?): Boolean =
    command.confirmation != Confirmation.ArmAndHold || armedTimeLeft != null

// "1:47". Rounds up, so it reads 2:00 right after arming and 0:01 in the last second.
fun formatTimeLeft(timeLeft: Duration): String {
    val seconds = (timeLeft.inWholeMilliseconds + 999) / 1000
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}

// What the card says under a pop about its channel
enum class PopContinuity { Yes, No, NoTelemetry }

// Continuity on a pyro channel from a board's telemetry, judged only on fresh telemetry, as the checklist does
fun popContinuity(
    channel: Int,
    telemetry: GooberPostTelemetryPayload?,
    telemetryAt: Duration?,
    now: Duration
): PopContinuity {
    if (telemetry == null || telemetryAt == null) return PopContinuity.NoTelemetry
    if (now - telemetryAt > FRESH_TELEMETRY) return PopContinuity.NoTelemetry
    return if (hasContinuity(telemetry.pyroState, channel)) PopContinuity.Yes else PopContinuity.No
}
```

- [ ] **Step 4: Run the tests**

Run the **Unit tests** commands.
Expected: `BUILD SUCCESSFUL`. The summaries show `CommandRulesTest` 12, `GooberTest` 6, `ChecklistRulesTest` 22, `ChecklistViewModelTest` 6 and `ScanRulesTest` 11 tests, all with `failures="0" errors="0"`.

- [ ] **Step 5: Commit (the user runs this)**

```bash
git add shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/CommandRules.kt \
  shared/src/commonTest/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/CommandRulesTest.kt
git commit -m "add board command rules with the firmware's codes"
```

---

### Task 4: The Commands group

**Files:**
- Create: `.../ble/presentation/component/HoldToConfirmButton.kt`
- Create: `.../ble/presentation/component/CommandPanel.kt`
- Modify: `.../ui/theme/Color.kt`
- Modify: `.../ble/presentation/UiEvent.kt`
- Modify: `.../ble/presentation/EnhancedBluetoothPeripheral.kt`
- Modify: `.../ble/presentation/BluetoothDeviceViewModel.kt`
- Modify: `.../ble/presentation/component/FoundDeviceCard.kt`
- Modify: `.../goober.kt`

**Interfaces:**
- Consumes: everything in `CommandRules.kt` (Task 3), and `currentEpochMillis(): Long` from `com.example.bluefalconcomposemultiplatform.checklist`.
- Produces:
  - `UiEvent.OnSendCommand(macId: String, command: BoardCommand)`. `UiEvent.OnSendSingleByteCommand` is gone.
  - `EnhancedBluetoothPeripheral.pyroArmedAtMillis: Long? = null`. Nothing sets it until Task 5, so every pop is refused until then.
  - `@Composable fun HoldToConfirmButton(label: String, onConfirmed: () -> Unit, modifier: Modifier = Modifier)`.
  - `@Composable fun CommandPanel(boardName: String, macId: String, onEvent: (UiEvent) -> Unit, modifier: Modifier = Modifier)`. Task 5 adds three parameters.
  - Colours `PlainButton`, `HoldTrack` and `HoldFill`. `CommandGreen` and `CommandPurple` are gone.

- [ ] **Step 1: Colours**

In `.../ui/theme/Color.kt`, replace

```kotlin
// Command button colors (unchanged from before the redesign)
val CommandRed = Color(0xFFD32F2F)
val CommandPurple = Color(0xFF8E24AA)
val CommandGreen = Color(0xFF388E3C)
```

with

```kotlin
// Board commands: plain buttons, with red kept for what can't be undone (Erase Flash uses ProblemRedText)
val PlainButton = Color(0x1CFFFFFF)       // white at 11%
val CommandRed = Color(0xFFD32F2F)        // the pops and the arm switch
val HoldTrack = Color(0xFF7A1E1E)         // a hold-to-confirm button before it fills
val HoldFill = Color(0xFFE53935)          // its fill
```

- [ ] **Step 2: The hold button**

Create `.../ble/presentation/component/HoldToConfirmButton.kt`:

```kotlin
package com.example.bluefalconcomposemultiplatform.ble.presentation.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluefalconcomposemultiplatform.ble.presentation.HOLD_TO_CONFIRM
import com.example.bluefalconcomposemultiplatform.ui.theme.HoldFill
import com.example.bluefalconcomposemultiplatform.ui.theme.HoldTrack
import kotlinx.coroutines.launch

// A button that acts only once it's been held for HOLD_TO_CONFIRM. While it's held, a brighter fill grows
// across it; letting go (or sliding off) before it's full empties it, and nothing happens.
@Composable
fun HoldToConfirmButton(
    label: String,
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier
) {
    // How far the hold has got, from 0 to 1
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val confirm by rememberUpdatedState(onConfirmed)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .defaultMinSize(minWidth = 132.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(HoldTrack)
            .drawBehind { drawRect(HoldFill, size = size.copy(width = size.width * progress.value)) }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    val hold = scope.launch {
                        progress.animateTo(
                            1f,
                            tween(HOLD_TO_CONFIRM.inWholeMilliseconds.toInt(), easing = LinearEasing)
                        )
                        confirm()
                    }
                    waitForUpOrCancellation()
                    if (hold.isActive) {
                        hold.cancel()
                        scope.launch { progress.snapTo(0f) }
                    }
                }
            }
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}
```

- [ ] **Step 3: The command panel**

Create `.../ble/presentation/component/CommandPanel.kt`:

```kotlin
package com.example.bluefalconcomposemultiplatform.ble.presentation.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluefalconcomposemultiplatform.ble.presentation.BoardCommand
import com.example.bluefalconcomposemultiplatform.ble.presentation.Confirmation
import com.example.bluefalconcomposemultiplatform.ble.presentation.UiEvent
import com.example.bluefalconcomposemultiplatform.ui.theme.PlainButton
import com.example.bluefalconcomposemultiplatform.ui.theme.ProblemRedText
import com.example.bluefalconcomposemultiplatform.ui.theme.TextPrimary
import com.example.bluefalconcomposemultiplatform.ui.theme.TextSecondary

// The connected card's commands, and the dialog that confirms the ones that ask first
@Composable
fun CommandPanel(
    boardName: String,
    macId: String,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    // The command whose dialog is open, if any
    var pending by remember { mutableStateOf<BoardCommand?>(null) }

    // AUX goes straight away; everything else opens its dialog
    fun tap(command: BoardCommand) {
        if (command.confirmation == Confirmation.None) onEvent(UiEvent.OnSendCommand(macId, command))
        else pending = command
    }

    Column(modifier.fillMaxWidth()) {
        Text(
            "COMMANDS",
            color = TextSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        listOf(
            listOf(BoardCommand.TX_LOCK, BoardCommand.REBOOT),
            listOf(BoardCommand.AUX_ON, BoardCommand.AUX_OFF)
        ).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { command ->
                    Button(
                        onClick = { tap(command) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PlainButton, contentColor = TextPrimary),
                        contentPadding = PaddingValues(vertical = 10.dp)
                    ) {
                        Text(command.label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        // Red is kept for what can't be undone
        OutlinedButton(
            onClick = { tap(BoardCommand.ERASE_FLASH) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.5.dp, ProblemRedText),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = ProblemRedText),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            Text(BoardCommand.ERASE_FLASH.label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }

    pending?.let { command ->
        CommandDialog(
            command = command,
            boardName = boardName,
            onSend = {
                onEvent(UiEvent.OnSendCommand(macId, command))
                pending = null
            },
            onDismiss = { pending = null }
        )
    }
}

@Composable
private fun CommandDialog(command: BoardCommand, boardName: String, onSend: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(dialogTitle(command, boardName), fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = { Text(dialogText(command), fontSize = 14.sp) },
        confirmButton = {
            if (command.confirmation == Confirmation.Tap) {
                TextButton(onClick = onSend) { Text(command.label) }
            } else {
                HoldToConfirmButton(
                    label = if (command == BoardCommand.ERASE_FLASH) "Hold to erase" else "Hold to fire",
                    onConfirmed = onSend
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

// Dialog wording, as in the board commands spec. AUX On and AUX Off have no dialog.
private fun dialogTitle(command: BoardCommand, board: String): String = when (command) {
    BoardCommand.POP_APOGEE -> "Fire APOGEE on $board?"
    BoardCommand.POP_MAINS -> "Fire MAINS on $board?"
    BoardCommand.ERASE_FLASH -> "Erase all flash on $board?"
    BoardCommand.TX_LOCK -> "TX Lock $board?"
    BoardCommand.REBOOT -> "Reboot $board?"
    BoardCommand.AUX_ON, BoardCommand.AUX_OFF -> "${command.label} $board?"
}

private fun dialogText(command: BoardCommand): String = when (command) {
    BoardCommand.POP_APOGEE, BoardCommand.POP_MAINS ->
        "Fires pyro channel ${command.pyroChannel} for 250 ms. The board doesn't check continuity first."
    BoardCommand.ERASE_FLASH ->
        "Recorded data can't be recovered. It takes 5–10 minutes: the LED turns blue, then red with a jingle " +
            "when done. Don't power the board off. The app will probably lose the connection meanwhile."
    BoardCommand.TX_LOCK ->
        "It prepares the flash for the flight log, then turns Bluetooth off: the LED turns purple, then green. " +
            "The app loses the board until it restarts."
    BoardCommand.REBOOT -> "It restarts, and the app loses the connection. Reconnect when it shows up again."
    BoardCommand.AUX_ON, BoardCommand.AUX_OFF -> ""
}
```

- [ ] **Step 4: The send event and the arming field**

Replace `.../ble/presentation/UiEvent.kt` with:

```kotlin
package com.example.bluefalconcomposemultiplatform.ble.presentation

import dev.bluefalcon.BluetoothCharacteristic

sealed interface UiEvent {
    data class OnConnectClick(val macId: String): UiEvent
    data class OnDisconnectClick(val macId: String): UiEvent

    data class OnReadCharacteristic(val macId: String, val characteristic: BluetoothCharacteristic): UiEvent
    data class OnWriteCharacteristic(val macId: String, val characteristic: BluetoothCharacteristic, val value: String): UiEvent
    data class OnSendCommand(val macId: String, val command: BoardCommand): UiEvent
}
```

In `.../ble/presentation/EnhancedBluetoothPeripheral.kt`, replace

```kotlin
    val batteryOnlyReads: Boolean = false
)
```

with

```kotlin
    val batteryOnlyReads: Boolean = false,
    // When the board's pops were armed, on the wall clock (milliseconds since 1970); null when not armed
    val pyroArmedAtMillis: Long? = null
)
```

- [ ] **Step 5: Send commands from the view model**

In `.../ble/presentation/BluetoothDeviceViewModel.kt`:

1. Add this import after `import com.example.bluefalconcomposemultiplatform.ble.data.PlatformBluetooth`:

```kotlin
import com.example.bluefalconcomposemultiplatform.checklist.currentEpochMillis
```

2. Replace the whole `sendSingleByteCommand` function with the code below. That's from its doc comment `/**` (starting `* Sends a single byte command to the BLE characteristic with UUID 00000001.`) to its closing brace, just before `fun onEvent`.

```kotlin
    // The characteristic the app reads and writes (UUID 00000001), once services are discovered
    private fun findCommandCharacteristic(peripheral: BluetoothPeripheral): BluetoothCharacteristic? =
        peripheral.services.values.flatMap { it.characteristics }.firstOrNull { characteristic ->
            val normalized = normalizeUuid(characteristic.uuid.toString())
            listOf("00000001-0000-1000-8000-00805f9b34fb", "0001", "00000001").any { normalized == normalizeUuid(it) }
        }

    /**
     * Sends a board command as [code, 0x21, 0x00, code, 0x00] (see commandPacket). A pop is sent only while
     * that board is armed, checked here on the wall clock whatever the screen shows, and sending it ends the
     * arming.
     */
    private fun sendCommand(macId: String, command: BoardCommand) {
        val device = _deviceState.value.devices[macId] ?: return
        if (!device.connected) return
        if (!canSend(command, armedTimeLeft(device.pyroArmedAtMillis, currentEpochMillis()))) {
            println("Not sending ${command.name}: ${device.peripheral.name} isn't armed")
            return
        }
        val characteristic = findCommandCharacteristic(device.peripheral) ?: return

        // A pop ends the arming, so each pop needs a fresh arm
        if (command.confirmation == Confirmation.ArmAndHold) {
            _deviceState.update { state ->
                val current = state.devices[macId] ?: return@update state
                val devices = HashMap(state.devices)
                devices[macId] = current.copy(pyroArmedAtMillis = null)
                state.copy(devices = devices)
            }
        }

        val packet = commandPacket(command)
        println("Sending ${command.name} to ${device.peripheral.name}: ${packet.joinToString(" ") { it.toHexString() }}")

        // Stop the read loop so the write isn't stuck behind reads, write, then restart reads when the
        // write is acknowledged (OnCharacteristicWriteComplete) or after 2 s
        readingJobs[macId]?.cancel()
        readingJobs.remove(macId)
        CoroutineScope(Dispatchers.IO).launch {
            delay(200)
            // Raw bytes: the String and ByteArray versions of writeCharacteristic re-encode them as text
            blueFalcon.writeCharacteristicWithoutEncoding(device.peripheral, characteristic, packet, null)
            delay(2000)
            val current = _deviceState.value.devices[macId]
            if (current?.connected == true && readingJobs[macId] == null) {
                startContinuousReading(macId, current.peripheral)
            }
        }
    }
```

3. In `onEvent`, replace

```kotlin
            is UiEvent.OnSendSingleByteCommand -> {
                sendSingleByteCommand(event.macId, event.msgType)
            }
```

with

```kotlin
            is UiEvent.OnSendCommand -> sendCommand(event.macId, event.command)
```

- [ ] **Step 6: Use the panel on the card**

In `.../ble/presentation/component/FoundDeviceCard.kt`:

1. Delete these imports. Nothing else in the file uses them once the old command code is gone:

```kotlin
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.bluefalconcomposemultiplatform.GooberMsgType
import com.example.bluefalconcomposemultiplatform.ui.theme.CommandGreen
import com.example.bluefalconcomposemultiplatform.ui.theme.CommandPurple
import com.example.bluefalconcomposemultiplatform.ui.theme.CommandRed
```

2. Replace `CommandPanel(macId, onEvent, Modifier.padding(top = 10.dp))` with

```kotlin
                CommandPanel(
                    boardName = deviceName ?: "No Name",
                    macId = macId,
                    onEvent = onEvent,
                    modifier = Modifier.padding(top = 10.dp)
                )
```

3. Delete everything from the line `// What each command button's color means; shown in the key under the buttons` to the end of the file: `CommandKind`, the old `BoardCommand`, the old `CommandPanel`, `CommandLegend` and `ConfirmationDialog`. The file now ends with `messageClassName`.

In `.../goober.kt`, delete the `GooberMsgType` enum and the two comment lines above it (`// Codes from an older protocol, …`). Nothing uses it any more.

- [ ] **Step 7: Compile, run the tests, and check nothing old is left**

Run the **iOS compile check**, then the **Unit tests** commands.
Expected: `BUILD SUCCESSFUL` with no `e:` lines. The test counts are the same as after Task 3 (12 + 6 + 22 + 6 + 11), all passing.

Then run:
```bash
grep -rn "GooberMsgType\|OnSendSingleByteCommand\|sendSingleByteCommand\|CommandGreen\|CommandPurple" shared/src
```
Expected: no output.

- [ ] **Step 8: Check on the phone with the fake board**

Skip this step if Task 2 couldn't get ASCENT-MAC onto the phone. Task 6, Step 2 covers it on a real board.

1. Start `build/fake-ascent telemetry 900`.
2. Run the **Build, install and launch on the iPhone** commands.
3. **Check the card says ASCENT-MAC**, then connect.

Expected:
1. Under the readings, "COMMANDS" heads two rows of plain buttons: TX Lock and Reboot, then AUX On and AUX Off. Erase Flash goes across the full width with a red outline. There's no colour key.
2. **AUX On:** no dialog. The Mac prints `WRITE 0x09 0x21 0x00 0x09 0x00 -> first-byte firmware: AUX_ON | packet firmware: AUX_ON`.
3. **AUX Off:** `WRITE 0x0A 0x21 0x00 0x0A 0x00 -> … AUX_OFF | … AUX_OFF`.
4. **Reboot:**
   - The dialog's title is "Reboot ASCENT-MAC?", and its text is "It restarts, and the app loses the connection. Reconnect when it shows up again."
   - **Cancel** prints nothing. **Reboot** prints `WRITE 0x04 0x21 0x00 0x04 0x00 -> … REBOOT | … REBOOT`.
5. **TX Lock:**
   - The dialog's title is "TX Lock ASCENT-MAC?", and its text is the TX Lock text from the spec.
   - **TX Lock** prints `WRITE 0x0D 0x21 0x00 0x0D 0x00 -> … TXLOCK_ON | … TXLOCK_ON`.
6. **Erase Flash:**
   - The dialog's title is "Erase all flash on ASCENT-MAC?", and its text is the Erase text from the spec.
   - Hold **Hold to erase** for about 1 s and let go: the fill empties and the Mac prints nothing.
   - Hold it for 2 s: the Mac prints `WRITE 0x16 0x21 0x00 0x16 0x00 -> … ERASE_FLASH | … ERASE_FLASH`, and the dialog closes.
7. The readings keep updating after each command.
8. The console shows no `Uncaught Kotlin exception`.

Stop the script afterwards.

- [ ] **Step 9: Commit (the user runs this)**

```bash
git add shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ui/theme/Color.kt \
  shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/component/HoldToConfirmButton.kt \
  shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/component/CommandPanel.kt \
  shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/UiEvent.kt \
  shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/EnhancedBluetoothPeripheral.kt \
  shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/BluetoothDeviceViewModel.kt \
  shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/component/FoundDeviceCard.kt \
  shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/goober.kt
git commit -m "send the firmware's command codes, with a held confirm for Erase Flash"
```

---

### Task 5: Ground test: arming and the pops

**Files:**
- Create: `.../ble/presentation/component/GroundTestPanel.kt`
- Modify: `.../ui/theme/Color.kt`
- Modify: `.../ble/presentation/BluetoothDeviceState.kt`
- Modify: `.../ble/presentation/UiEvent.kt`
- Modify: `.../ble/presentation/BluetoothDeviceViewModel.kt`
- Modify: `.../ble/presentation/component/CommandPanel.kt`
- Modify: `.../ble/presentation/component/FoundDeviceCard.kt`
- Modify: `.../ble/presentation/component/DeviceScanView.kt`

**Interfaces:**
- Consumes:
  - `armedTimeLeft`, `formatTimeLeft`, `PopContinuity`, `popContinuity` and `BoardCommand` (Task 3).
  - `CommandPanel` and `EnhancedBluetoothPeripheral.pyroArmedAtMillis` (Task 4).
  - `DROGUE_PYRO_CHANNEL` and `MAIN_PYRO_CHANNEL` (checklist).
- Produces:
  - `BluetoothDeviceState.nowMillis: Long`.
  - `UiEvent.OnArmPyro(macId: String)` and `UiEvent.OnDisarmPyro(macId: String)`.
  - `@Composable fun GroundTestPanel(armedTimeLeft: Duration?, apogeeContinuity: PopContinuity, mainsContinuity: PopContinuity, onArm: () -> Unit, onDisarm: () -> Unit, onPop: (BoardCommand) -> Unit, modifier: Modifier = Modifier)`.
  - `CommandPanel(boardName, macId, armedTimeLeft: Duration?, apogeeContinuity: PopContinuity, mainsContinuity: PopContinuity, onEvent, modifier)`.
  - `FoundDeviceCard(..., batteryOnlyReads, armedTimeLeft: Duration?, apogeeContinuity: PopContinuity, mainsContinuity: PopContinuity, onEvent)`.

- [ ] **Step 1: Colours**

In `.../ui/theme/Color.kt`, right after the line `val HoldFill = Color(0xFFE53935)          // its fill`, add:

```kotlin
val GroundTestBackground = Color(0x1AD32F2F)  // CommandRed at 10%
val GroundTestBorder = Color(0x59FF8A80)      // ProblemRedText at 35%
```

- [ ] **Step 2: The ground-test box**

Create `.../ble/presentation/component/GroundTestPanel.kt`:

```kotlin
package com.example.bluefalconcomposemultiplatform.ble.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluefalconcomposemultiplatform.ble.presentation.BoardCommand
import com.example.bluefalconcomposemultiplatform.ble.presentation.PopContinuity
import com.example.bluefalconcomposemultiplatform.ble.presentation.formatTimeLeft
import com.example.bluefalconcomposemultiplatform.ui.theme.CheckGreenText
import com.example.bluefalconcomposemultiplatform.ui.theme.CommandRed
import com.example.bluefalconcomposemultiplatform.ui.theme.GroundTestBackground
import com.example.bluefalconcomposemultiplatform.ui.theme.GroundTestBorder
import com.example.bluefalconcomposemultiplatform.ui.theme.PlainButton
import com.example.bluefalconcomposemultiplatform.ui.theme.ProblemRedText
import com.example.bluefalconcomposemultiplatform.ui.theme.SignalTrack
import com.example.bluefalconcomposemultiplatform.ui.theme.TextPrimary
import com.example.bluefalconcomposemultiplatform.ui.theme.TextSecondary
import kotlin.time.Duration

// The ground-test box: the arm switch with its countdown, the two pops, and each pop's continuity.
// The pops can't be tapped until the board is armed.
@Composable
fun GroundTestPanel(
    armedTimeLeft: Duration?,
    apogeeContinuity: PopContinuity,
    mainsContinuity: PopContinuity,
    onArm: () -> Unit,
    onDisarm: () -> Unit,
    onPop: (BoardCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    val armed = armedTimeLeft != null
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(GroundTestBackground)
            .border(1.dp, GroundTestBorder, shape)
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "GROUND TEST",
                color = ProblemRedText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                if (armedTimeLeft != null) "Armed · ${formatTimeLeft(armedTimeLeft)} left" else "Not armed",
                color = if (armed) ProblemRedText else TextSecondary,
                fontSize = 12.sp,
                fontWeight = if (armed) FontWeight.Bold else FontWeight.Normal
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
            Switch(
                checked = armed,
                onCheckedChange = { if (it) onArm() else onDisarm() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = CommandRed,
                    uncheckedThumbColor = TextSecondary,
                    uncheckedTrackColor = SignalTrack,
                    uncheckedBorderColor = Color.Transparent
                )
            )
            Spacer(Modifier.width(10.dp))
            Text("Arm pyro", color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PopColumn(BoardCommand.POP_APOGEE, apogeeContinuity, armed, onPop, Modifier.weight(1f))
            PopColumn(BoardCommand.POP_MAINS, mainsContinuity, armed, onPop, Modifier.weight(1f))
        }
    }
}

@Composable
private fun PopColumn(
    command: BoardCommand,
    continuity: PopContinuity,
    armed: Boolean,
    onPop: (BoardCommand) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = { onPop(command) },
            enabled = armed,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = CommandRed,
                contentColor = Color.White,
                disabledContainerColor = PlainButton,
                disabledContentColor = TextSecondary.copy(alpha = 0.5f)
            ),
            contentPadding = PaddingValues(vertical = 10.dp)
        ) {
            Text(command.label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        ContinuityLine(continuity, Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun ContinuityLine(continuity: PopContinuity, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        when (continuity) {
            PopContinuity.Yes -> {
                Icon(Icons.Filled.Check, contentDescription = null, tint = CheckGreenText, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(3.dp))
                Text("Continuity", color = CheckGreenText, fontSize = 12.sp)
            }
            PopContinuity.No -> {
                Icon(Icons.Filled.Close, contentDescription = null, tint = ProblemRedText, modifier = Modifier.size(13.dp))
                Spacer(Modifier.width(3.dp))
                Text("No continuity", color = ProblemRedText, fontSize = 12.sp)
            }
            PopContinuity.NoTelemetry -> Text("No telemetry", color = TextSecondary, fontSize = 12.sp)
        }
    }
}
```

- [ ] **Step 3: The wall clock in the state, and the arm events**

In `.../ble/presentation/BluetoothDeviceState.kt`, replace

```kotlin
    val now: Duration = Duration.ZERO
)
```

with

```kotlin
    val now: Duration = Duration.ZERO,
    // The wall clock (milliseconds since 1970) at the last refresh, for the pyro arming countdown
    val nowMillis: Long = 0L
)
```

In `.../ble/presentation/UiEvent.kt`, add these two lines right after the `OnSendCommand` line:

```kotlin
    data class OnArmPyro(val macId: String): UiEvent
    data class OnDisarmPyro(val macId: String): UiEvent
```

- [ ] **Step 4: Arming in the view model**

In `.../ble/presentation/BluetoothDeviceViewModel.kt`:

1. In `refreshBluetoothStatus()`, replace

```kotlin
        _deviceState.update { it.copy(bluetoothStatus = status, now = now()) }
```

with

```kotlin
        _deviceState.update { it.copy(bluetoothStatus = status, now = now(), nowMillis = currentEpochMillis()) }
```

2. In the `DeviceEvent.OnDeviceDisconnected` branch, replace

```kotlin
                                lastTelemetryAt = null,
                                batteryOnlyReads = false
                            )
```

with

```kotlin
                                lastTelemetryAt = null,
                                batteryOnlyReads = false,
                                // A disconnect disarms the board
                                pyroArmedAtMillis = null
                            )
```

3. Add this function right after `setBatteryOnlyReads`:

```kotlin
    // Arms a connected board's pops from now (wall clock), or disarms them with null. Arming also moves
    // nowMillis to the arming time, so the countdown shows 2:00 straight away instead of waiting for the
    // next refresh (an older nowMillis would read as a clock that went backwards).
    private fun setPyroArmedAt(macId: String, armedAtMillis: Long?) {
        _deviceState.update { state ->
            val device = state.devices[macId] ?: return@update state
            if (armedAtMillis != null && !device.connected) return@update state
            val devices = HashMap(state.devices)
            devices[macId] = device.copy(pyroArmedAtMillis = armedAtMillis)
            state.copy(devices = devices, nowMillis = armedAtMillis ?: state.nowMillis)
        }
    }
```

4. In `onEvent`, right after the `is UiEvent.OnSendCommand -> sendCommand(event.macId, event.command)` line, add:

```kotlin
            is UiEvent.OnArmPyro -> setPyroArmedAt(event.macId, currentEpochMillis())
            is UiEvent.OnDisarmPyro -> setPyroArmedAt(event.macId, null)
```

- [ ] **Step 5: Put the box in the command panel**

In `.../ble/presentation/component/CommandPanel.kt`:

1. Add these imports, keeping the import list in alphabetical order:

```kotlin
import androidx.compose.runtime.LaunchedEffect
import com.example.bluefalconcomposemultiplatform.ble.presentation.PopContinuity
import kotlin.time.Duration
```

2. Replace

```kotlin
fun CommandPanel(
    boardName: String,
    macId: String,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
```

with

```kotlin
fun CommandPanel(
    boardName: String,
    macId: String,
    // Time left before the board's arming runs out, or null when it isn't armed
    armedTimeLeft: Duration?,
    apogeeContinuity: PopContinuity,
    mainsContinuity: PopContinuity,
    onEvent: (UiEvent) -> Unit,
    modifier: Modifier = Modifier
) {
```

3. Right after the Erase Flash `OutlinedButton { … }` block, still inside the `Column`, add:

```kotlin
        GroundTestPanel(
            armedTimeLeft = armedTimeLeft,
            apogeeContinuity = apogeeContinuity,
            mainsContinuity = mainsContinuity,
            onArm = { onEvent(UiEvent.OnArmPyro(macId)) },
            onDisarm = { onEvent(UiEvent.OnDisarmPyro(macId)) },
            onPop = { pending = it },
            modifier = Modifier.padding(top = 10.dp)
        )
```

4. Replace the line `pending?.let { command ->` with

```kotlin
    // A pop's dialog closes when the arming ends: switched off, timed out, or after a disconnect
    val armed = armedTimeLeft != null
    LaunchedEffect(armed) {
        if (!armed && pending?.confirmation == Confirmation.ArmAndHold) pending = null
    }
    pending?.takeUnless { it.confirmation == Confirmation.ArmAndHold && !armed }?.let { command ->
```

- [ ] **Step 6: Pass the state down from the scan screen**

In `.../ble/presentation/component/FoundDeviceCard.kt`:

1. Add the import

```kotlin
import com.example.bluefalconcomposemultiplatform.ble.presentation.PopContinuity
```

2. In `FoundDeviceCard`'s parameters, replace

```kotlin
    batteryOnlyReads: Boolean,
    onEvent: (UiEvent) -> Unit
) {
```

with

```kotlin
    batteryOnlyReads: Boolean,
    // Time left before the board's arming runs out, or null when it isn't armed
    armedTimeLeft: Duration?,
    apogeeContinuity: PopContinuity,
    mainsContinuity: PopContinuity,
    onEvent: (UiEvent) -> Unit
) {
```

3. Replace the `CommandPanel(...)` call with

```kotlin
                CommandPanel(
                    boardName = deviceName ?: "No Name",
                    macId = macId,
                    armedTimeLeft = armedTimeLeft,
                    apogeeContinuity = apogeeContinuity,
                    mainsContinuity = mainsContinuity,
                    onEvent = onEvent,
                    modifier = Modifier.padding(top = 10.dp)
                )
```

In `.../ble/presentation/component/DeviceScanView.kt`:

1. Add these imports, keeping alphabetical order:

```kotlin
import com.example.bluefalconcomposemultiplatform.ble.presentation.armedTimeLeft
import com.example.bluefalconcomposemultiplatform.ble.presentation.popContinuity
import com.example.bluefalconcomposemultiplatform.checklist.DROGUE_PYRO_CHANNEL
import com.example.bluefalconcomposemultiplatform.checklist.MAIN_PYRO_CHANNEL
```

2. Replace

```kotlin
                batteryOnlyReads = device.batteryOnlyReads,
                onEvent = onEvent
```

with

```kotlin
                batteryOnlyReads = device.batteryOnlyReads,
                armedTimeLeft = armedTimeLeft(device.pyroArmedAtMillis, state.nowMillis),
                apogeeContinuity = popContinuity(DROGUE_PYRO_CHANNEL, device.lastTelemetry, device.lastTelemetryAt, state.now),
                mainsContinuity = popContinuity(MAIN_PYRO_CHANNEL, device.lastTelemetry, device.lastTelemetryAt, state.now),
                onEvent = onEvent
```

- [ ] **Step 7: Compile and run the tests**

Run the **iOS compile check**, then the **Unit tests** commands.
Expected: `BUILD SUCCESSFUL` with no `e:` lines. The test counts are the same as after Task 3 (12 + 6 + 22 + 6 + 11), all passing.

- [ ] **Step 8: Check on the phone with the fake board**

Skip this step if Task 2 couldn't get ASCENT-MAC onto the phone. Task 6, Step 2 covers it on a real board, but only in a team ground test.

1. Start `build/fake-ascent telemetry 900`.
2. Run the **Build, install and launch on the iPhone** commands.
3. **Check the card says ASCENT-MAC**, then connect. Watch the Mac's output throughout.

Expected:
1. **The box before arming:** below Erase Flash there's a red-tinted box headed "GROUND TEST", with "Not armed" on the right. The "Arm pyro" switch is off, and both pops are greyed out. Under Pop Apogee it says ✓ "Continuity" in green; under Pop Mains, ✗ "No continuity" in red.
2. **Greyed pops:** tapping a greyed pop does nothing.
3. **Arming:** turning the switch on shows "Armed · 2:00 left" counting down each second, and the pops turn red.
4. **Pop Apogee:**
   - The dialog's title is "Fire APOGEE on ASCENT-MAC?", and its text is "Fires pyro channel 1 for 250 ms. The board doesn't check continuity first."
   - Hold **Hold to fire** for about 1 s and let go: the fill empties and the Mac prints nothing.
   - Hold it for 2 s: the Mac prints `WRITE 0x05 0x21 0x00 0x05 0x00 -> first-byte firmware: PYRO1 (Pop Apogee) | packet firmware: PYRO1 (Pop Apogee)`.
   - The dialog closes, and the box shows "Not armed" again.
5. **Pop Mains:** arm again. The dialog title is "Fire MAINS on ASCENT-MAC?", with "channel 2". Holding prints `WRITE 0x06 0x21 0x00 0x06 0x00 -> … PYRO2 (Pop Mains) | … PYRO2 (Pop Mains)`, and the board is disarmed again.
6. **Switching off:** arm, then turn the switch off. It shows "Not armed", and the pops grey out.
7. **The 2 minutes running out:**
   - Arm, and wait until the countdown is under 0:10.
   - Open the Pop Apogee dialog and don't touch the hold button.
   - When the time runs out, the dialog closes by itself, the box shows "Not armed", and the Mac prints nothing.
8. **Disconnecting:** arm, tap Disconnect, then reconnect. The box shows "Not armed".
9. **Locking the phone:** arm, lock the phone for 2½ minutes, then unlock. The box shows "Not armed", or the board has disconnected.
10. **Battery mode:**
    - Stop the script, start `build/fake-ascent battery 900`, and reconnect.
    - Both pops say "No telemetry", and the packet panel says "This board's firmware doesn't send telemetry over Bluetooth".
11. **Console:** no `Uncaught Kotlin exception`.

Stop the script afterwards.

- [ ] **Step 9: Commit (the user runs this)**

```bash
git add shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ui/theme/Color.kt \
  shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/component/GroundTestPanel.kt \
  shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/BluetoothDeviceState.kt \
  shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/UiEvent.kt \
  shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/BluetoothDeviceViewModel.kt \
  shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/component/CommandPanel.kt \
  shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/component/FoundDeviceCard.kt \
  shared/src/commonMain/kotlin/com/example/bluefalconcomposemultiplatform/ble/presentation/component/DeviceScanView.kt
git commit -m "add the ground test: arm switch, pops and continuity"
```

---

### Task 6: Full check and the spec status

**Files:**
- Modify: `docs/superpowers/specs/2026-11-01-board-commands-design.md` (the status line)

- [ ] **Step 1: Tests and a fresh build**

Run the **Unit tests** commands, then the **Build, install and launch on the iPhone** commands.
Expected:
- All tests pass: 12 + 6 + 22 + 6 + 11 = 57.
- The app opens on the scan screen, and the checklist row still works.
- No `Uncaught Kotlin exception`.

- [ ] **Step 2: With a real board (when one is available)**

The spec's order. **First check the card shows the board you expect**, and stop if it doesn't.
1. **Telemetry:**
   - On packet firmware, the readings show and the header says TELEMETRY.
   - On first-byte firmware, the card says "This board's firmware doesn't send telemetry over Bluetooth".
2. **AUX On and AUX Off:** only `irec-dev`, `fsm-unique-counters-dev` and `event-logging-dev` do anything with them.
3. **Reboot:** the board restarts, and the card disconnects.
4. **TX Lock:** the LED turns purple, then green, and the card disconnects.
5. **Erase Flash:** only on a board whose recordings don't matter. It takes 5–10 minutes: the LED turns blue, then red with a jingle.
6. **The pops:** only as part of a team ground test with a test load, never with live charges on the bench.

If no board is available, leave this for later and say so in the pull request.

- [ ] **Step 3: Android (needs a teammate with the Android SDK and a phone)**

```bash
./gradlew :androidBlueFalconExampleMP:assembleDebug --console=plain
```
Expected: `BUILD SUCCESSFUL`. Then the teammate repeats Task 4, Step 8 and Task 5, Step 8 with the fake board, or Step 2 above with a real board.

- [ ] **Step 4: Rename the Mac back**

If Task 2 renamed the Mac, the user sets its name back under System Settings → General → About → Name.

- [ ] **Step 5: Update the spec status**

In `docs/superpowers/specs/2026-11-01-board-commands-design.md`, replace the line

```markdown
- **Status:** Design approved; not implemented yet
```

with the variant that matches what was checked:

- Checked with the fake board, but not yet on a real board or Android:
  ```markdown
  - **Status:** Implemented on branch `board-commands`; checked on iPhone with the Mac as a board; a real board and Android not yet checked
  ```
- The fake board didn't work, and there's no real board yet:
  ```markdown
  - **Status:** Implemented on branch `board-commands`; unit-tested; not yet checked with a board (the Mac didn't show up as one) or on Android
  ```
- Checked on a real board (Step 2 done):
  ```markdown
  - **Status:** Implemented on branch `board-commands`; checked on iPhone with a real board; Android not yet checked
  ```

- [ ] **Step 6: Commit (the user runs this)**

```bash
git add docs/superpowers/specs/2026-11-01-board-commands-design.md
git commit -m "mark board commands design as implemented"
```
