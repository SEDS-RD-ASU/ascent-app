package com.example.bluefalconcomposemultiplatform.checklist

import platform.Foundation.NSUserDefaults

class IosChecklistStorage : ChecklistStorage {
    private val defaults = NSUserDefaults.standardUserDefaults

    override fun load(key: String): String? = defaults.stringForKey(key)

    override fun save(key: String, text: String) {
        defaults.setObject(text, forKey = key)
    }
}
