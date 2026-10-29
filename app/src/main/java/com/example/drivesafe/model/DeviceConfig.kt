package com.example.drivesafe.model

object VehicleProfile {
    val OPTIONS = listOf("sepeda", "motor", "mobil")
}

/**
 * Semua yang dikirim ke characteristic CONFIG dalam satu JSON.
 * Kontak dan pengaturan dikirim bareng, jadi disimpan dan dibandingkan bareng.
 */
data class DeviceConfig(
    val owner: String,
    val profile: String,
    val countdownSeconds: Int,
    val contacts: List<EmergencyContact>
) {
    /** Dipakai untuk tahu apakah isi sekarang sudah sama dengan yang terakhir dikonfirmasi alat. */
    fun signature(): String =
        "$owner|$profile|$countdownSeconds|" +
                contacts.sortedBy { it.priority }
                    .joinToString(";") { "${it.priority},${it.name},${it.phone}" }

    companion object {
        val DEFAULT = DeviceConfig(
            owner = "",
            profile = "motor",
            countdownSeconds = 30,
            contacts = emptyList()
        )
    }
}