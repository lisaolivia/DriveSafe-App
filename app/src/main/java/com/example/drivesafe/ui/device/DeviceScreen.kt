package com.example.drivesafe.ui.device

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.drivesafe.ble.BleConstants
import com.example.drivesafe.ble.BleJsonParser
import com.example.drivesafe.model.BleDevice
import com.example.drivesafe.model.ConnectionState
import com.example.drivesafe.model.DeviceConfig
import com.example.drivesafe.model.DeviceConfigSnapshot
import com.example.drivesafe.model.DeviceStatus
import com.example.drivesafe.model.VehicleProfile
import com.example.drivesafe.ui.components.Pill
import com.example.drivesafe.ui.components.SectionCard
import com.example.drivesafe.ui.components.StatusColors
import com.example.drivesafe.ui.components.formatAge
import com.example.drivesafe.ui.components.signalLabel
import com.example.drivesafe.viewmodel.DeviceUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(
    ui: DeviceUiState,
    config: DeviceConfig,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onConnect: (BleDevice) -> Unit,
    onDisconnect: () -> Unit,
    onForget: () -> Unit,
    onSaveSettings: (String, String, Int, (String?) -> Unit) -> Unit,
    onSync: () -> Unit,
    onRecalibrate: () -> Unit,
    onCheckBalance: (String) -> Unit,
    onTestSms: () -> Unit,
    onSimulateCrash: () -> Unit
) {
    var confirmTestSms by remember { mutableStateOf(false) }
    var confirmCrash by remember { mutableStateOf(false) }

    val busy = ui.connection == ConnectionState.CONNECTING || ui.connection == ConnectionState.CONNECTED
    val calibrating = ui.status?.status == DeviceStatus.CALIB

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        item {
            Text("Alat", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }

        // ---- Koneksi ----
        item {
            SectionCard("Koneksi") {
                when {
                    ui.isOnline -> {
                        Pill("Terhubung", StatusColors.Ok)
                        Text(ui.deviceName ?: "DriveSafe", style = MaterialTheme.typography.titleLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = onDisconnect) { Text("Putuskan") }
                            TextButton(onClick = onForget) { Text("Lupakan alat") }
                        }
                    }

                    busy -> {
                        Text("Menghubungkan ke ${ui.deviceName ?: "alat"}…")
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        OutlinedButton(onClick = onDisconnect) { Text("Batal") }
                    }

                    else -> {
                        Pill("Offline", StatusColors.Neutral)
                        Text(
                            "Alat tetap mendeteksi kecelakaan dan mengirim SMS tanpa aplikasi. " +
                                    "Hubungkan hanya untuk melihat status dan mengubah pengaturan.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        if (ui.connection == ConnectionState.SCANNING) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            OutlinedButton(onClick = onStopScan) { Text("Berhenti mencari") }
                        } else {
                            Button(onClick = onScan) { Text("Cari alat DriveSafe") }
                        }
                    }
                }
            }
        }

        // ---- Hasil scan ----
        if (!ui.isOnline && !busy) {
            if (ui.scanResults.isEmpty()) {
                if (ui.connection == ConnectionState.SCANNING) {
                    item {
                        Text(
                            "Mencari… pastikan alat menyala dan tidak sedang tersambung ke HP lain.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                item { Text("Alat ditemukan", style = MaterialTheme.typography.titleMedium) }
                ui.scanResults.forEach { found ->
                    item(key = found.address) { ScanResultRow(found, onClick = { onConnect(found) }) }
                }
            }
        }

        // ---- Pengaturan ----
        item {
            SettingsSection(
                config = config,
                canSend = ui.isOnline && !ui.isSyncing,
                isSyncing = ui.isSyncing,
                onSave = onSaveSettings,
                onSync = onSync
            )
        }

        // ---- Pulsa ----
        item {
            BalanceSection(
                snapshot = ui.deviceConfig,
                online = ui.isOnline,
                canCheck = ui.isOnline &&
                        ui.status?.status == DeviceStatus.MONITOR &&
                        !ui.isCheckingBalance,
                isChecking = ui.isCheckingBalance,
                onCheck = onCheckBalance
            )
        }

        // ---- Kalibrasi ----
        item {
            SectionCard("Kalibrasi sensor") {
                Text(
                    "Lakukan kalau alat baru dipasang atau pembacaannya terasa meleset. " +
                            "Letakkan alat diam di permukaan datar selama sekitar 2 detik.",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (calibrating) {
                    Text("Sedang kalibrasi — jangan digerakkan.", color = StatusColors.Warn)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                } else {
                    FilledTonalButton(onClick = onRecalibrate, enabled = ui.isOnline) {
                        Text("Kalibrasi ulang")
                    }
                }
            }
        }

        // ---- Uji coba ----
        item {
            SectionCard("Uji coba") {
                Text(
                    "Kedua tes ini mengirim SMS sungguhan ke kontak utama. " +
                            "Pakai nomormu sendiri sebagai kontak utama.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { confirmTestSms = true }, enabled = ui.isOnline) {
                        Text("Kirim SMS uji")
                    }
                    OutlinedButton(onClick = { confirmCrash = true }, enabled = ui.isOnline) {
                        Text("Simulasi kecelakaan")
                    }
                }
            }
        }
    }

    if (confirmTestSms) {
        AlertDialog(
            onDismissRequest = { confirmTestSms = false },
            title = { Text("Kirim SMS uji?") },
            text = { Text("Alat akan mengirim satu SMS ke kontak utama.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmTestSms = false
                    onTestSms()
                }) { Text("Kirim") }
            },
            dismissButton = { TextButton(onClick = { confirmTestSms = false }) { Text("Batal") } }
        )
    }

    if (confirmCrash) {
        AlertDialog(
            onDismissRequest = { confirmCrash = false },
            title = { Text("Simulasi kecelakaan?") },
            text = {
                Text(
                    "Layar darurat akan muncul dengan hitung mundur. Batalkan setelah detik ke-3 " +
                            "kalau tidak ingin SMS terkirim ke kontak utama."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmCrash = false
                    onSimulateCrash()
                }) { Text("Mulai") }
            },
            dismissButton = { TextButton(onClick = { confirmCrash = false }) { Text("Batal") } }
        )
    }
}

