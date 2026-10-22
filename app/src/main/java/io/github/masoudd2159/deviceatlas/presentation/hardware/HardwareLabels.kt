package io.github.masoudd2159.deviceatlas.presentation.hardware

import io.github.masoudd2159.deviceatlas.R
import io.github.masoudd2159.deviceatlas.foundation.hardware.*

internal fun fieldLabel(value: HardwareField): Int = when (value) {
    HardwareField.MANUFACTURER -> R.string.hw_field_manufacturer
    HardwareField.BRAND -> R.string.hw_field_brand
    HardwareField.MODEL -> R.string.hw_field_model
    HardwareField.DEVICE -> R.string.hw_field_device
    HardwareField.PRODUCT -> R.string.hw_field_product
    HardwareField.HARDWARE -> R.string.hw_field_hardware
    HardwareField.BOARD -> R.string.hw_field_board
    HardwareField.RELEASE -> R.string.hw_field_release
    HardwareField.SDK -> R.string.hw_field_sdk
    HardwareField.BUILD_ID -> R.string.hw_field_build_id
    HardwareField.SECURITY_PATCH -> R.string.hw_field_security_patch
    HardwareField.SOC_MANUFACTURER -> R.string.hw_field_soc_manufacturer
    HardwareField.SOC_MODEL -> R.string.hw_field_soc_model
    HardwareField.ABIS -> R.string.hw_field_abis
    HardwareField.ABIS_32 -> R.string.hw_field_abis_32
    HardwareField.ABIS_64 -> R.string.hw_field_abis_64
    HardwareField.PRIMARY_ABI -> R.string.hw_field_primary_abi
    HardwareField.PROCESSORS -> R.string.hw_field_processors
    HardwareField.MEMORY -> R.string.hw_field_memory
    HardwareField.LOW_MEMORY -> R.string.hw_field_low_memory
    HardwareField.MEMORY_THRESHOLD -> R.string.hw_field_memory_threshold
    HardwareField.STORAGE -> R.string.hw_field_storage
    HardwareField.BATTERY_PRESENT -> R.string.hw_field_battery_present
    HardwareField.BATTERY_LEVEL -> R.string.hw_field_battery_level
    HardwareField.BATTERY_STATUS -> R.string.hw_field_battery_status
    HardwareField.BATTERY_SOURCE -> R.string.hw_field_battery_source
    HardwareField.BATTERY_HEALTH -> R.string.hw_field_battery_health
    HardwareField.TEMPERATURE -> R.string.hw_field_temperature
    HardwareField.VOLTAGE -> R.string.hw_field_voltage
    HardwareField.CHARGE -> R.string.hw_field_charge
    HardwareField.CURRENT -> R.string.hw_field_current
    HardwareField.ENERGY -> R.string.hw_field_energy
    HardwareField.CYCLES -> R.string.hw_field_cycles
    HardwareField.DISPLAY_MODE -> R.string.hw_field_display_mode
    HardwareField.REFRESH_RATE -> R.string.hw_field_refresh_rate
    HardwareField.DENSITY -> R.string.hw_field_density
    HardwareField.HDR -> R.string.hw_field_hdr
    HardwareField.GLES -> R.string.hw_field_gles
    HardwareField.VULKAN -> R.string.hw_field_vulkan
    HardwareField.GPU_MODEL -> R.string.hw_field_gpu_model
}

internal fun sectionLabel(value: Section): Int = when (value) {
    Section.IDENTITY -> R.string.hw_section_identity
    Section.ANDROID -> R.string.hw_section_android
    Section.CPU -> R.string.hw_section_cpu
    Section.MEMORY -> R.string.hw_section_memory
    Section.STORAGE -> R.string.hw_section_storage
    Section.BATTERY -> R.string.hw_section_battery
    Section.DISPLAY -> R.string.hw_section_display
    Section.GRAPHICS -> R.string.hw_section_graphics
}

internal fun reasonLabel(value: UnavailableReason): Int = when (value) {
    UnavailableReason.UNSUPPORTED -> R.string.hw_reason_unsupported
    UnavailableReason.NOT_REPORTED -> R.string.hw_reason_not_reported
    UnavailableReason.NOT_PRESENT -> R.string.hw_reason_not_present
    UnavailableReason.TEMPORARY -> R.string.hw_reason_temporary
    UnavailableReason.PERMISSION_RESTRICTED -> R.string.hw_reason_permission_restricted
    UnavailableReason.COLLECTION_FAILED -> R.string.hw_reason_collection_failed
    UnavailableReason.INVALID_RESPONSE -> R.string.hw_reason_invalid_response
    UnavailableReason.NATIVE_UNAVAILABLE -> R.string.hw_reason_native_unavailable
    UnavailableReason.NATIVE_FAILED -> R.string.hw_reason_native_failed
}

