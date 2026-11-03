package com.example.drivesafe.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.PedalBike
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.ContactPhone
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.filled.WifiTethering
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.drivesafe.model.ConnectionState
import com.example.drivesafe.model.DeviceConfig
import com.example.drivesafe.model.DeviceStatus
import com.example.drivesafe.ui.components.DsCard
import com.example.drivesafe.ui.components.Pill
import com.example.drivesafe.ui.components.profileLabel
import com.example.drivesafe.ui.components.statusColor
import com.example.drivesafe.ui.components.statusLabel
import com.example.drivesafe.ui.theme.BorderLight
import com.example.drivesafe.ui.theme.BrandBlue
import com.example.drivesafe.ui.theme.ChipDark
import com.example.drivesafe.ui.theme.DangerRed
import com.example.drivesafe.ui.theme.DangerRedSoft
import com.example.drivesafe.ui.theme.EmergencyRed
import com.example.drivesafe.ui.theme.SafeGreen
import com.example.drivesafe.ui.theme.SafeGreenBorder
import com.example.drivesafe.ui.theme.SafeGreenSoft
import com.example.drivesafe.ui.theme.SurfaceMuted
import com.example.drivesafe.ui.theme.TextPrimary
import com.example.drivesafe.ui.theme.TextSecondary
import com.example.drivesafe.ui.theme.WarnAmber
import com.example.drivesafe.ui.theme.WarnAmberSoft
import com.example.drivesafe.viewmodel.DeviceUiState
import kotlinx.coroutines.launch

private const val SOS_HOLD_MS = 3_000

