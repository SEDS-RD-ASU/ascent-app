package com.example.bluefalconcomposemultiplatform.ble.presentation

import com.example.bluefalconcomposemultiplatform.ble.data.BleDelegate
import com.example.bluefalconcomposemultiplatform.ble.data.DeviceEvent
import com.example.bluefalconcomposemultiplatform.gooberParse
import com.example.bluefalconcomposemultiplatform.GooberPacket
import dev.bluefalcon.BlueFalcon
import dev.bluefalcon.BluetoothCharacteristic
import dev.bluefalcon.BluetoothPeripheral
import dev.icerock.moko.mvvm.viewmodel.ViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.OptIn

@OptIn(kotlin.uuid.ExperimentalUuidApi::class)
class BluetoothDeviceViewModel(
    private val blueFalcon: BlueFalcon,
    delegate: BleDelegate = BleDelegate()
): ViewModel() {

    private val _deviceState: MutableStateFlow<BluetoothDeviceState> = MutableStateFlow(BluetoothDeviceState())
    val deviceState: StateFlow<BluetoothDeviceState> get() = _deviceState
    
    // Expose latest characteristic values via StateFlow for UI access
    private val _latestValues: MutableStateFlow<Map<String, String>> = MutableStateFlow(mapOf())
    val latestValues: StateFlow<Map<String, String>> get() = _latestValues
    
    // Expose parsed Goober packets via StateFlow
    private val _parsedPackets: MutableStateFlow<Map<String, GooberPacket>> = MutableStateFlow(mapOf())
    val parsedPackets: StateFlow<Map<String, GooberPacket>> get() = _parsedPackets
    
    // Track continuous reading jobs per device
    private val readingJobs: MutableMap<String, Job> = mutableMapOf()
    
    /**
     * Get the latest characteristic value (hex) for a peripheral (non-suspend for UI)
     */
    fun getLatestCharacteristicValue(macId: String): String? {
        return _latestValues.value[macId]
    }
    
    /**
     * Get the latest parsed Goober packet for a peripheral (non-suspend for UI)
     */
    fun getLatestParsedPacket(macId: String): GooberPacket? {
        return _parsedPackets.value[macId]
    }
    
    // Helper function to convert byte to hex string (multiplatform compatible)
    private fun Byte.toHexString(): String {
        val byte = this.toInt() and 0xFF
        return if (byte < 16) "0${byte.toString(16).uppercase()}" else byte.toString(16).uppercase()
    }
    
    // Helper function to normalize UUID for comparison (handles short and full formats)
    private fun normalizeUuid(uuid: String): String {
        // Remove hyphens and convert to lowercase for comparison
        val cleaned = uuid.replace("-", "").lowercase()
        // If it's a short UUID (less than 32 hex chars), it's likely 16-bit
        // Extract the last 4 hex digits for 16-bit comparison
        return if (cleaned.length <= 8) {
            // Short UUID - extract last 4 digits
            cleaned.takeLast(4).padStart(4, '0')
        } else {
            // Full UUID - extract the first 8 hex digits (which represent the 16-bit short UUID part)
            // Format: AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE
            // The first 8 hex digits (AAAAAAAA) contain the short UUID
            cleaned.take(8).takeLast(4).padStart(4, '0')
        }
    }

    init {
        delegate.setListener {event ->
            when(event) {
                is DeviceEvent.OnDeviceConnected -> {
                    _deviceState.update { state ->
                        val updateDevices = state.devices.toMutableMap()
                        updateDevices[event.macId]?.let {
                            updateDevices[event.macId] = it.copy(connected = true)
                        }
                        state.copy(
                            devices = HashMap(updateDevices),
                            updateVersion = state.updateVersion + 1
                        )
                    }
                    // Discover services after connection using the connected peripheral
                    // println("DEBUG: Calling discoverServices on ${event.peripheral.name}")
                    blueFalcon.discoverServices(event.peripheral)
                }

                is DeviceEvent.OnDeviceDisconnected -> {
                    // Cancel continuous reading job for this device
                    readingJobs[event.macId]?.cancel()
                    readingJobs.remove(event.macId)
                    
                    _deviceState.update { state ->
                        val updateDevices = state.devices.toMutableMap()
                        updateDevices[event.macId]?.let {
                            updateDevices[event.macId] = it.copy(connected = false)
                        }
                        state.copy(
                            devices = HashMap(updateDevices),
                            updateVersion = state.updateVersion + 1
                        )
                    }
                }

                is DeviceEvent.OnCharacteristicReadComplete -> {
                    // Update latest characteristic value if it's UUID 00000001
                    val currentUuidString = event.characteristic.uuid.toString()
                    // println("DEBUG: OnCharacteristicReadComplete - UUID: $currentUuidString")
                    // println("DEBUG: Characteristic name: ${event.characteristic.name}")
                    
                    // Target UUID - handle both short and full UUID formats
                    // Short: "0001" or "00000001"
                    // Full: "00000001-0000-1000-8000-00805f9b34fb"
                    val targetUuidString = "00000001-0000-1000-8000-00805f9b34fb"
                    val targetShortUuid = "0001"
                    val targetShortUuid2 = "00000001"
                    
                    // Normalize UUIDs for comparison (handle short and full formats)
                    val normalizedCurrent = normalizeUuid(currentUuidString)
                    val normalizedTarget = normalizeUuid(targetUuidString)
                    val normalizedTargetShort = normalizeUuid(targetShortUuid)
                    val normalizedTargetShort2 = normalizeUuid(targetShortUuid2)
                    
                    // println("DEBUG: Comparing UUIDs - Current: $normalizedCurrent, Target: $normalizedTarget")
                    
                    val matches = normalizedCurrent == normalizedTarget || 
                                 normalizedCurrent == normalizedTargetShort || 
                                 normalizedCurrent == normalizedTargetShort2
                    
                    if (matches) {
                        // println("DEBUG: UUID matches! Updating latest value")
                        event.characteristic.value?.let { bytes ->
                            val hexValue = bytes.joinToString(" ", prefix = "[", postfix = "]") { 
                                it.toHexString()
                            }
                            // println("DEBUG: Hex value: $hexValue")
                            
                            // Parse the bytes using gooberParse
                            val parsedPacket = gooberParse(bytes, bytes.size, debug = true)
                            
                            if (parsedPacket != null) {
                                // println("DEBUG: Successfully parsed Goober packet")
                                // println("  - DEV_ID: 0x${parsedPacket.devId.toString(16).padStart(2, '0').uppercase()}")
                                // println("  - DEV_MODE: 0x${parsedPacket.devMode.toString(16).padStart(2, '0').uppercase()}")
                                // println("  - SEQ_ID: 0x${parsedPacket.seqId.toString(16).padStart(2, '0').uppercase()}")
                                // println("  - MSG_CLS: ${parsedPacket.msgCls}")
                                // println("  - PAYLOAD_SIZE: ${parsedPacket.payloadSize}")
                                
                                // Update parsed packets StateFlow
                                _parsedPackets.update { current ->
                                    current.toMutableMap().apply {
                                        put(event.macId, parsedPacket)
                                    }
                                }
                            } else {
                                // println("DEBUG: Failed to parse Goober packet - buffer may be too small or invalid")
                            }
                            
                            // Update StateFlow for UI (keep hex value for backward compatibility)
                            _latestValues.update { current ->
                                current.toMutableMap().apply {
                                    put(event.macId, hexValue)
                                }
                            }
                        }
                    } else {
                        // println("DEBUG: UUID does not match - skipping update")
                    }
                }

                is DeviceEvent.OnCharacteristicWriteComplete -> {
                    println("DEBUG WRITE: Write completed - Success: ${event.success}")
                    
                    // Resume continuous reading after successful write
                    if (event.success) {
                        val deviceState = _deviceState.value.devices[event.macId]
                        if (deviceState?.connected == true && readingJobs[event.macId] == null) {
                            println("  - Resuming continuous reading...")
                            // Small delay to ensure write operation fully completes
                            CoroutineScope(Dispatchers.IO).launch {
                                delay(100)
                                startContinuousReading(event.macId, deviceState.peripheral)
                            }
                        }
                    }
                }

                is DeviceEvent.OnServicesDiscovered -> {
                    // println("DEBUG: Services discovered for ${event.peripheral.name}: ${event.peripheral.services.size} services")
                    // println("DEBUG: Event peripheral services details:")
                    event.peripheral.services.forEach { (uuid, service) ->
                        // println("  - Service: ${service.name}, UUID: $uuid, Characteristics: ${service.characteristics.size}")
                    }
                    
                    _deviceState.update { state ->
                        val updateDevices = state.devices.toMutableMap()
                        // Update the peripheral with latest services
                        val peripheralState = updateDevices[event.peripheral.uuid]
                        if (peripheralState != null) {
                            // println("DEBUG: Updating peripheral in state with ${event.peripheral.services.size} services")
                            updateDevices[event.peripheral.uuid] = EnhancedBluetoothPeripheral(
                                connected = peripheralState.connected,
                                peripheral = event.peripheral
                            )
                            // println("DEBUG: After update, checking device in state:")
                            val updatedDevice = updateDevices[event.peripheral.uuid]
                            println("  - Connected: ${updatedDevice?.connected}")
                            println("  - Services count in state: ${updatedDevice?.peripheral?.services?.size}")
                        }
                        state.copy(
                            devices = HashMap(updateDevices),
                            updateVersion = state.updateVersion + 1
                        )
                    }
                    
                    // Start continuous reading if device is connected and has target characteristic
                    val macId = event.peripheral.uuid
                    val deviceState = _deviceState.value.devices[macId]
                    if (deviceState?.connected == true) {
                        startContinuousReading(macId, event.peripheral)
                    }
                }
            }
        }
        blueFalcon.delegates.add(delegate)
        CoroutineScope(Dispatchers.IO).launch {
            blueFalcon.peripherals.collect { peripherals ->
                val uniqueKeys = _deviceState.value.devices.keys.toList()
                val filteredPeripheral = peripherals.filter { 
                    !uniqueKeys.contains(it.uuid) && 
                    it.name?.contains("ASCENT", ignoreCase = true) == true
                }
                filteredPeripheral.map { peripheral ->
                    _deviceState.update {
                        val updateDevices = it.devices.toMutableMap()
                        updateDevices[peripheral.uuid] = EnhancedBluetoothPeripheral(false, peripheral)
                        it.copy(
                            devices = HashMap(updateDevices),
                            updateVersion = it.updateVersion + 1
                        )
                    }
                }
            }
        }
    }

    /**
     * Start continuous reading for a device with target characteristic (UUID 00000001)
     */
    private fun startContinuousReading(macId: String, peripheral: BluetoothPeripheral) {
        // Cancel any existing reading job for this device
        readingJobs[macId]?.cancel()
        
        // Find the target characteristic (UUID 00000001)
        val targetCharacteristic = peripheral.services.values.flatMap { it.characteristics }
            .firstOrNull { characteristic ->
                val characteristicUuidString = characteristic.uuid.toString()
                val normalizedCharUuid = normalizeUuid(characteristicUuidString)
                val targetUuidStrings = listOf(
                    "00000001-0000-1000-8000-00805f9b34fb",
                    "0001",
                    "00000001"
                )
                targetUuidStrings.any { target ->
                    val normalizedTarget = normalizeUuid(target)
                    normalizedCharUuid == normalizedTarget
                }
            }
        
        targetCharacteristic?.let { characteristic ->
            // println("DEBUG: Starting continuous reading for device $macId")
            val job = CoroutineScope(Dispatchers.IO).launch {
                while (true) {
                    try {
                        val currentDeviceState = _deviceState.value.devices[macId]
                        if (currentDeviceState?.connected == true) {
                            // Use the latest peripheral from state to ensure we have the most up-to-date reference
                            val currentPeripheral = currentDeviceState.peripheral
                            blueFalcon.readCharacteristic(currentPeripheral, characteristic)
                            delay(10) // Small delay between reads (100ms)
                        } else {
                            // Device disconnected, exit loop
                            break
                        }
                    } catch (e: Exception) {
                        // println("DEBUG: Error during continuous read: ${e.message}")
                        delay(10) // Wait before retrying
                    }
                }
            }
            readingJobs[macId] = job
        } ?: println("DEBUG: Target characteristic not found for device $macId")
    }

    /**
     * Sends a single byte command to the BLE characteristic with UUID 00000001.
     * Writes the byte sequence: 0x69 0x00 0x01 (MSG_TYPE) 0x01
     * 
     * @param macId The MAC ID of the device
     * @param msgType The MSG_TYPE byte value (0-255)
     */
    public fun sendSingleByteCommand(macId: String, msgType: UByte) {
        _deviceState.value.devices[macId]?.let { deviceState ->
            if (!deviceState.connected) {
                return@let
            }
            
            // Create the byte array: 0x69 0x00 0x01 (MSG_TYPE) 0x00
            val byteArray = byteArrayOf(
                0x69.toByte(),
                0x00.toByte(),
                0x01.toByte(),
                msgType.toByte(),
                0x01.toByte(),
                0x01.toByte()
            )
            
            // Get fresh device state and find characteristic from current state
            _deviceState.value.devices[macId]?.let { currentDeviceState ->
                // Find the target characteristic (UUID 00000001) from current state
                val targetCharacteristic = currentDeviceState.peripheral.services.values.flatMap { it.characteristics }
                    .firstOrNull { characteristic ->
                        val characteristicUuidString = characteristic.uuid.toString()
                        val normalizedCharUuid = normalizeUuid(characteristicUuidString)
                        val targetUuidStrings = listOf(
                            "00000001-0000-1000-8000-00805f9b34fb",
                            "0001",
                            "00000001"
                        )
                        targetUuidStrings.any { target ->
                            val normalizedTarget = normalizeUuid(target)
                            normalizedCharUuid == normalizedTarget
                        }
                    }
                
                targetCharacteristic?.let { characteristic ->
                    // Convert byte array to string - each byte becomes a character
                    // This is binary-safe and preserves exact byte values (like "123" example)
                    val binaryString = buildString {
                        byteArray.forEach { byte ->
                            append(byte.toInt().toChar())
                        }
                    }
                    
                    println("DEBUG WRITE: Sending command to ${currentDeviceState.peripheral.name}")
                    println("  - MSG_TYPE: 0x${msgType.toString(16).padStart(2, '0').uppercase()}")
                    println("  - Characteristic UUID: ${characteristic.uuid}")
                    println("  - Byte Array: ${byteArray.joinToString(" ") { byte -> 
                        val hex = (byte.toInt() and 0xFF).toString(16).uppercase()
                        "0x${hex.padStart(2, '0')}"
                    }}")
                    println("  - String length: ${binaryString.length} chars")
                    
                    // Stop continuous reading to allow write
                    println("  - Stopping continuous reading...")
                    readingJobs[macId]?.cancel()
                    readingJobs.remove(macId)
                    
                    // Wait for cancellation to complete, then write
                    CoroutineScope(Dispatchers.IO).launch {
                        delay(200) // Wait for read cancellation to complete
                        println("  - Calling writeCharacteristic with binary String...")
                        blueFalcon.writeCharacteristic(currentDeviceState.peripheral, characteristic, binaryString, null)
                        println("  - Write initiated, waiting for callback...")
                        
                        // Resume reading after a longer delay if no callback
                        delay(2000)
                        if (deviceState.connected && readingJobs[macId] == null) {
                            println("  - No callback received, resuming reading...")
                            startContinuousReading(macId, currentDeviceState.peripheral)
                        }
                    }
                }
            }
        }
    }

    fun onEvent(event: UiEvent) {
        when(event) {
            UiEvent.OnScanClick -> {
                blueFalcon.scan()
            }

            is UiEvent.OnConnectClick -> {
                _deviceState.value.devices[event.macId]?.let {
                    blueFalcon.connect(it.peripheral, false)
                }
            }

            is UiEvent.OnDisconnectClick -> {
                _deviceState.value.devices[event.macId]?.let {
                    blueFalcon.disconnect(it.peripheral)
                }
            }

            is UiEvent.OnReadCharacteristic -> {
                _deviceState.value.devices[event.macId]?.let { deviceState ->
                    blueFalcon.readCharacteristic(deviceState.peripheral, event.characteristic)
                }
            }
            is UiEvent.OnWriteCharacteristic -> {
                _deviceState.value.devices[event.macId]?.let { deviceState ->
                    blueFalcon.writeCharacteristic(deviceState.peripheral, event.characteristic, event.value, null)
                }
            }
            is UiEvent.OnSendSingleByteCommand -> {
                sendSingleByteCommand(event.macId, event.msgType)
            }
        }
    }

}