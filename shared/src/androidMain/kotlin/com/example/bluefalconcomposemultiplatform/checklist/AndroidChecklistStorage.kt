package com.example.bluefalconcomposemultiplatform.checklist

import android.content.Context

class AndroidChecklistStorage(context: Context) : ChecklistStorage {
    private val preferences = context.getSharedPreferences("goober-connect", Context.MODE_PRIVATE)

    override fun load(key: String): String? = preferences.getString(key, null)

    override fun save(key: String, text: String) {
        preferences.edit().putString(key, text).apply()
    }
}
