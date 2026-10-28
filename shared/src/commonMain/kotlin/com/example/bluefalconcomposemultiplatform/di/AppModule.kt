package com.example.bluefalconcomposemultiplatform.di

import com.example.bluefalconcomposemultiplatform.ble.data.PlatformBluetooth
import dev.bluefalcon.BlueFalcon

expect class AppModule {
    val blueFalcon: BlueFalcon
    val platformBluetooth: PlatformBluetooth
}
