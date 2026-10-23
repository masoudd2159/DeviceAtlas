package io.github.masoudd2159.deviceatlas.presentation.interactive

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.masoudd2159.deviceatlas.R
import io.github.masoudd2159.deviceatlas.foundation.interactive.*
import io.github.masoudd2159.deviceatlas.presentation.components.*
import io.github.masoudd2159.deviceatlas.presentation.hardware.number

@Composable
fun InteractiveVisibility(session: InteractiveSession, visible: Boolean) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, session, visible) {
        fun update(event: Lifecycle.Event? = null) = session.setEligibility(visible,
            owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED), event == Lifecycle.Event.ON_DESTROY)
        val observer = LifecycleEventObserver { _, event -> update(event) }
        owner.lifecycle.addObserver(observer); update()
        onDispose { owner.lifecycle.removeObserver(observer); session.setEligibility(false, false) }
    }
}

@Composable
fun InteractivePanel(state: InteractiveState, session: InteractiveSession?) {
    val running = state.status == InteractiveStatus.Running
    val active = running || state.status == InteractiveStatus.Preparing
    val focus = remember { FocusRequester() }
    var wasActive by remember { mutableStateOf(false) }
    LaunchedEffect(active) { if (wasActive && !active) focus.requestFocus(); wasActive = active }
    SectionHeader(stringResource(R.string.interactive_title))
    Text(stringResource(R.string.interactive_privacy))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        InteractiveKind.entries.forEach { kind ->
            FilterChip(state.kind == kind, { session?.select(kind) }, label = { Text(stringResource(kind.label())) },
                modifier = Modifier.testTag("interactive_${kind.name}"))
        }
    }
    InformationCard {
        SectionHeader(stringResource(state.kind.label()))
        Text(stringResource(when (state.kind) {
            InteractiveKind.DISPLAY -> R.string.interactive_display_help
            InteractiveKind.TOUCH -> R.string.interactive_touch_help
            InteractiveKind.SENSOR -> R.string.interactive_sensor_help
            InteractiveKind.HAPTIC -> R.string.interactive_haptic_help
        }))
        Text(statusText(state.status), Modifier.semantics { liveRegion = LiveRegionMode.Polite }.testTag("interactive_status"))
        if (state.retiring) Text(stringResource(R.string.interactive_retiring))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ session?.start() }, enabled = state.canStart, modifier = Modifier.focusRequester(focus).testTag("interactive_start")) {
                Text(stringResource(if (state.kind == InteractiveKind.SENSOR) R.string.interactive_start_listening else R.string.interactive_start))
            }
            OutlinedButton({ session?.stop() }, enabled = active, modifier = Modifier.testTag("interactive_exit")) {
                Text(stringResource(if (state.kind == InteractiveKind.SENSOR) R.string.interactive_stop_listening else R.string.interactive_exit))
            }
            TextButton({ session?.stop(); session?.observe(ManualObservation.UNABLE) }) { Text(stringResource(R.string.interactive_skip)) }
        }
        when (state.kind) {
            InteractiveKind.DISPLAY -> if (running) {
                Text(stringResource(state.pattern.label()), Modifier.testTag("display_pattern"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ session?.pattern(-1) }, modifier = Modifier.testTag("display_previous")) { Text(stringResource(R.string.interactive_previous)) }
                    OutlinedButton({ session?.pattern(1) }, modifier = Modifier.testTag("display_next")) { Text(stringResource(R.string.interactive_next)) }
                }
                DisplaySurface(state.pattern)
            }
            InteractiveKind.TOUCH -> {
                TouchSummary(state.touch)
                if (state.touch.geometryReset) Text(stringResource(R.string.interactive_geometry_reset))
                OutlinedButton({ session?.clearTouch() }, modifier = Modifier.testTag("touch_clear")) { Text(stringResource(R.string.interactive_clear)) }
                if (running) TouchSurface(state.touch, session)
            }
            InteractiveKind.SENSOR -> SensorPanel(state, session)
            InteractiveKind.HAPTIC -> {
                Button({ session?.pulse() }, enabled = running && state.pulseAllowed, modifier = Modifier.testTag("haptic_pulse")) {
                    Text(stringResource(R.string.interactive_pulse))
                }
                if (state.pulseRequested) Text(stringResource(R.string.interactive_pulse_requested))
                if (!state.pulseAllowed) Text(stringResource(R.string.interactive_cooldown))
            }
        }
        SectionHeader(stringResource(R.string.interactive_manual))
        ManualObservation.entries.forEach { observation ->
            val selected = state.results.getValue(state.kind).observation == observation
            FilterChip(selected, { session?.observe(observation) }, label = {
                Text(observationText(observation, state.kind == InteractiveKind.HAPTIC))
            })
        }
    }
    InformationCard {
        SectionHeader(stringResource(R.string.interactive_summary))
        InteractiveKind.entries.forEach { kind ->
            val result = state.results.getValue(kind)
            InformationRow(stringResource(kind.label()), statusText(result.status))
            if (kind == InteractiveKind.TOUCH && result.status != InteractiveStatus.Idle) {
                Text(stringResource(R.string.interactive_coverage, number(result.visitedCells), number(result.totalCells),
                    number(result.visitedCells * 100.0 / result.totalCells)))
                Text(stringResource(R.string.interactive_max_contacts, number(result.maximumContacts)))
            }
            if (kind == InteractiveKind.SENSOR && result.status != InteractiveStatus.Idle) {
                result.sensor?.let { Text(stringResource(it.label())) }
                Text(if (result.summary == null) stringResource(R.string.interactive_no_reading)
                    else stringResource(R.string.interactive_valid, number(result.summary.count)))
            }
            if (kind == InteractiveKind.HAPTIC && result.pulseRequested) Text(stringResource(R.string.interactive_pulse_requested))
            Text(observationText(result.observation, kind == InteractiveKind.HAPTIC))
        }
    }
}

