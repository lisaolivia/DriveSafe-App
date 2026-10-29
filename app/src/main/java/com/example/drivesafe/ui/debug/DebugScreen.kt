package com.example.drivesafe.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.drivesafe.ble.BleJsonParser
import com.example.drivesafe.model.AccidentEvent
import com.example.drivesafe.model.ConnectionState
import com.example.drivesafe.model.DeviceConfig
import com.example.drivesafe.model.DeviceConfigSnapshot
import com.example.drivesafe.model.DeviceStatus
import com.example.drivesafe.model.EventPhase
import com.example.drivesafe.model.DeviceStatusPayload
import com.example.drivesafe.model.VehicleProfile
import com.example.drivesafe.viewmodel.ContactViewModel
import com.example.drivesafe.viewmodel.CountdownUi
import com.example.drivesafe.viewmodel.DeviceUiState
import com.example.drivesafe.viewmodel.DeviceViewModel

/**
 * Layar uji untuk memastikan SEMUA fitur BLE jalan. Bukan UI final:
 * UI final tinggal membaca DeviceUiState / ContactViewModel yang sama.
 */
@Composable
fun DebugScreen(device: DeviceViewModel, contacts: ContactViewModel) {

    val ui by device.ui.collectAsStateWithLifecycle()
    val config by contacts.config.collectAsStateWithLifecycle()
    val needsSync by contacts.needsSync.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        device.messages.collect { snackbar.showSnackbar(it) }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            ui.countdown?.let { countdown ->
                item { CountdownCard(countdown, onCancel = device::cancelAlarm) }
            }

            if (ui.status?.status == DeviceStatus.CALIB) {
                item { CalibrationCard() }
            }

            item {
                ConnectionCard(
                    ui = ui,
                    onScan = device::startScan,
                    onStopScan = device::stopScan,
                    onDisconnect = device::disconnect,
                    onForget = device::forgetDevice,
                    onSelect = device::connect
                )
            }

            ui.status?.let { status -> item { StatusCard(status) } }
            ui.lastEvent?.let { event -> item { EventCard(event, onDismiss = device::dismissEvent) } }

            item {
                DeviceConfigCard(
                    snapshot = ui.deviceConfig,
                    local = config,
                    isReading = ui.isReadingConfig,
                    enabled = ui.isOnline,
                    onRead = { device.readDeviceConfig() },
                    onImport = device::importFromDevice
                )
            }

            item {
                SettingsCard(config) { owner, profile, countdown, onResult ->
                    contacts.saveSettings(owner, profile, countdown, onResult)
                }
            }

            item {
                ContactsCard(
                    config = config,
                    needsSync = needsSync,
                    canSync = ui.isOnline && !ui.isSyncing,
                    isSyncing = ui.isSyncing,
                    onAdd = contacts::addContact,
                    onUpdate = contacts::updateContact,
                    onDelete = contacts::deleteContact,
                    onPrimary = contacts::setPrimary,
                    onSync = device::syncNow
                )
            }

            item {
                TestCard(
                    enabled = ui.isOnline,
                    onTestSms = device::sendTestSms,
                    onSimCrash = device::simulateCrash,
                    onRecal = device::recalibrate
                )
            }
        }
    }
}

// ---------------------------------------------------------------------

@Composable
private fun CountdownCard(countdown: CountdownUi, onCancel: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("KECELAKAAN TERDETEKSI", style = MaterialTheme.typography.titleLarge)
            Text("${countdown.secondsLeft} detik", style = MaterialTheme.typography.displaySmall)
            Button(onClick = onCancel, enabled = countdown.canCancel) {
                Text(if (countdown.canCancel) "SAYA BAIK-BAIK SAJA" else "Tunggu 3 detik...")
            }
        }
    }
}

