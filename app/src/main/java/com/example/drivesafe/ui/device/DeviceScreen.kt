package com.example.drivesafe.ui.device

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.drivesafe.ble.BleConstants
import com.example.drivesafe.ble.BleJsonParser
import com.example.drivesafe.model.BleDevice
import com.example.drivesafe.model.ConnectionState
import com.example.drivesafe.model.DeviceConfig
import com.example.drivesafe.model.DeviceConfigSnapshot
import com.example.drivesafe.model.DeviceStatus
import com.example.drivesafe.model.VehicleProfile
import com.example.drivesafe.ui.components.DsCard
import com.example.drivesafe.ui.components.DsTextField
import com.example.drivesafe.ui.components.IconTitleRow
import com.example.drivesafe.ui.components.NoticeBox
import com.example.drivesafe.ui.components.Pill
import com.example.drivesafe.ui.components.PrimaryButton
import com.example.drivesafe.ui.components.SecondaryButton
import com.example.drivesafe.ui.components.SectionCard
import com.example.drivesafe.ui.components.StatusColors
import com.example.drivesafe.ui.components.formatAge
import com.example.drivesafe.ui.components.profileLabel
import com.example.drivesafe.ui.components.signalLabel
import com.example.drivesafe.ui.home.vehicleIcon
import com.example.drivesafe.ui.theme.BorderInput
import com.example.drivesafe.ui.theme.BrandBlue
import com.example.drivesafe.ui.theme.BrandBlueSoft
import com.example.drivesafe.ui.theme.DangerRed
import com.example.drivesafe.ui.theme.SafeGreen
import com.example.drivesafe.ui.theme.SafeGreenBorder
import com.example.drivesafe.ui.theme.SafeGreenDark
import com.example.drivesafe.ui.theme.SafeGreenSoft
import com.example.drivesafe.ui.theme.SurfaceWhite
import com.example.drivesafe.ui.theme.TextPrimary
import com.example.drivesafe.ui.theme.TextSecondary
import com.example.drivesafe.ui.theme.WarnAmber
import com.example.drivesafe.ui.theme.WarnAmberSoft
import com.example.drivesafe.viewmodel.DeviceUiState

