package com.example.drivesafe.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.ContactPhone
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.SettingsInputComponent
import androidx.compose.material.icons.filled.ContactPhone
import androidx.compose.material.icons.filled.SettingsInputComponent
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.drivesafe.model.DeviceConfig
import com.example.drivesafe.model.DeviceStatus
import com.example.drivesafe.ui.components.NoticeBox
import com.example.drivesafe.ui.components.PrimaryButton
import com.example.drivesafe.ui.contacts.ContactsScreen
import com.example.drivesafe.ui.device.DeviceScreen
import com.example.drivesafe.ui.emergency.EmergencyScreen
import com.example.drivesafe.ui.home.HomeScreen
import com.example.drivesafe.ui.theme.AppBackground
import com.example.drivesafe.ui.theme.BrandBlue
import com.example.drivesafe.ui.theme.BrandBlueDark
import com.example.drivesafe.ui.theme.DangerRed
import com.example.drivesafe.ui.theme.SafeGreen
import com.example.drivesafe.ui.theme.SafeGreenSoft
import com.example.drivesafe.ui.theme.SurfaceMuted
import com.example.drivesafe.ui.theme.TextPrimary
import com.example.drivesafe.ui.theme.TextSecondary
import com.example.drivesafe.ui.theme.WarnAmber
import com.example.drivesafe.ui.theme.WarnAmberSoft
import com.example.drivesafe.viewmodel.ContactViewModel
import com.example.drivesafe.viewmodel.DeviceUiState
import com.example.drivesafe.viewmodel.DeviceViewModel

private const val TAB_HOME = 0
private const val TAB_CONTACTS = 1
private const val TAB_DEVICE = 2

/**
 * Akar UI: tiga tab (Beranda, Kontak, IoT) + layar darurat yang menutupi semuanya.
 * Layar darurat muncul otomatis dari state alat, bukan dari navigasi.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveSafeRoot(device: DeviceViewModel, contacts: ContactViewModel) {

    val ui by device.ui.collectAsStateWithLifecycle()
    val config by contacts.config.collectAsStateWithLifecycle()
    val needsSync by contacts.needsSync.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(TAB_HOME) }
    var showNotifications by remember { mutableStateOf(false) }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        device.messages.collect { snackbar.showSnackbar(it) }
    }

    // Layar darurat: saat hitung mundur berjalan, setelah dibatalkan, dan selama rincian kejadian belum ditutup.
    // ALERT dihitung juga supaya tidak berkedip di antara COUNTDOWN dan kedatangan EVENT.
    val isAlert = ui.status?.status == DeviceStatus.ALERT
    var alertDismissed by remember { mutableStateOf(false) }
    LaunchedEffect(isAlert) { if (!isAlert) alertDismissed = false }

    val showEmergency = ui.countdown != null || ui.cancelledAt != null ||
            ui.lastEvent != null || (isAlert && !alertDismissed)
    BackHandler(enabled = showEmergency) { /* sengaja kosong: tidak boleh tertutup tidak sengaja */ }

    val notices = buildNotices(ui, config, needsSync)

    Box(Modifier.fillMaxSize()) {

        Scaffold(
            containerColor = AppBackground,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                TopHeader(
                    hasNotices = notices.any { it.level != NoticeLevel.Ok },
                    onBell = { showNotifications = true }
                )
            },
            bottomBar = {
                BottomNav(selected = tab, onSelect = { tab = it })
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (tab) {
                    TAB_HOME -> HomeScreen(
                        ui = ui,
                        config = config,
                        onOpenDevice = { tab = TAB_DEVICE },
                        onSos = device::simulateCrash
                    )

                    TAB_CONTACTS -> ContactsScreen(
                        ui = ui,
                        config = config,
                        needsSync = needsSync,
                        onAdd = contacts::addContact,
                        onUpdate = contacts::updateContact,
                        onDelete = contacts::deleteContact,
                        onPrimary = contacts::setPrimary,
                        onSync = device::syncNow,
                        onImport = device::importFromDevice,
                        onRefresh = { device.readDeviceConfig() }
                    )

                    else -> DeviceScreen(
                        ui = ui,
                        config = config,
                        needsSync = needsSync,
                        onScan = device::startScan,
                        onStopScan = device::stopScan,
                        onConnect = device::connect,
                        onDisconnect = device::disconnect,
                        onForget = device::forgetDevice,
                        onSaveSettings = contacts::saveSettings,
                        onSync = device::syncNow,
                        onRecalibrate = device::recalibrate,
                        onCheckBalance = device::checkBalance,
                        onTestSms = device::sendTestSms,
                        onSimulateCrash = device::simulateCrash
                    )
                }
            }
        }

        if (showNotifications) {
            ModalBottomSheet(
                onDismissRequest = { showNotifications = false },
                containerColor = Color.White
            ) {
                NotificationSheet(
                    notices = notices,
                    onAction = { action ->
                        showNotifications = false
                        when (action) {
                            NoticeAction.Contacts -> tab = TAB_CONTACTS
                            NoticeAction.Device -> tab = TAB_DEVICE
                            NoticeAction.Sync -> device.syncNow()
                        }
                    }
                )
            }
        }

        if (showEmergency) {
            EmergencyScreen(
                countdown = ui.countdown,
                cancelledAt = ui.cancelledAt,
                event = ui.lastEvent,
                status = ui.status,
                config = config,
                isSendingNow = ui.isSendingNow,
                onCancel = device::cancelAlarm,
                onSendNow = device::sendNow,
                onDismissCancelled = device::dismissCancelled,
                onDismiss = {
                    alertDismissed = true
                    device.dismissEvent()
                }
            )
        }
    }
}

