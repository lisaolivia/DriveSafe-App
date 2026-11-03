package com.example.drivesafe.ui.device

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.CarCrash
import androidx.compose.material.icons.outlined.CloudUpload
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.drivesafe.ble.BleConstants
import com.example.drivesafe.ble.BleJsonParser
import com.example.drivesafe.model.DeviceConfig
import com.example.drivesafe.model.DeviceConfigSnapshot
import com.example.drivesafe.model.DeviceStatus
import com.example.drivesafe.model.VehicleProfile
import com.example.drivesafe.ui.components.DsCard
import com.example.drivesafe.ui.components.DsTextField
import com.example.drivesafe.ui.components.Pill
import com.example.drivesafe.ui.components.PrimaryButton
import com.example.drivesafe.ui.components.formatAge
import com.example.drivesafe.ui.components.profileLabel
import com.example.drivesafe.ui.components.statusColor
import com.example.drivesafe.ui.components.statusLabel
import com.example.drivesafe.ui.home.vehicleIcon
import com.example.drivesafe.ui.theme.BorderLight
import com.example.drivesafe.ui.theme.BrandBlue
import com.example.drivesafe.ui.theme.BrandBlueSoft
import com.example.drivesafe.ui.theme.DangerRed
import com.example.drivesafe.ui.theme.DangerRedSoft
import com.example.drivesafe.ui.theme.SafeGreen
import com.example.drivesafe.ui.theme.SafeGreenBorder
import com.example.drivesafe.ui.theme.SafeGreenSoft
import com.example.drivesafe.ui.theme.SurfaceMuted
import com.example.drivesafe.ui.theme.SurfaceWhite
import com.example.drivesafe.ui.theme.TextPrimary
import com.example.drivesafe.ui.theme.TextSecondary
import com.example.drivesafe.ui.theme.WarnAmber
import com.example.drivesafe.ui.theme.WarnAmberSoft
import com.example.drivesafe.viewmodel.DeviceUiState

/** Panel bawah yang sedang terbuka. */
private enum class Sheet { Owner, Vehicle, Countdown, Balance }

/** Konfirmasi sebelum aksi yang berefek ke alat / mengirim SMS sungguhan. */
private enum class Confirm { Recalibrate, TestSms, Crash, Forget }

