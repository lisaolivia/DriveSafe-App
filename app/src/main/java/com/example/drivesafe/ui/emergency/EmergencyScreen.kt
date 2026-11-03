package com.example.drivesafe.ui.emergency

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CarCrash
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.drivesafe.model.AccidentEvent
import com.example.drivesafe.model.DeviceConfig
import com.example.drivesafe.model.DeviceStatusPayload
import com.example.drivesafe.model.EmergencyContact
import com.example.drivesafe.model.EventPhase
import com.example.drivesafe.ui.components.PrimaryButton
import com.example.drivesafe.ui.components.formatClock
import com.example.drivesafe.ui.components.profileLabel
import com.example.drivesafe.ui.theme.AppBackground
import com.example.drivesafe.ui.theme.BorderInput
import com.example.drivesafe.ui.theme.BorderLight
import com.example.drivesafe.ui.theme.BrandBlue
import com.example.drivesafe.ui.theme.BrandBlueDark
import com.example.drivesafe.ui.theme.BrandBlueSoft
import com.example.drivesafe.ui.theme.DangerRed
import com.example.drivesafe.ui.theme.DangerRedDark
import com.example.drivesafe.ui.theme.DangerRedSoft
import com.example.drivesafe.ui.theme.SafeGreen
import com.example.drivesafe.ui.theme.SafeGreenDark
import com.example.drivesafe.ui.theme.SurfaceMuted
import com.example.drivesafe.ui.theme.TextPrimary
import com.example.drivesafe.ui.theme.TextSecondary
import com.example.drivesafe.ui.theme.WarnAmber
import com.example.drivesafe.viewmodel.CountdownUi
import kotlin.random.Random

private val CountdownRed = Color(0xFFC62828)

/**
 * Layar penuh saat kecelakaan. Tiga tahap:
 * - hitung mundur dengan "Saya baik-baik saja" (st = COUNTDOWN)
 * - "False alarm dibatalkan" kalau alarm dibatalkan sebelum SMS terkirim
 * - rincian kejadian + kontak yang diberitahu (ALERT, EVENT SENDING lalu SENT)
 */
@Composable
fun EmergencyScreen(
    countdown: CountdownUi?,
    cancelledAt: Long?,
    event: AccidentEvent?,
    status: DeviceStatusPayload?,
    config: DeviceConfig,
    onCancel: () -> Unit,
    onDismissCancelled: () -> Unit,
    onDismiss: () -> Unit
) {
    when {
        countdown != null -> CountdownStage(countdown, onCancel)
        // EVENT berarti SMS dikirim, jadi rincian kecelakaan selalu menang atas layar "dibatalkan"
        event != null -> AlertStage(event, status, config, onCancel, onDismiss)
        cancelledAt != null -> CancelledStage(cancelledAt, onDismissCancelled)
        else -> AlertStage(event, status, config, onCancel, onDismiss)
    }
}

// ======================================================================
// Hitung mundur
// ======================================================================

