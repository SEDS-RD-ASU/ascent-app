package com.example.bluefalconcomposemultiplatform.checklist

import com.example.bluefalconcomposemultiplatform.GooberPostTelemetryPayload
import kotlin.math.abs
import kotlin.math.round
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.DurationUnit

// ---- Which board the checklist is reading ----

// What the checklist needs to know about one board, taken from the Bluetooth view model's state
data class BoardSnapshot(
    val name: String,
    val connected: Boolean,
    val telemetry: GooberPostTelemetryPayload?,
    // When that telemetry arrived, on the Bluetooth view model's clock
    val telemetryAt: Duration?
)

sealed interface BoardFocus {
    data object NoBoard : BoardFocus
    data object TooMany : BoardFocus
    data class One(
        val name: String,
        val telemetry: GooberPostTelemetryPayload?,
        // How long ago the telemetry arrived (never negative), or null if none has
        val telemetryAge: Duration?
    ) : BoardFocus
}

// The readings come from exactly one connected board
fun boardFocus(boards: List<BoardSnapshot>, now: Duration): BoardFocus {
    val connected = boards.filter { it.connected }
    if (connected.isEmpty()) return BoardFocus.NoBoard
    if (connected.size > 1) return BoardFocus.TooMany
    val board = connected.single()
    return BoardFocus.One(
        name = board.name,
        telemetry = board.telemetry,
        telemetryAge = board.telemetryAt?.let { (now - it).coerceAtLeast(Duration.ZERO) }
    )
}

// ---- Reading blocks ----

// The first line of a reading block, e.g. "ASCENT R3 SN4 · last packet 0.4 s ago"
data class BoardLine(
    // Shown in bold before the status, or null when there's no single board
    val name: String?,
    val status: String,
    // Fresh telemetry: the line gets a green dot and the readings are judged
    val fresh: Boolean
)

fun boardLine(focus: BoardFocus): BoardLine = when (focus) {
    BoardFocus.NoBoard -> BoardLine(null, "No board connected", fresh = false)
    BoardFocus.TooMany -> BoardLine(null, "Connect only one board", fresh = false)
    is BoardFocus.One -> {
        val age = focus.telemetryAge
        when {
            focus.telemetry == null || age == null -> BoardLine(focus.name, "no data yet", fresh = false)
            age > FRESH_TELEMETRY -> BoardLine(focus.name, "last data ${age.inWholeSeconds} s ago", fresh = false)
            else -> BoardLine(
                focus.name,
                "last packet ${formatFixed(age.toDouble(DurationUnit.SECONDS), 1)} s ago",
                fresh = true
            )
        }
    }
}

sealed interface ReadingValue {
    // Shown as a grey "—": no single board, or no fresh telemetry
    data object NoData : ReadingValue
    data class Fine(val text: String) : ReadingValue
    data class Problem(val text: String) : ReadingValue
}

// Same conversion as the Battery row on the connected card
fun batteryVolts(raw: UShort): Double = raw.toInt() / 2500.0

// Pyro channel n is bit n - 1 of pyroState, as on the connected card
fun hasContinuity(pyroState: UByte, channel: Int): Boolean =
    (pyroState.toInt() shr (channel - 1)) and 1 == 1

// A reading's value, judged only on fresh telemetry from exactly one board
fun readingValue(reading: BoardReading, focus: BoardFocus): ReadingValue {
    val board = focus as? BoardFocus.One ?: return ReadingValue.NoData
    val telemetry = board.telemetry ?: return ReadingValue.NoData
    val age = board.telemetryAge ?: return ReadingValue.NoData
    // Never judge the board on old data
    if (age > FRESH_TELEMETRY) return ReadingValue.NoData

    return when (reading) {
        BoardReading.Battery -> {
            val volts = batteryVolts(telemetry.batteryVoltage)
            val text = "${formatFixed(volts, 2)} V"
            if (volts >= MIN_BATTERY_VOLTS) ReadingValue.Fine(text)
            else ReadingValue.Problem("$text · needs $MIN_BATTERY_VOLTS V")
        }
        BoardReading.DrogueContinuity -> continuity(telemetry.pyroState, DROGUE_PYRO_CHANNEL)
        BoardReading.MainContinuity -> continuity(telemetry.pyroState, MAIN_PYRO_CHANNEL)
        BoardReading.Gps -> {
            val sats = telemetry.sats.toInt()
            val text = if (sats == 1) "1 satellite" else "$sats satellites"
            if (sats >= MIN_SATELLITES) ReadingValue.Fine(text)
            else ReadingValue.Problem("$text · needs $MIN_SATELLITES")
        }
    }
}

private fun continuity(pyroState: UByte, channel: Int): ReadingValue =
    if (hasContinuity(pyroState, channel)) ReadingValue.Fine("Continuity") else ReadingValue.Problem("No continuity")

// The red readings under a step, as the red-reading question lists them, e.g. "Battery: 7.10 V · needs 7.4 V"
fun problemsUnder(step: ChecklistStep, focus: BoardFocus): List<String> =
    step.readings.mapNotNull { reading ->
        (readingValue(reading, focus) as? ReadingValue.Problem)?.let { "${reading.label}: ${it.text}" }
    }

// ---- Ticks ----

// Who ticked a step, and when (wall-clock milliseconds since 1970)
data class TickRecord(val initials: String, val atMillis: Long)