/**
 * Tab IoT bergaya halaman Pengaturan: baris-baris berkelompok, ketuk satu baris untuk mengubahnya
 * lewat panel bawah. Perubahan pengaturan langsung dikirim ke alat kalau terhubung (lihat DriveSafeRoot).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceScreen(
    ui: DeviceUiState,
    config: DeviceConfig,
    needsSync: Boolean,
    onOpenHome: () -> Unit,
    onForget: () -> Unit,
    onSaveSettings: (String, String, Int, (String?) -> Unit) -> Unit,
    onSync: () -> Unit,
    onRecalibrate: () -> Unit,
    onCheckBalance: (String) -> Unit,
    onTestSms: () -> Unit,
    onSimulateCrash: () -> Unit
) {
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }

    val online = ui.isOnline
    val monitoring = ui.status?.status == DeviceStatus.MONITOR
    val calibrating = ui.status?.status == DeviceStatus.CALIB

    /** Simpan satu nilai, sisanya tetap seperti sekarang. */
    fun save(
        owner: String = config.owner,
        profile: String = config.profile,
        countdown: Int = config.countdownSeconds,
        done: (String?) -> Unit = {}
    ) = onSaveSettings(owner, profile, countdown, done)

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Pengaturan Alat", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            Text(
                "Ketuk salah satu baris untuk mengubahnya.",
                fontSize = 14.sp,
                color = TextSecondary
            )
        }

        DeviceCard(ui, onOpenHome)

        // ---- Profil berkendara: bisa diubah walau offline ----
        SettingsGroup("Profil Berkendara") {
            SettingsRow(
                icon = Icons.Outlined.Badge,
                title = "Nama Pengendara",
                value = config.owner.ifBlank { "Belum diisi" },
                onClick = { sheet = Sheet.Owner }
            )
            RowDivider()
            SettingsRow(
                icon = vehicleIcon(config.profile),
                title = "Jenis Kendaraan",
                value = profileLabel(config.profile),
                onClick = { sheet = Sheet.Vehicle }
            )
            RowDivider()
            SettingsRow(
                icon = Icons.Outlined.Timer,
                title = "Waktu Membatalkan",
                value = "${config.countdownSeconds} detik sebelum SMS dikirim",
                onClick = { sheet = Sheet.Countdown }
            )
        }
        SettingsSyncLine(online = online, needsSync = needsSync, isSyncing = ui.isSyncing, onSync = onSync)

        if (online) {
            // ---- Perawatan ----
            SettingsGroup("Perawatan Alat") {
                val balance = ui.deviceConfig?.balanceText.orEmpty()
                SettingsRow(
                    icon = Icons.Outlined.SimCard,
                    title = "Pulsa SIM Alat",
                    value = when {
                        ui.isCheckingBalance -> "Sedang dicek…"
                        balance.isNotBlank() -> balance
                        else -> "Belum pernah dicek"
                    },
                    busy = ui.isCheckingBalance,
                    onClick = { sheet = Sheet.Balance }
                )
                RowDivider()
                SettingsRow(
                    icon = Icons.Outlined.ScreenRotation,
                    title = "Kalibrasi Sensor",
                    value = if (calibrating) "Sedang kalibrasi, jangan gerakkan alat…"
                    else "Atur ulang sensor bila deteksi terasa meleset",
                    busy = calibrating,
                    enabled = !calibrating,
                    onClick = { confirm = Confirm.Recalibrate }
                )
            }

            // ---- Uji coba ----
            SettingsGroup(
                "Uji Coba",
                note = "Uji coba mengirim SMS sungguhan ke kontak utama."
            ) {
                SettingsRow(
                    icon = Icons.Outlined.Sms,
                    title = "Kirim SMS Uji",
                    value = "Pastikan SMS darurat bisa terkirim",
                    enabled = monitoring,
                    onClick = { confirm = Confirm.TestSms }
                )
                RowDivider()
                SettingsRow(
                    icon = Icons.Outlined.CarCrash,
                    title = "Simulasi Kecelakaan",
                    value = "Coba alur darurat lengkap",
                    tint = DangerRed,
                    enabled = monitoring,
                    onClick = { confirm = Confirm.Crash }
                )
            }
            if (!monitoring) {
                Text(
                    "Uji coba bisa dipakai saat alat sedang memantau.",
                    fontSize = 12.sp,
                    color = TextSecondary,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            SettingsGroup(null) {
                SettingsRow(
                    icon = Icons.Outlined.LinkOff,
                    title = "Lupakan Alat Ini",
                    value = null,
                    tint = DangerRed,
                    showChevron = false,
                    onClick = { confirm = Confirm.Forget }
                )
            }
        }
    }

    // ---------- Panel bawah ----------
    sheet?.let { current ->
        ModalBottomSheet(
            onDismissRequest = { sheet = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = SurfaceWhite
        ) {
            SheetBody {
                when (current) {
                    Sheet.Owner -> OwnerEditor(
                        initial = config.owner,
                        onSave = { name, done ->
                            save(owner = name) { error ->
                                done(error)
                                if (error == null) sheet = null
                            }
                        }
                    )

                    Sheet.Vehicle -> VehiclePicker(
                        selected = config.profile,
                        onSelect = { option ->
                            save(profile = option)
                            sheet = null
                        }
                    )

                    Sheet.Countdown -> CountdownEditor(
                        initial = config.countdownSeconds,
                        onSave = { seconds ->
                            save(countdown = seconds)
                            sheet = null
                        }
                    )

                    Sheet.Balance -> BalanceChecker(
                        snapshot = ui.deviceConfig,
                        canCheck = monitoring && !ui.isCheckingBalance,
                        isChecking = ui.isCheckingBalance,
                        onCheck = onCheckBalance
                    )
                }
            }
        }
    }

    // ---------- Konfirmasi ----------
    confirm?.let { current ->
        val (title, text, label) = when (current) {
            Confirm.Recalibrate -> Triple(
                "Kalibrasi ulang sensor?",
                "Letakkan alat diam di permukaan datar, lalu tekan Mulai. Jangan gerakkan alat selama ±2 detik.",
                "Mulai"
            )
            Confirm.TestSms -> Triple(
                "Kirim SMS uji?",
                "Alat akan mengirim satu SMS ke kontak utama.",
                "Kirim"
            )
            Confirm.Crash -> Triple(
                "Simulasi kecelakaan?",
                "Layar darurat akan muncul dengan hitung mundur. Tekan \"Saya Baik-baik Saja\" setelah detik ke-3 " +
                        "kalau tidak ingin SMS terkirim ke kontak.",
                "Mulai"
            )
            Confirm.Forget -> Triple(
                "Lupakan alat ini?",
                "Sambungan akan diputus dan alat perlu dicari lagi dari Beranda. Kontak di aplikasi tetap tersimpan.",
                "Lupakan"
            )
        }
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = SurfaceWhite,
            title = { Text(title, fontWeight = FontWeight.SemiBold) },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    when (current) {
                        Confirm.Recalibrate -> onRecalibrate()
                        Confirm.TestSms -> onTestSms()
                        Confirm.Crash -> onSimulateCrash()
                        Confirm.Forget -> onForget()
                    }
                }) {
                    Text(
                        label,
                        fontWeight = FontWeight.Bold,
                        color = if (current == Confirm.Crash || current == Confirm.Forget) DangerRed else BrandBlue
                    )
                }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Batal") } }
        )
    }
}

