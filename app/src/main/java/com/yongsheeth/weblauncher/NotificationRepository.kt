package com.yongsheeth.weblauncher

import android.service.notification.StatusBarNotification
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class WebNotification(
    val key: String,
    val packageName: String,
    val title: String,
    val text: String,
    val timestamp: Long
)

object NotificationRepository {
    private val _activeNotifications = MutableStateFlow<Map<String, WebNotification>>(emptyMap())
    val activeNotifications = _activeNotifications.asStateFlow()

    private var eventEmitter: ((String, String) -> Unit)? = null

    fun setEventEmitter(emitter: (String, String) -> Unit) {
        eventEmitter = emitter
    }

    fun addNotification(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        
        val webNav = WebNotification(
            key = sbn.key,
            packageName = sbn.packageName,
            title = title,
            text = text,
            timestamp = sbn.postTime
        )

        val current = _activeNotifications.value.toMutableMap()
        current[sbn.key] = webNav
        _activeNotifications.value = current

        eventEmitter?.invoke("onNotificationReceived", Json.encodeToString(webNav))
    }

    fun removeNotification(sbn: StatusBarNotification) {
        val current = _activeNotifications.value.toMutableMap()
        current.remove(sbn.key)
        _activeNotifications.value = current
    }

    fun clear() {
        _activeNotifications.value = emptyMap()
    }
}
