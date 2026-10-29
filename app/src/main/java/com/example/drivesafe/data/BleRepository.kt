package com.example.drivesafe.data

import com.example.drivesafe.ble.BleCommands
import com.example.drivesafe.ble.BleJsonParser
import com.example.drivesafe.ble.BleManager
import com.example.drivesafe.ble.BleScanner
import com.example.drivesafe.model.AccidentEvent
import com.example.drivesafe.model.BleDevice
import com.example.drivesafe.model.ConnectionState
import com.example.drivesafe.model.DeviceConfig
import com.example.drivesafe.model.DeviceConfigSnapshot
import com.example.drivesafe.model.DeviceStatus
import com.example.drivesafe.model.DeviceStatusPayload
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

sealed interface SyncResult {
    object Success : SyncResult
    object NotConnected : SyncResult
    data class Invalid(val message: String) : SyncResult
    object WriteFailed : SyncResult
    object NotConfirmed : SyncResult
    /** Tulisan diterima, tapi isi yang dibaca balik dari alat tidak sama (misal ada nomor yang ditolak diam-diam). */
    object ContentMismatch : SyncResult
}

sealed interface BalanceResult {
    object Success : BalanceResult
    object NotConnected : BalanceResult
    data class Invalid(val message: String) : BalanceResult
    /** Firmware hanya menjalankan cek pulsa saat state MONITOR. */
    object Busy : BalanceResult
    object WriteFailed : BalanceResult
    /** Alat menghentikan cek pulsa karena mendeteksi kejadian (firmware 1.2.0+). */
    object Interrupted : BalanceResult
    /** Perintah diterima tapi tidak ada hasil baru dalam batas waktu (SIM tanpa sinyal/pulsa, USSD gagal). */
    object NoReply : BalanceResult
}

/**
 * Penghubung BLE + penyimpanan lokal. ViewModel hanya bicara ke kelas ini.
 */
