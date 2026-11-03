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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.BluetoothDisabled
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.drivesafe.ble.BleJsonParser
import com.example.drivesafe.model.DeviceConfig
import com.example.drivesafe.model.EmergencyContact
import com.example.drivesafe.ui.components.DsCard
import com.example.drivesafe.ui.components.DsTextField
import com.example.drivesafe.ui.components.Pill
import com.example.drivesafe.ui.components.PrimaryButton
import com.example.drivesafe.ui.components.StatusColors
import com.example.drivesafe.ui.theme.BorderLight
import com.example.drivesafe.ui.theme.BrandBlue
import com.example.drivesafe.ui.theme.BrandBlueSoft
import com.example.drivesafe.ui.theme.BrandYellowSoft
import com.example.drivesafe.ui.theme.BrandYellowText
import com.example.drivesafe.ui.theme.DangerRed
import com.example.drivesafe.ui.theme.DangerRedDark
import com.example.drivesafe.ui.theme.DangerRedSoft
import com.example.drivesafe.ui.theme.SafeGreen
import com.example.drivesafe.ui.theme.SafeGreenBorder
import com.example.drivesafe.ui.theme.SafeGreenDark
import com.example.drivesafe.ui.theme.SafeGreenSoft
import com.example.drivesafe.ui.theme.SurfaceMuted
import com.example.drivesafe.ui.theme.SurfaceWhite
import com.example.drivesafe.ui.theme.TextPrimary
import com.example.drivesafe.ui.theme.TextSecondary
import com.example.drivesafe.ui.theme.WarnAmber
import com.example.drivesafe.ui.theme.WarnAmberSoft
import com.example.drivesafe.viewmodel.DeviceUiState

/** Isi lembar bawah: tambah kontak baru, atau ubah kontak yang ada. */
private sealed interface ContactSheet {
    object Add : ContactSheet
    data class Edit(val contact: EmergencyContact) : ContactSheet
}

/**
 * Tab Kontak dibuat sesederhana mungkin untuk orang awam:
 * - satu daftar, ketuk kontak untuk mengubah / menghapus / menjadikan utama
 * - satu tombol "Tambah Kontak"
 * - kontak dikirim ke alat otomatis setelah diubah (lihat DriveSafeRoot), jadi tidak ada tombol sinkron
 *   kecuali kalau perubahan dibuat saat alat belum terhubung.
 */
