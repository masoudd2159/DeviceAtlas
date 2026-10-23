package io.github.masoudd2159.deviceatlas.foundation.hardware

import java.util.Collections

enum class UnavailableReason {
    UNSUPPORTED, NOT_REPORTED, NOT_PRESENT, TEMPORARY, PERMISSION_RESTRICTED,
    COLLECTION_FAILED, INVALID_RESPONSE, NATIVE_UNAVAILABLE, NATIVE_FAILED,
    BASELINE, INSUFFICIENT,
}

sealed interface HardwareValue<out T> {
    data class Available<T>(val value: T) : HardwareValue<T>
    data class Unavailable(val reason: UnavailableReason) : HardwareValue<Nothing>
}

enum class Section { IDENTITY, ANDROID, CPU, MEMORY, STORAGE, BATTERY, DISPLAY, GRAPHICS }
enum class Source { BUILD, VERSION, RUNTIME, MEMORY_INFO, DEVICE_CONFIGURATION, STAT_FS, BATTERY_INTENT, BATTERY_MANAGER, DISPLAY_MANAGER, RESOURCES, PACKAGE_MANAGER, SENSOR_MANAGER }
enum class Freshness { SESSION, SNAPSHOT }
enum class UnitKind { NONE, GIB, PERCENT, CELSIUS, VOLT, MILLIAMP, MILLIAMP_HOUR, WATT_HOUR, HERTZ, DPI, MILLIWATT, MICROSECOND }

/** The field key supplies provenance and freshness even when its value is unavailable. */
enum class HardwareField(val section: Section, val source: Source, val freshness: Freshness = Freshness.SESSION) {
    MANUFACTURER(Section.IDENTITY, Source.BUILD), BRAND(Section.IDENTITY, Source.BUILD),
    MODEL(Section.IDENTITY, Source.BUILD), DEVICE(Section.IDENTITY, Source.BUILD),
    PRODUCT(Section.IDENTITY, Source.BUILD), HARDWARE(Section.IDENTITY, Source.BUILD), BOARD(Section.IDENTITY, Source.BUILD),
    RELEASE(Section.ANDROID, Source.VERSION), SDK(Section.ANDROID, Source.VERSION),
    BUILD_ID(Section.ANDROID, Source.BUILD), SECURITY_PATCH(Section.ANDROID, Source.VERSION),
    SOC_MANUFACTURER(Section.CPU, Source.BUILD), SOC_MODEL(Section.CPU, Source.BUILD),
    ABIS(Section.CPU, Source.BUILD), ABIS_32(Section.CPU, Source.BUILD), ABIS_64(Section.CPU, Source.BUILD),
    PRIMARY_ABI(Section.CPU, Source.BUILD), PROCESSORS(Section.CPU, Source.RUNTIME, Freshness.SNAPSHOT),
    MEMORY(Section.MEMORY, Source.MEMORY_INFO, Freshness.SNAPSHOT),
    LOW_MEMORY(Section.MEMORY, Source.MEMORY_INFO, Freshness.SNAPSHOT),
    MEMORY_THRESHOLD(Section.MEMORY, Source.MEMORY_INFO, Freshness.SNAPSHOT),
    STORAGE(Section.STORAGE, Source.STAT_FS, Freshness.SNAPSHOT),
    BATTERY_PRESENT(Section.BATTERY, Source.BATTERY_INTENT, Freshness.SNAPSHOT),
    BATTERY_LEVEL(Section.BATTERY, Source.BATTERY_INTENT, Freshness.SNAPSHOT),
    BATTERY_STATUS(Section.BATTERY, Source.BATTERY_INTENT, Freshness.SNAPSHOT),
    BATTERY_SOURCE(Section.BATTERY, Source.BATTERY_INTENT, Freshness.SNAPSHOT),
    BATTERY_HEALTH(Section.BATTERY, Source.BATTERY_INTENT, Freshness.SNAPSHOT),
    TEMPERATURE(Section.BATTERY, Source.BATTERY_INTENT, Freshness.SNAPSHOT),
    VOLTAGE(Section.BATTERY, Source.BATTERY_INTENT, Freshness.SNAPSHOT),
    CHARGE(Section.BATTERY, Source.BATTERY_MANAGER, Freshness.SNAPSHOT),
    CURRENT(Section.BATTERY, Source.BATTERY_MANAGER, Freshness.SNAPSHOT),
    ENERGY(Section.BATTERY, Source.BATTERY_MANAGER, Freshness.SNAPSHOT),
    CYCLES(Section.BATTERY, Source.BATTERY_INTENT, Freshness.SNAPSHOT),
    DISPLAY_MODE(Section.DISPLAY, Source.DISPLAY_MANAGER, Freshness.SNAPSHOT),
    REFRESH_RATE(Section.DISPLAY, Source.DISPLAY_MANAGER, Freshness.SNAPSHOT),
    DENSITY(Section.DISPLAY, Source.RESOURCES, Freshness.SNAPSHOT),
    HDR(Section.DISPLAY, Source.DISPLAY_MANAGER, Freshness.SNAPSHOT),
    GLES(Section.GRAPHICS, Source.DEVICE_CONFIGURATION), VULKAN(Section.GRAPHICS, Source.PACKAGE_MANAGER),
    GPU_MODEL(Section.GRAPHICS, Source.PACKAGE_MANAGER),
}