// ======================================================================
// Kartu alat
// ======================================================================

@Composable
private fun DeviceCard(ui: DeviceUiState, onOpenHome: () -> Unit) {
    val online = ui.isOnline
    DsCard(
        container = if (online) SafeGreenSoft else SurfaceMuted,
        border = if (online) SafeGreenBorder else BorderLight,
        onClick = if (online) null else onOpenHome
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier
                    .size(48.dp)
                    .background(if (online) BrandBlue else Color(0xFFB0B4BA), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.SettingsInputComponent,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    if (online) ui.deviceName ?: "DriveSafe" else "Alat belum terhubung",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = TextPrimary
                )
                val status = ui.status
                if (online && status != null) {
                    Pill(statusLabel(status.status), statusColor(status.status))
                } else {
                    Text(
                        "Ketuk untuk mencari alat di Beranda",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            }
            if (!online) {
                Icon(Icons.Outlined.Bluetooth, contentDescription = null, tint = BrandBlue)
            }
        }
    }
}

// ======================================================================
// Daftar pengaturan
// ======================================================================

@Composable
private fun SettingsGroup(
    title: String?,
    note: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title != null) {
            Text(
                title.uppercase(),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                color = BrandBlue,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        Surface(
            color = SurfaceWhite,
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, BorderLight),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(content = content)
        }
        if (note != null) {
            Text(note, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.padding(start = 4.dp))
        }
    }
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    value: String?,
    onClick: () -> Unit,
    tint: Color = BrandBlue,
    enabled: Boolean = true,
    busy: Boolean = false,
    showChevron: Boolean = true
) {
    val alpha = if (enabled || busy) 1f else 0.45f
    Surface(
        onClick = onClick,
        enabled = enabled,
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .background(
                        (if (tint == DangerRed) DangerRedSoft else BrandBlueSoft).copy(alpha = alpha),
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint.copy(alpha = alpha), modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = (if (tint == DangerRed) DangerRed else TextPrimary).copy(alpha = alpha)
                )
                if (value != null) {
                    Text(
                        value,
                        fontSize = 13.sp,
                        color = TextSecondary.copy(alpha = alpha),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            when {
                busy -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = BrandBlue)
                showChevron -> Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = TextSecondary.copy(alpha = alpha)
                )
            }
        }
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(Modifier.padding(start = 70.dp), color = BorderLight)
}

