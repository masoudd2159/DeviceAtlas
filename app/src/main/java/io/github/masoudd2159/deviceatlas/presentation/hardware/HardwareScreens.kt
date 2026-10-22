package io.github.masoudd2159.deviceatlas.presentation.hardware

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import io.github.masoudd2159.deviceatlas.R
import io.github.masoudd2159.deviceatlas.foundation.hardware.*
import io.github.masoudd2159.deviceatlas.foundation.hardware.HardwareValue.*
import io.github.masoudd2159.deviceatlas.presentation.components.*
import io.github.masoudd2159.deviceatlas.presentation.theme.FoundationSpacing
import java.text.DateFormat
import java.text.NumberFormat
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Date

@Composable
internal fun number(value: Number): String {
    val locale = LocalConfiguration.current.locales[0]
    val small = value.toDouble() != 0.0 && kotlin.math.abs(value.toDouble()) < 0.01
    val formatter = remember(locale, small) {
        if (small) DecimalFormat("0.###E0", DecimalFormatSymbols(locale))
        else NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2 }
    }
    return formatter.format(value)
}

@Composable
fun HardwareRefresh(state: HardwareUiState, refresh: () -> Unit) {
    Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        TextButton(onClick = refresh, enabled = !state.refreshing, modifier = Modifier.testTag("hardware_refresh")) {
            Text(stringResource(R.string.hw_refresh))
        }
        if (state.refreshing) Text(stringResource(R.string.hw_refreshing))
        if (state.failed) Text(stringResource(R.string.hw_refresh_failed), color = MaterialTheme.colorScheme.error)
        val snapshot = state.snapshot
        if (snapshot == null) Text(stringResource(R.string.hw_loading))
        else {
            val locale = LocalConfiguration.current.locales[0]
            val timestamp = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM, locale).format(Date(snapshot.capturedAtMillis))
            Text(stringResource(R.string.hw_captured, timestamp), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DatumRows(label: String, value: HardwareValue<HardwareDatum>) {
    when (value) {
        is Unavailable -> InformationRow(label, stringResource(reasonLabel(value.reason)))
        is Available -> when (val datum = value.value) {
            is HardwareDatum.Text -> InformationRow(label, datum.text, technical = true)
            is HardwareDatum.Flag -> InformationRow(label, stringResource(if (datum.enabled) R.string.hw_yes else R.string.hw_no))
            is HardwareDatum.Category -> InformationRow(label, stringResource(categoryLabel(datum.category)))
            is HardwareDatum.Numeric -> InformationRow(label, stringResource(R.string.hw_reason_native_failed))
            is HardwareDatum.Number -> InformationRow(label,
                if (datum.unit == UnitKind.NONE) number(datum.number)
                else stringResource(R.string.hw_number_unit, number(datum.number), stringResource(unitLabel(datum.unit))))
            is HardwareDatum.DisplayMode -> InformationRow(label, stringResource(R.string.hw_pixels_ratio,
                number(datum.width), number(datum.height), number(datum.ratioWidth), number(datum.ratioHeight)))
            is HardwareDatum.Capacity -> {
                InformationRow(label, stringResource(R.string.hw_total, number(datum.totalGiB)))
                Text(stringResource(R.string.hw_available, number(datum.availableGiB), percent(datum.availablePercent)))
                Text(stringResource(R.string.hw_unavailable_capacity, number(datum.unavailableGiB), percent(datum.unavailablePercent)))
            }
        }
    }
}

@Composable
private fun percent(value: Double): String {
    val locale = LocalConfiguration.current.locales[0]
    // Presentation-only scaling for the locale formatter; Rust owns the ratio.
    return NumberFormat.getPercentInstance(locale).apply { maximumFractionDigits = 1 }.format(value / 100.0)
}

@Composable
private fun FieldRow(snapshot: HardwareSnapshot, key: HardwareField) {
    Column(Modifier.testTag("hardware_${key.name}"), verticalArrangement = Arrangement.spacedBy(FoundationSpacing.small)) {
        DatumRows(stringResource(fieldLabel(key)), snapshot.fields[key] ?: Unavailable(UnavailableReason.NOT_REPORTED))
    }
}

@Composable
fun HardwareSummary(state: HardwareUiState) {
    val snapshot = state.snapshot ?: return
    InformationCard {
        SectionHeader(stringResource(R.string.device_summary))
        listOf(HardwareField.MANUFACTURER, HardwareField.MODEL, HardwareField.RELEASE,
            HardwareField.PRIMARY_ABI, HardwareField.MEMORY, HardwareField.STORAGE,
            HardwareField.BATTERY_LEVEL, HardwareField.BATTERY_STATUS, HardwareField.DISPLAY_MODE).forEach { FieldRow(snapshot, it) }
        SensorCount(snapshot.sensors)
    }
}

@Composable
fun HardwareDeviceScreen(state: HardwareUiState, refresh: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = FoundationSpacing.maxContentWidth).fillMaxWidth().testTag("hardware_device_list"),
            contentPadding = PaddingValues(FoundationSpacing.screen), verticalArrangement = Arrangement.spacedBy(FoundationSpacing.content)) {
            item { Text(stringResource(R.string.device_description)); HardwareRefresh(state, refresh) }
            state.snapshot?.let { snapshot ->
                items(Section.entries, key = { it.name }) { section ->
                    InformationCard {
                        SectionHeader(stringResource(sectionLabel(section)))
                        when (section) {
                            Section.MEMORY -> Text(stringResource(R.string.hw_memory_note))
                            Section.STORAGE -> Text(stringResource(R.string.hw_storage_note))
                            Section.DISPLAY -> Text(stringResource(R.string.hw_display_note))
                            else -> Unit
                        }
                        HardwareField.entries.filter { it.section == section }.forEach { FieldRow(snapshot, it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SensorCount(sensors: HardwareValue<List<SensorCapability>>) {
    Text(when (sensors) {
        is Unavailable -> stringResource(reasonLabel(sensors.reason))
        is Available -> stringResource(R.string.hw_sensor_count, number(sensors.value.size))
    })
}

@Composable
fun HardwareSensorsScreen(state: HardwareUiState, refresh: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(Modifier.widthIn(max = FoundationSpacing.maxContentWidth).fillMaxWidth().testTag("hardware_sensor_list"),
            contentPadding = PaddingValues(FoundationSpacing.screen), verticalArrangement = Arrangement.spacedBy(FoundationSpacing.content)) {
            item { Text(stringResource(R.string.sensors_description)); HardwareRefresh(state, refresh) }
            val inventory = state.snapshot?.sensors
            if (inventory != null) item { SensorCount(inventory) }
            if (inventory is Available) {
                if (inventory.value.isEmpty()) item { Text(stringResource(R.string.hw_sensors_empty)) }
                inventory.value.groupBy { it.type }.toSortedMap().forEach { (type, sensors) ->
                    item(key = "group_$type") { SectionHeader(sensorType(type)) }
                    items(sensors, key = { "sensor_${it.index}" }) { sensor -> SensorCard(sensor) }
                }
            }
        }
    }
}

@Composable
private fun sensorType(type: Int): String = sensorTypeLabel(type)?.let { stringResource(it) }
    ?: stringResource(R.string.hw_sensor_other, type)

@Composable
private fun SensorCard(sensor: SensorCapability) {
    var expanded by rememberSaveable(sensor.type, sensor.index) { mutableStateOf(false) }
    InformationCard {
        val name = sensor.fields[SensorField.NAME] ?: Unavailable(UnavailableReason.NOT_REPORTED)
        DatumRows(stringResource(R.string.hw_sensor_name), name)
        TextButton(onClick = { expanded = !expanded }) {
            Text(stringResource(if (expanded) R.string.hw_sensor_collapse else R.string.hw_sensor_details))
        }
        if (expanded) {
            Text(stringResource(R.string.hw_sensor_units, sensorUnits(sensor.type)))
            sensor.fields.filterKeys { it != SensorField.NAME }.forEach { (key,value) ->
                if (key == SensorField.REPORTING_MODE && value is Available && value.value is HardwareDatum.Number) {
                    val id = when (value.value.number) {
                        0.0 -> R.string.hw_continuous; 1.0 -> R.string.hw_on_change
                        2.0 -> R.string.hw_one_shot; 3.0 -> R.string.hw_special
                        else -> R.string.hw_reason_not_reported
                    }
                    InformationRow(stringResource(sensorLabel(key)), stringResource(id))
                } else DatumRows(stringResource(sensorLabel(key)), value)
            }
        }
    }
}

@Composable
private fun sensorUnits(type: Int): String = stringResource(when (type) {
    1, 9, 10, 35 -> R.string.hw_acceleration_unit
    2, 14 -> R.string.hw_magnetic_unit
    3, 36 -> R.string.hw_angle_unit
    4, 16 -> R.string.hw_rotation_unit
    5 -> R.string.hw_light_unit
    6 -> R.string.hw_pressure_unit
    7, 13 -> R.string.hw_unit_celsius
    8 -> R.string.hw_distance_unit
    12 -> R.string.hw_unit_percent
    19 -> R.string.hw_steps_unit
    21 -> R.string.hw_heart_unit
    else -> R.string.hw_type_units
})

@Composable
fun HardwareIntegrity(state: HardwareUiState) {
    val snapshot = state.snapshot ?: return
    val all = snapshot.fields.values + ((snapshot.sensors as? Available)?.value?.flatMap { it.fields.values } ?: emptyList())
    val missing = all.filterIsInstance<Unavailable>()
    InformationCard(Modifier.testTag("hardware_integrity")) {
        SectionHeader(stringResource(R.string.hw_integrity))
        Text(stringResource(R.string.hw_field_counts, number(all.count { it is Available }), number(missing.size)))
        Text(stringResource(R.string.hw_invalid_count, number(missing.count { it.reason == UnavailableReason.INVALID_RESPONSE })))
        Text(stringResource(R.string.hw_native_count, number(missing.count { it.reason in listOf(UnavailableReason.NATIVE_UNAVAILABLE, UnavailableReason.NATIVE_FAILED) })))
        Text(stringResource(R.string.hw_integrity_note))
    }
}
