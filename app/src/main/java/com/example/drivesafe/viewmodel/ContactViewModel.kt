package com.example.drivesafe.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.drivesafe.DriveSafeApp
import com.example.drivesafe.model.DeviceConfig
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * CRUD kontak + pengaturan. Mengirim ke alat dilakukan lewat DeviceViewModel.syncNow().
 * Fungsi yang bisa gagal validasi memanggil onResult(pesanError) atau onResult(null) kalau sukses.
 */
class ContactViewModel(app: Application) : AndroidViewModel(app) {

    private val configRepo = (app as DriveSafeApp).configRepository

    val config: StateFlow<DeviceConfig> =
        configRepo.config.stateIn(viewModelScope, SharingStarted.Eagerly, DeviceConfig.DEFAULT)

    val needsSync: StateFlow<Boolean> =
        configRepo.needsSync.stateIn(viewModelScope, SharingStarted.Eagerly, true)

    fun addContact(name: String, phone: String, onResult: (String?) -> Unit = {}) {
        viewModelScope.launch { onResult(configRepo.addContact(name, phone)) }
    }

    fun updateContact(id: String, name: String, phone: String, onResult: (String?) -> Unit = {}) {
        viewModelScope.launch { onResult(configRepo.updateContact(id, name, phone)) }
    }

    fun deleteContact(id: String) {
        viewModelScope.launch { configRepo.deleteContact(id) }
    }

    fun setPrimary(id: String) {
        viewModelScope.launch { configRepo.setPrimary(id) }
    }

    fun saveSettings(
        owner: String,
        profile: String,
        countdownSeconds: Int,
        onResult: (String?) -> Unit = {}
    ) {
        viewModelScope.launch { onResult(configRepo.saveSettings(owner, profile, countdownSeconds)) }
    }
}