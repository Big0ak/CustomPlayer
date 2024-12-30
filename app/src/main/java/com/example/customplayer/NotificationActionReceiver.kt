package com.example.customplayer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when(intent.action) {
            MusicService.ACTION_TOGGLE_PLAYBACK,
            MusicService.ACTION_NEXT -> {
                val serviceIntent = Intent(context, MusicService::class.java).apply {
                    action = intent.action
                }
                ContextCompat.startForegroundService(context, serviceIntent)
            }
        }
    }
}
