package com.example.drivesafe.ble

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object BlePermissions {

    fun getRequiredPermissions(): Array<String> {

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ (API 31+)
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            // Android 11 ke bawah
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    fun hasPermissions(context: Context): Boolean {

        return getRequiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(
                context,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun isBluetoothEnabled(context: Context): Boolean {

        val bluetoothManager =
            context.getSystemService(
                Context.BLUETOOTH_SERVICE
            ) as BluetoothManager

        val adapter = bluetoothManager.adapter

        return adapter != null && adapter.isEnabled
    }
}