class BleRepository(
    private val scanner: BleScanner,
    private val bleManager: BleManager,
    private val configRepository: ConfigRepository
) {

    // ---------- Yang diamati UI ----------

    val scanResults: StateFlow<List<BleDevice>> = scanner.devices

    /** SCANNING hanya ditampilkan kalau memang belum/tidak sedang tersambung. */
    val connectionState: Flow<ConnectionState> =
        combine(bleManager.connectionState, scanner.isScanning) { connection, scanning ->
            val idle = connection == ConnectionState.DISCONNECTED || connection == ConnectionState.ERROR
            if (scanning && idle) ConnectionState.SCANNING else connection
        }

    val status: StateFlow<DeviceStatusPayload?> = bleManager.status
    val lastEvent: StateFlow<AccidentEvent?> = bleManager.lastEvent
    val deviceName: StateFlow<String?> = bleManager.deviceName
    val messages: SharedFlow<String> = bleManager.messages
    val needsSync: Flow<Boolean> = configRepository.needsSync

    /** Isi CONFIG yang terakhir dibaca dari alat. null = belum pernah terbaca di sambungan ini. */
    private val _deviceConfig = MutableStateFlow<DeviceConfigSnapshot?>(null)
    /** Hanya berlaku selama tersambung: begitu putus, isi alat tidak boleh tampil seolah masih terbaca. */
    val deviceConfig: Flow<DeviceConfigSnapshot?> =
        combine(bleManager.connectionState, _deviceConfig) { connection, snapshot ->
            if (connection == ConnectionState.READY) snapshot else null
        }

    // ---------- Koneksi ----------

    /** @return null kalau scan jalan, atau pesan error. */
    fun startScan(): String? = scanner.startScan()

    fun stopScan() = scanner.stopScan()

    suspend fun connect(device: BleDevice) {
        scanner.stopScan()

        // Alat lain: buang sisa data alat sebelumnya (isi alat dan kejadian lama)
        val previous = configRepository.lastDeviceAddress.first()
        if (previous != device.address) {
            _deviceConfig.value = null
            bleManager.clearEvent()
        }

        configRepository.setLastDevice(device.address)
        bleManager.connect(device.address)
    }

    /** Sambung ke alat terakhir tanpa scan. false kalau belum pernah ada. */
    suspend fun autoConnect(): Boolean {
        val address = configRepository.lastDeviceAddress.first() ?: return false
        bleManager.connect(address)
        return true
    }

    fun disconnect() {
        bleManager.disconnect()
        _deviceConfig.value = null
    }

    suspend fun forgetDevice() {
        disconnect()
        configRepository.clearLastDevice()
    }

    /** Tutup layar notifikasi darurat. */
    fun clearEvent() = bleManager.clearEvent()

    // ---------- Baca konfigurasi dari alat ----------

    /**
     * Baca CONFIG dari alat. null kalau gagal -- termasuk firmware lama yang characteristic
     * CONFIG-nya belum bisa dibaca (firmware < 1.1.1).
     */
    suspend fun refreshDeviceConfig(): DeviceConfigSnapshot? {
        val json = bleManager.readConfig() ?: return null
        val snapshot = BleJsonParser.parseConfig(json) ?: return null
        _deviceConfig.value = snapshot
        return snapshot
    }

    /** Jadikan isi alat sebagai isi lokal (menimpa kontak dan pengaturan di HP). */
    suspend fun importFromDevice(): Boolean {
        val snapshot = refreshDeviceConfig() ?: return false
        val local = configRepository.currentConfig()
        configRepository.replaceAll(snapshot.toDeviceConfig(local))
        return true
    }

    // ---------- Perintah ----------

    suspend fun sendCommand(command: String): Boolean = bleManager.sendCommand(command)

    /**
     * Firmware tidak membalas perintah. Efek CANCEL dibuktikan lewat STATUS:
     * `st` harus berubah dari COUNTDOWN ke CANCELLED dalam ~1 detik.
     * @return true kalau pembatalan terkonfirmasi.
     */
    suspend fun cancelAlarm(): Boolean {
        if (!bleManager.sendCommand(BleCommands.CANCEL)) return false

        val confirmed = withTimeoutOrNull(CANCEL_CONFIRM_TIMEOUT_MS) {
            bleManager.status.first {
                it?.status == DeviceStatus.CANCELLED || it?.status == DeviceStatus.COOLDOWN
            }
        }
        return confirmed != null
    }

    // ---------- Sinkronisasi ----------

    /**
     * Kirim seluruh konfigurasi ke alat. Firmware MENGGANTI seluruh daftar kontak lama,
     * jadi selalu kirim daftar lengkap. Firmware menolak nilai tidak valid tanpa pesan,
     * jadi keberhasilan dibuktikan dengan MEMBACA BALIK CONFIG (firmware 1.1.1+) dan
     * membandingkan isinya. Kalau CONFIG tidak bisa dibaca (firmware lama), jatuh ke
     * pengecekan jumlah kontak lewat field `con` di STATUS -- itu tidak membuktikan isinya.
     */
    suspend fun syncConfig(): SyncResult {

        if (bleManager.connectionState.value != ConnectionState.READY) {
            return SyncResult.NotConnected
        }

        val config = configRepository.currentConfig()

        val json = BleJsonParser.buildConfigJson(config).getOrElse {
            return SyncResult.Invalid(it.message ?: "Konfigurasi tidak valid")
        }

        if (!bleManager.sendConfig(json)) return SyncResult.WriteFailed

        // Daftar kosong: kunci "contacts" tidak dikirim, jadi kosongkan lewat CLEARCON
        if (config.contacts.isEmpty() && !bleManager.sendCommand(BleCommands.CLEARCON)) {
            return SyncResult.WriteFailed
        }

        return when (verifyByReadBack(config)) {
            Verification.MATCH -> {
                configRepository.markSynced(config)
                SyncResult.Success
            }
            Verification.MISMATCH -> SyncResult.ContentMismatch
            Verification.UNREADABLE -> verifyByContactCount(config)
        }
    }

    private enum class Verification { MATCH, MISMATCH, UNREADABLE }

    private suspend fun verifyByReadBack(config: DeviceConfig): Verification {
        // CLEARCON diproses di loop alat, bukan seketika: beri beberapa kesempatan.
        repeat(READ_BACK_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(READ_BACK_RETRY_DELAY_MS)
            val snapshot = refreshDeviceConfig()
            if (snapshot == null) {
                if (attempt == 0) return Verification.UNREADABLE
            } else if (snapshot.matches(config)) {
                return Verification.MATCH
            }
        }
        return Verification.MISMATCH
    }

    private suspend fun verifyByContactCount(config: DeviceConfig): SyncResult {
        val expected = config.contacts.size
        val confirmed = withTimeoutOrNull(SYNC_CONFIRM_TIMEOUT_MS) {
            bleManager.status.first { it != null && it.contactCount == expected }
        }
        if (confirmed == null) return SyncResult.NotConfirmed

        configRepository.markSynced(config)
        return SyncResult.Success
    }

    // ---------- Cek pulsa ----------

    /**
     * Kirim `PULSA <kode>` lalu baca CONFIG berkala sampai `bal` berisi hasil BARU.
     * USSD memakan waktu sampai ~30 detik dan firmware tidak membalas lewat BLE, jadi hasil dikenali
     * dari `balage` (umur hasil, detik): hasil baru pasti lebih muda dari waktu sejak perintah dikirim.
     * Selama USSD jalan, STATUS dari alat bisa berhenti sebentar; itu normal.
     */
    suspend fun checkBalance(ussdCode: String): BalanceResult {

        if (bleManager.connectionState.value != ConnectionState.READY) return BalanceResult.NotConnected

        BleJsonParser.validateUssd(ussdCode)?.let { return BalanceResult.Invalid(it) }

        if (bleManager.status.value?.status != DeviceStatus.MONITOR) return BalanceResult.Busy

        val sentAt = System.currentTimeMillis()
        if (!bleManager.sendCommand(BleCommands.pulsa(ussdCode))) return BalanceResult.WriteFailed

        while (System.currentTimeMillis() - sentAt < BALANCE_TIMEOUT_MS) {
            delay(BALANCE_POLL_MS)

            // Firmware langsung membatalkan USSD begitu keadaan bukan MONITOR (ada kejadian)
            val current = bleManager.status.value?.status
            if (current == DeviceStatus.VERIFY || current == DeviceStatus.COUNTDOWN ||
                current == DeviceStatus.ALERT
            ) {
                return BalanceResult.Interrupted
            }

            val snapshot = refreshDeviceConfig() ?: continue
            val elapsedSeconds = (System.currentTimeMillis() - sentAt) / 1000
            if (snapshot.balanceText.isNotBlank() && snapshot.balanceAgeSeconds <= elapsedSeconds + 2) {
                return BalanceResult.Success
            }
        }
        return BalanceResult.NoReply
    }

    private companion object {
        const val BALANCE_TIMEOUT_MS = 45_000L
        const val BALANCE_POLL_MS = 3_000L
        const val CANCEL_CONFIRM_TIMEOUT_MS = 4_000L
        const val SYNC_CONFIRM_TIMEOUT_MS = 5_000L
        const val READ_BACK_ATTEMPTS = 4
        const val READ_BACK_RETRY_DELAY_MS = 600L
    }
}