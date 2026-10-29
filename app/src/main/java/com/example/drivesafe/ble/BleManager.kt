package com.example.drivesafe.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.drivesafe.model.AccidentEvent
import com.example.drivesafe.model.ConnectionState
import com.example.drivesafe.model.DeviceStatusPayload
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Satu-satunya kelas yang menyentuh BluetoothGatt.
 *
 * Alur: connect -> requestMtu(247) -> discoverServices -> aktifkan notifikasi
 * STATUS + EVENT -> READY. Kalau putus tanpa diminta user, coba sambung ulang.
 *
 * Android hanya mengizinkan SATU operasi GATT pada satu waktu, jadi semua
 * operasi (tulis descriptor, tulis characteristic) lewat antrean [enqueue].
 */
@SuppressLint("MissingPermission")
class BleManager(context: Context) {

    private val appContext = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())

    // ---------- State yang dibaca ViewModel ----------

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _status = MutableStateFlow<DeviceStatusPayload?>(null)
    val status: StateFlow<DeviceStatusPayload?> = _status.asStateFlow()

    private val _lastEvent = MutableStateFlow<AccidentEvent?>(null)
    val lastEvent: StateFlow<AccidentEvent?> = _lastEvent.asStateFlow()

    private val _deviceName = MutableStateFlow<String?>(null)
    val deviceName: StateFlow<String?> = _deviceName.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    // ---------- Internal ----------

    private var gatt: BluetoothGatt? = null
    private var targetAddress: String? = null
    private var userDisconnected = false
    private var reconnectAttempts = 0
    private var mtu = 23

    private val opLock = Any()
    private val opQueue = ArrayDeque<() -> Boolean>()
    private var opBusy = false

    private val pendingWrites = ConcurrentHashMap<UUID, CompletableDeferred<Boolean>>()
    private val pendingReads = ConcurrentHashMap<UUID, CompletableDeferred<ByteArray?>>()

    /** seq kejadian yang sudah ditutup user. SENT susulan dengan seq sama tidak boleh memunculkannya lagi. */
    private var dismissedSeq: Int? = null

    // =====================================================================
    // API publik
    // =====================================================================

    fun connect(address: String) {

        if (!BlePermissions.hasPermissions(appContext)) {
            fail("Izin Bluetooth belum diberikan")
            return
        }
        if (!BlePermissions.isBluetoothEnabled(appContext)) {
            fail("Bluetooth mati. Nyalakan dulu.")
            return
        }

        val state = _connectionState.value
        val active = state == ConnectionState.CONNECTING ||
                state == ConnectionState.CONNECTED ||
                state == ConnectionState.READY
        if (active && address == targetAddress) return

        userDisconnected = false
        targetAddress = address
        reconnectAttempts = 0
        openGatt(INITIAL_DELAY_MS)
    }

    fun disconnect() {

        userDisconnected = true
        targetAddress = null
        handler.removeCallbacksAndMessages(null)

        try {
            gatt?.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "disconnect failed", e)
        }
        closeGatt()

        _status.value = null
        dismissedSeq = null
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    /** Tulis JSON ke CONFIG (Write With Response). true = alat menerima tulisan. */
    suspend fun sendConfig(json: String): Boolean =
        write(BleConstants.CONFIG_CHARACTERISTIC_UUID, json.toByteArray(Charsets.UTF_8))

    /** Kirim perintah teks biasa ke CMD. true = tulisan diterima; EFEK-nya lihat di STATUS berikutnya. */
    suspend fun sendCommand(command: String): Boolean =
        write(BleConstants.CMD_CHARACTERISTIC_UUID, command.toByteArray(Charsets.UTF_8))

    /**
     * Baca CONFIG (firmware 1.1.1+): nama alat, pemilik, profil, hitung mundur, daftar kontak.
     * @return teks JSON, atau null kalau gagal / firmware lama yang belum bisa dibaca.
     */
    suspend fun readConfig(): String? {

        if (_connectionState.value != ConnectionState.READY) return null

        val uuid = BleConstants.CONFIG_CHARACTERISTIC_UUID
        val deferred = CompletableDeferred<ByteArray?>()
        if (pendingReads.putIfAbsent(uuid, deferred) != null) return null

        enqueue {
            val g = gatt
            val characteristic = g?.getService(BleConstants.SERVICE_UUID)?.getCharacteristic(uuid)

            val started = g != null && characteristic != null && g.readCharacteristic(characteristic)
            if (!started) pendingReads.remove(uuid)?.complete(null)
            started
        }

        val result = withTimeoutOrNull(READ_TIMEOUT_MS) { deferred.await() }
        if (result == null) pendingReads.remove(uuid, deferred)
        return result?.toString(Charsets.UTF_8)
    }

    /**
     * Panggil saat user menutup layar notifikasi darurat. Alat hanya mengirim EVENT dua kali per
     * kejadian (SENDING lalu SENT); nilai yang tersisa di sini tidak ikut terhapus dengan sendirinya.
     */
    fun clearEvent() {
        val seq = _lastEvent.value?.seq
        if (seq != null && seq >= 0) dismissedSeq = seq
        _lastEvent.value = null
    }

    // =====================================================================
    // Koneksi
    // =====================================================================

    private fun fail(message: String) {
        _messages.tryEmit(message)
        _connectionState.value = ConnectionState.ERROR
    }

    private fun openGatt(delayMs: Long) {

        val address = targetAddress ?: return

        handler.removeCallbacksAndMessages(null)
        closeGatt()
        _connectionState.value = ConnectionState.CONNECTING

        handler.postDelayed({

            if (userDisconnected || targetAddress != address) return@postDelayed

            val adapter =
                (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

            val device = try {
                adapter.getRemoteDevice(address)
            } catch (e: IllegalArgumentException) {
                null
            }

            if (device == null) {
                fail("Alamat alat tidak valid")
                return@postDelayed
            }

            Log.d(TAG, "connectGatt -> $address")
            gatt = device.connectGatt(
                appContext,
                false,
                gattCallback,
                BluetoothDevice.TRANSPORT_LE
            )

        }, delayMs)
    }

    private fun closeGatt() {

        synchronized(opLock) {
            opQueue.clear()
            opBusy = false
        }
        failPendingWrites()

        try {
            gatt?.close()
        } catch (e: Exception) {
            Log.w(TAG, "close failed", e)
        }
        gatt = null
    }

    private fun handleDisconnect(status: Int) {

        Log.w(TAG, "Disconnected (status=$status)")

        _status.value = null   // data lama tidak boleh tampil seolah masih live
        dismissedSeq = null    // alat bisa saja restart, nomor seq mulai dari awal lagi
        closeGatt()

        if (userDisconnected || targetAddress == null) {
            _connectionState.value = ConnectionState.DISCONNECTED
            return
        }

        if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
            _connectionState.value = ConnectionState.ERROR
            _messages.tryEmit("Tidak bisa terhubung ke alat. Pastikan alat menyala dan dekat dengan HP.")
            return
        }

        reconnectAttempts++
        openGatt(RECONNECT_DELAY_MS * reconnectAttempts)
    }

    /** Kesalahan yang tidak ada gunanya diulang (misal service tidak ada). */
    private fun fatal(message: String) {
        userDisconnected = true
        targetAddress = null
        handler.removeCallbacksAndMessages(null)
        closeGatt()
        fail(message)
    }

    // =====================================================================
    // Antrean operasi GATT (satu operasi pada satu waktu)
    // =====================================================================

    /** [op] mengembalikan true kalau operasi async berhasil DIMULAI (callback akan menyusul). */
    private fun enqueue(op: () -> Boolean) {
        synchronized(opLock) { opQueue.addLast(op) }
        pump()
    }

    private fun pump() {
        while (true) {
            val op = synchronized(opLock) {
                if (opBusy) null
                else opQueue.removeFirstOrNull()?.also { opBusy = true }
            } ?: return

            val started = try {
                op()
            } catch (e: Exception) {
                Log.e(TAG, "GATT op threw", e)
                false
            }

            if (started) return

            // Tidak ada callback yang akan datang -> lanjut ke operasi berikutnya
            synchronized(opLock) { opBusy = false }
        }
    }

    private fun opCompleted() {
        synchronized(opLock) { opBusy = false }
        pump()
    }

    private fun enableNotification(characteristicUuid: UUID) {
        enqueue {
            val g = gatt ?: return@enqueue false

            val characteristic = g.getService(BleConstants.SERVICE_UUID)
                ?.getCharacteristic(characteristicUuid)
            if (characteristic == null) {
                Log.e(TAG, "Characteristic $characteristicUuid tidak ditemukan")
                return@enqueue false
            }

            val cccd = characteristic.getDescriptor(BleConstants.CCCD_UUID)
            if (cccd == null) {
                Log.e(TAG, "CCCD tidak ada di $characteristicUuid")
                return@enqueue false
            }

            if (!g.setCharacteristicNotification(characteristic, true)) return@enqueue false

            writeDescriptorCompat(g, cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
        }
    }

    // =====================================================================
    // Menulis ke alat
    // =====================================================================

    private suspend fun write(uuid: UUID, bytes: ByteArray): Boolean {

        if (_connectionState.value != ConnectionState.READY) {
            _messages.tryEmit("Alat belum terhubung")
            return false
        }

        if (bytes.size > mtu - 3) {
            Log.w(TAG, "Payload ${bytes.size} byte > MTU-3 (${mtu - 3}); Android akan pakai long write")
        }

        val deferred = CompletableDeferred<Boolean>()
        if (pendingWrites.putIfAbsent(uuid, deferred) != null) {
            Log.w(TAG, "Masih ada tulisan ke $uuid yang berjalan")
            return false
        }

        enqueue {
            val g = gatt
            val characteristic = g?.getService(BleConstants.SERVICE_UUID)?.getCharacteristic(uuid)

            val started = if (g != null && characteristic != null) {
                writeCharacteristicCompat(g, characteristic, bytes)
            } else {
                false
            }

            if (!started) pendingWrites.remove(uuid)?.complete(false)
            started
        }

        val result = withTimeoutOrNull(WRITE_TIMEOUT_MS) { deferred.await() }
        if (result == null) pendingWrites.remove(uuid, deferred)
        return result ?: false
    }

    private fun failPendingWrites() {
        pendingWrites.values.forEach { it.complete(false) }
        pendingWrites.clear()
        pendingReads.values.forEach { it.complete(null) }
        pendingReads.clear()
    }

    @Suppress("DEPRECATION")
    private fun writeCharacteristicCompat(
        g: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        bytes: ByteArray
    ): Boolean {
        // WAJIB Write With Response: firmware mengumpulkan potongan sampai kurung kurawal seimbang
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeCharacteristic(
                characteristic,
                bytes,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            ) == BluetoothStatusCodes.SUCCESS
        } else {
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            characteristic.value = bytes
            g.writeCharacteristic(characteristic)
        }
    }

    @Suppress("DEPRECATION")
    private fun writeDescriptorCompat(
        g: BluetoothGatt,
        descriptor: BluetoothGattDescriptor,
        value: ByteArray
    ): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(descriptor, value) == BluetoothStatusCodes.SUCCESS
        } else {
            descriptor.value = value
            g.writeDescriptor(descriptor)
        }
    }

    // =====================================================================
    // Menerima dari alat
    // =====================================================================

    private fun handleNotification(uuid: UUID, value: ByteArray) {

        val json = value.toString(Charsets.UTF_8)

        when (uuid) {
            BleConstants.STATUS_CHARACTERISTIC_UUID ->
                BleJsonParser.parseStatus(json)?.let { _status.value = it }

            BleConstants.EVENT_CHARACTERISTIC_UUID -> {
                Log.d(TAG, "EVENT: $json")
                BleJsonParser.parseEvent(json)?.let { handleEvent(it) }
            }
        }
    }

    /**
     * Satu kecelakaan = dua notifikasi (SENDING lalu SENT) dengan seq yang sama, jadi keduanya
     * dianggap satu kejadian: yang kedua hanya memperbarui fase. Saring pakai seq, BUKAN ts
     * (ts = 0 selama GPS belum fix).
     */
    private fun handleEvent(event: AccidentEvent) {
        if (event.seq >= 0 && event.seq == dismissedSeq) return   // sudah ditutup user
        _lastEvent.value = event
    }

    // =====================================================================
    // Callback GATT
    // =====================================================================

    private fun completeRead(uuid: UUID, value: ByteArray?, status: Int) {
        Log.d(TAG, "Read $uuid status=$status size=${value?.size}")
        val ok = status == BluetoothGatt.GATT_SUCCESS && value != null
        pendingReads.remove(uuid)?.complete(if (ok) value else null)
        opCompleted()
    }

    private val gattCallback = object : BluetoothGattCallback() {

        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {

            Log.d(TAG, "onConnectionStateChange status=$status newState=$newState")

            if (newState == BluetoothProfile.STATE_CONNECTED &&
                status == BluetoothGatt.GATT_SUCCESS
            ) {
                reconnectAttempts = 0
                _deviceName.value = g.device.name
                _connectionState.value = ConnectionState.CONNECTED

                // MTU dulu, baru discover service (default 23 byte memotong JSON)
                if (!g.requestMtu(BleConstants.REQUESTED_MTU)) g.discoverServices()

            } else {
                // putus biasa, atau error (termasuk 133)
                handleDisconnect(status)
            }
        }

        override fun onMtuChanged(g: BluetoothGatt, newMtu: Int, status: Int) {

            if (status == BluetoothGatt.GATT_SUCCESS) mtu = newMtu
            Log.d(TAG, "MTU = $mtu (status=$status)")

            g.discoverServices()
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {

            val service = g.getService(BleConstants.SERVICE_UUID)

            if (status != BluetoothGatt.GATT_SUCCESS || service == null) {
                fatal("Service DriveSafe tidak ditemukan di perangkat ini")
                return
            }

            Log.d(TAG, "Service ditemukan, mengaktifkan notifikasi")

            enableNotification(BleConstants.STATUS_CHARACTERISTIC_UUID)
            enableNotification(BleConstants.EVENT_CHARACTERISTIC_UUID)

            enqueue {
                Log.d(TAG, "READY")
                _connectionState.value = ConnectionState.READY
                false   // bukan operasi GATT, langsung lanjut
            }
        }

        override fun onDescriptorWrite(
            g: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int
        ) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(TAG, "Gagal aktifkan notifikasi ${descriptor.characteristic.uuid}, status=$status")
            }
            opCompleted()
        }

        // Android 12 ke bawah
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCharacteristicRead(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                completeRead(characteristic.uuid, characteristic.value, status)
            }
        }

        // Android 13+
        override fun onCharacteristicRead(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            completeRead(characteristic.uuid, value, status)
        }

        override fun onCharacteristicWrite(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            Log.d(TAG, "Write ${characteristic.uuid} status=$status")
            pendingWrites.remove(characteristic.uuid)?.complete(status == BluetoothGatt.GATT_SUCCESS)
            opCompleted()
        }

        // Android 12 ke bawah
        @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                val value = characteristic.value ?: return
                handleNotification(characteristic.uuid, value)
            }
        }

        // Android 13+
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleNotification(characteristic.uuid, value)
        }
    }

    private companion object {
        const val TAG = "BLE"
        const val INITIAL_DELAY_MS = 300L       // jeda setelah stop scan, mencegah status 133
        const val RECONNECT_DELAY_MS = 1_000L
        const val MAX_RECONNECT_ATTEMPTS = 6
        const val WRITE_TIMEOUT_MS = 5_000L
        const val READ_TIMEOUT_MS = 5_000L
    }
}