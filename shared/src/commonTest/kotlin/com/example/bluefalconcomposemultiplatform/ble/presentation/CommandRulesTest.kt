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
