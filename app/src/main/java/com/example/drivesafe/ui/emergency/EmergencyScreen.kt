package com.example.drivesafe.ui.emergency

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.drivesafe.model.AccidentEvent
import com.example.drivesafe.model.EventPhase
import com.example.drivesafe.viewmodel.CountdownUi

private val Background = Color(0xFFB71C1C)
private val OnBackground = Color.White

/**
 * Layar penuh saat kecelakaan. Dua tahap:
 * - hitung mundur dengan tombol "Saya baik-baik saja" (st = COUNTDOWN)
 * - rincian kejadian setelah SMS dikirim (EVENT SENDING lalu SENT), dengan tombol Tutup.
 */
@Composable
fun EmergencyScreen(
    countdown: CountdownUi?,
    event: AccidentEvent?,
    onCancel: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Background)
            .systemBarsPadding()
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically)
        ) {
            if (countdown != null) {
                CountdownStage(countdown, onCancel)
            } else if (event != null) {
                EventStage(event, onDismiss)
            } else {
                // ALERT sudah mulai tapi rincian kejadian belum sampai
                Text(
                    "Mengirim SMS darurat…",
                    color = OnBackground,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                LinearProgressIndicator(
                    Modifier.fillMaxWidth(),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f)
                )
            }
        }
    }
}

@Composable
private fun CountdownStage(countdown: CountdownUi, onCancel: () -> Unit) {

    Text(
        "KECELAKAAN TERDETEKSI",
        color = OnBackground,
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )

    val progress = if (countdown.totalSeconds > 0) {
        countdown.secondsLeft.toFloat() / countdown.totalSeconds
    } else {
        0f
    }
    CountdownRing(progress = progress.coerceIn(0f, 1f), text = countdown.secondsLeft.toString())

    Text(
        "SMS darurat akan dikirim ke kontakmu kalau tidak dibatalkan.",
        color = OnBackground,
        textAlign = TextAlign.Center
    )

    Button(
        onClick = onCancel,
        enabled = countdown.canCancel,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Background,
            disabledContainerColor = Color.White.copy(alpha = 0.35f),
            disabledContentColor = Color.White
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Text(
            if (countdown.canCancel) "SAYA BAIK-BAIK SAJA" else "Tunggu sebentar…",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }

    Text(
        "Bisa juga dibatalkan lewat tombol fisik di alat.",
        color = OnBackground.copy(alpha = 0.85f),
        textAlign = TextAlign.Center
    )
}

@Composable
private fun EventStage(event: AccidentEvent, onDismiss: () -> Unit) {

    val uriHandler = LocalUriHandler.current
    val sending = event.phase == EventPhase.SENDING

    Text(
        if (sending) "Mengirim SMS darurat…" else "Pesan darurat terkirim",
        color = OnBackground,
        fontSize = 26.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )

    if (sending) {
        LinearProgressIndicator(
            Modifier.fillMaxWidth(),
            color = Color.White,
            trackColor = Color.White.copy(alpha = 0.3f)
        )
        Text(
            "Ini bisa makan waktu sekitar satu menit kalau sinyal lemah.",
            color = OnBackground.copy(alpha = 0.85f),
            textAlign = TextAlign.Center
        )
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.14f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        DetailLine("Jenis", event.typeName.ifBlank { "Tidak diketahui" })
        DetailLine("Keparahan", event.severityName.ifBlank { "-" })
        DetailLine("Gaya benturan", "${"%.1f".format(event.gForce)} g")
        DetailLine(
            "Waktu",
            if (event.timestampEpoch == 0L) "GPS belum fix"
            else java.text.DateFormat.getDateTimeInstance()
                .format(java.util.Date(event.timestampEpoch * 1000))
        )
        DetailLine(
            "Lokasi",
            if (event.latitude == 0.0 && event.longitude == 0.0) "Belum tersedia (GPS belum fix)"
            else "${"%.5f".format(event.latitude)}, ${"%.5f".format(event.longitude)}"
        )
    }

    if (event.mapUrl.isNotBlank() && !(event.latitude == 0.0 && event.longitude == 0.0)) {
        OutlinedButton(
            onClick = {
                val url = if (event.mapUrl.startsWith("http")) event.mapUrl else "https://${event.mapUrl}"
                uriHandler.openUri(url)
            },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
        ) { Text("Buka peta") }
    }

    Button(
        onClick = onDismiss,
        modifier = Modifier.fillMaxWidth(),
        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Background)
    ) { Text("Tutup", fontWeight = FontWeight.Bold) }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(label, color = OnBackground.copy(alpha = 0.75f), fontSize = 12.sp)
        Text(value, color = OnBackground, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CountdownRing(progress: Float, text: String) {
    Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 14.dp.toPx()
            val topLeft = Offset(stroke / 2, stroke / 2)
            val arcSize = Size(size.width - stroke, size.height - stroke)

            drawArc(
                color = Color.White.copy(alpha = 0.25f),
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
        Text(text, color = OnBackground, fontSize = 72.sp, fontWeight = FontWeight.Bold)
    }
}