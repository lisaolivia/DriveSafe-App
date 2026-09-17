package com.example.drivesafe

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.drivesafe.ble.BleCommands
import com.example.drivesafe.ble.BleJsonParser
import com.example.drivesafe.ble.BleManager
import com.example.drivesafe.ble.BlePermissions
import com.example.drivesafe.ble.BleScanner
import com.example.drivesafe.model.EmergencyContact
import com.example.drivesafe.ui.theme.DriveSafeTheme

class MainActivity : ComponentActivity() {

    private lateinit var bleManager: BleManager
    private lateinit var scanner: BleScanner

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

        bleManager = BleManager(this)

        scanner = BleScanner(this) { device ->
            scanner.stopScan()
            // Delay 300ms sebelum connect, biar radio BLE sempet "reset" mode
            android.os.Handler(mainLooper).postDelayed({
                bleManager.connect(device)
            }, 300)
        }

        setContent {
            DriveSafeTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .padding(16.dp)
                    ) {
                        Button(onClick = {
                            bleManager.sendCommand(BleCommands.CANCEL)
                        }) {
                            Text("Kirim CANCEL")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(onClick = {
                            bleManager.sendCommand(BleCommands.TEST_SMS)
                        }) {
                            Text("Kirim TEST_SMS")
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(onClick = {

                            val contacts = listOf(
                                EmergencyContact(name = "Ibu", phone = "+628123456789", priority = 1),
                                EmergencyContact(name = "Ayah", phone = "+628198765432", priority = 2)
                            )

                            val configJson = BleJsonParser.buildConfigJson(
                                owner = "adraw",
                                profile = "motor",
                                countdownSeconds = 30,
                                contacts = contacts
                            )

                            if (configJson != null) {
                                bleManager.sendConfig(configJson)
                            } else {
                                Log.e("BLE", "Config gagal dibuat, cek validasi input")
                            }

                        }) {
                            Text("Kirim Config Test")
                        }
                    }
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