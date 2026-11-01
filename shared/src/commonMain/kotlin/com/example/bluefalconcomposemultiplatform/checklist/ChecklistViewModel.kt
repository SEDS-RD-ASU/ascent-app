package com.example.bluefalconcomposemultiplatform.checklist

import dev.icerock.moko.mvvm.viewmodel.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

// Holds the checklist's ticks and the initials, and saves every change straight away.
// The readings aren't here: App.kt works them out from the live Bluetooth state.
class ChecklistViewModel(
    private val storage: ChecklistStorage,
    private val clock: () -> Long = ::currentEpochMillis
) : ViewModel() {

    private val _ticks = MutableStateFlow(decodeTicks(storage.load(TICKS_KEY), PREFLIGHT_STEPS.map { it.id }.toSet()))
    val ticks: StateFlow<ChecklistTicks> get() = _ticks

    private val _initials = MutableStateFlow(cleanInitials(storage.load(INITIALS_KEY).orEmpty()))
    val initials: StateFlow<String> get() = _initials

    // Ticks a step with the current initials and time. The screen asks for initials first, so without
    // any this does nothing.
    fun tick(step: ChecklistStep) {
        val initials = _initials.value
        if (initials.isEmpty()) return
        changeTicks { withTick(it, step.id, initials, clock()) }
    }

    fun untick(step: ChecklistStep) {
        changeTicks { withoutTick(it, step.id) }
    }

    fun setInitials(typed: String) {
        val initials = cleanInitials(typed)
        if (initials.isEmpty() || initials == _initials.value) return
        _initials.value = initials
        storage.save(INITIALS_KEY, initials)
    }

    // Clears every tick and the start time; the initials stay
    fun startNew() {
        changeTicks { ChecklistTicks() }
    }

    private fun changeTicks(update: (ChecklistTicks) -> ChecklistTicks) {
        val next = update(_ticks.value)
        if (next == _ticks.value) return
        _ticks.value = next
        storage.save(TICKS_KEY, encodeTicks(next))
    }
}
