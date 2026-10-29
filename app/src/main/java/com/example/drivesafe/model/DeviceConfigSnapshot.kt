package com.example.drivesafe.model

/**
 * Isi CONFIG yang DIBACA dari alat (firmware 1.1.1+). Ini yang benar-benar tersimpan di alat,
 * berbeda dengan [DeviceConfig] yang merupakan salinan lokal di HP.
 */
data class DeviceConfigSnapshot(
    val deviceName: String,
    val owner: String,
    val profile: String,
    val countdownSeconds: Int,
    /** Jumlah kontak tersimpan (cnt). Dipakai untuk indikator "2/5". */
    val contactCount: Int,
    /** Kapasitas maksimum (max), selalu 5. */
    val maxContacts: Int,
    val contacts: List<EmergencyContact>,
    /** Teks balasan operator dari cek pulsa terakhir (apa adanya). Kosong = belum pernah dicek. */
    val balanceText: String = "",
    /** Umur hasil cek pulsa dalam detik, dihitung alat saat CONFIG dibaca. 0 = belum pernah dicek. */
    val balanceAgeSeconds: Int = 0
) {
    /** Apakah isi di alat sama dengan [config] lokal. Urutan kontak dibandingkan menurut prioritas. */
    fun matches(config: DeviceConfig): Boolean {
        fun List<EmergencyContact>.key() = sortedBy { it.priority }.map { it.name to it.phone }
        return owner == config.owner &&
                profile == config.profile &&
                countdownSeconds == config.countdownSeconds &&
                contacts.key() == config.contacts.key()
    }

    fun toDeviceConfig(fallback: DeviceConfig): DeviceConfig = DeviceConfig(
        owner = owner.ifBlank { fallback.owner },
        profile = if (profile in VehicleProfile.OPTIONS) profile else fallback.profile,
        countdownSeconds = if (countdownSeconds in 5..120) countdownSeconds else fallback.countdownSeconds,
        contacts = contacts.sortedBy { it.priority }
    )
}