package com.example.drivesafe.model

enum class ConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,   // GATT tersambung, tapi belum siap dipakai
    READY,       // service ketemu + notifikasi STATUS/EVENT aktif
    ERROR
}