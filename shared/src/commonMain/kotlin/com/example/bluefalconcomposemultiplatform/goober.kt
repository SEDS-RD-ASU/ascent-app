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
