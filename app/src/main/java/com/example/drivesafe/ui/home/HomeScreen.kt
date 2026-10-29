package com.example.drivesafe.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.drivesafe.model.ConnectionState
import com.example.drivesafe.model.DeviceConfig
import com.example.drivesafe.ui.components.MetricTile
import com.example.drivesafe.ui.components.Pill
import com.example.drivesafe.ui.components.SectionCard
import com.example.drivesafe.ui.components.StatusColors
import com.example.drivesafe.ui.components.formatAge
import com.example.drivesafe.ui.components.statusColor
import com.example.drivesafe.ui.components.statusLabel
import com.example.drivesafe.viewmodel.DeviceUiState

@Composable
fun HomeScreen(
    ui: DeviceUiState,
    config: DeviceConfig,
    needsSync: Boolean,
    onOpenDevice: () -> Unit,
    onOpenContacts: () -> Unit,
    onSync: () -> Unit
) {
    val status = ui.status
    val online = ui.isOnline && status != null

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        item {
            Text("DriveSafe", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }

        // ---- Kartu status utama ----
        item {
            val hero = heroInfo(ui)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = hero.color.copy(alpha = 0.16f))
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (online) {
                        Pill("Terhubung · ${ui.deviceName ?: "DriveSafe"}", StatusColors.Ok)
                    } else {
                        Pill("Offline", StatusColors.Neutral)
                    }
                    Text(hero.title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(hero.subtitle, style = MaterialTheme.typography.bodyMedium)
                    if (!online && ui.connection != ConnectionState.CONNECTING) {
                        Button(onClick = onOpenDevice) { Text("Hubungkan alat") }
                    }
                }
            }
        }

        // ---- Metrik ----
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricTile(
                        "Baterai alat",
                        if (online) "${status!!.batteryPercent}%" else "–",
                        Modifier.weight(1f)
                    )
                    MetricTile(
                        "Sinyal seluler",
                        if (online) "${status!!.gsmSignalPercent}%" else "–",
                        Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricTile(
                        "GPS",
                        when {
                            !online -> "–"
                            status!!.gpsFix -> "Fix · ${status.satelliteCount} sat"
                            else -> "Belum fix"
                        },
                        Modifier.weight(1f)
                    )
                    MetricTile(
                        "Kecepatan",
                        if (online) "${"%.0f".format(status!!.speedKmh)} km/j" else "–",
                        Modifier.weight(1f)
                    )
                }
            }
        }

        // ---- Peringatan yang perlu ditindak ----
        val warnings = buildList {
            if (config.contacts.isEmpty()) add("Belum ada kontak darurat. SMS tidak akan terkirim ke siapa pun.")
            if (online && !status!!.sensorOk) add("Sensor alat bermasalah. Deteksi kecelakaan mungkin tidak bekerja.")
            if (online && !status!!.gpsFix) add("GPS belum mengunci. Lokasi di SMS darurat bisa belum akurat. Bawa alat ke tempat terbuka.")
            if (online && status!!.gsmSignalPercent == 0) add("Sinyal seluler alat 0%. SMS darurat mungkin tidak terkirim.")
        }
        if (warnings.isNotEmpty()) {
            item {
                SectionCard("Perlu diperhatikan") {
                    warnings.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                    if (config.contacts.isEmpty()) {
                        FilledTonalButton(onClick = onOpenContacts) { Text("Tambah kontak") }
                    }
                }
            }
        }

        // ---- Pulsa ----
        item {
            SectionCard("Pulsa SIM alat") {
                val snapshot = ui.deviceConfig
                if (online && snapshot != null && snapshot.balanceText.isNotBlank()) {
                    Text(snapshot.balanceText, style = MaterialTheme.typography.bodyLarge)
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
                FilledTonalButton(onClick = onOpenDevice) { Text("Cek pulsa") }
            }
        }

        // ---- Ringkasan kontak ----
        item {
            SectionCard("Kontak darurat") {
                val primary = config.contacts.minByOrNull { it.priority }
                Text(
                    if (primary == null) "Belum ada kontak"
                    else "${config.contacts.size} kontak · utama: ${primary.name}",
                    style = MaterialTheme.typography.bodyLarge
                )
                if (needsSync && config.contacts.isNotEmpty()) {
                    Pill("Belum dikirim ke alat", StatusColors.Warn)
                    if (ui.isOnline) {
                        Button(onClick = onSync, enabled = !ui.isSyncing) {
                            Text(if (ui.isSyncing) "Mengirim..." else "Kirim ke alat")
                        }
                    } else {
                        Text(
                            "Hubungkan alat dulu untuk mengirim kontak.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (config.contacts.isNotEmpty()) {
                    Pill("Tersinkron ke alat", StatusColors.Ok)
                }
                FilledTonalButton(onClick = onOpenContacts) { Text("Kelola kontak") }
            }
        }

        // ---- Profil ----
        item {
            SectionCard("Pengaturan aktif") {
                Text("Pemilik: ${config.owner.ifBlank { "belum diisi" }}")
                Text("Kendaraan: ${config.profile}")
                Text("Hitung mundur: ${config.countdownSeconds} detik")
            }
        }
    }
}

private class Hero(val title: String, val subtitle: String, val color: Color)

private fun heroInfo(ui: DeviceUiState): Hero {

    val status = ui.status

    if (!ui.isOnline || status == null) {
        return when (ui.connection) {
            ConnectionState.CONNECTING, ConnectionState.CONNECTED ->
                Hero("Menghubungkan…", "Mohon tunggu sebentar.", StatusColors.Neutral)
            else ->
                Hero(
                    "Alat tidak terhubung",
                    "Mode offline: alat tetap mendeteksi kecelakaan dan mengirim SMS tanpa aplikasi. " +
                            "Yang hilang hanya tampilan langsung dan pembatalan lewat aplikasi.",
                    StatusColors.Neutral
                )
        }
    }

    val subtitle = when (status.status) {
        com.example.drivesafe.model.DeviceStatus.MONITOR -> "Alat memantau perjalananmu."
        com.example.drivesafe.model.DeviceStatus.CALIB -> "Letakkan alat diam di permukaan datar."
        com.example.drivesafe.model.DeviceStatus.VERIFY -> "Ada guncangan, alat sedang memastikan."
        com.example.drivesafe.model.DeviceStatus.COOLDOWN -> "Alat kembali memantau sebentar lagi."
        com.example.drivesafe.model.DeviceStatus.CANCELLED -> "Alarm dibatalkan, tidak ada SMS yang dikirim."
        else -> ""
    }
    return Hero(statusLabel(status.status), subtitle, statusColor(status.status))
}