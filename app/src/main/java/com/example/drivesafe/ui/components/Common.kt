package com.example.drivesafe.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.drivesafe.model.DeviceStatus
import com.example.drivesafe.ui.theme.BorderInput
import com.example.drivesafe.ui.theme.BorderLight
import com.example.drivesafe.ui.theme.BrandBlue
import com.example.drivesafe.ui.theme.SurfaceMuted
import com.example.drivesafe.ui.theme.SurfaceWhite
import com.example.drivesafe.ui.theme.TextPrimary
import com.example.drivesafe.ui.theme.TextSecondary

/** Warna makna (aman / waspada / bahaya / netral). */
object StatusColors {
    val Ok = Color(0xFF16A34A)
    val Warn = Color(0xFFB45309)
    val Bad = Color(0xFFD32F2F)
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

/** Kartu putih bergaris tipis, dasar semua bagian di layar. */
@Composable
fun DsCard(
    modifier: Modifier = Modifier,
    container: Color = SurfaceWhite,
    border: Color = BorderLight,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    val inner: @Composable () -> Unit = {
        Column(Modifier.padding(contentPadding), content = content)
    }
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = container,
            border = BorderStroke(1.dp, border),
            content = inner
        )
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = container,
            border = BorderStroke(1.dp, border),
            content = inner
        )
    }
}

/** Kartu berjudul, untuk bagian pengaturan. */
@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    DsCard(modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
            content()
        }
    }
}

/** Tombol utama biru membulat dengan teks kapital, seperti "CARI & HUBUNGKAN". */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    container: Color = BrandBlue,
    content: Color = Color.White
) {
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .shadow(if (enabled) 4.dp else 0.dp, RoundedCornerShape(50), clip = false),
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container.copy(alpha = 0.4f),
            disabledContentColor = content.copy(alpha = 0.9f)
        )
    ) {
        if (loading) {
            CircularProgressIndicator(Modifier.size(18.dp), color = content, strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text.uppercase(), fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 0.3.sp)
    }
}

/** Tombol sekunder bergaris. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    color: Color = BrandBlue
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, if (enabled) color else BorderLight),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    }
}

/** Kolom isian dengan label di atas, latar abu-abu muda seperti mockup. */
@Composable
fun DsTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    trailingIcon: ImageVector? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    filled: Boolean = false,
    isError: Boolean = false
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 14.sp, color = TextPrimary, fontWeight = FontWeight.Medium)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = TextSecondary) },
            singleLine = true,
            isError = isError,
            trailingIcon = trailingIcon?.let { { Icon(it, contentDescription = null, tint = TextSecondary) } },
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = BorderInput,
                focusedBorderColor = BrandBlue,
                unfocusedContainerColor = if (filled) SurfaceMuted else SurfaceWhite,
                focusedContainerColor = SurfaceWhite
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** Label kecil berwarna, misal "Terhubung" atau "Belum dikirim". */
@Composable
fun Pill(text: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.14f)
    ) {
        Text(
            text,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
        )
    }
}

/** Kotak keterangan berwarna dengan ikon di kiri. */
@Composable
fun NoticeBox(
    icon: ImageVector,
    title: String?,
    body: String,
    container: Color,
    accent: Color,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null
) {
    DsCard(modifier, container = container, border = accent.copy(alpha = 0.25f)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (title != null) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = TextPrimary)
                }
                Text(body, fontSize = 13.sp, color = TextPrimary, lineHeight = 18.sp)
                action?.invoke()
            }
        }
    }
}

/** Baris "ikon bulat + judul + keterangan". */
@Composable
fun IconTitleRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    titleColor: Color = TextPrimary,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        icon()
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = titleColor)
            if (subtitle != null) Text(subtitle, fontSize = 13.sp, color = TextSecondary)
        }
        trailing?.invoke()
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

/** Label profil kendaraan untuk tampilan: "motor" -> "Motor". */
fun profileLabel(profile: String): String = when (profile.lowercase()) {
    "sepeda" -> "Sepeda"
    "motor" -> "Motor"
    "mobil" -> "Mobil"
    else -> profile.replaceFirstChar { it.uppercase() }.ifBlank { "-" }
}

/** Zona waktu Indonesia (WIB/WITA/WIT), jatuh ke singkatan bawaan kalau di luar Indonesia. */
fun timeZoneLabel(): String {
    val tz = java.util.TimeZone.getDefault()
    return when (tz.id) {
        "Asia/Jakarta", "Asia/Pontianak" -> "WIB"
        "Asia/Makassar" -> "WITA"
        "Asia/Jayapura" -> "WIT"
        else -> tz.getDisplayName(tz.inDaylightTime(java.util.Date()), java.util.TimeZone.SHORT)
    }
}

/** "14:23:07 WIB" */
fun formatClock(epochMillis: Long): String =
    java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(epochMillis)) +
            " " + timeZoneLabel()
