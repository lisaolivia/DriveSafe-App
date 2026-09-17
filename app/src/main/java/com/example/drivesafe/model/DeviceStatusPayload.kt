package com.example.drivesafe.model

data class DeviceStatusPayload(
    val status: DeviceStatus,
    val batteryPercent: Int,
    val sensorOk: Boolean,
    val gpsFix: Boolean,
    val satelliteCount: Int,
    val gsmSignalPercent: Int,
    val speedKmh: Float,
    val contactCount: Int,
    val countdownSeconds: Int,
    val profile: String
)