@Composable
private fun CountdownStage(countdown: CountdownUi, onCancel: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(CountdownRed)
    ) {
        MapPattern(Modifier.fillMaxSize(), lineColor = Color.White.copy(alpha = 0.10f))

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(color = Color.White.copy(alpha = 0.2f), shape = RoundedCornerShape(50)) {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Shield, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Text("DriveSafe", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            Spacer(Modifier.height(24.dp))
            Box(
                Modifier
                    .size(72.dp)
                    .background(Color.White, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.CarCrash, contentDescription = null, tint = DangerRed, modifier = Modifier.size(42.dp))
            }

            Spacer(Modifier.height(20.dp))
            Text(
                "KECELAKAAN\nTERDETEKSI!",
                color = Color.White,
                fontSize = 30.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "Sinyal darurat akan dikirim ke kontak darurat Anda dalam…",
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            )

            Spacer(Modifier.height(4.dp))
            val progress = if (countdown.totalSeconds > 0) {
                countdown.secondsLeft.toFloat() / countdown.totalSeconds
            } else {
                0f
            }
            Box(
                Modifier
                    .size(300.dp)
                    .background(Color.White.copy(alpha = 0.25f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                CountdownRing(progress.coerceIn(0f, 1f), countdown.secondsLeft.toString())
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onCancel,
                enabled = countdown.canCancel,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .shadow(6.dp, RoundedCornerShape(12.dp)),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = DangerRed,
                    disabledContainerColor = Color.White.copy(alpha = 0.6f),
                    disabledContentColor = DangerRed.copy(alpha = 0.7f)
                )
            ) {
                Text(
                    if (countdown.canCancel) "SAYA BAIK-BAIK SAJA" else "TUNGGU SEBENTAR…",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
private fun CountdownRing(progress: Float, text: String) {
    Box(Modifier.size(190.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 10.dp.toPx()
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arcSize = Size(size.width - stroke, size.height - stroke)

            drawArc(
                color = Color.White.copy(alpha = 0.3f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke)
            )
            drawArc(
                color = Color.White,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
        Text(text, color = Color.White, fontSize = 52.sp, fontWeight = FontWeight.ExtraBold)
    }
}

// ======================================================================
// False alarm dibatalkan
// ======================================================================

@Composable
private fun CancelledStage(cancelledAt: Long, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFDDEBE0), Color(0xFFF4F6F8))))
            .verticalScroll(rememberScrollState())
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color(0xFF2BC25A), Color(0xFF1E9E48))))
                .statusBarsPadding()
                .padding(vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Filled.GppGood, contentDescription = null, tint = Color.White, modifier = Modifier.size(84.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                "FALSE ALARM\nDIBATALKAN",
                color = Color.White,
                fontSize = 26.sp,
                lineHeight = 32.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        Column(
            Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                Modifier
                    .width(210.dp)
                    .height(240.dp)
                    .background(Color(0xFFCFE3D3).copy(alpha = 0.7f), RoundedCornerShape(50)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    Modifier
                        .size(140.dp)
                        .shadow(8.dp, CircleShape)
                        .background(Color.White, CircleShape)
                        .border(4.dp, Color(0xFFE6F2E8), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.GppGood, contentDescription = null, tint = SafeGreenDark, modifier = Modifier.size(90.dp))
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("Aman!", color = SafeGreenDark, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(6.dp))
            Text(
                "Notifikasi tidak jadi dikirim. Semua kontak darurat tidak dihubungi.",
                color = TextPrimary.copy(alpha = 0.85f),
                fontSize = 15.sp,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(40.dp))
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, BorderLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .background(SurfaceMuted, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.History, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
                    }
                    Column {
                        Text("STATUS PEMBATALAN", fontSize = 14.sp, letterSpacing = 1.sp, color = TextPrimary)
                        Text(
                            "Dibatalkan oleh pengguna pada ${formatClock(cancelledAt)}",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .shadow(4.dp, RoundedCornerShape(10.dp)),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandBlueDark)
            ) {
                Text("Kembali ke Home", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }
    }
}

// ======================================================================
// Kecelakaan terdeteksi (SMS sedang / sudah dikirim)
// ======================================================================

@Composable
private fun AlertStage(
    event: AccidentEvent?,
    status: DeviceStatusPayload?,
    config: DeviceConfig,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val sent = event?.phase == EventPhase.SENT

    // Jam kejadian: pakai waktu GPS kalau ada, kalau tidak pakai waktu HP saat event pertama diterima
    val receivedAt = remember(event?.seq) { System.currentTimeMillis() }
    val eventMillis = if (event != null && event.timestampEpoch > 0) event.timestampEpoch * 1000 else receivedAt
    val hasLocation = event != null && !(event.latitude == 0.0 && event.longitude == 0.0)

    Column(
        Modifier
            .fillMaxSize()
            .background(AppBackground)
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            // ---- Header merah ----
            Column(
                Modifier
                    .fillMaxWidth()
                    .shadow(6.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xFFC62828), DangerRedDark)))
                    .statusBarsPadding()
                    .padding(vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = Color.White, modifier = Modifier.size(64.dp))
                Text(
                    "KECELAKAAN\nTERDETEKSI",
                    color = Color.White,
                    fontSize = 26.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(4.dp))
                Text("Segera berikan pertolongan!", color = Color.White, fontSize = 14.sp)
            }

            Column(
                Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ---- Ringkasan kejadian ----
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.5.dp, DangerRed),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            Box(
                                Modifier
                                    .size(46.dp)
                                    .background(DangerRedSoft, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.CarCrash, contentDescription = null, tint = DangerRed, modifier = Modifier.size(24.dp))
                            }
                            Column {
                                Text(
                                    "Kecelakaan terdeteksi!",
                                    color = DangerRed,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    lineHeight = 28.sp
                                )
                                Text("Pukul ${formatClock(eventMillis)}", fontSize = 11.sp, color = TextSecondary)
                            }
                        }
                        HorizontalDivider(Modifier.padding(vertical = 14.dp), color = BorderLight)
                        Row {
                            Detail("KENDARAAN", vehicleName(status?.profile?.ifBlank { null } ?: config.profile), Modifier.weight(1f))
                            Detail("G-FORCE", event?.let { "%.2fg".format(it.gForce) } ?: "-", Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(14.dp))
                        Row {
                            Detail("TIPE BENTURAN", event?.typeName?.ifBlank { null } ?: "Tidak diketahui", Modifier.weight(1f))
                            Detail("KEPARAHAN", event?.severityName?.ifBlank { null } ?: "-", Modifier.weight(1f))
                        }
                    }
                }

                // ---- Lokasi ----
                Surface(color = Color(0xFFEFF1F4), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(
                            Modifier
                                .size(36.dp)
                                .background(BrandBlueSoft, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.LocationOn, contentDescription = null, tint = BrandBlueDark, modifier = Modifier.size(20.dp))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Lokasi Kejadian", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                            Text(
                                if (hasLocation) "%.4f,\n%.4f".format(event!!.latitude, event.longitude)
                                else if (event == null) "Menunggu data dari alat…"
                                else "Belum tersedia (GPS belum fix)",
                                fontFamily = if (hasLocation) FontFamily.Monospace else FontFamily.Default,
                                fontSize = 14.sp,
                                color = TextPrimary
                            )
                            if (hasLocation) {
                                Row(
                                    Modifier.clickable { uriHandler.openUri(mapsUrl(event!!)) },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("Buka di Google Maps", color = BrandBlueDark, fontSize = 15.sp)
                                    Icon(Icons.Outlined.OpenInNew, contentDescription = null, tint = BrandBlueDark, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        MapThumbnail(hasLocation)
                    }
                }

                // ---- Kontak yang diberitahu ----
                Text("Kontak yang diberitahu", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                val contacts = config.contacts.sortedBy { it.priority }
                if (contacts.isEmpty()) {
                    Text("Belum ada kontak darurat di aplikasi.", fontSize = 13.sp, color = TextSecondary)
                } else {
                    contacts.forEach { NotifiedContactRow(it, event?.phase) }
                }
            }
        }

        // ---- Tombol bawah ----
        HorizontalDivider(color = BorderLight)
        Column(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFFF4F5F7))
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (sent) {
                PrimaryButton("Kembali ke Home", onClick = onDismiss, container = BrandBlueDark)
            } else {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.5.dp, BorderInput),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextSecondary)
                ) {
                    Icon(Icons.Outlined.Cancel, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Batalkan — False Alarm", fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun Detail(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, fontSize = 11.sp, color = TextSecondary, letterSpacing = 0.5.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
    }
}

