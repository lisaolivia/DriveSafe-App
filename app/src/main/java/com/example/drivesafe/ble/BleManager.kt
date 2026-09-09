package com.example.drivesafe.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log

class BleManager(
    private val context: Context
) {

    private var bluetoothGatt: BluetoothGatt? = null

    private val gattCallback =
        object : BluetoothGattCallback() {

            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int
            ) {

                if (newState == BluetoothProfile.STATE_CONNECTED) {

                    Log.d("BLE", "Connected")

                    discoverServices(gatt)

                }

                if (newState == BluetoothProfile.STATE_DISCONNECTED) {

                    Log.d("BLE", "Disconnected")
                }
            }

            override fun onServicesDiscovered(
                gatt: BluetoothGatt,
                status: Int
            ) {

                if (status == BluetoothGatt.GATT_SUCCESS) {

                    val service =
                        gatt.getService(
                            BleConstants.SERVICE_UUID
                        )

                    if (service != null) {

                        Log.d("BLE", "Service Found")

                    } else {

                        Log.e("BLE", "Service NOT found - cek UUID atau ESP32 belum bikin service")
                    }
                }
            }
        }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {

        if (!BlePermissions.hasPermissions(context)) {
            Log.e("BLE", "Cannot connect: permission not granted")
            return
        }

        Log.d("BLE", "Connecting to ${device.address}...")

        bluetoothGatt =
            device.connectGatt(
                context,
                false,
                gattCallback
            )
    }

    @SuppressLint("MissingPermission")
    private fun discoverServices(gatt: BluetoothGatt) {

        Log.d("BLE", "Discovering services...")
        gatt.discoverServices()
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {

        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
    }
}