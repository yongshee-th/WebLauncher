# WebLauncher Master Engine — Complete API Reference

The **Master Engine** exposes the Android system to your Web UI. Every call is a
`@JavascriptInterface` method on `window.LauncherEngine`, wrapped in the friendlier
`WL` helper in `sdk.js`. This document describes the `WL` surface; where the
underlying engine method has a different name it is listed in the third column.

Structured data always crosses the bridge as a **JSON string** — the `WL`
wrappers parse it for you.

> **Requirement:** a custom Web UI **must** keep launcher settings reachable.
> `ProjectValidator` scans your `index.html` and any linked `.js` for the literal
> string `LauncherEngine.openLauncherSettings()` before loading a project. Keeping
> `sdk.js` satisfies this. The hardware fail-safe (**Volume Up + Volume Down**)
> works regardless.

---

## 🛡️ Security & Privacy

- **Inbound-only.** The engine never sends notifications, usage, location or app
  data to any server. Everything is handed to the Web UI for local rendering.
- **Special permissions.** Usage stats, notifications and media sessions all
  require access the user grants manually in system settings. Every affected API
  degrades to an empty value (`{}` / `[]` / `0`) rather than throwing.
- **Treat app-supplied text as hostile.** App labels, package names and media
  metadata are chosen by third-party apps, and your page has the entire bridge
  bound to it. Render them with `textContent`, never `innerHTML`.

---

## 📱 1. App Management

| `WL` method | Returns | Engine method |
| :--- | :--- | :--- |
| `getAppsList()` | `AppInfo[]` | — |
| `launchApp(pkg)` | — | — |
| `launchWithData(pkg, uri)` | — | `launchAppWithData` |
| `uninstallApp(pkg)` | — | — |
| `openAppSettings(pkg)` | — | — |
| `isAppInstalled(pkg)` | `boolean` | — |
| `getUsageStats(range)` | `AppUsage[]` | `getAppUsageStats` |
| `isDefaultLauncher()` | `boolean` | `getDefaultLauncher` |
| `openDefaultSettings()` | — | `openDefaultLauncherSettings` |

**`AppInfo`** — `{ name, packageName, iconUrl, category, isSystemApp }`

- `category` is derived from `ApplicationInfo.category` and is one of
  `GAME`, `MEDIA`, `SOCIAL`, `SYSTEM`, `Unknown`. It reflects what the app
  declares in its own manifest, so **many apps report `Unknown`**.
- `isSystemApp` is `FLAG_SYSTEM`. Android refuses to uninstall these, so hide or
  disable any uninstall affordance for them.
- `uninstallApp` fires `ACTION_DELETE`: **Android shows its own confirmation
  dialog**, so this can never remove an app silently.
- `getUsageStats(range)` takes `"day"`, `"week"` or `"month"`, and needs the
  Usage Access permission.

---

## 🔔 2. Notifications

| `WL` method | Returns | Engine method |
| :--- | :--- | :--- |
| `getNotifications()` | `WebNotification[]` | `getActiveNotifications` |
| `cancelNotification(key)` | — | — |
| `clearAllNotifications()` | — | — |
| `openDrawer()` | — | `openNotificationDrawer` |
| `getUnreadCount(pkg)` | `number` | `getUnreadNotificationCount` |

**`WebNotification`** — `{ key, packageName, title, text, timestamp }`
(`timestamp` is epoch milliseconds.)

Requires **Notification Access**. Dismissal is routed through the engine's
`NotificationListenerService`, since only that service may cancel notifications.

---

## 🎵 3. Media & Playback

Namespaced under **`WL.media`**. Reads the currently active `MediaSession`, so it
controls whatever app is actually playing. Requires **Notification Access** —
without it every call returns empty rather than failing.

