package com.example.bluefalconcomposemultiplatform.di

import com.example.bluefalconcomposemultiplatform.ble.data.IosPlatformBluetooth
import com.example.bluefalconcomposemultiplatform.ble.data.PlatformBluetooth
import com.example.bluefalconcomposemultiplatform.checklist.ChecklistStorage
import com.example.bluefalconcomposemultiplatform.checklist.IosChecklistStorage
import dev.bluefalcon.ApplicationContext
import dev.bluefalcon.BlueFalcon
import platform.UIKit.UIApplication

actual class AppModule {
    // Built once, on first use: each BlueFalcon creates its own Bluetooth manager
    actual val blueFalcon: BlueFalcon by lazy {
        BlueFalcon(
            log = null,
            context = UIApplication.sharedApplication
        )
    }

    actual val platformBluetooth: PlatformBluetooth = IosPlatformBluetooth()

    actual val checklistStorage: ChecklistStorage = IosChecklistStorage()
}