@OptIn(ExperimentalMaterial3Api::class)
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
    onImport: () -> Unit
) {
    var sheet by remember { mutableStateOf<ContactSheet?>(null) }
    var deleting by remember { mutableStateOf<EmergencyContact?>(null) }

    val contacts = config.contacts.sortedBy { it.priority }
    val max = BleJsonParser.MAX_CONTACTS
    val full = contacts.size >= max

    // HP baru / data aplikasi terhapus: aplikasi kosong tapi alat masih menyimpan kontak
    val deviceHasContacts = ui.deviceConfig?.contacts?.isNotEmpty() == true
    val canRestoreFromDevice = contacts.isEmpty() && deviceHasContacts && ui.isOnline

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        // ---- Judul ----
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Kontak Darurat",
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            Surface(color = BrandBlueSoft, shape = RoundedCornerShape(50)) {
                Text(
                    "${contacts.size}/$max",
                    color = BrandBlue,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
        }
        Text(
            "Saat kecelakaan, semua kontak di bawah menerima SMS berisi lokasi Anda. " +
                    "Kontak utama juga ditelepon.",
            fontSize = 14.sp,
            color = TextSecondary,
            lineHeight = 20.sp
        )

        Spacer(Modifier.height(4.dp))

        // ---- Daftar ----
        if (contacts.isEmpty()) {
            EmptyState()
        } else {
            contacts.forEach { contact ->
                ContactCard(contact, onClick = { sheet = ContactSheet.Edit(contact) })
            }
        }

        if (canRestoreFromDevice) {
            PrimaryButton(
                "Ambil Kontak dari Alat",
                icon = Icons.Outlined.SystemUpdateAlt,
                onClick = onImport,
                loading = ui.isReadingConfig
            )
        }

        if (!full) {
            PrimaryButton(
                "Tambah Kontak",
                icon = Icons.Outlined.PersonAdd,
                onClick = { sheet = ContactSheet.Add }
            )
        } else {
            Text(
                "Maksimal $max kontak. Hapus salah satu untuk menambah yang baru.",
                fontSize = 13.sp,
                color = TextSecondary
            )
        }

        // ---- Status di alat: satu baris, tombol hanya kalau memang perlu ----
        if (contacts.isNotEmpty()) {
            SyncStatus(
                state = syncState(ui, contacts, needsSync),
                onSync = onSync
            )
        }
    }

    // ---- Lembar tambah / ubah ----
    sheet?.let { current ->
        ModalBottomSheet(
            onDismissRequest = { sheet = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = SurfaceWhite
        ) {
            ContactForm(
                initial = (current as? ContactSheet.Edit)?.contact,
                onSave = { name, phone, done ->
                    val finish: (String?) -> Unit = { error ->
                        if (error == null) sheet = null
                        done(error)
                    }
                    when (current) {
                        ContactSheet.Add -> onAdd(name, phone, finish)
                        is ContactSheet.Edit -> onUpdate(current.contact.id, name, phone, finish)
                    }
                },
                onMakePrimary = { contact ->
                    onPrimary(contact.id)
                    sheet = null
                },
                onDelete = { contact ->
                    sheet = null
                    deleting = contact
                }
            )
        }
    }

    // ---- Konfirmasi hapus ----
    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = SurfaceWhite,
            title = { Text("Hapus ${target.name}?", fontWeight = FontWeight.SemiBold) },
            text = { Text("${target.name} tidak akan lagi menerima SMS saat terjadi kecelakaan.") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(target.id)
                    deleting = null
                }) { Text("Hapus", color = DangerRed, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Batal") } }
        )
    }
}

// ---------- Bagian layar ----------

