package com.example.bluefalconcomposemultiplatform.checklist

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

// The team's pre-flight checklist, as on the paper form. Edit the texts below to change it.
// A step's id is its number on the form (e.g. "9.11") and ticks are saved by id, so adding or removing a
// step renumbers the ones after it: start a new checklist after changing the list.

// A section of the form; the screen shows "<code> · <title>" above its steps
enum class ChecklistSection(val code: String, val title: String) {
    OnsitePreflightReady("8", "ONSITE PREFLIGHT READY"),
    PreLaunch("9", "PRE-LAUNCH"),
    Launch("G", "LAUNCH");

    val label: String get() = "$code · $title"
}

// Values the app reads from the connected ASCENT's telemetry
enum class BoardReading(val label: String) {
    Battery("Battery"),
    DrogueContinuity("Drogue continuity"),
    MainContinuity("Main continuity"),
    Gps("GPS")
}

data class ChecklistStep(
    val section: ChecklistSection,
    // The step's number within its section, as on the form
    val number: Int,
    val text: String,
    // Live readings shown under the step
    val readings: List<BoardReading> = emptyList()
) {
    // e.g. "9.11": how the step is saved, and what the red-reading question calls it
    val id: String get() = "${section.code}.$number"
}

// Limits for the readings. Confirm these with the team (battery type, pyro wiring) before flying.
const val MIN_BATTERY_VOLTS = 7.4          // nominal voltage of a 2-cell LiPo
const val MIN_SATELLITES = 6               // a solid GPS fix (4 is the minimum for any fix)
val FRESH_TELEMETRY: Duration = 3.seconds  // how recent the last telemetry packet must be
const val DROGUE_PYRO_CHANNEL = 1          // pyro channel n is bit n - 1 of the telemetry's pyroState
const val MAIN_PYRO_CHANNEL = 2

// Which readings show under which steps
private val READINGS_BY_STEP: Map<String, List<BoardReading>> = mapOf(
    "9.11" to listOf(BoardReading.Battery, BoardReading.DrogueContinuity, BoardReading.MainContinuity, BoardReading.Gps),
    "9.14" to listOf(BoardReading.Gps),
    "G.3" to listOf(BoardReading.Gps)
)

// Numbers a section's steps 1, 2, 3, … in the order given
private fun numbered(section: ChecklistSection, vararg texts: String): List<ChecklistStep> =
    texts.mapIndexed { index, text -> ChecklistStep(section, index + 1, text) }

val PREFLIGHT_STEPS: List<ChecklistStep> = (
    numbered(
        ChecklistSection.OnsitePreflightReady,
        "Ensure that all batteries being used are fully charged the day of flight.",
        "Install and power on payload into nosecone 6 radial M3 screws",
        "Install nosecone onto upper airframe with 4 M6 radial bolts. Be sure to use loctite",
        "Install lower airframe onto the fin can. Be sure to use loctite",
        "Attach first quick-link of the drogue to the bottom of the avbay and take off tape from the Nomex",
        "Attach second quick link to the top of the avbay. Ensure the tape is removed from the Nomex on the mains",
        "Unshort e-matches, trim ends to length, and attach securely to pass-through bolts. If needed, tape ends with electrical tape.",
        "Stack all sections of the rocket. Insert four M3 nylon shear pins into the avbay through the upper airframe and four M3 nylon shear pins into the avbay through the lower airframe."
    ) + numbered(
        ChecklistSection.PreLaunch,
        "Drive the rocket to the launch pad.",
        "Once at the pad we will ensure a clear radius around the rocket so that exhaust gasses do not cause a fire",
        "Once the area is safe to launch from, we will lower the rail and insert the rocket.",
        "Then raise the rocket to be vertical.",
        "With the vehicle vertical, only the minimum personnel will approach the vehicle for further procedures. The rest of the group will remain back at a good standoff distance.",
        "Next the nitrous lines will be hooked up to the tank.",
        "Ensure that the nitrous pressure is at optimal levels, ideally 750 PSI",
        "Once given clearance, a squirt leak test will be performed. The remainder of the checklist is only to be continued if the leak test was nominal.",
        "(One person with a handheld radio) Once the rocket is vertical and in launch orientation, we will arm the flight computers one at a time, starting with the Ascent avionics system.",
        "Once Ascent is armed, the arming personnel is to observe the beep sequence of Ascent for any error beeps.",
        "After Ascent is booted up, the arming personnel is to radio ground control from pad to ensure good telemetry, continuity, battery voltage, and GPS.",
        "The Featherweight GPS will be armed for flight after we are assigned a frequency.",
        "Next, the Blue Raven is armed for flight.",
        "Arming personnel must listen for the beeps from the Raven, and ensure that we still have GPS lock with the Featherweight and Ascent.",
        "Once all controllers and trackers are confirmed armed, the final pad personnel will wire the ignitors to the launch controller."
    ) + numbered(
        ChecklistSection.Launch,
        "Complete pre-Launch checklist and return to the flight line. (V.F.9)",
        "Check to see if live video is still transmitting",
        "Ensure the Featherweight and Ascent is still tracking. Failure of one system results in a scrub, follow V.I.1.",
        "Once avionics is go for launch, and we are given permission by the LCO, the fill for the rocket can be started. A rough time estimate for fill must be provided to the LCO",
        "Once the rocket is confirmed full, the pad is armed and the fill valve is closed.",
        "Once we are given the go for launch, the launch button can be pressed. Go Solar Flare"
    )
).map { step -> step.copy(readings = READINGS_BY_STEP[step.id].orEmpty()) }
