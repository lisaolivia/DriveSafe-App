package com.example.drivesafe.ble

import android.util.Log
import com.example.drivesafe.model.AccidentEvent
import com.example.drivesafe.model.DeviceConfig
import com.example.drivesafe.model.DeviceConfigSnapshot
import com.example.drivesafe.model.DeviceStatusPayload
import com.example.drivesafe.model.EmergencyContact
import com.example.drivesafe.model.VehicleProfile
import com.example.drivesafe.model.parseDeviceStatus
import com.example.drivesafe.model.parseEventPhase
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object BleJsonParser {

    // Batas dari firmware (lihat PANDUAN-ALAT-DRIVESAFE.md)
    const val MAX_CONTACTS = 5
    const val MAX_FIELD_BYTES = 15
    const val MAX_OWNER_BYTES = 19
    const val MIN_PHONE_LENGTH = 8
    const val MIN_COUNTDOWN = 5
    const val MAX_COUNTDOWN = 120
    private const val MAX_PAYLOAD_BYTES = 768

    private val PHONE_REGEX = Regex("^\\+?[0-9]+$")
    private val USSD_REGEX = Regex("^[*#0-9]+$")

    // Parser JSON firmware mencari kunci dengan strstr("\"kunci\"") tanpa memeriksa posisi. Kalau nilai
    // persis sama dengan nama kunci, kunci itu diabaikan diam-diam (kontak hilang / pengaturan tak terpakai).
    private val RESERVED_WORDS = setOf("n", "p", "pr", "owner", "profile", "countdown", "contacts")

    // org.json Android meng-escape "/" jadi "\/" dan parser firmware tidak meng-unescape,
    // jadi karakter ini ditolak dari awal.
    private val FORBIDDEN_CHARS = charArrayOf('"', '\\', '/')

    // ---------- PARSING (alat -> app) ----------

    fun parseStatus(json: String): DeviceStatusPayload? {
        return try {
            val obj = JSONObject(json)
            DeviceStatusPayload(
                status = parseDeviceStatus(obj.getString("st")),
                batteryPercent = obj.optInt("bat", 0),
                sensorOk = obj.optInt("sens", 0) == 1,
                gpsFix = obj.optInt("gps", 0) == 1,
                satelliteCount = obj.optInt("sat", 0),
                gsmSignalPercent = obj.optInt("gsm", 0),
                speedKmh = obj.optDouble("spd", 0.0).toFloat(),
                contactCount = obj.optInt("con", 0),
                countdownSeconds = obj.optInt("cd", 0),
                profile = obj.optString("prof", "")
            )
        } catch (e: JSONException) {
            Log.e("BLE_PARSE", "Gagal parse STATUS: $json", e)
            null
        }
    }

    fun parseEvent(json: String): AccidentEvent? {
        return try {
            val obj = JSONObject(json)
            // Alat mengosongkan nilai EVENT jadi "{}" setelah kembali MONITOR; itu bukan kejadian.
            if (!obj.has("ph")) return null
            AccidentEvent(
                seq = obj.optInt("seq", -1),
                phase = parseEventPhase(obj.getString("ph")),
                timestampEpoch = obj.optLong("ts", 0L),
                gForce = obj.optDouble("g", 0.0).toFloat(),
                jerk = obj.optDouble("j", 0.0).toFloat(),
                rotationRate = obj.optDouble("r", 0.0).toFloat(),
                roll = obj.optDouble("roll", 0.0).toFloat(),
                pitch = obj.optDouble("pitch", 0.0).toFloat(),
                severity = obj.optInt("sev", 0),
                severityName = obj.optString("sevn", ""),
                type = obj.optInt("type", 0),
                typeName = obj.optString("typen", ""),
                latitude = obj.optDouble("lat", 0.0),
                longitude = obj.optDouble("lon", 0.0),   // "lon", bukan "lng"
                mapUrl = obj.optString("map", "")
            )
        } catch (e: JSONException) {
            Log.e("BLE_PARSE", "Gagal parse EVENT: $json", e)
            null
        }
    }

    /** Balasan readCharacteristic CONFIG. Perhatikan: kunci atas beda dengan skema tulis (own, bukan owner). */
    fun parseConfig(json: String): DeviceConfigSnapshot? {
        return try {
            val obj = JSONObject(json)
            val array = obj.optJSONArray("c") ?: JSONArray()
            val contacts = (0 until array.length()).map { i ->
                val c = array.getJSONObject(i)
                EmergencyContact(
                    name = c.getString("n"),
                    phone = c.getString("p"),
                    priority = c.optInt("pr", i + 1)
                )
            }
            DeviceConfigSnapshot(
                deviceName = obj.optString("dev", ""),
                owner = obj.optString("own", ""),
                profile = obj.optString("prof", ""),
                countdownSeconds = obj.optInt("cd", 0),
                contactCount = obj.optInt("cnt", contacts.size),
                maxContacts = obj.optInt("max", MAX_CONTACTS),
                contacts = contacts,
                balanceText = obj.optString("bal", ""),
                balanceAgeSeconds = obj.optInt("balage", 0)
            )
        } catch (e: JSONException) {
            Log.e("BLE_PARSE", "Gagal parse CONFIG: $json", e)
            null
        }
    }

    // ---------- VALIDASI (sebelum dikirim, karena firmware menolak diam-diam) ----------

    fun validateText(label: String, value: String, maxBytes: Int = MAX_FIELD_BYTES): String? {
        if (value.isBlank()) return "$label tidak boleh kosong"
        if (value.toByteArray(Charsets.UTF_8).size > maxBytes) {
            return "$label maksimal $maxBytes karakter (batas alat)"
        }
        if (value.any { it in FORBIDDEN_CHARS || it.isISOControl() }) {
            return "$label tidak boleh mengandung tanda \" \\ atau /"
        }
        if (value in RESERVED_WORDS) {
            return "$label tidak boleh persis \"$value\" (kata ini dipakai alat sebagai kunci), pakai nama lain"
        }
        return null
    }

    /** Kode USSD cek pulsa, mis. *888# atau *123#. */
    fun validateUssd(code: String): String? {
        if (code.length < 3) return "Kode USSD terlalu pendek"
        if (code.length > BleConstants.MAX_USSD_LENGTH) {
            return "Kode USSD maksimal ${BleConstants.MAX_USSD_LENGTH} karakter"
        }
        if (!USSD_REGEX.matches(code)) return "Kode USSD hanya boleh angka, * dan #"
        return null
    }

    fun validatePhone(phone: String): String? {
        if (phone.length < MIN_PHONE_LENGTH) return "Nomor minimal $MIN_PHONE_LENGTH karakter"
        if (phone.length > MAX_FIELD_BYTES) return "Nomor maksimal $MAX_FIELD_BYTES karakter (batas alat)"
        if (!PHONE_REGEX.matches(phone)) return "Nomor hanya boleh angka, boleh diawali +"
        return null
    }

    fun validateContact(name: String, phone: String): String? =
        validateText("Nama", name) ?: validatePhone(phone)

    fun validateContact(contact: EmergencyContact): String? =
        validateContact(contact.name, contact.phone)

    fun isValidProfile(profile: String): Boolean = profile in VehicleProfile.OPTIONS

    fun isValidCountdown(seconds: Int): Boolean = seconds in MIN_COUNTDOWN..MAX_COUNTDOWN

    // ---------- BUILD (app -> alat) ----------

    /**
     * Susun JSON untuk characteristic CONFIG.
     * Kalau daftar kontak kosong, kunci "contacts" tidak disertakan (kontak di alat
     * tidak disentuh) -- untuk mengosongkan kontak alat pakai perintah CLEARCON.
     */
    fun buildConfigJson(config: DeviceConfig): Result<String> {

        validateText("Nama pemilik", config.owner, MAX_OWNER_BYTES)?.let { return failure(it) }

        if (!isValidProfile(config.profile)) {
            return failure("Profil kendaraan tidak valid")
        }
        if (!isValidCountdown(config.countdownSeconds)) {
            return failure("Hitung mundur harus $MIN_COUNTDOWN-$MAX_COUNTDOWN detik")
        }
        if (config.contacts.size > MAX_CONTACTS) {
            return failure("Maksimal $MAX_CONTACTS kontak")
        }
        for (contact in config.contacts) {
            validateContact(contact)?.let { return failure("${contact.name}: $it") }
        }

        val root = JSONObject()
        root.put("owner", config.owner)
        root.put("profile", config.profile)
        root.put("countdown", config.countdownSeconds)

        if (config.contacts.isNotEmpty()) {
            val array = JSONArray()
            for (contact in config.contacts.sortedBy { it.priority }) {
                array.put(
                    JSONObject()
                        .put("n", contact.name)
                        .put("p", contact.phone)
                        .put("pr", contact.priority)
                )
            }
            root.put("contacts", array)
        }

        val json = root.toString()

        if (json.toByteArray(Charsets.UTF_8).size > MAX_PAYLOAD_BYTES) {
            return failure("Konfigurasi melebihi batas $MAX_PAYLOAD_BYTES byte")
        }

        return Result.success(json)
    }

    private fun failure(message: String): Result<String> =
        Result.failure(IllegalArgumentException(message))
}