internal fun categoryLabel(value: HardwareCategory): Int = when (value) {
    HardwareCategory.CHARGING -> R.string.hw_category_charging
    HardwareCategory.DISCHARGING -> R.string.hw_category_discharging
    HardwareCategory.FULL -> R.string.hw_category_full
    HardwareCategory.NOT_CHARGING -> R.string.hw_category_not_charging
    HardwareCategory.UNPLUGGED -> R.string.hw_category_unplugged
    HardwareCategory.AC -> R.string.hw_category_ac
    HardwareCategory.USB -> R.string.hw_category_usb
    HardwareCategory.WIRELESS -> R.string.hw_category_wireless
    HardwareCategory.DOCK -> R.string.hw_category_dock
    HardwareCategory.GOOD -> R.string.hw_category_good
    HardwareCategory.OVERHEAT -> R.string.hw_category_overheat
    HardwareCategory.DEAD -> R.string.hw_category_dead
    HardwareCategory.OVER_VOLTAGE -> R.string.hw_category_over_voltage
    HardwareCategory.FAILURE -> R.string.hw_category_failure
    HardwareCategory.COLD -> R.string.hw_category_cold
}

internal fun sensorLabel(value: SensorField): Int = when (value) {
    SensorField.NAME -> R.string.hw_sensor_name
    SensorField.VENDOR -> R.string.hw_sensor_vendor
    SensorField.VERSION -> R.string.hw_sensor_version
    SensorField.POWER -> R.string.hw_sensor_power
    SensorField.RESOLUTION -> R.string.hw_sensor_resolution
    SensorField.RANGE -> R.string.hw_sensor_range
    SensorField.MIN_DELAY -> R.string.hw_sensor_min_delay
    SensorField.MAX_DELAY -> R.string.hw_sensor_max_delay
    SensorField.WAKE_UP -> R.string.hw_sensor_wake_up
    SensorField.REPORTING_MODE -> R.string.hw_sensor_reporting_mode
}

internal fun unitLabel(value: UnitKind): Int = when (value) {
    UnitKind.NONE -> R.string.hw_unit_none
    UnitKind.GIB -> R.string.hw_unit_gib
    UnitKind.PERCENT -> R.string.hw_unit_percent
    UnitKind.CELSIUS -> R.string.hw_unit_celsius
    UnitKind.VOLT -> R.string.hw_unit_volt
    UnitKind.MILLIAMP -> R.string.hw_unit_milliamp
    UnitKind.MILLIAMP_HOUR -> R.string.hw_unit_milliamp_hour
    UnitKind.WATT_HOUR -> R.string.hw_unit_watt_hour
    UnitKind.HERTZ -> R.string.hw_unit_hertz
    UnitKind.DPI -> R.string.hw_unit_dpi
    UnitKind.MILLIWATT -> R.string.hw_unit_milliwatt
    UnitKind.MICROSECOND -> R.string.hw_unit_microsecond
}

internal fun sensorTypeLabel(type: Int): Int? = when (type) {
    1 -> R.string.hw_type_1
    2 -> R.string.hw_type_2
    3 -> R.string.hw_type_3
    4 -> R.string.hw_type_4
    5 -> R.string.hw_type_5
    6 -> R.string.hw_type_6
    7 -> R.string.hw_type_7
    8 -> R.string.hw_type_8
    9 -> R.string.hw_type_9
    10 -> R.string.hw_type_10
    11 -> R.string.hw_type_11
    12 -> R.string.hw_type_12
    13 -> R.string.hw_type_13
    14 -> R.string.hw_type_14
    15 -> R.string.hw_type_15
    16 -> R.string.hw_type_16
    17 -> R.string.hw_type_17
    18 -> R.string.hw_type_18
    19 -> R.string.hw_type_19
    20 -> R.string.hw_type_20
    21 -> R.string.hw_type_21
    22 -> R.string.hw_type_22
    23 -> R.string.hw_type_23
    24 -> R.string.hw_type_24
    25 -> R.string.hw_type_25
    26 -> R.string.hw_type_26
    27 -> R.string.hw_type_27
    28 -> R.string.hw_type_28
    29 -> R.string.hw_type_29
    30 -> R.string.hw_type_30
    31 -> R.string.hw_type_31
    32 -> R.string.hw_type_32
    33 -> R.string.hw_type_33
    34 -> R.string.hw_type_34
    35 -> R.string.hw_type_35
    36 -> R.string.hw_type_36
    37 -> R.string.hw_type_37
    38 -> R.string.hw_type_38
    39 -> R.string.hw_type_39
    40 -> R.string.hw_type_40
    41 -> R.string.hw_type_41
    42 -> R.string.hw_type_42
    else -> null
}
