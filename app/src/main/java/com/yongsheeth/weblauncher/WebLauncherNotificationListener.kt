package com.yongsheeth.weblauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class WebLauncherNotificationListener : NotificationListenerService() {

    private val controlReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.getStringExtra("action")) {
                "cancel" -> {
                    val key = intent.getStringExtra("key")
                    key?.let { cancelNotification(it) }
                }
                "clear_all" -> cancelAllNotifications()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val filter = IntentFilter("com.yongsheeth.weblauncher.NOTIFICATION_CONTROL")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(controlReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(controlReceiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(controlReceiver)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        updateNotifications()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let { NotificationRepository.addNotification(it) }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        sbn?.let { NotificationRepository.removeNotification(it) }
    }

    private fun updateNotifications() {
        NotificationRepository.clear()
        activeNotifications?.forEach {
            NotificationRepository.addNotification(it)
        }
    }
}
