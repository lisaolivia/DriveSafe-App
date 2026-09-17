package com.example.drivesafe.model

data class EmergencyContact(
    val name: String,      // max 15 char efektif
    val phone: String,     // max 15 char efektif, min 8 char (dibawah itu diabaikan device)
    val priority: Int      // angka lebih kecil = prioritas lebih tinggi
)