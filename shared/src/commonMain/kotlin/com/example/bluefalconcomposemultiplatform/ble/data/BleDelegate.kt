package com.example.bluefalconcomposemultiplatform.ble.data

import dev.bluefalcon.AdvertisementDataRetrievalKeys
import dev.bluefalcon.BlueFalconDelegate
import dev.bluefalcon.BluetoothCharacteristic
import dev.bluefalcon.BluetoothCharacteristicDescriptor
import dev.bluefalcon.BluetoothPeripheral
import kotlinx.coroutines.*


class BleDelegate: BlueFalconDelegate {
    var writeChar: BluetoothCharacteristic? = null
    var readChar: BluetoothCharacteristic? = null

    private var onDeviceEvent: ((DeviceEvent) -> Unit)? = null
    private val characteristicsJobMap = mutableMapOf<String, Job>()
    private val scope = CoroutineScope(Dispatchers.Default)
    
    fun setListener(onEvent: (DeviceEvent) -> Unit) {
        onDeviceEvent = onEvent
    }
    override fun didCharacteristcValueChanged(
        bluetoothPeripheral: BluetoothPeripheral,
        bluetoothCharacteristic: BluetoothCharacteristic
    ) {
        bluetoothCharacteristic.value?.let { bytes ->
            // Debug print the read characteristic values as bytes
            // println("DEBUG: Read characteristic value from ${bluetoothPeripheral.name}")
            // println("  - Characteristic UUID: ${bluetoothCharacteristic.uuid}")
            // println("  - Characteristic name: ${bluetoothCharacteristic.name ?: "Unknown"}")
            // println("  - Value length: ${bytes.size} bytes")
            // println("  - Value (hex): ${bytes.joinToString(" ", prefix = "[", postfix = "]") { 
            //     val byte = it.toInt() and 0xFF
            //     if (byte < 16) "0${byte.toString(16).uppercase()}" else byte.toString(16).uppercase()
            // }}")
            // println("  - Value (decimal): ${bytes.joinToString(", ", prefix = "[", postfix = "]")}")
            
            // Notify that a characteristic read completed (value changed indicates read result)
            onDeviceEvent?.let {
                it(com.example.bluefalconcomposemultiplatform.ble.data.DeviceEvent.OnCharacteristicReadComplete(
                    bluetoothPeripheral.uuid,
                    bluetoothCharacteristic
                ))
            }
        }
    }

    override fun didConnect(bluetoothPeripheral: BluetoothPeripheral) {
        // println("DEBUG: didConnect called for ${bluetoothPeripheral.name}")
        onDeviceEvent?.let {
            it(DeviceEvent.OnDeviceConnected(bluetoothPeripheral.uuid, bluetoothPeripheral))
        }
    }

    override fun didDisconnect(bluetoothPeripheral: BluetoothPeripheral) {
        // Clean up any pending characteristic discovery jobs
        characteristicsJobMap[bluetoothPeripheral.uuid]?.cancel()
        characteristicsJobMap.remove(bluetoothPeripheral.uuid)
        
        onDeviceEvent?.let {
            it(DeviceEvent.OnDeviceDisconnected(bluetoothPeripheral.uuid))
        }
    }

    override fun didDiscoverCharacteristics(bluetoothPeripheral: BluetoothPeripheral) {
        // println("DEBUG: didDiscoverCharacteristics called for ${bluetoothPeripheral.name}")
        
        // Debounce multiple characteristic discoveries - only update once after all are discovered
        val uuid = bluetoothPeripheral.uuid
        characteristicsJobMap[uuid]?.cancel()
        characteristicsJobMap[uuid] = scope.launch {
            delay(150) // Wait 150ms to batch multiple discoveries
            onDeviceEvent?.let {
                println("DEBUG: Triggering OnServicesDiscovered after characteristics found")
                it(DeviceEvent.OnServicesDiscovered(bluetoothPeripheral))
            }
        }
    }

    override fun didDiscoverServices(bluetoothPeripheral: BluetoothPeripheral) {
        // println("DEBUG: didDiscoverServices called for ${bluetoothPeripheral.name}")
        onDeviceEvent?.let {
            it(DeviceEvent.OnServicesDiscovered(bluetoothPeripheral))
        }
    }

    override fun didReadDescriptor(
        bluetoothPeripheral: BluetoothPeripheral,
        bluetoothCharacteristicDescriptor: BluetoothCharacteristicDescriptor
    ) {

    }

    override fun didRssiUpdate(bluetoothPeripheral: BluetoothPeripheral) {
    }

    override fun didUpdateMTU(bluetoothPeripheral: BluetoothPeripheral, status: Int) {

    }

    override fun didWriteCharacteristic(
        bluetoothPeripheral: BluetoothPeripheral,
        bluetoothCharacteristic: BluetoothCharacteristic,
        success: Boolean
    ) {
        println("DEBUG WRITE: Write callback from ${bluetoothPeripheral.name}")
        println("  - Characteristic UUID: ${bluetoothCharacteristic.uuid}")
        println("  - Success: $success")
        
        // Notify that a characteristic write completed
        onDeviceEvent?.let {
            it(com.example.bluefalconcomposemultiplatform.ble.data.DeviceEvent.OnCharacteristicWriteComplete(
                bluetoothPeripheral.uuid,
                bluetoothCharacteristic,
                success
            ))
        }
    }

    override fun didWriteDescriptor(
        bluetoothPeripheral: BluetoothPeripheral,
        bluetoothCharacteristicDescriptor: BluetoothCharacteristicDescriptor
    ) {

    }
}