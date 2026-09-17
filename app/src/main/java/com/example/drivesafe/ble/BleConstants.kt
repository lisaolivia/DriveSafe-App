package com.example.drivesafe.ble

import java.util.UUID

object BleConstants {

    const val DEVICE_NAME_PREFIX = "DriveSafe-"

    val SERVICE_UUID: UUID =
        UUID.fromString("4f1a0000-9c1e-4a2b-8d33-7b21c0de5afe")

    // WRITE - kirim config/kontak ke ESP32 (JSON)
    val CONFIG_CHARACTERISTIC_UUID: UUID =
        UUID.fromString("4f1a0001-9c1e-4a2b-8d33-7b21c0de5afe")

    // NOTIFY - status berkala tiap 1 detik (JSON: st, bat, gps)
    val STATUS_CHARACTERISTIC_UUID: UUID =
        UUID.fromString("4f1a0002-9c1e-4a2b-8d33-7b21c0de5afe")

    // NOTIFY - event kecelakaan (fase SENDING/SENT)
    val EVENT_CHARACTERISTIC_UUID: UUID =
        UUID.fromString("4f1a0003-9c1e-4a2b-8d33-7b21c0de5afe")

    // WRITE - command (CANCEL, TEST_SMS, RECAL, SIMCRASH, CLEARCON)
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
}