@Composable
private fun ConnectionCard(
    ui: DeviceUiState,
    onScan: () -> Unit,
    onStopScan: () -> Unit,
    onDisconnect: () -> Unit,
    onForget: () -> Unit,
    onSelect: (com.example.drivesafe.model.BleDevice) -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

            Text(
                "Koneksi: ${ui.connection}" + (ui.deviceName?.let { " ($it)" } ?: ""),
                style = MaterialTheme.typography.titleMedium
            )

            if (ui.connection == ConnectionState.DISCONNECTED || ui.connection == ConnectionState.ERROR) {
                Text("Mode offline: alat tetap mendeteksi kecelakaan dan mengirim SMS tanpa aplikasi.")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (ui.connection == ConnectionState.SCANNING) {
                    Button(onClick = onStopScan) { Text("Stop scan") }
                } else {
                    Button(onClick = onScan) { Text("Cari alat") }
                }
                OutlinedButton(onClick = onDisconnect) { Text("Putuskan") }
                TextButton(onClick = onForget) { Text("Lupakan") }
            }

            ui.scanResults.forEach { found ->
                TextButton(onClick = { onSelect(found) }) {
                    Text("${found.name ?: "?"}  ${found.address}  ${found.rssi} dBm")
                }
            }
        }
    }
}

@Composable
private fun StatusCard(s: DeviceStatusPayload) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Status: ${s.status}", style = MaterialTheme.typography.titleMedium)
            Text("Baterai ${s.batteryPercent}%   GSM ${s.gsmSignalPercent}%")
            Text("Sensor ${if (s.sensorOk) "OK" else "bermasalah"}   GPS ${if (s.gpsFix) "fix" else "belum fix"} (${s.satelliteCount} sat)")
            Text("Kecepatan ${s.speedKmh} km/jam   Profil ${s.profile}")
            Text("Kontak di alat: ${s.contactCount}")
        }
    }
}

@Composable
private fun CalibrationCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Kalibrasi sensor", style = MaterialTheme.typography.titleMedium)
            Text("Letakkan perangkat diam di permukaan datar. Jangan digerakkan.")
        }
    }
}

@Composable
private fun EventCard(e: AccidentEvent, onDismiss: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val phaseText = when (e.phase) {
                EventPhase.SENDING -> "SMS sedang dikirim"
                EventPhase.SENT -> "Pengiriman selesai"
                EventPhase.UNKNOWN -> "?"
            }
            Text("Kejadian #${e.seq} - $phaseText", style = MaterialTheme.typography.titleMedium)
            Text("${e.typeName} - ${e.severityName}")
            Text("G ${e.gForce}   Roll ${e.roll}   Pitch ${e.pitch}")
            Text(
                if (e.timestampEpoch == 0L) "Waktu: GPS belum fix"
                else "Waktu: " + java.text.DateFormat.getDateTimeInstance()
                    .format(java.util.Date(e.timestampEpoch * 1000))
            )
            Text("Lokasi: ${e.latitude}, ${e.longitude}")
            Text(e.mapUrl)
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    }
}

@Composable
private fun DeviceConfigCard(
    snapshot: DeviceConfigSnapshot?,
    local: DeviceConfig,
    isReading: Boolean,
    enabled: Boolean,
    onRead: () -> Unit,
    onImport: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {

            Text("Isi di alat (dibaca dari alat)", style = MaterialTheme.typography.titleMedium)

            if (snapshot == null) {
                Text(
                    if (enabled) "Belum terbaca. Tekan tombol di bawah."
                    else "Hubungkan ke alat dulu untuk melihat isinya."
                )
            } else {
                Text("${snapshot.deviceName}   Kontak ${snapshot.contactCount}/${snapshot.maxContacts}")
                Text("Pemilik ${snapshot.owner}   Profil ${snapshot.profile}   Hitung mundur ${snapshot.countdownSeconds} dtk")
                snapshot.contacts.sortedBy { it.priority }.forEach {
                    Text("${it.priority}. ${it.name} - ${it.phone}")
                }
                if (snapshot.matches(local)) {
                    Text("Sama dengan di aplikasi", color = MaterialTheme.colorScheme.primary)
                } else {
                    Text("Beda dengan di aplikasi", color = MaterialTheme.colorScheme.error)
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRead, enabled = enabled && !isReading) {
                    Text(if (isReading) "Membaca..." else "Baca dari alat")
                }
                OutlinedButton(onClick = onImport, enabled = enabled && !isReading) {
                    Text("Pakai data alat")
                }
            }
        }
    }
}