/** Satu baris kecil di bawah "Profil Berkendara": sudah di alat / menunggu dikirim / sedang dikirim. */
@Composable
private fun SettingsSyncLine(online: Boolean, needsSync: Boolean, isSyncing: Boolean, onSync: () -> Unit) {
    val (icon, color, text) = when {
        isSyncing -> Triple(null, BrandBlue, "Menyimpan ke alat…")
        !needsSync && online -> Triple(Icons.Filled.CheckCircle, SafeGreen, "Pengaturan sudah tersimpan di alat")
        !needsSync -> return
        online -> Triple(Icons.Outlined.CloudUpload, WarnAmber, "Pengaturan belum tersimpan di alat")
        else -> Triple(Icons.Outlined.CloudUpload, WarnAmber, "Akan dikirim otomatis saat alat terhubung")
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 4.dp)
            .padding(top = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (icon == null) {
            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = color)
        } else {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        }
        Text(text, fontSize = 12.sp, color = TextSecondary, modifier = Modifier.weight(1f))
        if (online && needsSync && !isSyncing) {
            TextButton(onClick = onSync) { Text("Kirim", color = BrandBlue, fontWeight = FontWeight.Bold) }
        }
    }
}

// ======================================================================
// Isi panel bawah
// ======================================================================

@Composable
private fun SheetBody(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        content = content
    )
}

@Composable
private fun SheetTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        Text(subtitle, fontSize = 14.sp, color = TextSecondary)
    }
}

@Composable
private fun OwnerEditor(initial: String, onSave: (String, (String?) -> Unit) -> Unit) {
    var name by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }

    SheetTitle("Nama Pengendara", "Nama ini dicantumkan di SMS darurat.")
    DsTextField(
        label = "Nama (maks ${BleJsonParser.MAX_OWNER_BYTES} karakter)",
        value = name,
        onValueChange = { name = it; error = null },
        placeholder = "Contoh: Budi Santoso"
    )
    error?.let { Text(it, color = DangerRed, fontSize = 13.sp) }
    PrimaryButton("Simpan", onClick = { onSave(name) { error = it } })
}

