package com.example.bluefalconcomposemultiplatform.checklist

import java.text.DateFormat
import java.util.Date

actual fun currentEpochMillis(): Long = System.currentTimeMillis()

// The short time format for the phone's language, e.g. "7:15 PM". Android's separate 24-hour switch needs
// a Context, which this function doesn't have, so it isn't followed.
actual fun formatClockTime(epochMillis: Long): String =
    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(epochMillis))
