package com.example.drivesafe.model

enum class EventPhase {
    SENDING,
    SENT,
    UNKNOWN
}

fun parseEventPhase(value: String): EventPhase {
    return try {
        EventPhase.valueOf(value.trim())
    } catch (e: IllegalArgumentException) {
        EventPhase.UNKNOWN
    }
}

data class AccidentEvent(
    val phase: EventPhase,
    val timestampEpoch: Long,       // bisa 0 kalau GPS belum fix
    val gForce: Float,              // "g"
    val jerk: Float,                // "j"
    val rotationRate: Float,        // "r"
    val roll: Float,
    val pitch: Float,
    val severity: Int,              // 0/1/2
    val severityName: String,       // "sevn"
    val type: Int,                  // 0-5
    val typeName: String,           // "typen"
    val latitude: Double,
    val longitude: Double,          // note: "lon" bukan "lng"
    val mapUrl: String
)