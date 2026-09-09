package com.example.drivesafe.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.util.Log
import com.example.drivesafe.model.BleDevice

class BleScanner(
    private val context: Context,
    private val onDeviceFound: (BluetoothDevice) -> Unit
) {
    private val bluetoothManager =
        context.getSystemService(
            Context.BLUETOOTH_SERVICE
        ) as BluetoothManager

    private val bluetoothAdapter =
        bluetoothManager.adapter

    private val scanner =
        bluetoothAdapter?.bluetoothLeScanner

    private val scanCallback =
        object : ScanCallback() {

            @SuppressLint("MissingPermission")
            override fun onScanResult(
                callbackType: Int,
                result: ScanResult
            ) {

                val device = result.device
                val name = device.name ?: return

                if (name == BleConstants.DEVICE_NAME) {

                    Log.d(
                        "BLE",
                        "Device found: $name (${device.address})"
                    )

                    onDeviceFound(device)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e("BLE", "Scan failed with error code: $errorCode")
            }
        }

    @SuppressLint("MissingPermission")
    fun startScan() {

        if (!BlePermissions.hasPermissions(context)) {
            Log.e("BLE", "Cannot scan: permission not granted")
            return
        }

        if (!BlePermissions.isBluetoothEnabled(context)) {
            Log.e("BLE", "Cannot scan: Bluetooth is disabled")
            return
        }

        Log.d("BLE", "Starting scan...")
        scanner?.startScan(scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {

        Log.d("BLE", "Stopping scan...")
        scanner?.stopScan(scanCallback)
    }
}