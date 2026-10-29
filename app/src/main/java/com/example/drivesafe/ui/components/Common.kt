package com.example.drivesafe.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.drivesafe.model.DeviceStatus

/** Warna makna (aman / waspada / bahaya / netral), terbaca di tema terang maupun gelap. */
object StatusColors {
    val Ok = Color(0xFF43A047)
    val Warn = Color(0xFFF9A825)
    val Bad = Color(0xFFE53935)
    val Neutral = Color(0xFF8A8F98)
}

fun statusLabel(status: DeviceStatus): String = when (status) {
    DeviceStatus.BOOT -> "Alat menyala"
    DeviceStatus.CALIB -> "Kalibrasi sensor"
    DeviceStatus.MONITOR -> "Terpantau aman"
    DeviceStatus.VERIFY -> "Memeriksa guncangan"
    DeviceStatus.COUNTDOWN -> "Kecelakaan terdeteksi"
    DeviceStatus.ALERT -> "Mengirim SMS darurat"
    DeviceStatus.CANCELLED -> "Alarm dibatalkan"
    DeviceStatus.COOLDOWN -> "Jeda setelah kejadian"
    DeviceStatus.PAIRING -> "Mode pairing"
    else -> "Status tidak dikenal"
}

fun statusColor(status: DeviceStatus): Color = when (status) {
    DeviceStatus.MONITOR, DeviceStatus.CANCELLED -> StatusColors.Ok
    DeviceStatus.BOOT, DeviceStatus.CALIB, DeviceStatus.VERIFY, DeviceStatus.COOLDOWN -> StatusColors.Warn
    DeviceStatus.COUNTDOWN, DeviceStatus.ALERT -> StatusColors.Bad
    else -> StatusColors.Neutral
}

/** Kartu berjudul. Dasar semua bagian di layar. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier.fillMaxWidth()) {
        Column(
            Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

/** Label kecil berwarna, misal "Terhubung" atau "Belum dikirim". */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.16f)
    ) {
        Text(
            text,
            color = color,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

/** Angka besar dengan keterangan, dipakai di grid metrik Beranda. */
@Composable
fun MetricTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
    }
}

/** "baru saja", "5 menit lalu", "2 jam lalu", "3 hari lalu". */
fun formatAge(seconds: Int): String = when {
    seconds < 60 -> "baru saja"
    seconds < 3_600 -> "${seconds / 60} menit lalu"
    seconds < 86_400 -> "${seconds / 3_600} jam lalu"
    else -> "${seconds / 86_400} hari lalu"
}

fun signalLabel(rssi: Int): String = when {
    rssi >= -60 -> "Sinyal kuat"
    rssi >= -75 -> "Sinyal sedang"
    else -> "Sinyal lemah"
}