@Composable
fun HomeScreen(
    ui: DeviceUiState,
    config: DeviceConfig,
    onOpenDevice: () -> Unit,
    onSos: () -> Unit
) {
    val status = ui.status
    val online = ui.isOnline && status != null

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        ConnectionBanner(ui, onClick = onOpenDevice)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Info Perangkat",
                fontSize = 24.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary,
                modifier = Modifier.weight(1f)
            )
            if (online) Pill(statusLabel(status!!.status), statusColor(status.status))
        }

        if (!online) {
            DsCard(container = SurfaceMuted, onClick = onOpenDevice, contentPadding = 20.dp) {
                Text(
                    "Tidak Tersedia",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            return@Column
        }

        val st = status!!
        val profile = st.profile.ifBlank { config.profile }

        // ---- Baris ringkas: baterai, sinyal, kontak, kendaraan ----
        DsCard(container = SurfaceMuted, contentPadding = 12.dp) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MiniStat(Icons.Outlined.BatteryChargingFull, "${st.batteryPercent}%")
                MiniStat(Icons.Filled.SignalCellularAlt, gsmLabel(st.gsmSignalPercent))
                MiniStat(Icons.Outlined.ContactPhone, st.contactCount.toString())
                Surface(color = ChipDark, shape = RoundedCornerShape(8.dp)) {
                    Column(
                        Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(vehicleIcon(profile), contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                        Text(profileLabel(profile), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
        }

        // ---- Deteksi / GPS / Kecepatan ----
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            val calibrating = st.status == DeviceStatus.CALIB
            FlagTile(
                icon = Icons.Filled.Vibration,
                label = "Deteksi",
                value = when {
                    !st.sensorOk -> "OFF"
                    calibrating -> "KALIB"
                    else -> "ON"
                },
                tone = when {
                    !st.sensorOk -> Tone.Bad
                    calibrating -> Tone.Warn
                    else -> Tone.Ok
                },
                modifier = Modifier.weight(1f)
            )
            FlagTile(
                icon = Icons.Filled.GpsFixed,
                label = "GPS",
                value = if (st.gpsFix) "ON" else "CARI",
                tone = if (st.gpsFix) Tone.Ok else Tone.Warn,
                modifier = Modifier.weight(1f)
            )
            SpeedTile(st.speedKmh, Modifier.weight(1f))
        }

        // ---- Lokasi / GPS ----
        DsCard(container = SurfaceMuted) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Filled.LocationOn, contentDescription = null, tint = BrandBlue, modifier = Modifier.size(32.dp))
                Column {
                    Text("Lokasi", color = BrandBlue, fontSize = 13.sp)
                    Text(
                        if (st.gpsFix) "GPS terkunci · ${st.satelliteCount} satelit"
                        else "Mencari sinyal GPS (${st.satelliteCount} satelit)",
                        color = BrandBlue,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        SosButton(
            enabled = st.status == DeviceStatus.MONITOR,
            hasContacts = config.contacts.isNotEmpty(),
            onTrigger = onSos
        )
    }
}

// ---------- Banner koneksi ----------

@Composable
private fun ConnectionBanner(ui: DeviceUiState, onClick: () -> Unit) {
    val connecting = ui.connection == ConnectionState.CONNECTING || ui.connection == ConnectionState.CONNECTED
    val online = ui.isOnline

    val container = if (online) BrandBlue else SurfaceMuted
    val content = if (online) Color.White else TextPrimary

    Surface(
        onClick = onClick,
        color = container,
        shape = RoundedCornerShape(10.dp),
        border = if (online) null else BorderStroke(1.dp, BorderLight),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Icon(
                when {
                    online -> Icons.Filled.Bluetooth
                    connecting || ui.connection == ConnectionState.SCANNING -> Icons.Filled.BluetoothSearching
                    else -> Icons.Filled.BluetoothDisabled
                },
                contentDescription = null,
                tint = content
            )
            Column(Modifier.weight(1f)) {
                Text("Koneksi Perangkat", color = content.copy(alpha = 0.85f), fontSize = 12.sp)
                Text(
                    when {
                        online -> "Terhubung ke ${ui.deviceName ?: "DriveSafe"}"
                        connecting -> "Menghubungkan ke ${ui.deviceName ?: "alat"}…"
                        ui.connection == ConnectionState.SCANNING -> "Mencari alat…"
                        else -> "Tidak Terhubung"
                    },
                    color = content,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
            if (connecting) {
                CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp, color = content)
            } else {
                Box(
                    Modifier
                        .size(8.dp)
                        .background(if (online) SafeGreen else TextPrimary, CircleShape)
                )
            }
        }
    }
}

// ---------- Kartu kecil ----------

@Composable
private fun MiniStat(icon: ImageVector, text: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(22.dp))
        Text(text, fontSize = 12.sp, color = TextPrimary)
    }
}

private enum class Tone { Ok, Warn, Bad }

@Composable
private fun FlagTile(icon: ImageVector, label: String, value: String, tone: Tone, modifier: Modifier) {
    val (container, border, accent) = when (tone) {
        Tone.Ok -> Triple(SafeGreenSoft, SafeGreenBorder, SafeGreen)
        Tone.Warn -> Triple(WarnAmberSoft, WarnAmber.copy(alpha = 0.3f), WarnAmber)
        Tone.Bad -> Triple(DangerRedSoft.copy(alpha = 0.5f), DangerRed.copy(alpha = 0.3f), DangerRed)
    }
    DsCard(modifier.height(110.dp), container = container, border = border, contentPadding = 14.dp) {
        Icon(icon, contentDescription = null, tint = accent.darken(), modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(6.dp))
        Text(label, color = accent.darken(), fontSize = 13.sp)
        Text(value, color = accent, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SpeedTile(speed: Float, modifier: Modifier) {
    DsCard(
        modifier
            .height(110.dp)
            .shadow(3.dp, RoundedCornerShape(12.dp)),
        border = BorderLight,
        contentPadding = 14.dp
    ) {
        Icon(Icons.Outlined.Speed, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(6.dp))
        Text("Kecepatan", color = TextPrimary, fontSize = 13.sp)
        Row(verticalAlignment = Alignment.Bottom) {
            Text("%.0f".format(speed), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(" km/h", fontSize = 13.sp, color = TextPrimary, modifier = Modifier.padding(bottom = 4.dp))
        }
    }
}

private fun Color.darken(): Color = copy(red = red * 0.75f, green = green * 0.75f, blue = blue * 0.75f)

private fun gsmLabel(percent: Int): String = when {
    percent <= 0 -> "Tidak ada"
    percent < 30 -> "Lemah"
    else -> "OK"
}

fun vehicleIcon(profile: String): ImageVector = when (profile.lowercase()) {
    "mobil" -> Icons.Filled.DirectionsCar
    "sepeda" -> Icons.Filled.PedalBike
    else -> Icons.Filled.TwoWheeler
}

// ---------- Tombol SOS (tekan & tahan) ----------

@Composable
private fun SosButton(enabled: Boolean, hasContacts: Boolean, onTrigger: () -> Unit) {

    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var noContactWarning by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(150.dp)
                .pointerInput(enabled, hasContacts) {
                    detectTapGestures(onPress = {
                        if (!enabled) return@detectTapGestures
                        if (!hasContacts) {
                            noContactWarning = true
                            return@detectTapGestures
                        }
                        val hold = scope.launch {
                            progress.animateTo(1f, tween(SOS_HOLD_MS, easing = LinearEasing))
                            onTrigger()
                            progress.snapTo(0f)
                        }
                        tryAwaitRelease()
                        if (progress.value < 1f) {
                            hold.cancel()
                            scope.launch { progress.animateTo(0f, tween(200)) }
                        }
                    })
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.fillMaxSize()) {
                if (progress.value > 0f) {
                    val stroke = 6.dp.toPx()
                    drawArc(
                        color = EmergencyRed,
                        startAngle = -90f,
                        sweepAngle = 360f * progress.value,
                        useCenter = false,
                        topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                        size = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke),
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                }
            }
            Box(
                Modifier
                    .size(128.dp)
                    .shadow(10.dp, CircleShape)
                    .background(if (enabled) Color(0xFFB71C1C) else Color(0xFFB0B4BA), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.WifiTethering,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                    Text("SOS", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            if (enabled) "Tekan dan tahan 3 detik untuk mengaktifkan bantuan darurat"
            else "SOS bisa dipakai saat alat sedang memantau",
            fontSize = 13.sp,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp)
        )
    }

    if (noContactWarning) {
        AlertDialog(
            onDismissRequest = { noContactWarning = false },
            title = { Text("Belum ada kontak darurat") },
            text = { Text("Tambahkan dan kirim kontak darurat ke alat dulu supaya SOS bisa mengirim SMS.") },
            confirmButton = { TextButton(onClick = { noContactWarning = false }) { Text("Oke") } }
        )
    }
}
