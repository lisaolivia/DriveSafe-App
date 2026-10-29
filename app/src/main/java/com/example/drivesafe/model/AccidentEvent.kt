package com.example.drivesafe.model

enum class EventPhase { SENDING, SENT, UNKNOWN }

fun parseEventPhase(value: String): EventPhase = when (value.uppercase()) {
    "SENDING" -> EventPhase.SENDING
    "SENT" -> EventPhase.SENT
    else -> EventPhase.UNKNOWN
}

/**
 * Notifikasi dari characteristic EVENT. Satu kecelakaan = dua notifikasi (SENDING lalu SENT)
 * dengan [seq] yang sama. Saring duplikat pakai [seq], bukan [timestampEpoch].
 */
data class AccidentEvent(
    /** Nomor urut dari alat (naik sekali per kecelakaan). -1 kalau firmware lama tidak mengirimnya. */
    val seq: Int = -1,
    val phase: EventPhase,
    /** Epoch UTC dari GPS. 0 = GPS belum fix saat kejadian. */
    val timestampEpoch: Long,
    val gForce: Float,
    val jerk: Float,
    val rotationRate: Float,
    val roll: Float,
    val pitch: Float,
    /** 0 Ringan, 1 Sedang, 2 Parah */
    val severity: Int,
    val severityName: String,
    /** 0 Tidak diketahui .. 5 Terguling */
    val type: Int,
    val typeName: String,
    val latitude: Double,
    val longitude: Double,
    val mapUrl: String
)