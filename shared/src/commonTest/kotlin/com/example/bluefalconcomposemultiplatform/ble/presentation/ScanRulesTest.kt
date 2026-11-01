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
