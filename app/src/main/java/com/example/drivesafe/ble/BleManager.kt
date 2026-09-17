package com.example.drivesafe.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.util.Log
import android.bluetooth.BluetoothGattCharacteristic

class BleManager(
    private val context: Context
) {

    private var bluetoothGatt: BluetoothGatt? = null

    private var retryCount = 0

    private val gattCallback =
        object : BluetoothGattCallback() {

            @SuppressLint("MissingPermission")
            override fun onConnectionStateChange(
                gatt: BluetoothGatt,
                status: Int,
                newState: Int
            ) {

                if (status == 133) {

                    Log.e("BLE", "Got status 133, retrying...")

                    gatt.close()

                    if (retryCount < 2) {
                        retryCount++

                        // Delay dikit sebelum retry
                        android.os.Handler(context.mainLooper).postDelayed({
                            connect(gatt.device)
                        }, 600)
                    } else {
                        Log.e("BLE", "Failed after retries")
                        retryCount = 0
                    }

                    return
                }

                if (newState == BluetoothProfile.STATE_CONNECTED) {

                    Log.d("BLE", "Connected")
                    retryCount = 0

                    // Minta MTU lebih besar SEBELUM discover services
                    gatt.requestMtu(517)  // 517 = MTU maksimum yang didukung BLE
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

                    Log.d("BLE_DISCOVER", "Total services found: ${gatt.services.size}")

                    for (service in gatt.services) {

                        Log.d("BLE_DISCOVER", "Service UUID: ${service.uuid}")

                        for (characteristic in service.characteristics) {
                            Log.d("BLE_DISCOVER", "  -> Characteristic UUID: ${characteristic.uuid}")
                        }
                    }

                    val service =
                        gatt.getService(
                            BleConstants.SERVICE_UUID
                        )

                    if (service != null) {
                        Log.d("BLE", "Service Found")
                        subscribeToStatus()
                    } else {
                        Log.e("BLE", "Service NOT found - cek UUID atau ESP32 belum bikin service")
                    }
                }
            }

            override fun onCharacteristicWrite(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d("BLE", "Write SUCCESS: ${characteristic.uuid}")
                } else {
                    Log.e("BLE", "Write FAILED: ${characteristic.uuid}, status=$status")
                }
            }

            override fun onDescriptorWrite(
                gatt: BluetoothGatt,
                descriptor: android.bluetooth.BluetoothGattDescriptor,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d("BLE", "Subscribed successfully to ${descriptor.characteristic.uuid}")
                } else {
                    Log.e("BLE", "Failed to subscribe, status=$status")
                }
            }

            @Suppress("DEPRECATION")
            override fun onCharacteristicChanged(
                gatt: BluetoothGatt,
                characteristic: BluetoothGattCharacteristic
            ) {
                val json = String(characteristic.value)

                when (characteristic.uuid) {

                    BleConstants.STATUS_CHARACTERISTIC_UUID -> {
                        val payload = BleJsonParser.parseStatus(json)
                        if (payload != null) {
                            Log.d("BLE_STATUS", "st=${payload.status}, bat=${payload.batteryPercent}%, cd=${payload.countdownSeconds}s")
                        }
                    }

                    BleConstants.EVENT_CHARACTERISTIC_UUID -> {
                        val event = BleJsonParser.parseEvent(json)
                        if (event != null) {
                            Log.d("BLE_EVENT", "phase=${event.phase}, severity=${event.severityName}, type=${event.typeName}")
                        }
                    }
                }
            }

            @SuppressLint("MissingPermission")
            override fun onMtuChanged(
                gatt: BluetoothGatt,
                mtu: Int,
                status: Int
            ) {
                if (status == BluetoothGatt.GATT_SUCCESS) {
                    Log.d("BLE", "MTU changed to $mtu")
                } else {
                    Log.e("BLE", "MTU change failed, status=$status")
                }

                // Lanjut discover services setelah MTU beres (berhasil atau gagal,
                // tetap lanjut karena beberapa device gagal negotiate tapi tetap oke)
                discoverServices(gatt)
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
                gattCallback,
                android.bluetooth.BluetoothDevice.TRANSPORT_LE
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

    @SuppressLint("MissingPermission")
    fun sendConfig(configJson: String) {

        val service =
            bluetoothGatt?.getService(BleConstants.SERVICE_UUID) ?: run {
                Log.e("BLE", "Cannot send config: service not found")
                return
            }

        val characteristic =
            service.getCharacteristic(BleConstants.CONFIG_CHARACTERISTIC_UUID) ?: run {
                Log.e("BLE", "Cannot send config: characteristic not found")
                return
            }

        // WAJIB pakai WRITE_TYPE_DEFAULT (Write With Response)
        // karena firmware ngumpulin chunk sampai kurung kurawal seimbang.
        // Kalau pakai NO_RESPONSE dan chunk terakhir drop, config nyangkut
        // di buffer ESP32 tanpa error apapun.
        characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT

        characteristic.value = configJson.toByteArray()

        val success = bluetoothGatt?.writeCharacteristic(characteristic)

        Log.d("BLE", "sendConfig() write initiated: $success")
    }

    @SuppressLint("MissingPermission")
    fun sendCommand(command: String) {

        val service =
            bluetoothGatt?.getService(BleConstants.SERVICE_UUID) ?: run {
                Log.e("BLE", "Cannot send command: service not found")
                return
            }

        val characteristic =
            service.getCharacteristic(BleConstants.CMD_CHARACTERISTIC_UUID) ?: run {
                Log.e("BLE", "Cannot send command: characteristic not found")
                return
            }

        // Command plain text, bukan JSON. Firmware pakai strncasecmp jadi
        // case-insensitive dan prefix match, tapi kita tetep kirim persis
        // sesuai daftar biar konsisten.
        characteristic.value = command.toByteArray()

        val success = bluetoothGatt?.writeCharacteristic(characteristic)

        Log.d("BLE", "sendCommand($command) write initiated: $success")
    }

    @SuppressLint("MissingPermission")
    fun subscribeToStatus() {

        val service =
            bluetoothGatt?.getService(BleConstants.SERVICE_UUID) ?: run {
                Log.e("BLE", "Cannot subscribe: service not found")
                return
            }

        val characteristic =
            service.getCharacteristic(BleConstants.STATUS_CHARACTERISTIC_UUID) ?: run {
                Log.e("BLE", "Cannot subscribe: status characteristic not found")
                return
            }

        val success = bluetoothGatt?.setCharacteristicNotification(characteristic, true)
        Log.d("BLE", "setCharacteristicNotification result: $success")

        val cccd = characteristic.getDescriptor(BleConstants.CCCD_UUID)

        if (cccd == null) {
            Log.e("BLE", "CCCD descriptor not found on status characteristic")
            return
        }

        cccd.value = android.bluetooth.BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        bluetoothGatt?.writeDescriptor(cccd)
    }


}