@Composable
private fun DisplaySurface(pattern: DisplayPattern) {
    val description = stringResource(pattern.label())
    Canvas(Modifier.fillMaxWidth().height(360.dp).testTag("display_surface").semantics { contentDescription = description }) {
        when (pattern) {
            DisplayPattern.GRADIENT -> drawRect(Brush.horizontalGradient(listOf(Color.Black, Color.White)))
            DisplayPattern.GRID -> {
                drawRect(Color.White)
                for (x in 0..7) for (y in 0..11) if ((x + y) % 2 == 0)
                    drawRect(Color.Black, Offset(x * size.width / 8, y * size.height / 12), Size(size.width / 8, size.height / 12))
            }
            else -> drawRect(when (pattern) {
                DisplayPattern.RED -> Color.Red; DisplayPattern.GREEN -> Color.Green; DisplayPattern.BLUE -> Color.Blue
                DisplayPattern.WHITE -> Color.White; DisplayPattern.BLACK -> Color.Black; else -> Color(0xFF808080)
            })
        }
    }
}

@Composable
private fun TouchSummary(touch: TouchCoverage) {
    Text(stringResource(R.string.interactive_coverage, number(touch.visited.size), number(touch.cells), number(touch.visited.size * 100.0 / touch.cells)),
        Modifier.testTag("touch_coverage"))
    Text(stringResource(R.string.interactive_active_contacts, number(touch.activeCount)))
    Text(stringResource(R.string.interactive_max_contacts, number(touch.maximumContacts)), Modifier.testTag("touch_maximum"))
}

@Composable
private fun TouchSurface(touch: TouchCoverage, session: InteractiveSession?) {
    val description = stringResource(R.string.interactive_touch_surface)
    val primary = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.surface
    val outline = MaterialTheme.colorScheme.onSurface
    Canvas(Modifier.fillMaxWidth().height(420.dp).testTag("touch_surface")
        .semantics { contentDescription = description }
        .onSizeChanged { session?.resizeTouch(it.width, it.height) }
        .pointerInput(session) {
            try {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        session?.contacts(event.changes.filter { it.pressed }.map { Contact(it.id.value, it.position.x, it.position.y) })
                        event.changes.forEach { it.consume() }
                    }
                }
            } finally { session?.cancelContacts() }
        }) {
        drawRect(background)
        val cell = Size(size.width / touch.columns, size.height / touch.rows)
        for (index in 0 until touch.cells) {
            val origin = Offset(index % touch.columns * cell.width, index / touch.columns * cell.height)
            if (index in touch.visited) {
                drawRect(primary.copy(alpha = 0.3f), origin, cell)
                // A check-shaped stroke distinguishes observed cells without relying on color.
                drawLine(outline, origin + Offset(cell.width * .2f, cell.height * .5f), origin + Offset(cell.width * .45f, cell.height * .7f), 2.dp.toPx())
                drawLine(outline, origin + Offset(cell.width * .45f, cell.height * .7f), origin + Offset(cell.width * .8f, cell.height * .25f), 2.dp.toPx())
            }
            drawRect(outline, origin, cell, style = Stroke(1.dp.toPx()))
        }
        touch.contacts.forEach { drawCircle(outline, 12.dp.toPx(), Offset(it.x, it.y), style = Stroke(3.dp.toPx())) }
    }
}