| `WL.media` method | Returns | Engine method |
| :--- | :--- | :--- |
| `getPlaybackState()` | `PlaybackState` | — |
| `getQueue()` | `QueueItem[]` | `getMediaQueue` |
| `playPause()` | — | `mediaPlayPause` |
| `next()` / `previous()` | — | `mediaNext` / `mediaPrevious` |
| `seekTo(ms)` | — | `mediaSeekTo` |
| `playQueueItem(id)` | — | — |
| `getVolume()` | `VolumeLevels` | `getVolumeLevels` |
| `setVolume(type, level)` | — | `setVolumeLevel` |

**`PlaybackState`** — `{ title, artist, albumArt, isPlaying, duration, position, packageName }`

**`QueueItem`** — `{ id, title, artist, duration }`

Three things to get right:

1. **`duration` and `position` are in milliseconds.**
2. **`QueueItem.duration` is always `0`.** `MediaSession.QueueItem` carries no
   duration — omit the column rather than rendering `0:00`.
3. **No active session returns `{}`.** Check `packageName` before trusting the
   rest, and treat the empty case and the missing-permission case identically.

`albumArt` is a URL served by the art path handler (see §9), *not* base64. It is
keyed by package, not by track, so the WebView will happily serve a stale bitmap
after a track change — append a cache-busting query (`?t=<now>`) when the track
changes.

Position only advances on the device; the engine emits an event on state and
metadata changes, not continuously. Tick position locally and re-anchor to
`getPlaybackState()` occasionally rather than polling on a fast timer.

---

## 🌐 4. Network & Connectivity

| `WL` method | Returns | Engine method |
| :--- | :--- | :--- |
| `getWifi()` | `NetworkStatus` | `getWifiStatus` |
| `setWifiEnabled(bool)` | — | — |
| `getMobileData()` | `NetworkStatus` | `getMobileDataStatus` |
| `getBluetooth()` | `boolean` | `getBluetoothStatus` |
| `setBluetoothEnabled(bool)` | — | — |
| `getAirplaneMode()` | `boolean` | `getAirPlaneModeStatus` |

**`NetworkStatus`** — `{ type, ssid, signalStrength, isConnected }`
(`signalStrength` is RSSI in dBm: roughly −50 excellent, −100 unusable.)

On Android 10+ the OS forbids toggling radios directly, so `setWifiEnabled` and
`setBluetoothEnabled` **open the relevant settings panel** instead. The method
signature is unchanged; the behaviour is not. Reading the SSID also requires
location permission on Android 10+.

---

## 🔋 5. Hardware & Sensors

| `WL` method | Returns | Engine method |
| :--- | :--- | :--- |
| `getBattery()` | `BatteryStatus` | `getBatteryStatus` |
| `getStorage()` | `StorageMetrics` | `getStorageMetrics` |
| `getDeviceInfo()` | `DeviceInfo` | — |
| `vibrate(ms)` | — | — |
| `toggleTorch(bool)` | — | — |

- **`BatteryStatus`** — `{ level, isCharging, health, temperature, pluggedType }`
- **`StorageMetrics`** — `{ totalBytes, freeBytes, usedBytes, totalRAM, availRAM }` (bytes)
- **`DeviceInfo`** — `{ model, manufacturer, androidVersion, sdkVersion, screenWidth, screenHeight }`

`triggerHapticFeedback(type)` exists on the engine but is **not** wrapped in
`WL`; call `LauncherEngine.triggerHapticFeedback()` directly. Accepts `"click"`,
`"heavy"`, `"double_click"`.

---

## 🔊 6. Display & Audio

| `WL` method | Returns | Engine method |
| :--- | :--- | :--- |
| `setBrightness(level)` | — | `setScreenBrightness` |
| `getOrientation()` | `"portrait"` / `"landscape"` | `getDisplayOrientation` |
| `isDarkMode()` | `boolean` | `isDarkModeEnabled` |

`setBrightness` takes `0.0`–`1.0`. The engine has both a `Float` and a `String`
overload, and the `WL` wrapper passes a string — this matters because the JS
bridge coerces a mismatched type to `0`, which silently blanks the screen.
Volume lives under `WL.media` (§3).

