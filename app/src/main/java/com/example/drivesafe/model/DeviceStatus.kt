package com.example.drivesafe.model

enum class DeviceStatus {
    BOOT,
    CALIB,
    MONITOR,
    VERIFY,
    COUNTDOWN,
    ALERT,
    CANCELLED,
    COOLDOWN,
    PAIRING,
    UNKNOWN  // fallback kalau ada nilai baru yang belum kekover
}

fun parseDeviceStatus(value: String): DeviceStatus {
    return try {
        DeviceStatus.valueOf(value.trim())
    } catch (e: IllegalArgumentException) {
        DeviceStatus.UNKNOWN
    }
}