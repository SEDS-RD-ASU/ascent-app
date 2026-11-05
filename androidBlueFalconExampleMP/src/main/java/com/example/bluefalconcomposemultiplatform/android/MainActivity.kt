package com.example.bluefalconcomposemultiplatform.android

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.bluefalconcomposemultiplatform.App
import com.example.bluefalconcomposemultiplatform.di.AppModule

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ask for whatever Bluetooth scanning still needs. The scan screen checks again every second, so
        // scanning starts as soon as it's allowed.
        val missing = missingPermissions()
        if (missing.isNotEmpty()) {
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
                if (results.values.any { granted -> !granted }) {
                    Toast.makeText(this, "Cannot scan without permission.", Toast.LENGTH_LONG).show()
                }
            }.launch(missing.toTypedArray())
        }

        setContent {
            App(
                darkTheme = isSystemInDarkTheme(),
                dynamicColor = false,
                appModule = AppModule(LocalContext.current.applicationContext)
            )
        }
    }

    // What Bluetooth scanning needs, as AndroidPlatformBluetooth checks it: nearby devices (scan and connect)
    // on Android 12 and newer, precise location before that
    private fun missingPermissions(): List<String> {
        val needed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        return needed.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
    }
}