enum class HardwareCategory {
    CHARGING, DISCHARGING, FULL, NOT_CHARGING, UNPLUGGED, AC, USB, WIRELESS, DOCK,
    GOOD, OVERHEAT, DEAD, OVER_VOLTAGE, FAILURE, COLD,
}

/** Wire operations have fixed numeric IDs, independent of enum ordinals. */
enum class MetricOperation(val wireId: Long) {
    CAPACITY(1), LEVEL(2), TEMPERATURE(3), VOLTAGE(4), CURRENT(5), CHARGE(6), ENERGY(7),
    DISPLAY(8), NONNEGATIVE_REAL(9), POSITIVE_INTEGER(10), NONNEGATIVE_INTEGER(11), BYTES(12), POSITIVE_REAL(13),
}
data class MetricRequest(
    val operation: MetricOperation, val first: Long = 0, val second: Long = 0,
    val real: Double = 0.0, val unit: UnitKind = UnitKind.NONE,
)

sealed interface HardwareDatum {
    data class Text(val text: String) : HardwareDatum
    data class Flag(val enabled: Boolean) : HardwareDatum
    data class Category(val category: HardwareCategory) : HardwareDatum
    data class Numeric(val request: MetricRequest) : HardwareDatum
    data class Number(val number: Double, val unit: UnitKind) : HardwareDatum
    data class Capacity(val totalBytes: Long, val availableBytes: Long, val totalGiB: Double,
        val availableGiB: Double, val unavailableGiB: Double, val availablePercent: Double, val unavailablePercent: Double) : HardwareDatum
    data class DisplayMode(val width: Long, val height: Long, val ratioWidth: Long, val ratioHeight: Long) : HardwareDatum
}

enum class SensorField { NAME, VENDOR, VERSION, POWER, RESOLUTION, RANGE, MIN_DELAY, MAX_DELAY, WAKE_UP, REPORTING_MODE }
data class SensorCapability(
    val index: Int, val type: Int,
    val fields: Map<SensorField, HardwareValue<HardwareDatum>>,
)
data class HardwareSnapshot(
    val fields: Map<HardwareField, HardwareValue<HardwareDatum>>,
    val sensors: HardwareValue<List<SensorCapability>>,
    val capturedAtMillis: Long,
)

fun <K, V> immutableMap(values: Map<K, V>): Map<K, V> = Collections.unmodifiableMap(LinkedHashMap(values))
fun <T> immutableList(values: List<T>): List<T> = Collections.unmodifiableList(ArrayList(values))

fun interface HardwareCollector { suspend fun collect(): HardwareSnapshot }
fun interface HardwareProcessor { suspend fun process(request: MetricRequest): HardwareValue<HardwareDatum> }
fun interface HardwareReader { suspend fun read(): HardwareSnapshot }