// The saved part of the checklist
data class ChecklistTicks(
    // When the first tick on this checklist was made
    val startedAtMillis: Long? = null,
    // Step id -> its tick
    val ticks: Map<String, TickRecord> = emptyMap()
)

// Letters only, in capitals, at most four, e.g. " c.r " -> "CR"
fun cleanInitials(typed: String): String = typed.filter { it.isLetter() }.uppercase().take(4)

// Ticks a step; the first tick on an empty checklist sets the start time
fun withTick(ticks: ChecklistTicks, stepId: String, initials: String, nowMillis: Long): ChecklistTicks =
    ChecklistTicks(
        startedAtMillis = ticks.startedAtMillis ?: nowMillis,
        ticks = ticks.ticks + (stepId to TickRecord(initials, nowMillis))
    )

// Unticking keeps the start time
fun withoutTick(ticks: ChecklistTicks, stepId: String): ChecklistTicks = ticks.copy(ticks = ticks.ticks - stepId)

sealed interface TapAction {
    data object Untick : TapAction
    data object AskInitials : TapAction
    // A red reading under the step: ask before ticking
    data class ConfirmProblems(val problems: List<String>) : TapAction
    data object Tick : TapAction
}

// What tapping a step should do: untick it, or tick it after asking for initials and then about red readings
fun tapAction(step: ChecklistStep, ticks: ChecklistTicks, initials: String, focus: BoardFocus): TapAction {
    if (step.id in ticks.ticks) return TapAction.Untick
    if (initials.isEmpty()) return TapAction.AskInitials
    val problems = problemsUnder(step, focus)
    return if (problems.isEmpty()) TapAction.Tick else TapAction.ConfirmProblems(problems)
}

// ---- Progress ----

data class ChecklistProgress(val done: Int, val total: Int) {
    val allDone: Boolean get() = total > 0 && done == total
}

fun progress(steps: List<ChecklistStep>, ticks: ChecklistTicks): ChecklistProgress =
    ChecklistProgress(done = steps.count { it.id in ticks.ticks }, total = steps.size)

// The bold line on the progress card
fun progressHeadline(progress: ChecklistProgress): String =
    if (progress.allDone) "All ${progress.total} steps done" else "${progress.done} of ${progress.total} done"

// The line under "Pre-flight checklist" on the scan screen
fun entryLine(progress: ChecklistProgress, startedAt: String?): String = when {
    progress.allDone -> "All ${progress.total} steps done"
    progress.done == 0 && startedAt == null -> "Not started · ${progress.total} steps"
    startedAt == null -> "${progress.done} of ${progress.total} done"
    else -> "${progress.done} of ${progress.total} done · started $startedAt"
}

// A checklist started more than this long ago gets a banner suggesting a new one
val STALE_AFTER: Duration = 12.hours

// Whole hours since the checklist started, once it's old enough for the banner; otherwise null
fun staleHours(startedAtMillis: Long?, nowMillis: Long): Long? {
    if (startedAtMillis == null) return null
    val age = (nowMillis - startedAtMillis).milliseconds
    return if (age > STALE_AFTER) age.inWholeHours else null
}

// ---- Saving ----

private const val TICKS_FORMAT = "v2"

// One line of text, e.g. "v2;started=1792721854000;tick=8.1,CR,1792721854000;tick=8.2,CR,1792722214000"
fun encodeTicks(ticks: ChecklistTicks): String = buildList {
    add(TICKS_FORMAT)
    add("started=${ticks.startedAtMillis ?: ""}")
    ticks.ticks.entries.sortedBy { it.key }.forEach { (id, tick) -> add("tick=$id,${tick.initials},${tick.atMillis}") }
}.joinToString(";")

// Anything unreadable (including the first draft's "v1" format) gives an empty checklist;
// ticks for unknown steps, or with missing parts, are dropped
fun decodeTicks(text: String?, knownIds: Set<String>): ChecklistTicks {
    val parts = text?.split(";") ?: return ChecklistTicks()
    if (parts.first() != TICKS_FORMAT) return ChecklistTicks()
    var startedAt: Long? = null
    val ticks = mutableMapOf<String, TickRecord>()
    for (part in parts.drop(1)) {
        when {
            part.startsWith("started=") -> startedAt = part.removePrefix("started=").toLongOrNull()
            part.startsWith("tick=") -> {
                val fields = part.removePrefix("tick=").split(",")
                if (fields.size != 3) continue
                val (id, typedInitials, at) = fields
                val initials = cleanInitials(typedInitials)
                val atMillis = at.toLongOrNull()
                if (id in knownIds && initials.isNotEmpty() && atMillis != null) {
                    ticks[id] = TickRecord(initials, atMillis)
                }
            }
        }
    }
    return ChecklistTicks(startedAt, ticks)
}

// ---- Formatting ----

// A number with a fixed number of decimals, e.g. formatFixed(7.1, 2) = "7.10" (common code has no String.format)
fun formatFixed(value: Double, decimals: Int): String {
    var factor = 1L
    repeat(decimals) { factor *= 10 }
    val scaled = round(abs(value) * factor).toLong()
    val sign = if (value < 0 && scaled != 0L) "-" else ""
    val whole = scaled / factor
    if (decimals == 0) return "$sign$whole"
    val fraction = (scaled % factor).toString().padStart(decimals, '0')
    return "$sign$whole.$fraction"
}
