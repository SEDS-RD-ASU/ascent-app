package com.example.bluefalconcomposemultiplatform.checklist

import kotlin.test.Test
import kotlin.test.assertEquals

class ChecklistViewModelTest {

    private class FakeStorage(vararg saved: Pair<String, String>) : ChecklistStorage {
        val values = mutableMapOf(*saved)
        var saves = 0
        override fun load(key: String): String? = values[key]
        override fun save(key: String, text: String) {
            values[key] = text
            saves++
        }
    }

    private val allIds = PREFLIGHT_STEPS.map { it.id }.toSet()
    private val firstStep = PREFLIGHT_STEPS.first()   // 8.1

    @Test
    fun loadsWhatWasSavedBefore() {
        val storage = FakeStorage(TICKS_KEY to "v2;started=1000;tick=8.1,CR,1000", INITIALS_KEY to "cr")
        val viewModel = ChecklistViewModel(storage, clock = { 5_000L })
        assertEquals(
            ChecklistTicks(startedAtMillis = 1_000, ticks = mapOf("8.1" to TickRecord("CR", 1_000))),
            viewModel.ticks.value
        )
        assertEquals("CR", viewModel.initials.value)
    }

    @Test
    fun aTickIsStampedAndSavedStraightAway() {
        val storage = FakeStorage()
        val viewModel = ChecklistViewModel(storage, clock = { 5_000L })
        viewModel.setInitials("cr")
        viewModel.tick(firstStep)
        val expected = ChecklistTicks(startedAtMillis = 5_000, ticks = mapOf("8.1" to TickRecord("CR", 5_000)))
        assertEquals(expected, viewModel.ticks.value)
        assertEquals(expected, decodeTicks(storage.values[TICKS_KEY], allIds))
        assertEquals("CR", storage.values[INITIALS_KEY])
    }

    @Test
    fun withoutInitialsATickDoesNothing() {
        val storage = FakeStorage()
        val viewModel = ChecklistViewModel(storage, clock = { 5_000L })
        viewModel.tick(firstStep)
        assertEquals(ChecklistTicks(), viewModel.ticks.value)
        assertEquals(0, storage.saves)
    }

    @Test
    fun untickingKeepsTheStartTime() {
        val storage = FakeStorage(TICKS_KEY to "v2;started=1000;tick=8.1,CR,1000", INITIALS_KEY to "CR")
        val viewModel = ChecklistViewModel(storage, clock = { 5_000L })
        viewModel.untick(firstStep)
        assertEquals(ChecklistTicks(startedAtMillis = 1_000), viewModel.ticks.value)
        assertEquals(ChecklistTicks(startedAtMillis = 1_000), decodeTicks(storage.values[TICKS_KEY], allIds))
    }

    @Test
    fun startNewClearsTheTicksButKeepsTheInitials() {
        val storage = FakeStorage(TICKS_KEY to "v2;started=1000;tick=8.1,CR,1000", INITIALS_KEY to "CR")
        val viewModel = ChecklistViewModel(storage, clock = { 5_000L })
        viewModel.startNew()
        assertEquals(ChecklistTicks(), viewModel.ticks.value)
        assertEquals(ChecklistTicks(), decodeTicks(storage.values[TICKS_KEY], allIds))
        assertEquals("CR", viewModel.initials.value)
    }

    @Test
    fun emptyInitialsAreIgnoredAndNothingIsSavedWhenNothingChanges() {
        val storage = FakeStorage()
        val viewModel = ChecklistViewModel(storage, clock = { 5_000L })
        viewModel.setInitials("12")   // no letters
        viewModel.startNew()          // already empty
        assertEquals("", viewModel.initials.value)
        assertEquals(0, storage.saves)
    }
}
