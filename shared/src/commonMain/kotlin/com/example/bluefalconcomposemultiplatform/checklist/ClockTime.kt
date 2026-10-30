package com.example.bluefalconcomposemultiplatform.checklist

// The phone's wall clock, in milliseconds since 1970 (Kotlin 2.0's standard library has no wall clock)
expect fun currentEpochMillis(): Long

// A wall-clock time the way the phone shows times, e.g. "7:15 PM" or "19:15"
expect fun formatClockTime(epochMillis: Long): String
