package com.example.bluefalconcomposemultiplatform

// Request type constants
const val REQ_PINGPONG: UByte = 0xFFu        // Expect POST_PINGPONG
const val REQ_TELEM: UByte = 0x66u          // Expect POST_TELEM
const val REQ_AUX_ACTIVATE: UByte = 0x6Eu  // Expect POST_TELEM
const val REQ_AUX_DEACTIVATE: UByte = 0x6Fu // Expect POST_TELEM
const val REQ_TXLOCK_ACTIVATE: UByte = 0x78u // Expect POST_TXLOCK_ACTIVATE
const val REQ_REBOOT: UByte = 0x82u         // Expect slave reboot (and a beep)

// Post type constants
const val POST_PINGPONG: UByte = 0x01u
const val POST_TXLOCK_ACTIVATE: UByte = 0x79u

// GOOBER Message Types
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

// Telemetry payload structure
data class GooberPostTelemetryPayload(
    val timestamp: UInt,        // 4 bytes
    val latitude: Int,          // 4 bytes
    val longitude: Int,         // 4 bytes
    val altitudeAgl: Float,    // 4 bytes
    val verticalVelocity: Float, // 4 bytes
    val xAcc: Float,            // 4 bytes
    val gyrX: Float,            // 4 bytes
    val pyroState: UByte,       // 1 byte
    val sats: UByte,            // 1 byte
    val flightState: UByte,     // 1 byte
    val batteryVoltage: UShort  // 2 bytes
)

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
    
    companion object {
        // Size of telemetry payload (largest union member)
        // 7 * 4 bytes (UInt/Int/Float) + 3 * 1 byte (UByte) + 1 * 2 bytes (UShort) = 33 bytes
        // Note: Actual size may include padding depending on alignment
        const val MAX_PAYLOAD_SIZE = 33 // sizeof(goober_post_telemetry_payload_t)
    }
}

// GOOBER Message Structure
data class GooberPacket(
    var devId: UByte,           // 1 byte (DEV_ID)
    var devMode: UByte,         // 1 byte (DEV_MODE)
    var seqId: UByte,           // 1 byte (SEQ_ID)
    var msgCls: GooberMsgType,  // 1 byte (MSG_CLS)
    var payloadSize: UByte,     // 1 byte (PAYLOAD_SIZE)
    var payload: GooberPayload  // N bytes
)

object GooberSeqCounter {
    var seqCounter: UByte = 0x01u

    fun next(): UByte {
        val ret = seqCounter
        seqCounter = (seqCounter + 1u).toUByte()
        if (seqCounter == 0x00u.toUByte() || seqCounter == 0xFFu.toUByte()) {
            seqCounter = 0x01u
        }
        return ret
    }
}

fun gooberCreatePacket(
    devId: UByte,
    isMaster: Boolean,
    txIntent: Boolean,
    txLock: Boolean,
    messageClass: GooberMsgType,
    payloadSize: UByte,
    payload: GooberPayload
): GooberPacket {
    var devMode: UByte = 0u
    if (isMaster) {
        devMode = (devMode.toInt() or (1 shl 1)).toUByte()
    }
    if (txIntent) {
        devMode = (devMode.toInt() or (1 shl 2)).toUByte()
    }
    if (txLock) {
        devMode = (devMode.toInt() or (1 shl 3)).toUByte()
    }

    val seqId = GooberSeqCounter.next()

    return GooberPacket(
        devId = devId,
        devMode = devMode,
        seqId = seqId,
        msgCls = messageClass,
        payloadSize = payloadSize,
        payload = payload
    )
}

fun gooberParse(rxBuffer: ByteArray, rxBufferSize: Int, debug: Boolean = false): GooberPacket? {
    // Need at least 5 bytes for header
    if (rxBufferSize < 5 || rxBuffer.size < rxBufferSize) {
        return null
    }
    
    val devId = rxBuffer[0].toUByte()
    val devMode = rxBuffer[1].toUByte()
    val seqId = rxBuffer[2].toUByte()
    val msgClsByte = rxBuffer[3].toUByte()
    val payloadSize = rxBuffer[4].toUByte()
    
    // Get message class enum value, or use a default if not found
    val msgCls = GooberMsgType.fromByte(msgClsByte) 
        ?: GooberMsgType.MSG_TYPE_REQ_PINGPONG // Default fallback
    
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
    val payload = when (msgCls) {
        GooberMsgType.MSG_TYPE_POST_TELEM -> {
            // Parse telemetry payload (33 bytes total)
            if (actualPayloadSize.toInt() >= 33) {
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
                        xAcc = payloadBytes.readFloat(20),
                        gyrX = payloadBytes.readFloat(24),
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
        }
        else -> {
            // For other message types or single byte payloads, use raw
            if (actualPayloadSize.toInt() == 1) {
                GooberPayload.SingleByte(GooberPostSingleBytePayload(payloadBytes[0].toUByte()))
            } else {
                GooberPayload.Raw(payloadBytes)
            }
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
        println("[GOOBER_PARSE] MSG_CLS: 0x${msgClsByte.toString(16).padStart(2, '0').uppercase()}")
        println("[GOOBER_PARSE] PAYLOAD_SIZE: $payloadSize (actual: $actualPayloadSize)")
        print("[GOOBER_PARSE] Raw payload: ")
        payloadBytes.forEach { byte ->
            print("${byte.toUByte().toString(16).padStart(2, '0').uppercase()} ")
        }
        println("\n")
    }
    
    return receivedPacket
}