@Composable
private fun EmptyState() {
    DsCard(container = SurfaceMuted, contentPadding = 24.dp) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(56.dp)
                    .background(DangerRedSoft, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = DangerRedDark, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text("Belum ada kontak", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(
                "Tambahkan keluarga atau teman yang perlu dihubungi saat Anda kecelakaan.",
                fontSize = 13.sp,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun ContactCard(contact: EmergencyContact, onClick: () -> Unit) {
    val isPrimary = contact.priority == 1

    DsCard(onClick = onClick, contentPadding = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier
                    .size(46.dp)
                    .background(DangerRedSoft, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(initials(contact.name), color = DangerRedDark, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(contact.name, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = TextPrimary)
                    if (isPrimary) Pill("Utama", BrandYellowText, container = BrandYellowSoft)
                }
                Text(contact.phone, fontSize = 15.sp, color = TextSecondary)
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Ubah ${contact.name}",
                tint = TextSecondary
            )
        }
    }
}

private enum class SyncState {
    /** Alat tidak terhubung, ada perubahan yang menunggu dikirim. */
    OfflinePending,
    /** Alat tidak terhubung, tidak bisa dipastikan isi alat. */
    Offline,
    /** Sedang mengirim atau membaca isi alat. */
    Working,
    /** Kontak di alat SAMA dengan di aplikasi (dibaca langsung dari alat). */
    Match,
    /** Terhubung tapi isi alat beda atau belum terbaca. */
    Mismatch
}

/** "Tersimpan di alat" hanya kalau isi yang dibaca dari alat benar-benar sama dengan di aplikasi. */
private fun syncState(ui: DeviceUiState, contacts: List<EmergencyContact>, needsSync: Boolean): SyncState {
    if (!ui.isOnline) return if (needsSync) SyncState.OfflinePending else SyncState.Offline
    if (ui.isSyncing || ui.isReadingConfig) return SyncState.Working

    val onDevice = ui.deviceConfig?.contacts ?: return SyncState.Mismatch
    fun List<EmergencyContact>.key() = sortedBy { it.priority }.map { it.name to it.phone }
    return if (onDevice.key() == contacts.key()) SyncState.Match else SyncState.Mismatch
}

@Composable
private fun SyncStatus(state: SyncState, onSync: () -> Unit) {
    val container: Color
    val border: Color
    val iconTint: Color
    val icon: ImageVector?
    val text: String
    when (state) {
        SyncState.Working -> {
            container = BrandBlueSoft; border = BrandBlue.copy(alpha = 0.25f); iconTint = BrandBlue; icon = null
            text = "Menyimpan kontak ke alat…"
        }
        SyncState.Match -> {
            container = SafeGreenSoft; border = SafeGreenBorder; iconTint = SafeGreen; icon = Icons.Filled.CheckCircle
            text = "Semua kontak sudah tersimpan di alat"
        }
        SyncState.Mismatch -> {
            container = WarnAmberSoft; border = WarnAmber.copy(alpha = 0.3f); iconTint = WarnAmber
            icon = Icons.Outlined.CloudUpload
            text = "Kontak di alat belum sama dengan di aplikasi"
        }
        SyncState.OfflinePending -> {
            container = WarnAmberSoft; border = WarnAmber.copy(alpha = 0.3f); iconTint = WarnAmber
            icon = Icons.Outlined.CloudUpload
            text = "Belum tersimpan di alat. Akan dikirim otomatis saat alat terhubung."
        }
        SyncState.Offline -> {
            container = SurfaceMuted; border = BorderLight; iconTint = TextSecondary; icon = Icons.Outlined.BluetoothDisabled
            text = "Alat tidak terhubung. Hubungkan di Beranda untuk memastikan kontak tersimpan di alat."
        }
    }

    DsCard(container = container, border = border, contentPadding = 14.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (icon == null) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = iconTint)
            } else {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
            }
            Text(text, fontSize = 13.sp, color = TextPrimary, modifier = Modifier.weight(1f))
            if (state == SyncState.Mismatch) {
                TextButton(onClick = onSync) {
                    Text("Kirim", color = BrandBlue, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ContactForm(
    initial: EmergencyContact?,
    onSave: (String, String, (String?) -> Unit) -> Unit,
    onMakePrimary: (EmergencyContact) -> Unit,
    onDelete: (EmergencyContact) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var phone by remember { mutableStateOf(initial?.phone ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            if (initial == null) "Tambah Kontak" else "Ubah Kontak",
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary
        )

        DsTextField(
            label = "Nama",
            value = name,
            onValueChange = { name = it; error = null },
            placeholder = "Contoh: Ayah"
        )
        DsTextField(
            label = "Nomor HP",
            value = phone,
            onValueChange = { phone = it; error = null },
            placeholder = "Contoh: 0812 3456 7890",
            keyboardType = KeyboardType.Phone
        )
        error?.let { Text(it, color = DangerRed, fontSize = 13.sp) }

        PrimaryButton(
            "Simpan",
            onClick = { onSave(name, phone) { result -> error = result } }
        )

        if (initial != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (initial.priority != 1) {
                    TextButton(onClick = { onMakePrimary(initial) }) {
                        Icon(Icons.Outlined.Star, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Jadikan Utama", color = BrandBlue)
                    }
                } else {
                    Spacer(Modifier.size(1.dp))
                }
                TextButton(onClick = { onDelete(initial) }) {
                    Icon(Icons.Outlined.Delete, contentDescription = null, tint = DangerRed, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Hapus", color = DangerRed)
                }
            }
            if (initial.priority != 1) {
                Text(
                    "Kontak utama ditelepon setelah SMS terkirim.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )
            }
        }
    }
}

private fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(2).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}
