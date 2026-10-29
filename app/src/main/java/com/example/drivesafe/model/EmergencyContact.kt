package com.example.drivesafe.model

import java.util.UUID

data class EmergencyContact(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val phone: String,
    val priority: Int   // angka terkecil = kontak utama (penerima missed call)
)