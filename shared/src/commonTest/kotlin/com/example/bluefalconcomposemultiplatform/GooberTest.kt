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
