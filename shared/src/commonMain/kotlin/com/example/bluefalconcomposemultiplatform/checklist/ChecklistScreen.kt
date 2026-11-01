package com.example.bluefalconcomposemultiplatform.checklist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluefalconcomposemultiplatform.ui.theme.AsuGold
import com.example.bluefalconcomposemultiplatform.ui.theme.CardBackground
import com.example.bluefalconcomposemultiplatform.ui.theme.CardBorder
import com.example.bluefalconcomposemultiplatform.ui.theme.CheckGreenText
import com.example.bluefalconcomposemultiplatform.ui.theme.ConnectedGreen
import com.example.bluefalconcomposemultiplatform.ui.theme.OnAsuGold
import com.example.bluefalconcomposemultiplatform.ui.theme.OnCheckGreen
import com.example.bluefalconcomposemultiplatform.ui.theme.ProblemRedText
import com.example.bluefalconcomposemultiplatform.ui.theme.ReadingBlockBackground
import com.example.bluefalconcomposemultiplatform.ui.theme.ReadingBlockBorder
import com.example.bluefalconcomposemultiplatform.ui.theme.RingEmpty
import com.example.bluefalconcomposemultiplatform.ui.theme.RowDivider
import com.example.bluefalconcomposemultiplatform.ui.theme.SignalTrack
import com.example.bluefalconcomposemultiplatform.ui.theme.TextPrimary
import com.example.bluefalconcomposemultiplatform.ui.theme.TextSecondary

private val CardShape = RoundedCornerShape(14.dp)

// The row under the header on the scan screen that opens the checklist
@Composable
fun ChecklistEntryRow(summary: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(CardBackground)
            .border(1.dp, AsuGold.copy(alpha = 0.45f), CardShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(28.dp).background(AsuGold, RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = OnAsuGold, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Pre-flight checklist",
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 19.sp
            )
            Text(summary, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = AsuGold)
    }
}

