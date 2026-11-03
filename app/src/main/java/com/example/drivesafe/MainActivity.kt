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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import com.example.drivesafe.ble.BlePermissions
import com.example.drivesafe.ui.DriveSafeRoot
import com.example.drivesafe.ui.splash.SplashScreen
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
            if (!permissions.values.all { it }) {
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

        // Tidak menyambung otomatis: alat baru dicari saat user menekan CARI di Beranda
        if (!BlePermissions.hasPermissions(this)) {
            permissionLauncher.launch(BlePermissions.getRequiredPermissions())
        }

        setContent {
            DriveSafeTheme {
                // Layar pembuka hanya sekali per buka aplikasi (tidak muncul lagi saat layar diputar)
                var showSplash by rememberSaveable { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    delay(SPLASH_DURATION_MS)
                    showSplash = false
                }

                Box(Modifier.fillMaxSize()) {
                    DriveSafeRoot(
                        device = deviceViewModel,
                        contacts = contactViewModel
                    )
                    AnimatedVisibility(visible = showSplash, enter = EnterTransition.None, exit = fadeOut()) {
                        SplashScreen()
                    }
                }
            }
        }
    }

    private companion object {
        const val SPLASH_DURATION_MS = 1_600L
    }
}
