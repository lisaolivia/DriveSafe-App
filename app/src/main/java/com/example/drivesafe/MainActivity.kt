package com.example.drivesafe

import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.example.drivesafe.ble.BlePermissions
import com.example.drivesafe.ui.DriveSafeRoot
import com.example.drivesafe.ui.theme.DriveSafeTheme
import com.example.drivesafe.viewmodel.ContactViewModel
import com.example.drivesafe.viewmodel.DeviceViewModel

class MainActivity : ComponentActivity() {

    private val deviceViewModel: DeviceViewModel by viewModels()
    private val contactViewModel: ContactViewModel by viewModels()

    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (permissions.values.all { it }) {
                deviceViewModel.autoConnect()
            } else {
                Toast.makeText(
                    this,
                    "Izin Bluetooth dibutuhkan untuk terhubung ke alat",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Desain hanya varian terang, jadi ikon status bar selalu gelap
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )

        if (BlePermissions.hasPermissions(this)) {
            deviceViewModel.autoConnect()
        } else {
            permissionLauncher.launch(BlePermissions.getRequiredPermissions())
        }

        setContent {
            DriveSafeTheme {
                DriveSafeRoot(
                    device = deviceViewModel,
                    contacts = contactViewModel
                )
            }
        }
    }
}