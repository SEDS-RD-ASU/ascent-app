package com.example.bluefalconcomposemultiplatform.ble.data

import platform.Foundation.NSURL
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationOpenSettingsURLString

class IosPlatformBluetooth : PlatformBluetooth {
    // On iOS, BlueFalcon's scan() exceptions already say why Bluetooth isn't ready
    override fun problem(): BluetoothStatus? = null

    override fun openAppSettings() {
        val url = NSURL.URLWithString(UIApplicationOpenSettingsURLString) ?: return
        UIApplication.sharedApplication.openURL(url, options = emptyMap<Any?, Any>(), completionHandler = null)
    }
}
