package com.predator.futures

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.webkit.JavascriptInterface

class AndroidBridge(private val context: Context) {

    interface Listener {
        fun onSignal(direction: String, score: Int, price: String, reason: String)
        fun onFlowEvent(type: String, severity: String, text: String)
        fun onConnection(ok: Boolean, detail: String)
    }

    var listener: Listener? = null

    @JavascriptInterface
    fun signal(direction: String, score: Int, price: String, reason: String) {
        Log.d("PredatorJS", "SIGNAL $direction score=$score @ $price : $reason")
        listener?.onSignal(direction, score, price, reason)
        postSignalNotification(direction, score, price, reason)
    }

    @JavascriptInterface
    fun flowEvent(type: String, severity: String, text: String) {
        Log.d("PredatorJS", "EVENT [$type/$severity] $text")
        listener?.onFlowEvent(type, severity, text)
        // Only high severity events vibrate + notify (to avoid spam)
        if (severity == "high") {
            vibrate()
            postFlowNotification(type, text)
        }
    }

    @JavascriptInterface
    fun connection(ok: Boolean, detail: String) {
        listener?.onConnection(ok, detail)
    }

    @JavascriptInterface
    fun log(msg: String) {
        Log.d("PredatorJS", msg)
    }

    private fun vibrate() {
        try {
            val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vm.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 80, 60, 80), -1))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(longArrayOf(0, 80, 60, 80), -1)
            }
        } catch (_: Exception) {}
    }

    private fun postSignalNotification(direction: String, score: Int, price: String, reason: String) {
        val title = when (direction.uppercase()) {
            "AL" -> "🟢 AL sinyali"
            "SAT" -> "🔴 SAT sinyali"
            "IZLEMEDE", "İZLEMEDE" -> "🟡 İzlemede"
            else -> "⚡ $direction"
        }
        notify(App.NOTIF_ID_SIGNAL + (0..1000).random(), title, "$price · Skor $score/100\n$reason")
    }

    private fun postFlowNotification(type: String, text: String) {
        val title = when (type.uppercase()) {
            "WHALE" -> "🐋 Whale"
            "SWEEP" -> "🌊 Sweep"
            "DELTA BURST" -> "⚡ Delta Burst"
            "ABSORPTION?" -> "🧽 Absorpsiyon"
            "SPOOF?" -> "🎭 Spoof"
            "USER ALARM", "ALARM" -> "🔔 Alarm"
            else -> "📡 $type"
        }
        notify(App.NOTIF_ID_SIGNAL + (0..1000).random(), title, text)
    }

    private fun notify(id: Int, title: String, text: String) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val pi = PendingIntent.getActivity(
            context, id, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(context, App.CHANNEL_SIGNALS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        ContextCompat.getSystemService(context, NotificationManager::class.java)?.notify(id, n)
    }
}
