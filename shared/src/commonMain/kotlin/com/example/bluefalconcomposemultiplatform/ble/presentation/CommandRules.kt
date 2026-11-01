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
