package io.github.masoudd2159.deviceatlas.foundation.interactive

enum class InteractiveKind { DISPLAY, TOUCH, SENSOR, HAPTIC }
enum class DisplayPattern { RED, GREEN, BLUE, WHITE, BLACK, GRAY, GRADIENT, GRID }
enum class SensorKind(val dimensions: Int, val unit: String) {
    ACCELEROMETER(3, "m/s²"), GYROSCOPE(3, "rad/s"), LIGHT(1, "lux"), PROXIMITY(1, "cm")
}
enum class ManualObservation { NOT_TESTED, NO_ISSUE, POSSIBLE_ISSUE, UNSURE, UNABLE }
enum class InteractiveStop { USER, HIDDEN, BACKGROUND, DESTROYED, TIMEOUT, CONFLICT }
enum class InteractiveUnavailable { NOT_PRESENT, REGISTRATION_FAILED, PLATFORM, NATIVE, INVALID_DATA }
sealed interface InteractiveStatus {
    data object Idle : InteractiveStatus
    data object Preparing : InteractiveStatus
    data object Running : InteractiveStatus
    data object Completed : InteractiveStatus
    data class Stopped(val reason: InteractiveStop) : InteractiveStatus
    data class Unavailable(val reason: InteractiveUnavailable) : InteractiveStatus
}
data class SensorSummary(val count: Int, val minimum: Double, val maximum: Double,
    val mean: Double, val latest: Double, val range: Double)
data class SensorReading(val values: List<Double>, val receivedNanos: Long, val accuracy: Int)
data class SensorInfo(val maximumRange: Double?)
interface SensorSubscription { fun close() }
class InteractiveFailure(val reason: InteractiveUnavailable) : RuntimeException()
/** Main-confined gateway; callbacks copy platform values and never retain SensorEvent. */
interface InteractiveSensors {
    fun open(kind: SensorKind, receive: (SensorReading) -> Unit): Pair<SensorInfo, SensorSubscription>
}
interface InteractiveHaptics {
    fun pulse(millis: Long)
    fun cancel()
}
fun interface SensorAnalyzer { suspend fun summarize(kind: SensorKind, samples: List<SensorReading>): SensorSummary }
data class Contact(val id: Long, val x: Float, val y: Float)
data class TouchCoverage(val width: Int = 0, val height: Int = 0, val columns: Int = 8, val rows: Int = 12,
    val visited: Set<Int> = emptySet(), val contacts: List<Contact> = emptyList(),
    val activeCount: Int = 0, val maximumContacts: Int = 0, val geometryReset: Boolean = false) {
    val cells get() = columns * rows
    fun cell(x: Float, y: Float): Int? {
        if (!x.isFinite() || !y.isFinite() || width <= 0 || height <= 0 || x < 0 || y < 0 || x >= width || y >= height) return null
        return (y / height * rows).toInt() * columns + (x / width * columns).toInt()
    }
    fun resize(w: Int, h: Int): TouchCoverage = if (w == width && h == height) this else
        TouchCoverage(w.coerceAtLeast(0), h.coerceAtLeast(0), if (w > h) 12 else 8, if (w > h) 8 else 12,
            geometryReset = width > 0 && height > 0)
    fun update(points: List<Contact>): TouchCoverage {
        val distinct = points.distinctBy { it.id }
        return copy(visited = (visited + distinct.mapNotNull { cell(it.x, it.y) }).toSet(),
            contacts = distinct.filter { cell(it.x, it.y) != null }.take(10).toList(),
            activeCount = distinct.size, maximumContacts = maxOf(maximumContacts, distinct.size))
    }
}
data class InteractiveResult(val status: InteractiveStatus = InteractiveStatus.Idle,
    val observation: ManualObservation = ManualObservation.NOT_TESTED,
    val visitedCells: Int = 0, val totalCells: Int = 96, val maximumContacts: Int = 0,
    val sensor: SensorKind? = null, val summary: SensorSummary? = null, val pulseRequested: Boolean = false)
data class InteractiveState(val kind: InteractiveKind = InteractiveKind.DISPLAY,
    val status: InteractiveStatus = InteractiveStatus.Idle, val canStart: Boolean = false, val retiring: Boolean = false,
    val pattern: DisplayPattern = DisplayPattern.RED, val touch: TouchCoverage = TouchCoverage(),
    val sensor: SensorKind = SensorKind.ACCELEROMETER, val sensorInfo: SensorInfo? = null,
    val reading: SensorReading? = null, val summary: SensorSummary? = null,
    val ageMillis: Long? = null, val rejectedSamples: Int = 0,
    val pulseRequested: Boolean = false, val pulseAllowed: Boolean = true,
    val results: Map<InteractiveKind, InteractiveResult> = InteractiveKind.entries.associateWith { InteractiveResult() })