@Composable
private fun SensorPanel(state: InteractiveState, session: InteractiveSession?) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SensorKind.entries.forEach { sensor ->
            FilterChip(state.sensor == sensor, { session?.selectSensor(sensor) }, label = { Text(stringResource(sensor.label())) },
                modifier = Modifier.testTag("sensor_${sensor.name}"))
        }
    }
    Text(stringResource(R.string.interactive_sensor_limits))
    val unit = state.sensor.unit
    state.sensorInfo?.maximumRange?.let { Text(stringResource(R.string.interactive_sensor_range, number(it), unit)) }
    state.reading?.let { reading ->
        val axes = listOf("X", "Y", "Z")
        reading.values.forEachIndexed { index, value -> InformationRow(if (state.sensor.dimensions == 3) axes[index] else stringResource(R.string.interactive_value),
            "${number(value)} $unit", technical = true) }
        Text(stringResource(R.string.interactive_age, number(state.ageMillis ?: 0)))
    }
    state.summary?.let { summary ->
        Text(stringResource(R.string.interactive_valid, number(summary.count)))
        Text(stringResource(if (state.sensor.dimensions == 3) R.string.interactive_magnitude else R.string.interactive_scalar))
        InformationRow(stringResource(R.string.interactive_statistics),
            stringResource(R.string.interactive_statistics_values, number(summary.minimum), number(summary.maximum), number(summary.mean), number(summary.range), unit), technical = true)
    } ?: Text(stringResource(R.string.interactive_no_reading))
    if (state.rejectedSamples > 0) Text(stringResource(R.string.interactive_rejected, number(state.rejectedSamples)))
}

internal fun InteractiveKind.label(): Int = when (this) {
    InteractiveKind.DISPLAY -> R.string.interactive_display; InteractiveKind.TOUCH -> R.string.interactive_touch
    InteractiveKind.SENSOR -> R.string.interactive_sensor; InteractiveKind.HAPTIC -> R.string.interactive_haptic
}
internal fun SensorKind.label(): Int = when (this) {
    SensorKind.ACCELEROMETER -> R.string.interactive_accelerometer; SensorKind.GYROSCOPE -> R.string.interactive_gyroscope
    SensorKind.LIGHT -> R.string.interactive_light; SensorKind.PROXIMITY -> R.string.interactive_proximity
}
internal fun DisplayPattern.label(): Int = when (this) {
    DisplayPattern.RED -> R.string.interactive_red; DisplayPattern.GREEN -> R.string.interactive_green; DisplayPattern.BLUE -> R.string.interactive_blue
    DisplayPattern.WHITE -> R.string.interactive_white; DisplayPattern.BLACK -> R.string.interactive_black; DisplayPattern.GRAY -> R.string.interactive_gray
    DisplayPattern.GRADIENT -> R.string.interactive_gradient; DisplayPattern.GRID -> R.string.interactive_grid
}
@Composable
private fun observationText(observation: ManualObservation, haptic: Boolean): String = stringResource(when (observation) {
    ManualObservation.NOT_TESTED -> R.string.interactive_not_tested
    ManualObservation.NO_ISSUE -> if (haptic) R.string.interactive_felt else R.string.interactive_no_issue
    ManualObservation.POSSIBLE_ISSUE -> if (haptic) R.string.interactive_not_felt else R.string.interactive_possible_issue
    ManualObservation.UNSURE -> R.string.interactive_unsure; ManualObservation.UNABLE -> R.string.interactive_unable
})
@Composable
private fun statusText(status: InteractiveStatus): String = stringResource(when (status) {
    InteractiveStatus.Idle -> R.string.interactive_not_tested
    InteractiveStatus.Preparing -> R.string.interactive_preparing
    InteractiveStatus.Running -> R.string.interactive_running
    InteractiveStatus.Completed -> R.string.interactive_completed
    is InteractiveStatus.Stopped -> when (status.reason) {
        InteractiveStop.USER -> R.string.interactive_stopped; InteractiveStop.HIDDEN -> R.string.interactive_hidden
        InteractiveStop.BACKGROUND -> R.string.interactive_background; InteractiveStop.DESTROYED -> R.string.interactive_destroyed
        InteractiveStop.TIMEOUT -> R.string.interactive_timeout; InteractiveStop.CONFLICT -> R.string.interactive_conflict
    }
    is InteractiveStatus.Unavailable -> when (status.reason) {
        InteractiveUnavailable.NOT_PRESENT -> R.string.interactive_absent; InteractiveUnavailable.REGISTRATION_FAILED -> R.string.interactive_registration
        InteractiveUnavailable.NATIVE -> R.string.interactive_native; InteractiveUnavailable.INVALID_DATA -> R.string.interactive_invalid
        InteractiveUnavailable.PLATFORM -> R.string.interactive_unavailable
    }
})