@Composable
private fun NotifiedContactRow(contact: EmergencyContact, phase: EventPhase?) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, BorderLight),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .background(Color(0xFFE5E7EB), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(initials(contact.name), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = TextPrimary)
            }
            Column(Modifier.weight(1f)) {
                Text(contact.name, fontSize = 15.sp, color = TextPrimary)
                Text(contact.phone, fontSize = 11.sp, color = TextSecondary)
            }
            when (phase) {
                EventPhase.SENT -> StatusTag(Icons.Filled.CheckCircle, "SMS Terkirim", SafeGreen)
                else -> StatusTag(Icons.Outlined.Sync, "Mengirim...", WarnAmber)
            }
        }
    }
}

@Composable
private fun StatusTag(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(text, color = color, fontSize = 12.sp)
    }
}

/** Gambar peta mini bergaya malam dengan pin merah (dekoratif). */
@Composable
private fun MapThumbnail(hasLocation: Boolean) {
    Box(
        Modifier
            .size(96.dp)
            .shadow(3.dp, RoundedCornerShape(10.dp))
            .background(Color(0xFF1C2230), RoundedCornerShape(10.dp)),
        contentAlignment = Alignment.Center
    ) {
        MapPattern(Modifier.fillMaxSize(), lineColor = Color(0xFF5A6478).copy(alpha = 0.7f), seed = 3, glow = true)
        Icon(
            Icons.Filled.LocationOn,
            contentDescription = null,
            tint = if (hasLocation) Color(0xFFE53935) else Color(0xFF8A8F98),
            modifier = Modifier.size(28.dp)
        )
    }
}

/** Pola jalan acak (tetap untuk seed yang sama), dipakai sebagai latar peta. */
@Composable
private fun MapPattern(modifier: Modifier, lineColor: Color, seed: Int = 7, glow: Boolean = false) {
    Canvas(modifier) {
        val rnd = Random(seed)
        drawRoads(rnd, lineColor, count = 22, minWidth = 1f, maxWidth = 3f)
        drawRoads(rnd, lineColor.copy(alpha = lineColor.alpha * 1.6f), count = 4, minWidth = 4f, maxWidth = 8f)
        if (glow) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFFFFC107).copy(alpha = 0.35f), Color.Transparent),
                    center = center,
                    radius = size.minDimension / 2
                ),
                radius = size.minDimension / 2
            )
        }
    }
}

private fun DrawScope.drawRoads(rnd: Random, color: Color, count: Int, minWidth: Float, maxWidth: Float) {
    repeat(count) {
        val horizontal = rnd.nextBoolean()
        val a = rnd.nextFloat()
        val b = (a + (rnd.nextFloat() - 0.5f) * 0.6f)
        val start = if (horizontal) Offset(0f, size.height * a) else Offset(size.width * a, 0f)
        val end = if (horizontal) Offset(size.width, size.height * b) else Offset(size.width * b, size.height)
        drawLine(
            color = color,
            start = start,
            end = end,
            strokeWidth = (minWidth + rnd.nextFloat() * (maxWidth - minWidth)) * density
        )
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

private fun vehicleName(profile: String): String = when (profile.lowercase()) {
    "motor" -> "Sepeda Motor"
    "sepeda" -> "Sepeda"
    "mobil" -> "Mobil"
    else -> profileLabel(profile)
}

private fun mapsUrl(event: AccidentEvent): String = when {
    event.mapUrl.startsWith("http") -> event.mapUrl
    event.mapUrl.isNotBlank() -> "https://${event.mapUrl}"
    else -> "https://maps.google.com/?q=${event.latitude},${event.longitude}"
}
