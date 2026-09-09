package com.example.drivesafe.ble

import java.util.UUID

object BleConstants {

    const val DEVICE_NAME = "ACCIDENT_DETECTOR"

    val SERVICE_UUID: UUID =
        UUID.fromString(
            "12345678-1234-1234-1234-123456789abc"
        )

    val STATUS_CHARACTERISTIC_UUID: UUID =
        UUID.fromString(
            "12345678-1234-1234-1234-123456789001"
        )

    val CONTACT_CHARACTERISTIC_UUID: UUID =
        UUID.fromString(
            "12345678-1234-1234-1234-123456789002"
        )

    val COMMAND_CHARACTERISTIC_UUID: UUID =
        UUID.fromString(
            "12345678-1234-1234-1234-123456789003"
        )

    val RESPONSE_CHARACTERISTIC_UUID: UUID =
        UUID.fromString(
            "12345678-1234-1234-1234-123456789004"
        )

    // CCCD UUID standar buat enable notification (dipake nanti di Bagian 14)
    val CCCD_UUID: UUID =
        UUID.fromString(
            "00002902-0000-1000-8000-00805f9b34fb"
        )
}