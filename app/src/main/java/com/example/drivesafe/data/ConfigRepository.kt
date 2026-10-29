package com.example.drivesafe.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.example.drivesafe.ble.BleJsonParser
import com.example.drivesafe.model.DeviceConfig
import com.example.drivesafe.model.EmergencyContact
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Sumber kebenaran untuk kontak + pengaturan di sisi aplikasi.
 * Alat tidak punya perintah "baca kontak", jadi data di sini yang jadi acuan.
 *
 * Disimpan di DataStore (bukan Room): firmware hanya menerima maksimal 5 kontak,
 * jadi satu string JSON sudah cukup dan tidak butuh plugin KSP.
 */
class ConfigRepository(context: Context) {

    private val store = context.applicationContext.driveSafeDataStore

    val config: Flow<DeviceConfig> = store.data.map { it.toConfig() }

    /** true = isi sekarang beda dengan yang terakhir dikonfirmasi alat. */
    val needsSync: Flow<Boolean> =
        store.data.map { it.toConfig().signature() != it[SYNCED_SIGNATURE] }

    val lastDeviceAddress: Flow<String?> = store.data.map { it[LAST_DEVICE] }

    suspend fun currentConfig(): DeviceConfig = config.first()

    // ---------- Kontak ----------

    /** @return null kalau berhasil, atau pesan error untuk ditampilkan. */
    suspend fun addContact(name: String, phone: String): String? {

        val n = name.trim()
        val p = phone.trim()
        var error: String? = null

        store.edit { prefs ->
            val list = decodeContacts(prefs[CONTACTS])

            error = BleJsonParser.validateContact(n, p)
                ?: when {
                    list.size >= BleJsonParser.MAX_CONTACTS ->
                        "Maksimal ${BleJsonParser.MAX_CONTACTS} kontak (batas alat)"
                    list.any { it.phone == p } ->
                        "Nomor ini sudah terdaftar"
                    else -> null
                }

            if (error == null) {
                val added = list + EmergencyContact(name = n, phone = p, priority = list.size + 1)
                prefs[CONTACTS] = encodeContacts(renumber(added))
            }
        }

        return error
    }

    suspend fun updateContact(id: String, name: String, phone: String): String? {

        val n = name.trim()
        val p = phone.trim()
        var error: String? = null

        store.edit { prefs ->
            val list = decodeContacts(prefs[CONTACTS])

            error = BleJsonParser.validateContact(n, p)
                ?: if (list.any { it.id != id && it.phone == p }) "Nomor ini sudah terdaftar" else null

            if (error == null) {
                val updated = list.map { if (it.id == id) it.copy(name = n, phone = p) else it }
                prefs[CONTACTS] = encodeContacts(updated)
            }
        }

        return error
    }

    suspend fun deleteContact(id: String) {
        store.edit { prefs ->
            val list = decodeContacts(prefs[CONTACTS]).filterNot { it.id == id }
            prefs[CONTACTS] = encodeContacts(renumber(list))
        }
    }

    /** Jadikan kontak ini prioritas 1 (penerima missed call). */
    suspend fun setPrimary(id: String) {
        store.edit { prefs ->
            val list = decodeContacts(prefs[CONTACTS])
            val target = list.firstOrNull { it.id == id } ?: return@edit
            val reordered = listOf(target) + list.filterNot { it.id == id }
            prefs[CONTACTS] = encodeContacts(renumber(reordered))
        }
    }

    // ---------- Pengaturan ----------

    suspend fun saveSettings(owner: String, profile: String, countdownSeconds: Int): String? {

        val o = owner.trim()

        BleJsonParser.validateText("Nama pemilik", o, BleJsonParser.MAX_OWNER_BYTES)?.let { return it }
        if (!BleJsonParser.isValidProfile(profile)) return "Profil kendaraan tidak valid"
        if (!BleJsonParser.isValidCountdown(countdownSeconds)) {
            return "Hitung mundur harus ${BleJsonParser.MIN_COUNTDOWN}-${BleJsonParser.MAX_COUNTDOWN} detik"
        }

        store.edit {
            it[OWNER] = o
            it[PROFILE] = profile
            it[COUNTDOWN] = countdownSeconds
        }
        return null
    }

    // ---------- Sinkronisasi & alat terakhir ----------

    /** Ganti seluruh isi lokal dengan [config] (misal hasil baca dari alat) dan tandai sudah sinkron. */
    suspend fun replaceAll(config: DeviceConfig) {
        store.edit {
            it[OWNER] = config.owner
            it[PROFILE] = config.profile
            it[COUNTDOWN] = config.countdownSeconds
            it[CONTACTS] = encodeContacts(renumber(config.contacts.sortedBy { c -> c.priority }))
        }
        markSynced(currentConfig())
    }

    suspend fun markSynced(config: DeviceConfig) {
        store.edit { it[SYNCED_SIGNATURE] = config.signature() }
    }

    /**
     * Penanda "sudah sinkron" berlaku untuk SATU alat. Ganti alat = alat baru belum pernah
     * menerima isi aplikasi, jadi penandanya dihapus.
     */
    suspend fun setLastDevice(address: String) {
        store.edit {
            if (it[LAST_DEVICE] != address) it.remove(SYNCED_SIGNATURE)
            it[LAST_DEVICE] = address
        }
    }

    suspend fun clearLastDevice() {
        store.edit {
            it.remove(LAST_DEVICE)
            it.remove(SYNCED_SIGNATURE)
        }
    }

    // ---------- Helper ----------

    private fun Preferences.toConfig() = DeviceConfig(
        owner = this[OWNER] ?: DeviceConfig.DEFAULT.owner,
        profile = this[PROFILE] ?: DeviceConfig.DEFAULT.profile,
        countdownSeconds = this[COUNTDOWN] ?: DeviceConfig.DEFAULT.countdownSeconds,
        contacts = decodeContacts(this[CONTACTS])
    )

    private fun renumber(list: List<EmergencyContact>): List<EmergencyContact> =
        list.mapIndexed { index, contact -> contact.copy(priority = index + 1) }

    private fun encodeContacts(list: List<EmergencyContact>): String {
        val array = JSONArray()
        for (c in list) {
            array.put(
                JSONObject()
                    .put("id", c.id)
                    .put("n", c.name)
                    .put("p", c.phone)
                    .put("pr", c.priority)
            )
        }
        return array.toString()
    }

    private fun decodeContacts(json: String?): List<EmergencyContact> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).map { i ->
                val o = array.getJSONObject(i)
                EmergencyContact(
                    id = o.getString("id"),
                    name = o.getString("n"),
                    phone = o.getString("p"),
                    priority = o.getInt("pr")
                )
            }.sortedBy { it.priority }
        } catch (e: JSONException) {
            emptyList()
        }
    }

    private companion object {
        val CONTACTS = stringPreferencesKey("contacts_json")
        val OWNER = stringPreferencesKey("owner")
        val PROFILE = stringPreferencesKey("profile")
        val COUNTDOWN = intPreferencesKey("countdown")
        val LAST_DEVICE = stringPreferencesKey("last_device_address")
        val SYNCED_SIGNATURE = stringPreferencesKey("synced_signature")
    }
}