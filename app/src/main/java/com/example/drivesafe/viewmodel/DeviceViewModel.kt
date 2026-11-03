package com.example.drivesafe.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.drivesafe.DriveSafeApp
import com.example.drivesafe.ble.BleCommands
import com.example.drivesafe.data.BalanceResult
import com.example.drivesafe.data.SyncResult
import com.example.drivesafe.model.AccidentEvent
import com.example.drivesafe.model.BleDevice
import com.example.drivesafe.model.ConnectionState
import com.example.drivesafe.model.DeviceConfigSnapshot
import com.example.drivesafe.model.DeviceStatus
import com.example.drivesafe.model.DeviceStatusPayload
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** Hitung mundur kecelakaan. Non-null hanya saat st == COUNTDOWN. */
data class CountdownUi(
    val secondsLeft: Int,
    /** Durasi awal hitung mundur (untuk lingkaran progres). */
    val totalSeconds: Int,
    /** Firmware menolak CANCEL kurang dari 3 detik setelah deteksi, jadi tombol dikunci dulu. */
    val canCancel: Boolean
)

/** Kontrak untuk UI. Frontend cukup membaca ini. */
data class DeviceUiState(
    val connection: ConnectionState = ConnectionState.DISCONNECTED,
    val deviceName: String? = null,
    val status: DeviceStatusPayload? = null,
    val lastEvent: AccidentEvent? = null,
    val scanResults: List<BleDevice> = emptyList(),
    val needsSync: Boolean = true,
    val isSyncing: Boolean = false,
    val countdown: CountdownUi? = null,
    /** Isi CONFIG yang dibaca dari alat (firmware 1.1.1+). null = belum terbaca. */
    val deviceConfig: DeviceConfigSnapshot? = null,
    val isReadingConfig: Boolean = false,
    val isCheckingBalance: Boolean = false,
    /** Waktu (epoch ms) alarm dibatalkan sebelum SMS terkirim. Non-null = tampilkan layar "False alarm". */
    val cancelledAt: Long? = null,
    val isSendingNow: Boolean = false
) {
    val isOnline: Boolean get() = connection == ConnectionState.READY
}

class DeviceViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as DriveSafeApp).bleRepository

    private val _ui = MutableStateFlow(DeviceUiState())
    val ui: StateFlow<DeviceUiState> = _ui.asStateFlow()

    /** Pesan sekali tampil (snackbar/toast). */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var peakCountdown = 0
    private var wasReady = false
    private var previousStatus: DeviceStatus? = null

    /** Kejadian yang sedang berjalan sudah sampai tahap kirim SMS (ALERT terlihat atau EVENT datang). */
    private var incidentEscalated = false

    init {
        viewModelScope.launch {
            repo.connectionState.collect { v ->
                _ui.update { it.copy(connection = v) }

                // Begitu siap, baca isi alat supaya halaman Kontak menampilkan data yang sebenarnya
                val ready = v == ConnectionState.READY
                if (ready && !wasReady) readDeviceConfig(silent = true)
                wasReady = ready
            }
        }
        viewModelScope.launch {
            repo.deviceConfig.collect { v -> _ui.update { it.copy(deviceConfig = v) } }
        }
        viewModelScope.launch {
            repo.deviceName.collect { v -> _ui.update { it.copy(deviceName = v) } }
        }
        viewModelScope.launch {
            repo.status.collect { st ->
                val countdown = buildCountdown(st)
                val now = st?.status
                if (now == DeviceStatus.COUNTDOWN && previousStatus != DeviceStatus.COUNTDOWN) {
                    incidentEscalated = false   // kejadian baru
                }
                if (now == DeviceStatus.ALERT) incidentEscalated = true
                val cancelled = isCancelTransition(previousStatus, now)
                previousStatus = now ?: previousStatus
                _ui.update {
                    it.copy(
                        status = st,
                        countdown = countdown,
                        cancelledAt = if (cancelled) System.currentTimeMillis() else it.cancelledAt
                    )
                }
            }
        }
        viewModelScope.launch {
            repo.lastEvent.collect { v ->
                // EVENT hanya dikirim alat kalau SMS benar-benar dikirim, jadi bukan pembatalan
                if (v != null) incidentEscalated = true
                _ui.update { it.copy(lastEvent = v, cancelledAt = if (v != null) null else it.cancelledAt) }
            }
        }
        viewModelScope.launch {
            repo.scanResults.collect { v -> _ui.update { it.copy(scanResults = v) } }
        }
        viewModelScope.launch {
            repo.needsSync.collect { v -> _ui.update { it.copy(needsSync = v) } }
        }
        viewModelScope.launch {
            repo.messages.collect { _messages.emit(it) }
        }
    }

    /**
     * Hanya CANCELLED yang eksplisit dianggap pembatalan. COUNTDOWN -> COOLDOWN TIDAK dipakai:
     * saat mengirim SMS/missed call alat sibuk dan STATUS ALERT sering tidak sempat terkirim,
     * jadi aplikasi melihat COUNTDOWN langsung ke COOLDOWN padahal SMS sudah terkirim.
     */
    private fun isCancelTransition(from: DeviceStatus?, to: DeviceStatus?): Boolean =
        to == DeviceStatus.CANCELLED && from != to && !incidentEscalated &&
                (from == DeviceStatus.COUNTDOWN || from == DeviceStatus.ALERT)

    private fun buildCountdown(st: DeviceStatusPayload?): CountdownUi? {

        if (st == null || st.status != DeviceStatus.COUNTDOWN) {
            peakCountdown = 0
            return null
        }

        peakCountdown = maxOf(peakCountdown, st.countdownSeconds)
        val elapsed = peakCountdown - st.countdownSeconds

        return CountdownUi(
            secondsLeft = st.countdownSeconds,
            totalSeconds = peakCountdown,
            canCancel = elapsed >= MIN_CANCEL_ELAPSED_SECONDS
        )
    }

    // ---------- Koneksi ----------

    fun startScan() {
        val error = repo.startScan()
        if (error != null) viewModelScope.launch { _messages.emit(error) }
    }

    fun stopScan() = repo.stopScan()

    fun connect(device: BleDevice) {
        viewModelScope.launch { repo.connect(device) }
    }

    /** Panggil setelah izin Bluetooth diberikan. Sambung ke alat terakhir kalau ada. */
    fun autoConnect() {
        viewModelScope.launch { repo.autoConnect() }
    }

    fun disconnect() = repo.disconnect()

    fun forgetDevice() {
        viewModelScope.launch { repo.forgetDevice() }
    }

    // ---------- Kecelakaan ----------

    /** Tombol "SAYA BAIK-BAIK SAJA". */
    fun cancelAlarm() {
        viewModelScope.launch {
            val confirmed = repo.cancelAlarm()
            // repo menganggap COOLDOWN juga konfirmasi; pastikan SMS memang belum dikirim
            if (confirmed && !incidentEscalated && _ui.value.lastEvent == null) {
                _ui.update { it.copy(cancelledAt = it.cancelledAt ?: System.currentTimeMillis()) }
            }
            if (confirmed) {
                recalibrateAfterCancel()
            } else {
                _messages.emit(
                    "Pembatalan belum terkonfirmasi. Tekan SAYA BAIK-BAIK SAJA sekali lagi."
                )
            }
        }
    }

    /**
     * Guncangan yang memicu alarm bisa menggeser acuan sensor, jadi setelah "Saya baik-baik saja"
     * alat dikalibrasi ulang. RECAL baru dikirim setelah alat kembali MONITOR (selesai cooldown).
     */
    private suspend fun recalibrateAfterCancel() {
        val backToMonitor = withTimeoutOrNull(RECAL_AFTER_CANCEL_TIMEOUT_MS) {
            repo.status.first { it?.status == DeviceStatus.MONITOR }
        }
        if (backToMonitor == null) {
            _messages.emit("Alat belum kembali memantau. Kalibrasi ulang manual dari tab IoT.")
            return
        }
        val ok = repo.sendCommand(BleCommands.RECAL)
        _messages.emit(
            if (ok) "Kalibrasi ulang dimulai, letakkan alat diam di permukaan datar"
            else "Gagal memulai kalibrasi ulang. Coba dari tab IoT."
        )
    }

    /** Tutup layar notifikasi darurat (juga setelah "Saya baik-baik saja"). */
    fun dismissEvent() = repo.clearEvent()

    /** Tutup layar "False alarm dibatalkan". */
    fun dismissCancelled() {
        _ui.update { it.copy(cancelledAt = null) }
        repo.clearEvent()
    }

    /**
     * Tombol "KIRIM SEKARANG": lewati sisa hitung mundur. Butuh firmware yang mengenal SENDNOW;
     * kalau alat tidak pindah ke ALERT, SMS tetap terkirim otomatis saat hitung mundur habis.
     */
    fun sendNow() {
        if (_ui.value.isSendingNow) return
        _ui.update { it.copy(isSendingNow = true) }

        viewModelScope.launch {
            val confirmed = repo.sendNow()
            _ui.update { it.copy(isSendingNow = false) }
            if (!confirmed) {
                _messages.emit(
                    "Alat belum merespons KIRIM SEKARANG. SMS tetap dikirim otomatis saat hitung mundur habis."
                )
            }
        }
    }

    // ---------- Baca isi alat ----------

    fun readDeviceConfig(silent: Boolean = false) {

        if (_ui.value.isReadingConfig) return
        _ui.update { it.copy(isReadingConfig = true) }

        viewModelScope.launch {
            val snapshot = repo.refreshDeviceConfig()
            _ui.update { it.copy(isReadingConfig = false) }
            if (snapshot == null && !silent) {
                _messages.emit("Gagal membaca isi alat. Firmware harus versi 1.1.1 atau lebih baru.")
            }
        }
    }

    /** Ambil kontak dan pengaturan dari alat, timpa yang ada di HP. */
    fun importFromDevice() {
        viewModelScope.launch {
            val ok = repo.importFromDevice()
            _messages.emit(
                if (ok) "Data dari alat dimuat ke aplikasi" else "Gagal membaca isi alat"
            )
        }
    }

    // ---------- Cek pulsa ----------

    /** Hasilnya muncul di [DeviceUiState.deviceConfig] (balanceText, balanceAgeSeconds). */
    fun checkBalance(ussdCode: String) {

        if (_ui.value.isCheckingBalance) return
        _ui.update { it.copy(isCheckingBalance = true) }

        viewModelScope.launch {
            val message = when (val result = repo.checkBalance(ussdCode.trim())) {
                BalanceResult.Success -> "Pulsa berhasil dicek"
                BalanceResult.NotConnected -> "Alat belum terhubung"
                is BalanceResult.Invalid -> result.message
                BalanceResult.Busy ->
                    "Cek pulsa hanya bisa saat alat sedang memantau (bukan saat kalibrasi atau kejadian)"
                BalanceResult.WriteFailed -> "Gagal mengirim perintah ke alat. Coba lagi."
                BalanceResult.Interrupted -> "Cek pulsa dihentikan karena alat mendeteksi kejadian."
                BalanceResult.NoReply ->
                    "Alat belum memberi hasil. Pastikan SIM punya sinyal dan kodenya sesuai operator, lalu coba lagi."
            }
            _ui.update { it.copy(isCheckingBalance = false) }
            _messages.emit(message)
        }
    }

    // ---------- Sinkronisasi ----------

    fun syncNow() {

        if (_ui.value.isSyncing) return
        _ui.update { it.copy(isSyncing = true) }

        viewModelScope.launch {
            val message = when (val result = repo.syncConfig()) {
                SyncResult.Success -> "Tersinkronisasi ke alat"
                SyncResult.NotConnected -> "Alat belum terhubung"
                is SyncResult.Invalid -> result.message
                SyncResult.WriteFailed -> "Gagal mengirim ke alat. Coba lagi."
                SyncResult.NotConfirmed ->
                    "Terkirim, tapi alat belum mengonfirmasi jumlah kontak. Coba lagi."
                SyncResult.ContentMismatch ->
                    "Terkirim, tapi isi di alat beda dengan di aplikasi. Cek nomor/nama (nomor minimal 8 karakter) lalu coba lagi."
            }
            _ui.update { it.copy(isSyncing = false) }
            _messages.emit(message)
        }
    }

    // ---------- Perintah lain ----------

    /** Mengirim SMS BENERAN ke kontak prioritas pertama. */
    fun sendTestSms() = sendSimple(BleCommands.TEST_SMS, "TEST_SMS terkirim ke alat")

    /** Memicu kecelakaan buatan. Alur penuh jalan, termasuk SMS kalau tidak dibatalkan. */
    fun simulateCrash() = sendSimple(BleCommands.SIMCRASH, "SIMCRASH terkirim ke alat")

    fun recalibrate() = sendSimple(BleCommands.RECAL, "RECAL terkirim, jangan gerakkan alat")

    private fun sendSimple(command: String, okMessage: String) {
        viewModelScope.launch {
            val ok = repo.sendCommand(command)
            _messages.emit(if (ok) okMessage else "Gagal mengirim $command")
        }
    }

    private companion object {
        const val MIN_CANCEL_ELAPSED_SECONDS = 3
        const val RECAL_AFTER_CANCEL_TIMEOUT_MS = 120_000L
    }
}