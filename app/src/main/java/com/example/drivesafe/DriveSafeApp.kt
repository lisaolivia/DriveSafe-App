package com.example.drivesafe

import android.app.Application
import com.example.drivesafe.ble.BleManager
import com.example.drivesafe.ble.BleScanner
import com.example.drivesafe.data.BleRepository
import com.example.drivesafe.data.ConfigRepository

/**
 * Menyimpan satu instance BleManager untuk seluruh proses, jadi koneksi BLE
 * tidak putus waktu Activity dibuat ulang (rotasi layar dll).
 */
class DriveSafeApp : Application() {

    lateinit var configRepository: ConfigRepository
        private set

    lateinit var bleRepository: BleRepository
        private set

    override fun onCreate() {
        super.onCreate()
        configRepository = ConfigRepository(this)
        bleRepository = BleRepository(
            scanner = BleScanner(this),
            bleManager = BleManager(this),
            configRepository = configRepository
        )
    }
}