// ---------- Header ----------

@Composable
private fun TopHeader(hasNotices: Boolean, onBell: () -> Unit) {
    Surface(
        color = Color(0xFFF1F3F5),
        shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
    ) {
        Row(
            Modifier
                .statusBarsPadding()
                .padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BrandMark(Modifier.weight(1f))
            IconButton(onClick = onBell) {
                Box {
                    Icon(Icons.Outlined.Notifications, contentDescription = "Notifikasi", tint = TextPrimary)
                    if (hasNotices) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .align(Alignment.TopEnd)
                                .background(DangerRed, CircleShape)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BrandMark(modifier: Modifier = Modifier, color: Color = BrandBlueDark) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(Icons.Filled.Shield, contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
        Text("DriveSafe", color = color, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

// ---------- Navigasi bawah ----------

private class NavItem(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val NAV_ITEMS = listOf(
    NavItem("Beranda", Icons.Outlined.Dashboard, Icons.Filled.Dashboard),
    NavItem("Kontak", Icons.Outlined.ContactPhone, Icons.Filled.ContactPhone),
    NavItem("IoT", Icons.Outlined.SettingsInputComponent, Icons.Filled.SettingsInputComponent)
)

@Composable
private fun BottomNav(selected: Int, onSelect: (Int) -> Unit) {
    Surface(
        color = Color(0xFFF1F3F5),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
    ) {
        Row(
            Modifier
                .navigationBarsPadding()
                .padding(horizontal = 40.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            NAV_ITEMS.forEachIndexed { index, item ->
                val isSelected = index == selected
                Surface(
                    onClick = { onSelect(index) },
                    shape = RoundedCornerShape(50),
                    color = if (isSelected) BrandBlue else Color.Transparent
                ) {
                    Column(
                        Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            if (isSelected) item.selectedIcon else item.icon,
                            contentDescription = null,
                            tint = if (isSelected) Color.White else TextPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            item.label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) Color.White else TextPrimary
                        )
                    }
                }
            }
        }
    }
}

// ---------- Notifikasi (lonceng) ----------

private enum class NoticeLevel { Ok, Warn, Bad }
private enum class NoticeAction { Contacts, Device, Sync }

private class Notice(
    val level: NoticeLevel,
    val text: String,
    val actionLabel: String? = null,
    val action: NoticeAction? = null
)

private fun buildNotices(ui: DeviceUiState, config: DeviceConfig, needsSync: Boolean): List<Notice> {
    val status = ui.status
    val online = ui.isOnline && status != null
    return buildList {
        if (!ui.isOnline) {
            add(
                Notice(
                    NoticeLevel.Warn,
                    "Alat tidak terhubung. Alat tetap mendeteksi kecelakaan dan mengirim SMS tanpa aplikasi, " +
                            "tapi alarm palsu tidak bisa dibatalkan selama tidak terhubung.",
                    "Hubungkan", NoticeAction.Device
                )
            )
        }
        if (config.contacts.isEmpty()) {
            add(Notice(NoticeLevel.Bad, "Belum ada kontak darurat. SMS tidak akan terkirim ke siapa pun.", "Tambah kontak", NoticeAction.Contacts))
        } else if (needsSync) {
            add(
                Notice(
                    NoticeLevel.Warn,
                    "Perubahan kontak / pengaturan belum dikirim ke alat.",
                    if (ui.isOnline) "Kirim ke alat" else null,
                    if (ui.isOnline) NoticeAction.Sync else null
                )
            )
        }
        if (online && !status!!.sensorOk) add(Notice(NoticeLevel.Bad, "Sensor alat bermasalah. Deteksi kecelakaan mungkin tidak bekerja."))
        if (online && !status!!.gpsFix) add(Notice(NoticeLevel.Warn, "GPS belum mengunci. Lokasi di SMS darurat bisa belum akurat. Bawa alat ke tempat terbuka."))
        if (online && status!!.gsmSignalPercent == 0) add(Notice(NoticeLevel.Bad, "Sinyal seluler alat 0%. SMS darurat mungkin tidak terkirim."))
        if (isEmpty()) add(Notice(NoticeLevel.Ok, "Semua aman. Alat siap memantau perjalananmu."))
    }
}

@Composable
private fun NotificationSheet(notices: List<Notice>, onAction: (NoticeAction) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Notifikasi", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
        notices.forEach { notice ->
            val (container, accent, icon) = when (notice.level) {
                NoticeLevel.Ok -> Triple(SafeGreenSoft, SafeGreen, Icons.Filled.CheckCircle)
                NoticeLevel.Warn -> Triple(WarnAmberSoft, WarnAmber, Icons.Filled.Warning)
                NoticeLevel.Bad -> Triple(Color(0xFFFFEBEE), DangerRed, Icons.Filled.Warning)
            }
            NoticeBox(
                icon = icon,
                title = null,
                body = notice.text,
                container = container,
                accent = accent,
                action = if (notice.action != null && notice.actionLabel != null) {
                    {
                        Spacer(Modifier.height(6.dp))
                        PrimaryButton(
                            notice.actionLabel,
                            onClick = { onAction(notice.action) },
                            modifier = Modifier.height(40.dp)
                        )
                    }
                } else null
            )
        }
        Text(
            "Ketuk di luar panel untuk menutup.",
            fontSize = 12.sp,
            color = TextSecondary
        )
    }
}
