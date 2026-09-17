package com.example.drivesafe.ble

import android.util.Log
import com.example.drivesafe.model.AccidentEvent
import com.example.drivesafe.model.DeviceStatusPayload
import com.example.drivesafe.model.EmergencyContact
import com.example.drivesafe.model.parseDeviceStatus
import com.example.drivesafe.model.parseEventPhase
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object BleJsonParser {

    // ---------- PARSING (device -> app) ----------

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
            Log.e("BLE_PARSE", "Failed to parse STATUS json: $json", e)
            null
        }
    }

    fun parseEvent(json: String): AccidentEvent? {

        return try {
            val obj = JSONObject(json)

            AccidentEvent(
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
                longitude = obj.optDouble("lon", 0.0),  // ingat: "lon" bukan "lng"
                mapUrl = obj.optString("map", "")
            )

        } catch (e: JSONException) {
            Log.e("BLE_PARSE", "Failed to parse EVENT json: $json", e)
            null
        }
    }

    // ---------- BUILDING (app -> device) ----------

    /**
     * Validasi sebelum dikirim. Firmware ignore diam-diam kalau invalid,
     * jadi kita cegah dari sisi Android dulu biar user tau di UI kalau ada
     * input yang gak valid, bukan diem-diem gagal.
     */
    fun validateContact(contact: EmergencyContact): String? {

        if (contact.name.isBlank()) {
            return "Nama tidak boleh kosong"
        }

        if (contact.name.length > 15) {
            return "Nama maksimal 15 karakter"
        }

        if (contact.phone.length < 8) {
            return "Nomor terlalu pendek (minimal 8 karakter)"
        }

        if (contact.phone.length > 15) {
            return "Nomor maksimal 15 karakter"
        }

        return null  // valid
    }

    fun validateProfile(profile: String): Boolean {
        val validProfiles = setOf(
            "sepeda", "motor", "mobil",
            "car", "motorcycle", "bike", "bicycle"
        )
        return profile.lowercase() in validProfiles
    }

    fun validateCountdown(seconds: Int): Boolean {
        return seconds in 5..120
    }

    /**
     * Build JSON config untuk dikirim ke CONFIG_CHARACTERISTIC_UUID.
     * Return null kalau ada input yang gagal validasi.
     */
    fun buildConfigJson(
        owner: String,
        profile: String,
        countdownSeconds: Int,
        contacts: List<EmergencyContact>
    ): String? {

        if (!validateProfile(profile)) {
            Log.e("BLE_BUILD", "Invalid profile: $profile")
            return null
        }

        if (!validateCountdown(countdownSeconds)) {
            Log.e("BLE_BUILD", "Invalid countdown: $countdownSeconds (must be 5-120)")
            return null
        }

        for (contact in contacts) {
            val error = validateContact(contact)
            if (error != null) {
                Log.e("BLE_BUILD", "Invalid contact ${contact.name}: $error")
                return null
            }
        }

        val contactsArray = JSONArray()

        for (contact in contacts) {
            val contactObj = JSONObject()
            contactObj.put("n", contact.name)
            contactObj.put("p", contact.phone)
            contactObj.put("pr", contact.priority)
            contactsArray.put(contactObj)
        }

        val root = JSONObject()
        root.put("owner", owner)
        root.put("profile", profile)
        root.put("countdown", countdownSeconds)
        root.put("contacts", contactsArray)

        val result = root.toString()

        // Sanity check ukuran payload terhadap batas firmware
        if (result.toByteArray().size > 768) {
            Log.e("BLE_BUILD", "Config payload exceeds 768 bytes: ${result.length} chars")
            return null
        }

        return result
    }
}