---

## 🖼️ 7. Personalization & Storage

| `WL` method | Returns | Engine method |
| :--- | :--- | :--- |
| `getWallpaper()` | base64 JPEG | `getSystemWallpaper` |
| `setWallpaper(base64, target)` | — | `setSystemWallpaper` |
| `setData(key, value)` | — | `setPersistentData` |
| `getData(key)` | `string` | `getPersistentData` |

`target` is `"home"`, `"lock"` or `"both"`. The key-value store is backed by
DataStore and **survives cache clears and app updates** — it is the right place
for user preferences. `getData` returns `""` for a missing key. Values are
strings; `JSON.stringify` anything structured.

---

## 🧭 8. Navigation

| `WL` method | Engine method |
| :--- | :--- |
| `openSettings()` | `openLauncherSettings` |
| `openSystemSettings(type)` | — |
| `reload()` | `reloadEngine` |

`type` accepts `wifi`, `bluetooth`, `display`, `sound`, `battery`, `location`,
`accessibility`, `date`. Anything else opens the top-level settings screen.

---

## 📡 9. Real-time Events

Subscribe with `window.addEventListener('native:EVENT_NAME', handler)`. The
payload is on `event.detail`, already parsed.

| Event | Payload |
| :--- | :--- |
| `onAppListChanged` | `{ action, packageName }` — `action` is `added` / `removed` |
| `onBatteryStatusChanged` | `{ level, isCharging }` |
| `onNetworkStateChanged` | `{ isOnline, connectionType }` |
| `onNotificationReceived` | Full `WebNotification` |
| `onPlaybackStateChanged` | Full `PlaybackState` |
| `onVolumeChanged` | `{ streamType, action }` — physical volume keys |
| `onScreenStateChanged` | `{ isScreenOn }` |
| `onHomeButtonPressed` | `{}` |

`onScreenStateChanged` is worth handling: pausing animations and timers while the
screen is off is the single biggest battery win available to a Web UI.

---

## 🚀 10. Asset Protocol

Everything is served from the virtual origin
`https://appassets.androidplatform.net`:

| Path | Serves |
| :--- | :--- |
| `/assets/` | Files bundled in the APK |
| `/local_project/` | Your project folder (SAF) or downloaded GitHub UI |
| `/app-icon/<pkg>` | An installed app's icon, rendered to PNG on demand |
| `/album-art/<pkg>` | Album art from that package's media session, as JPEG |

Both image handlers let you use a plain `<img src>` instead of shipping base64
through the bridge. A missing app icon returns a transparent 1×1 pixel rather
than an error; missing album art returns nothing, so handle the `error` event.

---

## 🛠️ 11. Developer & Debugging

- **UI sources** — bundled assets, a local folder via Storage Access Framework,
  or a GitHub zip that the engine downloads and extracts.
- **Diagnostics** — the 🐞 icon in launcher settings shows live console output,
  network requests, device info and the contents of the key-value store.
- **Remote debugging** — enable it in Diagnostics → Storage, then connect via USB
  and open `chrome://inspect`.
- **`reload()`** — reloads the WebView without restarting the launcher.
- **Fail-safe** — **Volume Up + Volume Down** together opens native settings from
  anywhere, even if your UI fails to render.

---

## 🔐 12. Permissions

Granted at install: `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`,
`CHANGE_WIFI_STATE`, `VIBRATE`, `MODIFY_AUDIO_SETTINGS`, `SET_WALLPAPER`,
`EXPAND_STATUS_BAR`, `QUERY_ALL_PACKAGES`, `BLUETOOTH*`.

Requested at runtime: `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` —
required for the Wi-Fi SSID on Android 10+.

Granted manually by the user in system settings:

| Access | Unlocks |
| :--- | :--- |
| Notification Access | §2 notifications, **and all of §3 media** |
| Usage Access | `getUsageStats()` |

Media and notifications share one permission — granting Notification Access
enables both.