@Composable
private fun SettingsCard(
    config: DeviceConfig,
    onSave: (String, String, Int, (String?) -> Unit) -> Unit
) {
    var owner by remember(config.owner) { mutableStateOf(config.owner) }
    var profile by remember(config.profile) { mutableStateOf(config.profile) }
    var countdown by remember(config.countdownSeconds) { mutableStateOf(config.countdownSeconds.toString()) }
    var error by remember { mutableStateOf<String?>(null) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

            Text("Pengaturan alat", style = MaterialTheme.typography.titleMedium)

            OutlinedTextField(
                value = owner,
                onValueChange = { owner = it },
                label = { Text("Nama pemilik (maks ${BleJsonParser.MAX_OWNER_BYTES})") },
                singleLine = true
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                VehicleProfile.OPTIONS.forEach { option ->
                    RadioButton(selected = profile == option, onClick = { profile = option })
                    Text(option)
                }
            }

            OutlinedTextField(
                value = countdown,
                onValueChange = { countdown = it.filter(Char::isDigit) },
                label = {
                    Text("Hitung mundur (${BleJsonParser.MIN_COUNTDOWN}-${BleJsonParser.MAX_COUNTDOWN} detik)")
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(onClick = {
                onSave(owner, profile, countdown.toIntOrNull() ?: 0) { error = it }
            }) { Text("Simpan") }
        }
    }
}

@Composable
private fun ContactsCard(
    config: DeviceConfig,
    needsSync: Boolean,
    canSync: Boolean,
    isSyncing: Boolean,
    onAdd: (String, String, (String?) -> Unit) -> Unit,
    onUpdate: (String, String, String, (String?) -> Unit) -> Unit,
    onDelete: (String) -> Unit,
    onPrimary: (String) -> Unit,
    onSync: () -> Unit
) {
    var editingId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

            Text(
                "Kontak darurat (${config.contacts.size}/${BleJsonParser.MAX_CONTACTS})",
                style = MaterialTheme.typography.titleMedium
            )

            config.contacts.forEach { contact ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${contact.priority}. ${contact.name} - ${contact.phone}",
                        modifier = Modifier.weight(1f)
                    )
                    if (contact.priority != 1) {
                        TextButton(onClick = { onPrimary(contact.id) }) { Text("Utama") }
                    }
                    TextButton(onClick = {
                        editingId = contact.id
                        name = contact.name
                        phone = contact.phone
                        error = null
                    }) { Text("Ubah") }
                    TextButton(onClick = { onDelete(contact.id) }) { Text("Hapus") }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nama (maks 15)") },
                singleLine = true
            )
            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                label = { Text("Nomor (8-15, contoh +628123456789)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
            )

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    val done: (String?) -> Unit = { result ->
                        error = result
                        if (result == null) {
                            name = ""
                            phone = ""
                            editingId = null
                        }
                    }
                    val id = editingId
                    if (id == null) onAdd(name, phone, done) else onUpdate(id, name, phone, done)
                }) {
                    Text(if (editingId == null) "Tambah" else "Simpan perubahan")
                }

                if (editingId != null) {
                    OutlinedButton(onClick = {
                        editingId = null
                        name = ""
                        phone = ""
                        error = null
                    }) { Text("Batal") }
                }
            }

            Text(
                if (needsSync) "Belum disinkronkan ke alat" else "Tersinkronisasi",
                color = if (needsSync) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )

            Button(onClick = onSync, enabled = canSync) {
                Text(if (isSyncing) "Mengirim..." else "Sync ke alat")
            }
        }
    }
}

@Composable
private fun TestCard(
    enabled: Boolean,
    onTestSms: () -> Unit,
    onSimCrash: () -> Unit,
    onRecal: () -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {

            Text("Pengujian", style = MaterialTheme.typography.titleMedium)
            Text(
                "TEST_SMS dan SIMCRASH mengirim SMS beneran. Pakai nomor sendiri sebagai kontak, " +
                        "dan batalkan SIMCRASH setelah detik ke-3.",
                color = MaterialTheme.colorScheme.error
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onTestSms, enabled = enabled) { Text("TEST_SMS") }
                OutlinedButton(onClick = onSimCrash, enabled = enabled) { Text("SIMCRASH") }
                OutlinedButton(onClick = onRecal, enabled = enabled) { Text("RECAL") }
            }
        }
    }
}