package com.example.bluefalconcomposemultiplatform.checklist

// Where the checklist is saved on the phone: UserDefaults on iOS, SharedPreferences on Android
interface ChecklistStorage {
    fun load(key: String): String?
    fun save(key: String, text: String)
}

// The keys it's saved under on both platforms
const val TICKS_KEY = "preflight-checklist"
const val INITIALS_KEY = "preflight-initials"
