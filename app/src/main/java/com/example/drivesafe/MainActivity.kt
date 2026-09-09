package com.example.drivesafe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.drivesafe.ui.theme.DriveSafeTheme
import androidx.activity.result.contract.ActivityResultContracts
import com.example.drivesafe.ble.BlePermissions
import com.example.drivesafe.ble.BleScanner
import com.example.drivesafe.ble.BleManager

class MainActivity : ComponentActivity() {
    private val permissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->

            val allGranted = permissions.values.all { it }

            if (allGranted) {
                // Semua permission granted, aman lanjut scan/connect
            } else {
                // Ada yang ditolak, tampilkan pesan ke user
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!BlePermissions.hasPermissions(this)) {
            permissionLauncher.launch(
                BlePermissions.getRequiredPermissions()
            )
        }

        val bleManager = BleManager(this)

        lateinit var scanner: BleScanner

        scanner = BleScanner(this) { device ->
            scanner.stopScan()
            bleManager.connect(device)
        }

        setContent {
            DriveSafeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting(
                        name = "Android",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }

        android.os.Handler(mainLooper).postDelayed({
            scanner.startScan()
        }, 2000)
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    DriveSafeTheme {
        Greeting("Android")
    }
}