@Composable
private fun ScanResultRow(device: BleDevice, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(device.name ?: "Tanpa nama", style = MaterialTheme.typography.titleMedium)
                Text(
                    device.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(signalLabel(device.rssi), style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun BalanceSection(
    snapshot: DeviceConfigSnapshot?,
    online: Boolean,
    canCheck: Boolean,
    isChecking: Boolean,
    onCheck: (String) -> Unit
) {
    var code by rememberSaveable { mutableStateOf("*888#") }

    SectionCard("Pulsa SIM alat") {

        Text(
            "SMS darurat tidak terkirim kalau pulsa habis, jadi cek sebelum berkendara.",
            style = MaterialTheme.typography.bodyMedium
        )

        val balance = snapshot?.balanceText.orEmpty()
        if (snapshot != null && balance.isNotBlank()) {
            Text(balance, style = MaterialTheme.typography.titleMedium)
            Text(
                "Dicek ${formatAge(snapshot.balanceAgeSeconds)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                if (online) "Belum pernah dicek." else "Hubungkan alat untuk melihat hasil terakhir.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text("Kode USSD operator", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = code == "*888#",
                onClick = { code = "*888#" },
                label = { Text("Telkomsel") }
            )
            FilterChip(
                selected = code == "*123#",
                onClick = { code = "*123#" },
                label = { Text("Indosat / XL") }
            )
        }
        OutlinedTextField(
            value = code,
            onValueChange = { input ->
                code = input.filter { it.isDigit() || it == '*' || it == '#' }
                    .take(BleConstants.MAX_USSD_LENGTH)
            },
            label = { Text("Kode USSD") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            modifier = Modifier.fillMaxWidth()
        )

        if (isChecking) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(
                "Menunggu balasan operator, bisa sampai 45 detik…",
                style = MaterialTheme.typography.bodySmall
            )
        } else {
            FilledTonalButton(onClick = { onCheck(code) }, enabled = canCheck) {
                Text("Cek pulsa")
            }
            if (online && !canCheck) {
                Text(
                    "Hanya bisa saat alat sedang memantau.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsSection(
    config: DeviceConfig,
    canSend: Boolean,
    isSyncing: Boolean,
    onSave: (String, String, Int, (String?) -> Unit) -> Unit,
    onSync: () -> Unit
) {
    var owner by remember(config.owner) { mutableStateOf(config.owner) }
    var profile by remember(config.profile) { mutableStateOf(config.profile) }
    var countdown by remember(config.countdownSeconds) {
        mutableStateOf(config.countdownSeconds.toFloat())
    }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    SectionCard("Pengaturan") {

        OutlinedTextField(
            value = owner,
            onValueChange = {
                owner = it
                message = null
            },
            label = { Text("Nama pemilik (maks ${BleJsonParser.MAX_OWNER_BYTES} karakter)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Text("Jenis kendaraan", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VehicleProfile.OPTIONS.forEach { option ->
                FilterChip(
                    selected = profile == option,
                    onClick = {
                        profile = option
                        message = null
                    },
                    label = { Text(option.replaceFirstChar { it.uppercase() }) }
                )
            }
        }

        Text(
            "Hitung mundur: ${countdown.toInt()} detik",
            style = MaterialTheme.typography.labelLarge
        )
        Slider(
            value = countdown,
            onValueChange = {
                countdown = it
                message = null
            },
            valueRange = BleJsonParser.MIN_COUNTDOWN.toFloat()..BleJsonParser.MAX_COUNTDOWN.toFloat()
        )
        Text(
            "Waktu untuk membatalkan sebelum SMS darurat dikirim.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        message?.let {
            Text(it, color = if (isError) MaterialTheme.colorScheme.error else StatusColors.Ok)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                onSave(owner, profile, countdown.toInt()) { error ->
                    isError = error != null
                    message = error ?: "Tersimpan di aplikasi. Kirim ke alat agar berlaku."
                }
            }) { Text("Simpan") }

            FilledTonalButton(onClick = onSync, enabled = canSend) {
                Text(if (isSyncing) "Mengirim..." else "Kirim ke alat")
            }
        }
    }
}