// The whole checklist: progress and initials at the top, the steps by section, and a way to start again
@Composable
fun ChecklistScreen(
    ticks: ChecklistTicks,
    initials: String,
    focus: BoardFocus,
    // Hours since the start, once the checklist is old enough to suggest a new one
    staleHours: Long?,
    onTick: (ChecklistStep) -> Unit,
    onUntick: (ChecklistStep) -> Unit,
    onSetInitials: (String) -> Unit,
    onStartNew: () -> Unit,
    onBack: () -> Unit
) {
    // Open dialogs. When the initials dialog was opened by tapping a step, that step carries on afterwards.
    var editingInitials by remember { mutableStateOf(false) }
    var stepAfterInitials by remember { mutableStateOf<ChecklistStep?>(null) }
    var confirming by remember { mutableStateOf<Pair<ChecklistStep, List<String>>?>(null) }
    var confirmingStartNew by remember { mutableStateOf(false) }

    // Initials first, then the red-reading question, then the tick
    fun handleTap(step: ChecklistStep, initialsNow: String) {
        when (val action = tapAction(step, ticks, initialsNow, focus)) {
            TapAction.Untick -> onUntick(step)
            TapAction.AskInitials -> {
                stepAfterInitials = step
                editingInitials = true
            }
            is TapAction.ConfirmProblems -> confirming = step to action.problems
            TapAction.Tick -> onTick(step)
        }
    }

    val stepProgress = progress(PREFLIGHT_STEPS, ticks)
    val startedAt = ticks.startedAtMillis?.let(::formatClockTime)

    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Back",
                    tint = AsuGold,
                    modifier = Modifier.size(32.dp)
                )
            }
            Text(
                "Pre-flight checklist",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 24.sp
            )
        }
        ProgressCard(
            progress = stepProgress,
            startedAt = startedAt,
            initials = initials,
            onInitialsClick = {
                stepAfterInitials = null
                editingInitials = true
            },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp)
        ) {
            if (staleHours != null) {
                item { StaleBanner(staleHours, onStartNew = { confirmingStartNew = true }) }
            }
            ChecklistSection.entries.forEach { section ->
                val sectionSteps = PREFLIGHT_STEPS.filter { it.section == section }
                if (sectionSteps.isNotEmpty()) {
                    item { SectionLabel(section.label) }
                    item { SectionCard(sectionSteps, ticks, focus, onStepTap = { step -> handleTap(step, initials) }) }
                }
            }
            item {
                OutlinedButton(
                    onClick = { confirmingStartNew = true },
                    border = BorderStroke(1.dp, TextPrimary.copy(alpha = 0.28f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp)
                ) {
                    Text("Start new checklist", fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (editingInitials) {
        InitialsDialog(
            current = initials,
            onSave = { typed ->
                editingInitials = false
                onSetInitials(typed)
                // Carry on with the step that asked for initials, now that there are some
                stepAfterInitials?.let { step -> handleTap(step, cleanInitials(typed)) }
                stepAfterInitials = null
            },
            onDismiss = {
                editingInitials = false
                stepAfterInitials = null
            }
        )
    }

    confirming?.let { (step, problems) ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text("Tick ${step.id} anyway?", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = { Text(problems.joinToString("\n"), fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = {
                    confirming = null
                    onTick(step)
                }) { Text("Tick anyway") }
            },
            dismissButton = {
                TextButton(onClick = { confirming = null }) { Text("Cancel") }
            }
        )
    }

    if (confirmingStartNew) {
        AlertDialog(
            onDismissRequest = { confirmingStartNew = false },
            title = { Text("Start a new checklist?", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
            text = { Text("This clears every tick, with its initials and time. Your initials stay set.", fontSize = 14.sp) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingStartNew = false
                    onStartNew()
                }) { Text("Start new") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingStartNew = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun ProgressCard(
    progress: ChecklistProgress,
    startedAt: String?,
    initials: String,
    onInitialsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(CardBackground)
            .border(1.dp, CardBorder, CardShape)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    progressHeadline(progress),
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 18.sp
                )
                if (startedAt != null) {
                    Text("started $startedAt", color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
                }
            }
            InitialsChip(initials, onInitialsClick)
        }
        // The bar turns green once every step is done
        val fraction = if (progress.total == 0) 0f else progress.done.toFloat() / progress.total
        Box(
            Modifier
                .padding(top = 8.dp)
                .fillMaxWidth()
                .height(5.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(SignalTrack)
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .fillMaxHeight()
                    .background(if (progress.allDone) ConnectedGreen else AsuGold)
            )
        }
    }
}

@Composable
private fun InitialsChip(initials: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(9.dp)
    Column(
        modifier = Modifier
            .clip(shape)
            .border(1.dp, AsuGold.copy(alpha = 0.55f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 3.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "INITIALS",
            color = TextSecondary,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            lineHeight = 10.sp
        )
        Text(
            initials.ifEmpty { "—" },
            color = AsuGold,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp,
            lineHeight = 18.sp
        )
    }
}

@Composable
private fun StaleBanner(hours: Long, onStartNew: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(CardShape)
            .background(CardBackground)
            .border(1.dp, AsuGold.copy(alpha = 0.45f), CardShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            "Started $hours h ago. Start a new checklist?",
            color = TextPrimary,
            fontSize = 13.sp,
            lineHeight = 17.sp,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Button(
            onClick = onStartNew,
            colors = ButtonDefaults.buttonColors(containerColor = AsuGold, contentColor = OnAsuGold),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text("Start new", fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SectionLabel(label: String) {
    Text(
        label,
        color = AsuGold,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.5.sp,
        lineHeight = 14.sp,
        modifier = Modifier.padding(start = 2.dp, top = 14.dp, bottom = 6.dp)
    )
}

// One section's steps in a card
@Composable
private fun SectionCard(
    steps: List<ChecklistStep>,
    ticks: ChecklistTicks,
    focus: BoardFocus,
    onStepTap: (ChecklistStep) -> Unit
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(CardBackground)
            .border(1.dp, CardBorder, CardShape)
    ) {
        steps.forEachIndexed { index, step ->
            if (index > 0) HorizontalDivider(color = RowDivider)
            StepRow(step, ticks.ticks[step.id], focus, onClick = { onStepTap(step) })
        }
    }
}

@Composable
private fun StepRow(step: ChecklistStep, tick: TickRecord?, focus: BoardFocus, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.Top
    ) {
        StepMark(ticked = tick != null)
        Spacer(Modifier.width(8.dp))
        Text(
            "${step.number}.",
            color = TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            lineHeight = 18.sp,
            textAlign = TextAlign.End,
            modifier = Modifier.width(24.dp)
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(step.text, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp)
            if (tick != null) {
                Text(
                    "${tick.initials} · ${formatClockTime(tick.atMillis)}",
                    color = CheckGreenText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            if (step.readings.isNotEmpty()) {
                ReadingBlock(step.readings, focus, Modifier.padding(top = 8.dp))
            }
        }
    }
}

// The circle on the left of each step. An icon rather than a text glyph: the theme's 24 sp line height
// pushes small text out of a small circle.
@Composable
private fun StepMark(ticked: Boolean) {
    if (ticked) {
        Box(Modifier.size(20.dp).background(ConnectedGreen, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, contentDescription = "Ticked", tint = OnCheckGreen, modifier = Modifier.size(14.dp))
        }
    } else {
        Box(Modifier.size(20.dp).border(2.dp, RingEmpty, CircleShape))
    }
}

// Live readings under a step: which board and how fresh its data is, then one row per reading
@Composable
private fun ReadingBlock(readings: List<BoardReading>, focus: BoardFocus, modifier: Modifier = Modifier) {
    val line = boardLine(focus)
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(ReadingBlockBackground)
            .border(1.dp, ReadingBlockBorder, shape)
            .padding(horizontal = 9.dp, vertical = 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 4.dp)) {
            if (line.fresh) {
                Box(Modifier.size(7.dp).background(ConnectedGreen, CircleShape))
                Spacer(Modifier.width(6.dp))
            }
            Text(
                buildAnnotatedString {
                    line.name?.let { name ->
                        withStyle(SpanStyle(color = TextPrimary, fontWeight = FontWeight.Bold)) { append(name) }
                        append(" · ")
                    }
                    append(line.status)
                },
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
        }
        HorizontalDivider(color = RowDivider)
        readings.forEach { reading ->
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(
                    reading.label,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                when (val value = readingValue(reading, focus)) {
                    ReadingValue.NoData ->
                        Text("—", color = TextSecondary.copy(alpha = 0.7f), fontSize = 12.sp, lineHeight = 16.sp)
                    is ReadingValue.Fine ->
                        Text(value.text, color = CheckGreenText, fontSize = 12.sp, lineHeight = 16.sp)
                    is ReadingValue.Problem ->
                        Text(
                            value.text,
                            color = ProblemRedText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 16.sp
                        )
                }
            }
        }
    }
}

@Composable
private fun InitialsDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var typed by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Your initials", fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Saved with each step you tick.", fontSize = 14.sp)
                OutlinedTextField(
                    value = typed,
                    // Letters only, in capitals, at most four, as they're typed
                    onValueChange = { typed = cleanInitials(it) },
                    singleLine = true,
                    placeholder = { Text("e.g. CR") },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        autoCorrect = false
                    ),
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(typed) }, enabled = typed.isNotEmpty()) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
