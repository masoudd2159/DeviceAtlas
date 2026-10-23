package io.github.masoudd2159.deviceatlas.data.hardware

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.os.StatFs
import android.view.Display
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.masoudd2159.deviceatlas.di.HardwareIoDispatcher
import io.github.masoudd2159.deviceatlas.foundation.hardware.*
import io.github.masoudd2159.deviceatlas.foundation.hardware.HardwareField.*
import io.github.masoudd2159.deviceatlas.foundation.hardware.HardwareValue.*
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

/** Public API snapshots only. No receivers are registered, no listeners, paths or identifiers escape. */
@Singleton
class AndroidHardwareCollector @Inject constructor(
    @ApplicationContext private val context: Context,
    @HardwareIoDispatcher private val dispatcher: CoroutineDispatcher,
) : HardwareCollector {
    private val session = mutableMapOf<HardwareField, HardwareValue<HardwareDatum>>()

    override suspend fun collect(): HardwareSnapshot = withContext(dispatcher) {
        val fields = linkedMapOf<HardwareField, HardwareValue<HardwareDatum>>()
        fun put(key: HardwareField, block: () -> HardwareValue<HardwareDatum>) {
            val cached = session[key]
            val value = cached ?: collectSafely(block)
            fields[key] = value
            if (key.freshness == Freshness.SESSION && (value is Available ||
                    value == Unavailable(UnavailableReason.UNSUPPORTED))) session[key] = value
        }
        fun text(key: HardwareField, value: () -> String?) = put(key) { platformText(value()) }
        text(MANUFACTURER) { Build.MANUFACTURER }; text(BRAND) { Build.BRAND }
        text(MODEL) { Build.MODEL }; text(DEVICE) { Build.DEVICE }; text(PRODUCT) { Build.PRODUCT }
        text(HARDWARE) { Build.HARDWARE }; text(BOARD) { Build.BOARD }
        text(RELEASE) { Build.VERSION.RELEASE }; text(BUILD_ID) { Build.ID }
        text(SECURITY_PATCH) { Build.VERSION.SECURITY_PATCH }
        put(SDK) { numeric(MetricOperation.POSITIVE_INTEGER, Build.VERSION.SDK_INT.toLong()) }
        put(SOC_MANUFACTURER) { if (Build.VERSION.SDK_INT >= 31) platformText(Build.SOC_MANUFACTURER) else Unavailable(UnavailableReason.UNSUPPORTED) }
        put(SOC_MODEL) { if (Build.VERSION.SDK_INT >= 31) platformText(Build.SOC_MODEL) else Unavailable(UnavailableReason.UNSUPPORTED) }
        text(ABIS) { Build.SUPPORTED_ABIS.joinToString(", ") }
        put(ABIS_32) { if (Build.SUPPORTED_32_BIT_ABIS.isEmpty()) Unavailable(UnavailableReason.NOT_PRESENT) else platformText(Build.SUPPORTED_32_BIT_ABIS.joinToString(", ")) }
        put(ABIS_64) { if (Build.SUPPORTED_64_BIT_ABIS.isEmpty()) Unavailable(UnavailableReason.NOT_PRESENT) else platformText(Build.SUPPORTED_64_BIT_ABIS.joinToString(", ")) }
        text(PRIMARY_ABI) { Build.SUPPORTED_ABIS.firstOrNull() }
        put(PROCESSORS) { numeric(MetricOperation.POSITIVE_INTEGER, Runtime.getRuntime().availableProcessors().toLong()) }

        val memory = collectSafely {
            val service = context.getSystemService(ActivityManager::class.java)
            if (service == null) Unavailable(UnavailableReason.TEMPORARY)
            else Available(ActivityManager.MemoryInfo().also(service::getMemoryInfo))
        }
        put(MEMORY) { memory.map { numeric(MetricOperation.CAPACITY, it.totalMem, it.availMem) } }
        put(LOW_MEMORY) { memory.map { Available(HardwareDatum.Flag(it.lowMemory)) } }
        put(MEMORY_THRESHOLD) { memory.map { numeric(MetricOperation.BYTES, it.threshold, unit = UnitKind.GIB) } }
        put(STORAGE) {
            val fs = StatFs(context.dataDir.absolutePath)
            numeric(MetricOperation.CAPACITY, fs.totalBytes, fs.availableBytes)
        }

        val battery = collectSafely {
            val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            if (intent == null) Unavailable(UnavailableReason.TEMPORARY) else Available(intent)
        }
        fun extra(intent: Intent, name: String): Int? = if (intent.hasExtra(name)) intent.getIntExtra(name, Int.MIN_VALUE) else null
        fun batteryValue(key: HardwareField, block: (Intent) -> HardwareValue<HardwareDatum>) = put(key) {
            battery.map { intent ->
                if (intent.hasExtra(BatteryManager.EXTRA_PRESENT) && !intent.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false))
                    Unavailable(UnavailableReason.NOT_PRESENT) else block(intent)
            }
        }
        put(BATTERY_PRESENT) { battery.map {
            if (it.hasExtra(BatteryManager.EXTRA_PRESENT)) Available(HardwareDatum.Flag(it.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false)))
            else Unavailable(UnavailableReason.NOT_REPORTED)
        } }
        batteryValue(BATTERY_LEVEL) {
            val level = extra(it, BatteryManager.EXTRA_LEVEL); val scale = extra(it, BatteryManager.EXTRA_SCALE)
            if (level == null || scale == null) Unavailable(UnavailableReason.NOT_REPORTED)
            else numeric(MetricOperation.LEVEL, level.toLong(), scale.toLong(), unit = UnitKind.PERCENT)
        }
        batteryValue(BATTERY_STATUS) { category(extra(it, BatteryManager.EXTRA_STATUS), mapOf(
            BatteryManager.BATTERY_STATUS_CHARGING to HardwareCategory.CHARGING,
            BatteryManager.BATTERY_STATUS_DISCHARGING to HardwareCategory.DISCHARGING,
            BatteryManager.BATTERY_STATUS_FULL to HardwareCategory.FULL,
            BatteryManager.BATTERY_STATUS_NOT_CHARGING to HardwareCategory.NOT_CHARGING)) }
        batteryValue(BATTERY_SOURCE) { category(extra(it, BatteryManager.EXTRA_PLUGGED), mapOf(
            0 to HardwareCategory.UNPLUGGED, BatteryManager.BATTERY_PLUGGED_AC to HardwareCategory.AC,
            BatteryManager.BATTERY_PLUGGED_USB to HardwareCategory.USB,
            BatteryManager.BATTERY_PLUGGED_WIRELESS to HardwareCategory.WIRELESS,
            8 to HardwareCategory.DOCK)) }
        batteryValue(BATTERY_HEALTH) { category(extra(it, BatteryManager.EXTRA_HEALTH), mapOf(
            BatteryManager.BATTERY_HEALTH_GOOD to HardwareCategory.GOOD,
            BatteryManager.BATTERY_HEALTH_OVERHEAT to HardwareCategory.OVERHEAT,
            BatteryManager.BATTERY_HEALTH_DEAD to HardwareCategory.DEAD,
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE to HardwareCategory.OVER_VOLTAGE,
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE to HardwareCategory.FAILURE,
            BatteryManager.BATTERY_HEALTH_COLD to HardwareCategory.COLD)) }
        fun batteryNumber(key: HardwareField, name: String, operation: MetricOperation, unit: UnitKind) = batteryValue(key) {
            extra(it,name)?.let { value -> numeric(operation, value.toLong(), unit = unit) } ?: Unavailable(UnavailableReason.NOT_REPORTED)
        }
        batteryNumber(TEMPERATURE, BatteryManager.EXTRA_TEMPERATURE, MetricOperation.TEMPERATURE, UnitKind.CELSIUS)
        batteryNumber(VOLTAGE, BatteryManager.EXTRA_VOLTAGE, MetricOperation.VOLTAGE, UnitKind.VOLT)
        if (Build.VERSION.SDK_INT >= 34) batteryNumber(CYCLES, BatteryManager.EXTRA_CYCLE_COUNT, MetricOperation.NONNEGATIVE_INTEGER, UnitKind.NONE)
        else put(CYCLES) { Unavailable(UnavailableReason.UNSUPPORTED) }
        fun property(key: HardwareField, id: Int, operation: MetricOperation, unit: UnitKind, long: Boolean = false) = batteryValue(key) {
            val manager = context.getSystemService(BatteryManager::class.java)
            if (manager == null) Unavailable(UnavailableReason.TEMPORARY)
            else {
                // API 26 getIntProperty narrows Long.MIN_VALUE to zero. The public
                // long accessor retains the unsupported sentinel for every property ID.
                val value = manager.getLongProperty(id)
                if (!long && value == Int.MIN_VALUE.toLong()) Unavailable(UnavailableReason.NOT_REPORTED)
                else batteryProperty(value, Long.MIN_VALUE, operation, unit)
            }
        }
        property(CHARGE, BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER, MetricOperation.CHARGE, UnitKind.MILLIAMP_HOUR)
        property(CURRENT, BatteryManager.BATTERY_PROPERTY_CURRENT_NOW, MetricOperation.CURRENT, UnitKind.MILLIAMP)
        property(ENERGY, BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER, MetricOperation.ENERGY, UnitKind.WATT_HOUR, true)

        val display = collectSafely {
            context.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)?.let { Available(it) }
                ?: Unavailable(UnavailableReason.NOT_PRESENT)
        }
        val mode = collectSafely {
            when (display) {
                is Available -> Available(display.value.mode)
                is Unavailable -> display
            }
        }
        put(DISPLAY_MODE) { mode.map { numeric(MetricOperation.DISPLAY, it.physicalWidth.toLong(), it.physicalHeight.toLong()) } }
        put(REFRESH_RATE) { mode.map { numeric(MetricOperation.POSITIVE_REAL, real = it.refreshRate.toDouble(), unit = UnitKind.HERTZ) } }
        put(DENSITY) { numeric(MetricOperation.POSITIVE_INTEGER, context.resources.displayMetrics.densityDpi.toLong(), unit = UnitKind.DPI) }
        @Suppress("DEPRECATION") // HDR capabilities remain the public API on our API 26 baseline.
        fun hdr(): HardwareValue<HardwareDatum> = display.map {
            it.hdrCapabilities?.let { hdr -> Available(HardwareDatum.Flag(hdr.supportedHdrTypes.isNotEmpty())) }
                ?: Unavailable(UnavailableReason.NOT_REPORTED)
        }
        put(HDR, ::hdr)
        text(GLES) {
            val info = context.getSystemService(ActivityManager::class.java)?.deviceConfigurationInfo
            if (info == null || info.reqGlEsVersion == 0) null else info.glEsVersion
        }
        put(VULKAN) {
            val feature = context.packageManager.systemAvailableFeatures.firstOrNull { it.name == PackageManager.FEATURE_VULKAN_HARDWARE_VERSION }
            when {
                feature == null -> Unavailable(UnavailableReason.NOT_PRESENT)
                feature.version <= 0 -> Unavailable(UnavailableReason.NOT_REPORTED)
                else -> platformText("${feature.version ushr 22}.${(feature.version ushr 12) and 0x3ff}.${feature.version and 0xfff}")
            }
        }
        put(GPU_MODEL) { Unavailable(UnavailableReason.UNSUPPORTED) }
        HardwareSnapshot(immutableMap(fields), sensors(), System.currentTimeMillis())
    }

    private fun sensors(): HardwareValue<List<SensorCapability>> = collectSafely {
        val manager = context.getSystemService(SensorManager::class.java)
            ?: return@collectSafely Unavailable(UnavailableReason.TEMPORARY)
        Available(immutableList(manager.getSensorList(Sensor.TYPE_ALL).mapIndexed { index, sensor ->
            val fields = linkedMapOf<SensorField, HardwareValue<HardwareDatum>>()
            fun put(key: SensorField, block: () -> HardwareValue<HardwareDatum>) { fields[key] = collectSafely(block) }
            put(SensorField.NAME) { platformText(sensor.name) }; put(SensorField.VENDOR) { platformText(sensor.vendor) }
            put(SensorField.VERSION) { numeric(MetricOperation.NONNEGATIVE_INTEGER, sensor.version.toLong()) }
            put(SensorField.POWER) { numeric(MetricOperation.NONNEGATIVE_REAL, real = sensor.power.toDouble(), unit = UnitKind.MILLIAMP) }
            // Range/resolution units depend on type. UI names documented units or explicitly says type-specific units.
            val knownUnits = sensor.type in setOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 13, 14, 16, 19, 21, 35, 36)
            put(SensorField.RESOLUTION) { if (knownUnits) numeric(MetricOperation.NONNEGATIVE_REAL, real = sensor.resolution.toDouble()) else Unavailable(UnavailableReason.UNSUPPORTED) }
            put(SensorField.RANGE) { if (knownUnits) numeric(MetricOperation.NONNEGATIVE_REAL, real = sensor.maximumRange.toDouble()) else Unavailable(UnavailableReason.UNSUPPORTED) }
            put(SensorField.WAKE_UP) { Available(HardwareDatum.Flag(sensor.isWakeUpSensor)) }
            put(SensorField.REPORTING_MODE) { numeric(MetricOperation.NONNEGATIVE_INTEGER, sensor.reportingMode.toLong()) }
            val streams = sensor.reportingMode == Sensor.REPORTING_MODE_CONTINUOUS || sensor.reportingMode == Sensor.REPORTING_MODE_ON_CHANGE
            put(SensorField.MIN_DELAY) { if (streams) numeric(MetricOperation.NONNEGATIVE_INTEGER, sensor.minDelay.toLong(), unit = UnitKind.MICROSECOND) else Unavailable(UnavailableReason.UNSUPPORTED) }
            put(SensorField.MAX_DELAY) { if (streams) numeric(MetricOperation.NONNEGATIVE_INTEGER, sensor.maxDelay.toLong(), unit = UnitKind.MICROSECOND) else Unavailable(UnavailableReason.UNSUPPORTED) }
            SensorCapability(index, sensor.type, immutableMap(fields))
        }))
    }
}

private inline fun <T> HardwareValue<T>.map(block: (T) -> HardwareValue<HardwareDatum>): HardwareValue<HardwareDatum> = when (this) {
    is Available -> block(value)
    is Unavailable -> this
}

private fun category(value: Int?, values: Map<Int, HardwareCategory>): HardwareValue<HardwareDatum> =
    values[value]?.let { Available(HardwareDatum.Category(it)) } ?: Unavailable(UnavailableReason.NOT_REPORTED)
