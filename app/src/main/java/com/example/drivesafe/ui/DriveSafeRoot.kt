package com.example.drivesafe.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.drivesafe.model.DeviceStatus
import com.example.drivesafe.ui.contacts.ContactsScreen
import com.example.drivesafe.ui.device.DeviceScreen
import com.example.drivesafe.ui.emergency.EmergencyScreen
import com.example.drivesafe.ui.home.HomeScreen
import com.example.drivesafe.viewmodel.ContactViewModel
import com.example.drivesafe.viewmodel.DeviceViewModel

private const val TAB_HOME = 0
private const val TAB_CONTACTS = 1
private const val TAB_DEVICE = 2

/**
 * Akar UI: tiga tab (Beranda, Kontak, Alat) + layar darurat yang menutupi semuanya.
 * Layar darurat muncul otomatis dari state alat, bukan dari navigasi.
 */
@Composable
fun DriveSafeRoot(device: DeviceViewModel, contacts: ContactViewModel) {

    val ui by device.ui.collectAsStateWithLifecycle()
    val config by contacts.config.collectAsStateWithLifecycle()
    val needsSync by contacts.needsSync.collectAsStateWithLifecycle()

    var tab by rememberSaveable { mutableStateOf(TAB_HOME) }

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        device.messages.collect { snackbar.showSnackbar(it) }
    }

    // Layar darurat: saat hitung mundur berjalan, dan selama rincian kejadian belum ditutup.
    // ALERT dihitung juga supaya tidak berkedip di antara COUNTDOWN dan kedatangan EVENT.
    val isAlert = ui.status?.status == DeviceStatus.ALERT
    var alertDismissed by remember { mutableStateOf(false) }
    LaunchedEffect(isAlert) { if (!isAlert) alertDismissed = false }

    val showEmergency = ui.countdown != null || ui.lastEvent != null || (isAlert && !alertDismissed)
    BackHandler(enabled = showEmergency) { /* sengaja kosong: tidak boleh tertutup tidak sengaja */ }

    Box(Modifier.fillMaxSize()) {

        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == TAB_HOME,
                        onClick = { tab = TAB_HOME },
                        icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                        label = { Text("Beranda") }
                    )
                    NavigationBarItem(
                        selected = tab == TAB_CONTACTS,
                        onClick = { tab = TAB_CONTACTS },
                        icon = { Icon(Icons.Filled.Person, contentDescription = null) },
                        label = { Text("Kontak") }
                    )
                    NavigationBarItem(
                        selected = tab == TAB_DEVICE,
                        onClick = { tab = TAB_DEVICE },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        label = { Text("Alat") }
                    )
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (tab) {
                    TAB_HOME -> HomeScreen(
                        ui = ui,
                        config = config,
                        needsSync = needsSync,
                        onOpenDevice = { tab = TAB_DEVICE },
                        onOpenContacts = { tab = TAB_CONTACTS },
                        onSync = device::syncNow
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

        if (showEmergency) {
            EmergencyScreen(
                countdown = ui.countdown,
                event = ui.lastEvent,
                onCancel = device::cancelAlarm,
                onDismiss = {
                    alertDismissed = true
                    device.dismissEvent()
                }
            )
        }
    }
}