package com.example.bluefalconcomposemultiplatform.checklist

import com.example.bluefalconcomposemultiplatform.GooberPostTelemetryPayload
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class ChecklistRulesTest {

    private val allIds = PREFLIGHT_STEPS.map { it.id }.toSet()

    private fun step(id: String) = PREFLIGHT_STEPS.first { it.id == id }

    // A telemetry packet whose readings are all fine unless a test changes them
    private fun packet(
        batteryRaw: UShort = 19750u,   // 7.90 V
        pyroState: UByte = 0b0011u,    // channels 1 and 2 have continuity
        sats: UByte = 9u
    ) = GooberPostTelemetryPayload(
        timestamp = 0u, latitude = 0, longitude = 0,
        altitudeAgl = 0f, verticalVelocity = 0f, xAcc = 0f, gyrX = 0f,
        pyroState = pyroState, sats = sats, flightState = 0u, batteryVoltage = batteryRaw
    )

    // One connected board whose telemetry arrived `age` ago
    private fun oneBoard(telemetry: GooberPostTelemetryPayload? = packet(), age: Duration = 400.milliseconds) =
        boardFocus(
            listOf(
                BoardSnapshot(
                    name = "ASCENT R3 SN4",
                    connected = true,
                    telemetry = telemetry,
                    telemetryAt = if (telemetry == null) null else 10.seconds
                )
            ),
            now = 10.seconds + age
        )

    @Test
    fun theTeamsTwentyNineStepsHaveUniqueIds() {
        assertEquals(29, PREFLIGHT_STEPS.size)
        assertEquals(29, allIds.size)
        assertEquals(listOf(8, 15, 6), ChecklistSection.entries.map { section -> PREFLIGHT_STEPS.count { it.section == section } })
        assertEquals("8.1", PREFLIGHT_STEPS.first().id)
        assertEquals("G.6", PREFLIGHT_STEPS.last().id)
        assertEquals("Drive the rocket to the launch pad.", step("9.1").text)
    }

    @Test
    fun readingsSitUnderOnlyThreeSteps() {
        assertEquals(
            listOf(BoardReading.Battery, BoardReading.DrogueContinuity, BoardReading.MainContinuity, BoardReading.Gps),
            step("9.11").readings
        )
        assertEquals(listOf(BoardReading.Gps), step("9.14").readings)
        assertEquals(listOf(BoardReading.Gps), step("G.3").readings)
        assertEquals(setOf("9.11", "9.14", "G.3"), PREFLIGHT_STEPS.filter { it.readings.isNotEmpty() }.map { it.id }.toSet())
    }

    @Test
    fun sectionLabels() {
        assertEquals("8 · ONSITE PREFLIGHT READY", ChecklistSection.OnsitePreflightReady.label)
        assertEquals("9 · PRE-LAUNCH", ChecklistSection.PreLaunch.label)
        assertEquals("G · LAUNCH", ChecklistSection.Launch.label)
    }

    @Test
    fun batteryIsFineFromSevenPointFourVolts() {
        assertEquals(ReadingValue.Fine("7.40 V"), readingValue(BoardReading.Battery, oneBoard(packet(batteryRaw = 18500u))))
        assertEquals(
            ReadingValue.Problem("7.39 V · needs 7.4 V"),
            readingValue(BoardReading.Battery, oneBoard(packet(batteryRaw = 18475u)))
        )
    }

    @Test
    fun continuityReadsTheRightBits() {
        // Only channel 1 (bit 0)
        val drogueOnly = oneBoard(packet(pyroState = 0b0001u))
        assertEquals(ReadingValue.Fine("Continuity"), readingValue(BoardReading.DrogueContinuity, drogueOnly))
        assertEquals(ReadingValue.Problem("No continuity"), readingValue(BoardReading.MainContinuity, drogueOnly))
        // Channels 3 and 4 don't count for drogue or main
        val otherChannels = oneBoard(packet(pyroState = 0b1100u))
        assertEquals(ReadingValue.Problem("No continuity"), readingValue(BoardReading.DrogueContinuity, otherChannels))
        assertEquals(ReadingValue.Problem("No continuity"), readingValue(BoardReading.MainContinuity, otherChannels))
    }

    @Test
    fun gpsNeedsSixSatellites() {
        assertEquals(ReadingValue.Fine("6 satellites"), readingValue(BoardReading.Gps, oneBoard(packet(sats = 6u))))
        assertEquals(
            ReadingValue.Problem("5 satellites · needs 6"),
            readingValue(BoardReading.Gps, oneBoard(packet(sats = 5u)))
        )
    }

    @Test
    fun theFirstLineShowsTheBoardAndHowFreshItsDataIs() {
        assertEquals(BoardLine("ASCENT R3 SN4", "last packet 0.4 s ago", fresh = true), boardLine(oneBoard(age = 400.milliseconds)))
        // Exactly 3 s old still counts as fresh
        assertEquals(BoardLine("ASCENT R3 SN4", "last packet 3.0 s ago", fresh = true), boardLine(oneBoard(age = 3.seconds)))
        assertEquals(
            BoardLine("ASCENT R3 SN4", "last data 3 s ago", fresh = false),
            boardLine(oneBoard(age = 3.seconds + 500.milliseconds))
        )
        assertEquals(BoardLine("ASCENT R3 SN4", "no data yet", fresh = false), boardLine(oneBoard(telemetry = null)))
        assertEquals(BoardLine(null, "No board connected", fresh = false), boardLine(BoardFocus.NoBoard))
        assertEquals(BoardLine(null, "Connect only one board", fresh = false), boardLine(BoardFocus.TooMany))
    }

    @Test
    fun oldOrMissingDataIsNeverJudged() {
        val stale = oneBoard(packet(batteryRaw = 17750u), age = 3.seconds + 500.milliseconds)
        BoardReading.entries.forEach { reading ->
            assertEquals(ReadingValue.NoData, readingValue(reading, stale))
        }
        assertEquals(ReadingValue.NoData, readingValue(BoardReading.Gps, oneBoard(telemetry = null)))
        assertEquals(ReadingValue.NoData, readingValue(BoardReading.Gps, BoardFocus.NoBoard))
    }

    @Test
    fun telemetryAgeIsNeverNegative() {
        // The view model's clock can trail a packet's arrival time by a moment
        val focus = boardFocus(listOf(BoardSnapshot("ASCENT", true, packet(), telemetryAt = 11.seconds)), now = 10.seconds)
        assertEquals(BoardLine("ASCENT", "last packet 0.0 s ago", fresh = true), boardLine(focus))
    }

    @Test
    fun onlyOneConnectedBoardIsRead() {
        val idle = BoardSnapshot("ASCENT A", connected = false, telemetry = null, telemetryAt = null)
        val linked = BoardSnapshot("ASCENT B", connected = true, telemetry = null, telemetryAt = null)
        assertEquals(BoardFocus.NoBoard, boardFocus(listOf(idle), now = 0.seconds))
        assertEquals(
            BoardFocus.One("ASCENT B", telemetry = null, telemetryAge = null),
            boardFocus(listOf(idle, linked), now = 0.seconds)
        )
        assertEquals(BoardFocus.TooMany, boardFocus(listOf(linked, linked.copy(name = "ASCENT C")), now = 0.seconds))
    }

    @Test
    fun problemsUnderAStepAreListedForTheQuestion() {
        val focus = oneBoard(packet(batteryRaw = 17750u, pyroState = 0b0001u))
        assertEquals(
            listOf("Battery: 7.10 V · needs 7.4 V", "Main continuity: No continuity"),
            problemsUnder(step("9.11"), focus)
        )
        assertEquals(emptyList<String>(), problemsUnder(step("9.14"), focus))   // GPS is fine
        assertEquals(emptyList<String>(), problemsUnder(step("8.1"), focus))    // no readings
    }

    @Test
    fun tappingATickedStepUnticksIt() {
        val ticks = ChecklistTicks(startedAtMillis = 1, ticks = mapOf("8.1" to TickRecord("CR", 1)))
        assertEquals(TapAction.Untick, tapAction(step("8.1"), ticks, initials = "", focus = BoardFocus.NoBoard))
    }

    @Test
    fun initialsComeFirst() {
        assertEquals(
            TapAction.AskInitials,
            tapAction(step("9.11"), ChecklistTicks(), initials = "", focus = oneBoard(packet(batteryRaw = 17750u)))
        )
    }

    @Test
    fun aRedReadingAsksFirstButGreyReadingsDoNot() {
        assertEquals(
            TapAction.ConfirmProblems(listOf("Battery: 7.10 V · needs 7.4 V")),
            tapAction(step("9.11"), ChecklistTicks(), "CR", oneBoard(packet(batteryRaw = 17750u)))
        )
        assertEquals(TapAction.Tick, tapAction(step("9.11"), ChecklistTicks(), "CR", BoardFocus.NoBoard))
        assertEquals(TapAction.Tick, tapAction(step("9.11"), ChecklistTicks(), "CR", oneBoard()))
        assertEquals(TapAction.Tick, tapAction(step("8.1"), ChecklistTicks(), "CR", BoardFocus.NoBoard))
    }

    @Test
    fun aTickStampsInitialsAndTimeAndTheFirstOneStartsTheChecklist() {
        val first = withTick(ChecklistTicks(), "8.1", "CR", nowMillis = 5_000)
        assertEquals(ChecklistTicks(startedAtMillis = 5_000, ticks = mapOf("8.1" to TickRecord("CR", 5_000))), first)
        val second = withTick(first, "8.2", "JM", nowMillis = 9_000)
        assertEquals(5_000L, second.startedAtMillis)
        assertEquals(TickRecord("JM", 9_000), second.ticks["8.2"])
        // Unticking keeps the start time
        assertEquals(
            ChecklistTicks(startedAtMillis = 5_000, ticks = mapOf("8.2" to TickRecord("JM", 9_000))),
            withoutTick(second, "8.1")
        )
    }

    @Test
    fun typedInitialsAreCleanedUp() {
        assertEquals("CR", cleanInitials(" c.r "))
        assertEquals("ABCD", cleanInitials("abcdef"))
        assertEquals("", cleanInitials("12;,"))
    }

    @Test
    fun progressAndTheScanScreenLine() {
        val ticks = ChecklistTicks(startedAtMillis = 1, ticks = mapOf("8.1" to TickRecord("CR", 1), "8.2" to TickRecord("CR", 2)))
        val progress = progress(PREFLIGHT_STEPS, ticks)
        assertEquals(ChecklistProgress(done = 2, total = 29), progress)
        assertEquals("2 of 29 done", progressHeadline(progress))
        assertEquals("2 of 29 done · started 7:15 PM", entryLine(progress, "7:15 PM"))
        assertEquals("Not started · 29 steps", entryLine(ChecklistProgress(done = 0, total = 29), startedAt = null))
    }

    @Test
    fun everythingDone() {
        val all = ChecklistTicks(startedAtMillis = 1, ticks = PREFLIGHT_STEPS.associate { it.id to TickRecord("CR", 1) })
        val done = progress(PREFLIGHT_STEPS, all)
        assertTrue(done.allDone)
        assertEquals("All 29 steps done", progressHeadline(done))
        assertEquals("All 29 steps done", entryLine(done, "7:15 PM"))
    }

    @Test
    fun aChecklistIsOldAfterTwelveHours() {
        val started = 1_000_000L
        assertNull(staleHours(started, started + 12.hours.inWholeMilliseconds))
        assertEquals(12L, staleHours(started, started + (12.hours + 1.minutes).inWholeMilliseconds))
        assertEquals(14L, staleHours(started, started + (14.hours + 20.minutes).inWholeMilliseconds))
        assertNull(staleHours(null, started))
    }

    @Test
    fun ticksSurviveSavingAndLoading() {
        val ticks = ChecklistTicks(
            startedAtMillis = 1_792_721_854_000,
            ticks = mapOf("8.2" to TickRecord("JM", 1_792_722_214_000), "8.1" to TickRecord("CR", 1_792_721_854_000))
        )
        val text = encodeTicks(ticks)
        assertEquals("v2;started=1792721854000;tick=8.1,CR,1792721854000;tick=8.2,JM,1792722214000", text)
        assertEquals(ticks, decodeTicks(text, allIds))
        assertEquals(ChecklistTicks(), decodeTicks(encodeTicks(ChecklistTicks()), allIds))
    }

    @Test
    fun unreadableOldOrUnknownSavedDataIsIgnored() {
        assertEquals(ChecklistTicks(), decodeTicks(null, allIds))
        assertEquals(ChecklistTicks(), decodeTicks("not a checklist", allIds))
        // The first draft's format
        assertEquals(ChecklistTicks(), decodeTicks("v1;started=5;manual=on-rail;hand=battery", allIds))
        // A removed step, a missing time, missing initials and a bad time are all skipped
        assertEquals(
            ChecklistTicks(startedAtMillis = 5, ticks = mapOf("8.1" to TickRecord("CR", 6))),
            decodeTicks("v2;started=5;tick=8.1,CR,6;tick=removed-step,CR,7;tick=8.2,CR;tick=8.3,,8;tick=8.4,CR,soon", allIds)
        )
    }

    @Test
    fun numbersAreFormattedWithFixedDecimals() {
        assertEquals("7.10", formatFixed(7.1, 2))
        assertEquals("0.4", formatFixed(0.44, 1))
        assertEquals("12", formatFixed(12.2, 0))
        assertEquals("-1.5", formatFixed(-1.5, 1))
    }
}
