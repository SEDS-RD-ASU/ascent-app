package com.example.bluefalconcomposemultiplatform.di

import android.content.Context
import com.example.bluefalconcomposemultiplatform.ble.data.AndroidPlatformBluetooth
import com.example.bluefalconcomposemultiplatform.ble.data.PlatformBluetooth
import dev.bluefalcon.ApplicationContext
import dev.bluefalcon.BlueFalcon

actual class AppModule(
    private val context: Context
) {
    // Built once, on first use: each BlueFalcon creates its own Bluetooth manager
    actual val blueFalcon: BlueFalcon by lazy {
        BlueFalcon(
            log = null,
            context as ApplicationContext,
        )
    }

    actual val platformBluetooth: PlatformBluetooth = AndroidPlatformBluetooth(context)
}
