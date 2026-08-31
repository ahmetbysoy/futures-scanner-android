package com.predator.futures

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val signals = NotificationChannel(
                CHANNEL_SIGNALS,
                "Sinyaller & Alarmlar",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Whale, sweep, absorption, AL/SAT sinyalleri"
                enableLights(true)
                enableVibration(true)
            }
            val connection = NotificationChannel(
                CHANNEL_CONNECTION,
                "Bağlantı",
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "WS bağlantı durumu" }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannels(listOf(signals, connection))
        }
    }

    companion object {
        const val CHANNEL_SIGNALS = "signals"
        const val CHANNEL_CONNECTION = "connection"
        const val NOTIF_ID_SIGNAL = 1001
    }
}