@Composable
private fun VehiclePicker(selected: String, onSelect: (String) -> Unit) {
    SheetTitle("Jenis Kendaraan", "Pilih kendaraan yang dipasangi alat.")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        VehicleProfile.OPTIONS.forEach { option ->
            val active = option == selected
            Surface(
                onClick = { onSelect(option) },
                shape = RoundedCornerShape(14.dp),
                color = if (active) BrandBlue else SurfaceWhite,
                border = BorderStroke(if (active) 0.dp else 1.dp, BorderLight),
                modifier = Modifier.weight(1f)
            ) {
                Column(
                    Modifier.padding(vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        vehicleIcon(option),
                        contentDescription = null,
                        tint = if (active) Color.White else TextPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                    Text(
                        profileLabel(option),
                        fontWeight = FontWeight.SemiBold,
                        color = if (active) Color.White else TextPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun CountdownEditor(initial: Int, onSave: (Int) -> Unit) {
    val min = BleJsonParser.MIN_COUNTDOWN
    val max = BleJsonParser.MAX_COUNTDOWN
    var seconds by remember { mutableStateOf(initial.coerceIn(min, max).toFloat()) }

    SheetTitle(
        "Waktu Membatalkan",
        "Lama hitung mundur setelah kecelakaan terdeteksi. Selama itu Anda bisa menekan \"Saya Baik-baik Saja\"."
    )
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.Bottom
    ) {
        Text("${seconds.toInt()}", fontSize = 48.sp, fontWeight = FontWeight.Bold, color = BrandBlue)
        Text(" detik", fontSize = 18.sp, color = TextSecondary, modifier = Modifier.padding(bottom = 10.dp))
    }
    Slider(
        value = seconds,
        onValueChange = { seconds = it },
        valueRange = min.toFloat()..max.toFloat(),
        colors = SliderDefaults.colors(thumbColor = BrandBlue, activeTrackColor = BrandBlue)
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(10, 15, 30, 60).filter { it in min..max }.forEach { preset ->
            val active = seconds.toInt() == preset
            Surface(
                onClick = { seconds = preset.toFloat() },
                shape = RoundedCornerShape(50),
                color = if (active) BrandBlue else BrandBlueSoft,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    "$preset dtk",
                    color = if (active) Color.White else BrandBlue,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
    PrimaryButton("Simpan", onClick = { onSave(seconds.toInt()) })
}

@Composable
private fun BalanceChecker(
    snapshot: DeviceConfigSnapshot?,
    canCheck: Boolean,
    isChecking: Boolean,
    onCheck: (String) -> Unit
) {
    val operators = listOf("Telkomsel" to "*888#", "Indosat / XL" to "*123#")
    var code by rememberSaveable { mutableStateOf("*888#") }
    var custom by rememberSaveable { mutableStateOf(false) }

    SheetTitle("Pulsa SIM Alat", "SMS darurat tidak bisa terkirim kalau pulsa SIM di alat habis.")

    val balance = snapshot?.balanceText.orEmpty()
    DsCard(container = SurfaceMuted, contentPadding = 14.dp) {
        Text(
            if (balance.isNotBlank()) balance else "Belum pernah dicek",
            fontSize = 14.sp,
            color = TextPrimary,
            fontWeight = if (balance.isNotBlank()) FontWeight.Normal else FontWeight.SemiBold
        )
        if (snapshot != null && balance.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text("Dicek ${formatAge(snapshot.balanceAgeSeconds)}", fontSize = 12.sp, color = TextSecondary)
        }
    }

    Text("Operator SIM di alat", fontSize = 14.sp, fontWeight = FontWeight.Medium, color = TextPrimary)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        operators.forEach { (label, ussd) ->
            OperatorChip(label, active = !custom && code == ussd, modifier = Modifier.weight(1f)) {
                custom = false
                code = ussd
            }
        }
        OperatorChip("Lainnya", active = custom, modifier = Modifier.weight(1f)) { custom = true }
    }
    if (custom) {
        DsTextField(
            label = "Kode cek pulsa",
            value = code,
            onValueChange = { input ->
                code = input.filter { it.isDigit() || it == '*' || it == '#' }.take(BleConstants.MAX_USSD_LENGTH)
            },
            placeholder = "Contoh: *888#",
            keyboardType = KeyboardType.Phone
        )
    }

    if (isChecking) {
        LinearProgressIndicator(Modifier.fillMaxWidth(), color = BrandBlue)
        Text("Menunggu balasan operator, bisa sampai 45 detik…", fontSize = 12.sp, color = TextSecondary)
    } else {
        PrimaryButton("Cek Pulsa", onClick = { onCheck(code) }, enabled = canCheck)
        if (!canCheck) {
            Surface(color = WarnAmberSoft, shape = RoundedCornerShape(10.dp)) {
                Text(
                    "Cek pulsa hanya bisa saat alat sedang memantau.",
                    fontSize = 12.sp,
                    color = TextPrimary,
                    modifier = Modifier.padding(10.dp)
                )
            }
        }
    }
}

@Composable
private fun OperatorChip(label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (active) BrandBlue else SurfaceWhite,
        border = BorderStroke(1.dp, if (active) BrandBlue else BorderLight),
        modifier = modifier
    ) {
        Text(
            label,
            color = if (active) Color.White else TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp)
        )
    }
}
