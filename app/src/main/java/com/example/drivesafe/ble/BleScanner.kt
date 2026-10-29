package com.example.drivesafe.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.drivesafe.model.BleDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Tugas tunggal: mencari alat DriveSafe di sekitar.
 * Tidak connect sendiri -- hasilnya dilaporkan lewat [devices], yang memutuskan
 * mau connect ke mana adalah pemanggil.
 */
@SuppressLint("MissingPermission")
class BleScanner(context: Context) {

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())

    private val _devices = MutableStateFlow<List<BleDevice>>(emptyList())
    val devices: StateFlow<List<BleDevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val stopRunnable = Runnable { stopScan() }

    private val scanCallback = object : ScanCallback() {

        override fun onScanResult(callbackType: Int, result: ScanResult) {

            val record = result.scanRecord
            val name = record?.deviceName ?: result.device.name

            Log.d(TAG, "Seen: name=$name addr=${result.device.address} rssi=${result.rssi} uuids=${record?.serviceUuids}")

            val hasService =
                record?.serviceUuids?.any { it.uuid == BleConstants.SERVICE_UUID } == true
            val hasPrefix =
                name?.startsWith(BleConstants.DEVICE_NAME_PREFIX, ignoreCase = true) == true

            // Terima kalau service UUID cocok ATAU namanya berawalan "DriveSafe-"
            if (!hasService && !hasPrefix) return

            val found = BleDevice(
                name = name,
                address = result.device.address,
                rssi = result.rssi
            )

            _devices.update { old ->
                (old.filterNot { it.address == found.address } + found)
                    .sortedByDescending { it.rssi }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "Scan failed: $errorCode")
            _isScanning.value = false
        }
    }

    private fun adapter(): BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

    /** @return null kalau scan jalan, atau pesan error untuk ditampilkan ke user. */
    fun startScan(): String? {

        if (!BlePermissions.hasPermissions(appContext)) {
            return "Izin Bluetooth belum diberikan"
        }
        if (!BlePermissions.isBluetoothEnabled(appContext)) {
            return "Bluetooth mati. Nyalakan dulu."
        }
        if (_isScanning.value) return null

        val scanner = adapter()?.bluetoothLeScanner
            ?: return "Scanner BLE tidak tersedia"

        _devices.value = emptyList()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        return try {
            scanner.startScan(null, settings, scanCallback)
            _isScanning.value = true
            handler.removeCallbacks(stopRunnable)
            handler.postDelayed(stopRunnable, SCAN_TIMEOUT_MS)
            Log.d(TAG, "Scan started")
            null
        } catch (e: Exception) {
            Log.e(TAG, "startScan failed", e)
            "Gagal memulai scan: ${e.message}"
        }
    }

    fun stopScan() {

        handler.removeCallbacks(stopRunnable)
        if (!_isScanning.value) return

        try {
            adapter()?.bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: Exception) {
            Log.w(TAG, "stopScan failed", e)
        }

        _isScanning.value = false
        Log.d(TAG, "Scan stopped")
    }

    private companion object {
        const val TAG = "BLE"
        const val SCAN_TIMEOUT_MS = 15_000L
    }
}