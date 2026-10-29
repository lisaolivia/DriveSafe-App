package com.example.drivesafe.ui.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.drivesafe.ble.BleJsonParser
import com.example.drivesafe.model.DeviceConfig
import com.example.drivesafe.model.DeviceConfigSnapshot
import com.example.drivesafe.model.EmergencyContact
import com.example.drivesafe.ui.components.Pill
import com.example.drivesafe.ui.components.SectionCard
import com.example.drivesafe.ui.components.StatusColors
import com.example.drivesafe.viewmodel.DeviceUiState

private sealed interface ContactDialogState {
    object Add : ContactDialogState
    data class Edit(val contact: EmergencyContact) : ContactDialogState
}

@Composable
fun ContactsScreen(
    ui: DeviceUiState,
    config: DeviceConfig,
    needsSync: Boolean,
    onAdd: (String, String, (String?) -> Unit) -> Unit,
    onUpdate: (String, String, String, (String?) -> Unit) -> Unit,
    onDelete: (String) -> Unit,
    onPrimary: (String) -> Unit,
    onSync: () -> Unit,
    onImport: () -> Unit,
    onRefresh: () -> Unit
) {
    var dialog by remember { mutableStateOf<ContactDialogState?>(null) }
    var deleting by remember { mutableStateOf<EmergencyContact?>(null) }

    val contacts = config.contacts.sortedBy { it.priority }
    val max = BleJsonParser.MAX_CONTACTS
    val snapshot = ui.deviceConfig

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        item {
            Text("Kontak darurat", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }

        // ---- Ringkasan + sinkronisasi ----
        item {
            SectionCard("Status kontak") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${contacts.size}/$max kontak", style = MaterialTheme.typography.titleLarge)
                    if (needsSync) Pill("Belum dikirim ke alat", StatusColors.Warn)
                    else Pill("Tersinkron", StatusColors.Ok)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { dialog = ContactDialogState.Add }, enabled = contacts.size < max) {
                        Text("Tambah kontak")
                    }
                    FilledTonalButton(onClick = onSync, enabled = ui.isOnline && !ui.isSyncing) {
                        Text(if (ui.isSyncing) "Mengirim..." else "Kirim ke alat")
                    }
                }

                if (!ui.isOnline) {
                    Text(
                        "Hubungkan alat di tab Alat untuk mengirim kontak. Perubahan tetap tersimpan di aplikasi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // ---- Isi yang tersimpan di alat (dibaca dari alat) ----
        item {
            DeviceContentsCard(
                snapshot = snapshot,
                local = config,
                online = ui.isOnline,
                isReading = ui.isReadingConfig,
                onRefresh = onRefresh,
                onImport = onImport
            )
        }

        // ---- Daftar kontak di aplikasi ----
        item {
            Text(
                "Kontak di aplikasi",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        if (contacts.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Belum ada kontak", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Tambahkan orang yang harus dihubungi kalau terjadi kecelakaan. " +
                                    "Kontak pertama menerima SMS dan panggilan tak terjawab.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        } else {
            items(contacts, key = { it.id }) { contact ->
                ContactRow(
                    contact = contact,
                    onEdit = { dialog = ContactDialogState.Edit(contact) },
                    onDelete = { deleting = contact },
                    onPrimary = { onPrimary(contact.id) }
                )
            }
        }
    }

    // ---- Dialog tambah / ubah ----
    when (val d = dialog) {
        ContactDialogState.Add -> ContactDialog(
            title = "Tambah kontak",
            initial = null,
            onSubmit = onAdd,
            onDismiss = { dialog = null }
        )
        is ContactDialogState.Edit -> ContactDialog(
            title = "Ubah kontak",
            initial = d.contact,
            onSubmit = { name, phone, done -> onUpdate(d.contact.id, name, phone, done) },
            onDismiss = { dialog = null }
        )
        null -> Unit
    }

    // ---- Konfirmasi hapus ----
    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Hapus kontak?") },
            text = { Text("${target.name} tidak akan menerima SMS darurat lagi setelah dikirim ke alat.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(target.id)
                    deleting = null
                }) { Text("Hapus") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Batal") } }
        )
    }
}

@Composable
private fun DeviceContentsCard(
    snapshot: DeviceConfigSnapshot?,
    local: DeviceConfig,
    online: Boolean,
    isReading: Boolean,
    onRefresh: () -> Unit,
    onImport: () -> Unit
) {
    SectionCard("Tersimpan di alat") {

        if (snapshot == null) {
            Text(
                if (online) "Isi alat belum dibaca." else "Hubungkan alat di tab Alat untuk melihat isinya.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "${snapshot.contactCount}/${snapshot.maxContacts} kontak",
                    style = MaterialTheme.typography.titleLarge
                )
                if (snapshot.matches(local)) Pill("Sama dengan aplikasi", StatusColors.Ok)
                else Pill("Beda dengan aplikasi", StatusColors.Warn)
            }

            Text("Nama alat: ${snapshot.deviceName.ifBlank { "-" }}", style = MaterialTheme.typography.bodyMedium)
            Text("Pemilik: ${snapshot.owner.ifBlank { "-" }}", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Kendaraan: ${snapshot.profile.ifBlank { "-" }} · hitung mundur ${snapshot.countdownSeconds} detik",
                style = MaterialTheme.typography.bodyMedium
            )

            if (snapshot.contacts.isEmpty()) {
                Text(
                    "Belum ada kontak di alat.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                snapshot.contacts.sortedBy { it.priority }.forEach {
                    Text(
                        "${it.priority}. ${it.name} - ${it.phone}",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onRefresh, enabled = online && !isReading) {
                Text(
                    when {
                        isReading -> "Membaca..."
                        snapshot == null -> "Baca isi alat"
                        else -> "Baca ulang"
                    }
                )
            }
            if (snapshot != null && !snapshot.matches(local)) {
                TextButton(onClick = onImport, enabled = online && !isReading) {
                    Text("Pakai data alat")
                }
            }
        }
    }
}

@Composable
private fun ContactRow(
    contact: EmergencyContact,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPrimary: () -> Unit
) {
    val isPrimary = contact.priority == 1

    Card(Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    contact.priority.toString(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Column(Modifier.weight(1f)) {
                Text(contact.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(contact.phone, style = MaterialTheme.typography.bodyMedium)
                if (isPrimary) {
                    Text(
                        "Utama · ditelepon setelah SMS",
                        style = MaterialTheme.typography.labelMedium,
                        color = StatusColors.Ok
                    )
                }
            }

            if (!isPrimary) {
                IconButton(onClick = onPrimary) {
                    Icon(Icons.Filled.Star, contentDescription = "Jadikan kontak utama")
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "Ubah")
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Hapus")
            }
        }
    }
}

@Composable
private fun ContactDialog(
    title: String,
    initial: EmergencyContact?,
    onSubmit: (String, String, (String?) -> Unit) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nama (maks ${BleJsonParser.MAX_FIELD_BYTES} karakter)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Nomor, contoh +628123456789") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSubmit(name, phone) { result ->
                    if (result == null) onDismiss() else error = result
                }
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}