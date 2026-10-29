package com.example.drivesafe.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore

// Harus dideklarasikan SEKALI saja di seluruh project (dua kali = crash)
val Context.driveSafeDataStore: DataStore<Preferences> by preferencesDataStore(name = "drivesafe_prefs")