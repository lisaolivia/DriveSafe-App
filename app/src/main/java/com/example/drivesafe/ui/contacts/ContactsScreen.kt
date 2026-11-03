package com.example.drivesafe.ui.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.drivesafe.ble.BleJsonParser
import com.example.drivesafe.model.DeviceConfig
import com.example.drivesafe.model.DeviceConfigSnapshot
import com.example.drivesafe.model.EmergencyContact
import com.example.drivesafe.ui.components.DsCard
import com.example.drivesafe.ui.components.DsTextField
import com.example.drivesafe.ui.components.NoticeBox
import com.example.drivesafe.ui.components.Pill
import com.example.drivesafe.ui.components.PrimaryButton
import com.example.drivesafe.ui.components.SecondaryButton
import com.example.drivesafe.ui.components.SectionCard
import com.example.drivesafe.ui.components.StatusColors
import com.example.drivesafe.ui.components.profileLabel
import com.example.drivesafe.ui.theme.BrandBlue
import com.example.drivesafe.ui.theme.BrandBlueSoft
import com.example.drivesafe.ui.theme.DangerRed
import com.example.drivesafe.ui.theme.DangerRedDark
import com.example.drivesafe.ui.theme.DangerRedSoft
import com.example.drivesafe.ui.theme.SurfaceWhite
import com.example.drivesafe.ui.theme.TextPrimary
import com.example.drivesafe.ui.theme.TextSecondary
import com.example.drivesafe.ui.theme.WarnAmber
import com.example.drivesafe.ui.theme.WarnAmberSoft
import com.example.drivesafe.viewmodel.DeviceUiState

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
    var editMode by rememberSaveable { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<EmergencyContact?>(null) }

    // Form tambah / ubah (mode edit)
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var name by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    fun resetForm() {
        editingId = null
        name = ""
        phone = ""
        error = null
    }

    val contacts = config.contacts.sortedBy { it.priority }
    val max = BleJsonParser.MAX_CONTACTS
    val full = contacts.size >= max

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {

        // ---- Judul ----
        Column {
            Text(
                "DAFTAR AKTIF",
                color = BrandBlue,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                letterSpacing = 1.sp
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Kontak Darurat",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    modifier = Modifier.weight(1f)
                )
                if (editMode) {
                    IconButton(onClick = { editMode = false; resetForm() }) {
                        Icon(Icons.Filled.Check, contentDescription = "Selesai mengedit", tint = BrandBlue)
                    }
                } else {
                    Surface(color = BrandBlueSoft, shape = RoundedCornerShape(50)) {
                        Text(
                            "${contacts.size}/$max",
                            color = BrandBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }

        // ---- Daftar kontak ----
        if (contacts.isEmpty()) {
            DsCard(contentPadding = 20.dp) {
                Text("Belum ada kontak", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Tambahkan orang yang harus dihubungi kalau terjadi kecelakaan. " +
                            "Kontak utama menerima SMS dan panggilan tak terjawab.",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
            }
        } else {
            contacts.forEach { contact ->
                ContactCard(
                    contact = contact,
                    editMode = editMode,
                    selected = contact.id == editingId,
                    onClick = if (editMode) {
                        {
                            editingId = contact.id
                            name = contact.name
                            phone = contact.phone
                            error = null
                        }
                    } else null,
                    onPrimary = { onPrimary(contact.id) },
                    onDelete = { deleting = contact }
                )
            }
        }

        if (editMode) {
            // ---- Form ----
            DsTextField(
                label = "Nama Kontak",
                value = name,
                onValueChange = { name = it; error = null },
                placeholder = "Contoh: Ayah",
                filled = true
            )
            DsTextField(
                label = "Nomor",
                value = phone,
                onValueChange = { phone = it; error = null },
                placeholder = "Contoh: +62 812-3452-7892",
                keyboardType = KeyboardType.Phone,
                filled = true
            )
            error?.let { Text(it, color = DangerRed, fontSize = 13.sp) }

            val editing = editingId
            if (editing != null) {
                PrimaryButton(
                    "Simpan Perubahan",
                    icon = Icons.Outlined.Save,
                    onClick = {
                        onUpdate(editing, name, phone) { result ->
                            if (result == null) resetForm() else error = result
                        }
                    }
                )
                TextButton(onClick = { resetForm() }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text("Batal ubah", color = TextSecondary)
                }
            } else {
                PrimaryButton(
                    if (full) "Kontak penuh ($max/$max)" else "Tambah Kontak Baru",
                    icon = Icons.Outlined.PersonAdd,
                    enabled = !full,
                    onClick = {
                        onAdd(name, phone) { result ->
                            if (result == null) resetForm() else error = result
                        }
                    }
                )
            }
            Text(
                "Ketuk kartu kontak untuk mengubahnya, ★ untuk menjadikannya kontak utama.",
                fontSize = 12.sp,
                color = TextSecondary
            )
        } else {
            PrimaryButton("Tambah/Edit Kontak", icon = Icons.Filled.Edit, onClick = { editMode = true })
        }

        // ---- Sinkronisasi ----
        if (needsSync && contacts.isNotEmpty()) {
            NoticeBox(
                icon = Icons.Outlined.Sync,
                title = "Belum dikirim ke alat",
                body = if (ui.isOnline) "Kirim supaya alat memakai daftar kontak terbaru."
                else "Hubungkan alat di tab IoT untuk mengirim. Perubahan tetap tersimpan di aplikasi.",
                container = WarnAmberSoft,
                accent = WarnAmber,
                action = if (ui.isOnline) {
                    {
                        Spacer(Modifier.height(6.dp))
                        PrimaryButton(
                            if (ui.isSyncing) "Mengirim…" else "Kirim ke Alat",
                            onClick = onSync,
                            loading = ui.isSyncing,
                            modifier = Modifier.height(40.dp)
                        )
                    }
                } else null
            )
        }

        if (!editMode) {
            NoticeBox(
                icon = Icons.Filled.Info,
                title = "Keamanan Utama Anda",
                body = "Kontak di atas akan menerima lokasi GPS secara otomatis melalui pesan teks (SMS) " +
                        "segera setelah guncangan hebat atau kecelakaan terdeteksi.",
                container = Color(0xFFDCE3FB),
                accent = BrandBlueDarkish
            )

            DeviceContentsCard(
                snapshot = ui.deviceConfig,
                local = config,
                online = ui.isOnline,
                isReading = ui.isReadingConfig,
                onRefresh = onRefresh,
                onImport = onImport
            )
        }
    }

    // ---- Konfirmasi hapus ----
    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = SurfaceWhite,
            title = { Text("Hapus kontak?", fontWeight = FontWeight.SemiBold) },
            text = { Text("${target.name} tidak akan menerima SMS darurat lagi setelah dikirim ke alat.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(target.id)
                    if (editingId == target.id) resetForm()
                    deleting = null
                }) { Text("Hapus", color = DangerRed, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Batal") } }
        )
    }
}

private val BrandBlueDarkish = Color(0xFF1E40AF)

@Composable
private fun ContactCard(
    contact: EmergencyContact,
    editMode: Boolean,
    selected: Boolean,
    onClick: (() -> Unit)?,
    onPrimary: () -> Unit,
    onDelete: () -> Unit
) {
    val isPrimary = contact.priority == 1

    DsCard(
        onClick = onClick,
        border = if (selected) BrandBlue else com.example.drivesafe.ui.theme.BorderLight,
        contentPadding = 16.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier
                    .size(46.dp)
                    .background(DangerRedSoft, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.AccountTree, contentDescription = null, tint = DangerRedDark, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(contact.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TextPrimary)
                    if (isPrimary) Pill("Utama", StatusColors.Ok)
                }
                Text(contact.phone, fontSize = 15.sp, color = TextSecondary)
            }
            if (editMode) {
                IconButton(onClick = onPrimary, enabled = !isPrimary) {
                    Icon(
                        if (isPrimary) Icons.Filled.Star else Icons.Outlined.StarOutline,
                        contentDescription = "Jadikan kontak utama",
                        tint = if (isPrimary) WarnAmber else TextSecondary
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Hapus", tint = TextSecondary)
                }
            }
        }
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
    SectionCard("Tersimpan di Alat") {

        if (snapshot == null) {
            Text(
                if (online) "Isi alat belum dibaca." else "Hubungkan alat di tab IoT untuk melihat isinya.",
                fontSize = 13.sp,
                color = TextSecondary
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${snapshot.contactCount}/${snapshot.maxContacts} kontak",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = TextPrimary
                )
                if (snapshot.matches(local)) Pill("Sama dengan aplikasi", StatusColors.Ok)
                else Pill("Beda dengan aplikasi", StatusColors.Warn)
            }
            Text(
                "Pengendara: ${snapshot.owner.ifBlank { "-" }} · ${profileLabel(snapshot.profile)} · " +
                        "hitung mundur ${snapshot.countdownSeconds} dtk",
                fontSize = 13.sp,
                color = TextSecondary
            )
            snapshot.contacts.sortedBy { it.priority }.forEach {
                Text("${it.priority}. ${it.name} — ${it.phone}", fontSize = 14.sp, color = TextPrimary)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(
                when {
                    isReading -> "Membaca…"
                    snapshot == null -> "Baca isi alat"
                    else -> "Baca ulang"
                },
                onClick = onRefresh,
                enabled = online && !isReading,
                icon = Icons.Outlined.Sync,
                modifier = Modifier.weight(1f)
            )
            if (snapshot != null && !snapshot.matches(local)) {
                SecondaryButton(
                    "Pakai data alat",
                    onClick = onImport,
                    enabled = online && !isReading,
                    icon = Icons.Outlined.SystemUpdateAlt,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