@Composable
fun DeviceScreen(
    ui: DeviceUiState,
    config: DeviceConfig,
    needsSync: Boolean,
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
    var confirmForget by remember { mutableStateOf(false) }

    val connecting = ui.connection == ConnectionState.CONNECTING || ui.connection == ConnectionState.CONNECTED
    val scanning = ui.connection == ConnectionState.SCANNING
    val calibrating = ui.status?.status == DeviceStatus.CALIB

    // Isian dibagi antara mode "hubungkan" dan kartu pengaturan
    var owner by remember(config.owner) { mutableStateOf(config.owner) }
    var profile by remember(config.profile) { mutableStateOf(config.profile) }
    var countdown by remember(config.countdownSeconds) { mutableStateOf(config.countdownSeconds.toFloat()) }
    var formMessage by remember { mutableStateOf<String?>(null) }
    var formError by remember { mutableStateOf(false) }

    fun save(afterSaved: () -> Unit) {
        onSaveSettings(owner, profile, countdown.toInt()) { error ->
            formError = error != null
            formMessage = error
            if (error == null) afterSaved()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        DeviceHero()

        Text(
            if (ui.isOnline) "Alat Terhubung" else "Hubungkan ke Alat",
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            if (ui.isOnline) "Atur pengendara, kendaraan, dan uji fungsi alat di bawah ini"
            else "Nyalakan alat DriveSafe di dekat HP lalu tekan tombol di bawah",
            fontSize = 15.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        if (!ui.isOnline) {
            // ---- Form hubungkan ----
            DsTextField(
                label = "Nama Pengendara",
                value = owner,
                onValueChange = { owner = it; formMessage = null },
                placeholder = "Contoh: Budi Santoso",
                trailingIcon = Icons.Outlined.Badge
            )
            ProfileDropdown(profile, onSelect = { profile = it; formMessage = null })

            when {
                scanning -> {
                    PrimaryButton("Berhenti mencari", onClick = onStopScan, loading = false)
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = BrandBlue)
                }
                connecting -> PrimaryButton("Menghubungkan…", onClick = {}, loading = true)
                else -> PrimaryButton("Cari & Hubungkan", onClick = { save(onScan) })
            }

            formMessage?.let { Text(it, color = if (formError) DangerRed else SafeGreenDark, fontSize = 13.sp) }

            // ---- Hasil scan ----
            if (!connecting) {
                if (ui.scanResults.isNotEmpty()) {
                    Text("Alat ditemukan", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = TextPrimary)
                    ui.scanResults.forEach { found -> ScanResultRow(found, onClick = { onConnect(found) }) }
                } else if (scanning) {
                    Text(
                        "Mencari… pastikan alat menyala dan tidak sedang tersambung ke HP lain.",
                        fontSize = 13.sp,
                        color = TextSecondary
                    )
                }
            }

            if (ui.connection == ConnectionState.ERROR) {
                NoticeBox(
                    icon = Icons.Outlined.Info,
                    title = "Gagal terhubung",
                    body = "Pastikan Bluetooth aktif dan alat berada di dekat HP, lalu coba lagi.",
                    container = Color(0xFFFFEBEE),
                    accent = DangerRed
                )
            }

            NoticeBox(
                icon = Icons.Outlined.Info,
                title = "Mode offline",
                body = "Alat tetap mendeteksi kecelakaan dan mengirim SMS tanpa aplikasi. " +
                        "Tapi alarm palsu hanya bisa dibatalkan lewat aplikasi, jadi tetap hubungkan saat berkendara.",
                container = BrandBlueSoft,
                accent = BrandBlue
            )
        } else {
            // ---- Terhubung ----
            ConnectedCard(ui.deviceName ?: "DriveSafe")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SecondaryButton("Putuskan", onClick = onDisconnect, modifier = Modifier.weight(1f))
                SecondaryButton(
                    "Lupakan alat",
                    onClick = { confirmForget = true },
                    color = DangerRed,
                    modifier = Modifier.weight(1f)
                )
            }

            // ---- Pengaturan ----
            SectionCard("Pengendara & Kendaraan") {
                DsTextField(
                    label = "Nama Pengendara (maks ${BleJsonParser.MAX_OWNER_BYTES} karakter)",
                    value = owner,
                    onValueChange = { owner = it; formMessage = null },
                    placeholder = "Contoh: Budi Santoso",
                    trailingIcon = Icons.Outlined.Badge
                )
                ProfileDropdown(profile, onSelect = { profile = it; formMessage = null })

                Text(
                    "Hitung mundur: ${countdown.toInt()} detik",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextPrimary
                )
                Slider(
                    value = countdown,
                    onValueChange = { countdown = it; formMessage = null },
                    valueRange = BleJsonParser.MIN_COUNTDOWN.toFloat()..BleJsonParser.MAX_COUNTDOWN.toFloat(),
                    colors = SliderDefaults.colors(thumbColor = BrandBlue, activeTrackColor = BrandBlue)
                )
                Text(
                    "Waktu untuk membatalkan sebelum SMS darurat dikirim.",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                if (needsSync) Pill("Belum dikirim ke alat", StatusColors.Warn)
                else Pill("Tersinkron dengan alat", StatusColors.Ok)

                formMessage?.let { Text(it, color = if (formError) DangerRed else SafeGreenDark, fontSize = 13.sp) }

                PrimaryButton(
                    if (ui.isSyncing) "Mengirim…" else "Simpan & Kirim ke Alat",
                    onClick = { save(onSync) },
                    loading = ui.isSyncing
                )
            }

            // ---- Pulsa ----
            BalanceSection(
                snapshot = ui.deviceConfig,
                canCheck = ui.status?.status == DeviceStatus.MONITOR && !ui.isCheckingBalance,
                isChecking = ui.isCheckingBalance,
                onCheck = onCheckBalance
            )

            // ---- Kalibrasi ----
            SectionCard("Kalibrasi Sensor") {
                IconTitleRow(
                    icon = { CircleIcon(Icons.Outlined.ScreenRotation, BrandBlue, BrandBlueSoft) },
                    title = if (calibrating) "Sedang kalibrasi…" else "Kalibrasi ulang",
                    subtitle = if (calibrating) "Jangan gerakkan alat."
                    else "Letakkan alat diam di permukaan datar selama ±2 detik."
                )
                if (calibrating) {
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = WarnAmber)
                } else {
                    SecondaryButton("Kalibrasi ulang", onClick = onRecalibrate, modifier = Modifier.fillMaxWidth())
                }
            }

            // ---- Uji coba ----
            SectionCard("Uji Coba") {
                NoticeBox(
                    icon = Icons.Outlined.Science,
                    title = null,
                    body = "Kedua tes ini mengirim SMS sungguhan ke kontak utama. Pakai nomormu sendiri sebagai kontak utama.",
                    container = WarnAmberSoft,
                    accent = WarnAmber
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("SMS uji", onClick = { confirmTestSms = true }, modifier = Modifier.weight(1f))
                    SecondaryButton(
                        "Simulasi crash",
                        onClick = { confirmCrash = true },
                        color = DangerRed,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    if (confirmTestSms) {
        ConfirmDialog(
            title = "Kirim SMS uji?",
            text = "Alat akan mengirim satu SMS ke kontak utama.",
            confirm = "Kirim",
            onConfirm = { confirmTestSms = false; onTestSms() },
            onDismiss = { confirmTestSms = false }
        )
    }
    if (confirmCrash) {
        ConfirmDialog(
            title = "Simulasi kecelakaan?",
            text = "Layar darurat akan muncul dengan hitung mundur. Batalkan setelah detik ke-3 " +
                    "kalau tidak ingin SMS terkirim ke kontak utama.",
            confirm = "Mulai",
            onConfirm = { confirmCrash = false; onSimulateCrash() },
            onDismiss = { confirmCrash = false }
        )
    }
    if (confirmForget) {
        ConfirmDialog(
            title = "Lupakan alat?",
            text = "Aplikasi tidak akan menyambung otomatis ke alat ini lagi. Kontak di aplikasi tetap tersimpan.",
            confirm = "Lupakan",
            onConfirm = { confirmForget = false; onForget() },
            onDismiss = { confirmForget = false }
        )
    }
}

// ---------- Bagian layar ----------

@Composable
private fun DeviceHero() {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(190.dp)
                .background(BrandBlue.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(152.dp)
                    .background(BrandBlue.copy(alpha = 0.28f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(96.dp)
                        .background(BrandBlue, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.SettingsInputComponent,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(46.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectedCard(name: String) {
    DsCard(container = SafeGreenSoft, border = SafeGreenBorder) {
        IconTitleRow(
            icon = {
                Box(
                    Modifier
                        .size(34.dp)
                        .background(SafeGreen, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
            },
            title = "Terhubung: $name",
            subtitle = "Perangkat siap digunakan",
            titleColor = SafeGreenDark
        )
    }
}

@Composable
private fun ScanResultRow(device: BleDevice, onClick: () -> Unit) {
    DsCard(onClick = onClick) {
        IconTitleRow(
            icon = { CircleIcon(Icons.Outlined.Bluetooth, BrandBlue, BrandBlueSoft) },
            title = device.name ?: "Tanpa nama",
            subtitle = device.address,
            trailing = { Pill(signalLabel(device.rssi), BrandBlue) }
        )
    }
}

@Composable
private fun CircleIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, tint: Color, bg: Color) {
    Box(
        Modifier
            .size(40.dp)
            .background(bg, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileDropdown(selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Profil Kendaraan", fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
            OutlinedTextField(
                value = profileLabel(selected),
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                leadingIcon = { Icon(vehicleIcon(selected), contentDescription = null, tint = TextSecondary) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = BorderInput,
                    focusedBorderColor = BrandBlue,
                    unfocusedContainerColor = SurfaceWhite,
                    focusedContainerColor = SurfaceWhite
                ),
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                containerColor = SurfaceWhite
            ) {
                VehicleProfile.OPTIONS.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(profileLabel(option)) },
                        leadingIcon = { Icon(vehicleIcon(option), contentDescription = null) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun BalanceSection(
    snapshot: DeviceConfigSnapshot?,
    canCheck: Boolean,
    isChecking: Boolean,
    onCheck: (String) -> Unit
) {
    var code by rememberSaveable { mutableStateOf("*888#") }

    SectionCard("Pulsa SIM Alat") {

        val balance = snapshot?.balanceText.orEmpty()
        IconTitleRow(
            icon = { CircleIcon(Icons.Outlined.SimCard, BrandBlue, BrandBlueSoft) },
            title = if (balance.isNotBlank()) balance else "Belum pernah dicek",
            subtitle = if (snapshot != null && balance.isNotBlank()) "Dicek ${formatAge(snapshot.balanceAgeSeconds)}"
            else "SMS darurat tidak terkirim kalau pulsa habis."
        )

        Text("Kode USSD operator", fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("*888#" to "Telkomsel", "*123#" to "Indosat / XL").forEach { (value, label) ->
                FilterChip(
                    selected = code == value,
                    onClick = { code = value },
                    label = { Text(label) },
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = BrandBlue,
                        selectedLabelColor = Color.White
                    )
                )
            }
        }
        DsTextField(
            label = "Kode USSD",
            value = code,
            onValueChange = { input ->
                code = input.filter { it.isDigit() || it == '*' || it == '#' }.take(BleConstants.MAX_USSD_LENGTH)
            },
            placeholder = "*888#",
            keyboardType = KeyboardType.Phone
        )

        if (isChecking) {
            LinearProgressIndicator(Modifier.fillMaxWidth(), color = BrandBlue)
            Text("Menunggu balasan operator, bisa sampai 45 detik…", fontSize = 12.sp, color = TextSecondary)
        } else {
            PrimaryButton("Cek Pulsa", onClick = { onCheck(code) }, enabled = canCheck)
            if (!canCheck) {
                Text("Hanya bisa saat alat sedang memantau.", fontSize = 12.sp, color = TextSecondary)
            }
        }
        Spacer(Modifier.height(2.dp))
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite,
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirm, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
