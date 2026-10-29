package com.example.drivesafe.ble

import java.util.UUID

object BleConstants {

    const val DEVICE_NAME_PREFIX = "DriveSafe-"

    // Panduan firmware: requestMtu(247) sebelum discover service
    const val REQUESTED_MTU = 247

    // Buffer perintah di firmware 23 byte: "PULSA " (6) + kode USSD, jadi kode maksimal 17 karakter
    const val MAX_USSD_LENGTH = 17

    val SERVICE_UUID: UUID =
        UUID.fromString("4f1a0000-9c1e-4a2b-8d33-7b21c0de5afe")

    // WRITE  - app -> alat (JSON: owner, profile, countdown, contacts)
    val CONFIG_CHARACTERISTIC_UUID: UUID =
        UUID.fromString("4f1a0001-9c1e-4a2b-8d33-7b21c0de5afe")

    // NOTIFY - alat -> app, tiap 1 detik (JSON status)
    val STATUS_CHARACTERISTIC_UUID: UUID =
        UUID.fromString("4f1a0002-9c1e-4a2b-8d33-7b21c0de5afe")

    // NOTIFY - alat -> app, hanya saat kecelakaan (JSON event)
    val EVENT_CHARACTERISTIC_UUID: UUID =
        UUID.fromString("4f1a0003-9c1e-4a2b-8d33-7b21c0de5afe")

    // WRITE  - app -> alat (teks biasa, bukan JSON)
    val CMD_CHARACTERISTIC_UUID: UUID =
        UUID.fromString("4f1a0004-9c1e-4a2b-8d33-7b21c0de5afe")

    val CCCD_UUID: UUID =
        UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}

object BleCommands {
    const val CANCEL = "CANCEL"
    const val TEST_SMS = "TEST_SMS"
    const val RECAL = "RECAL"
    const val SIMCRASH = "SIMCRASH"
    const val CLEARCON = "CLEARCON"

    /** Cek pulsa lewat USSD. Kodenya ikut dikirim karena beda tiap operator. */
    fun pulsa(ussdCode: String) = "PULSA $ussdCode"
}