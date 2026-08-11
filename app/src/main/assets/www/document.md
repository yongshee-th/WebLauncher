# WebLauncher Master Engine - Complete API Reference

The **Master Engine** provides deep integration with the Android system, allowing you to build a fully-featured Launcher UI. All APIs are exposed through `window.LauncherEngine` and simplified via the `WL` helper in `sdk.js`.

## 🛡️ Security & Privacy
- **Inbound-Only Policy**: Sensitive local data (Notifications, GPS, Usage) is never sent to external servers by the engine.
- **Local Processing**: All system metrics and app lists are returned directly to the Web UI for local rendering.
- **Permissions**: Some APIs require special user authorization (Usage Stats, Notification Access). The Engine will guide the user to the correct settings page.

---

## 📱 1. App Management
- `getAppsList()`: Returns `AppInfo[]` (Name, Pkg, IconURL, Category).
- `launchApp(pkg)`: Opens the specified app.
- `launchWithData(pkg, uri)`: Launches an app with a deep-link (e.g., `https://google.com`).
- `uninstallApp(pkg)`: Triggers the system uninstallation dialog.
- `openAppSettings(pkg)`: Opens the Android "App Info" page for that package.
- `isAppInstalled(pkg)`: Returns `boolean`.
- `getUsageStats(range)`: Returns screen time data for apps. Range: `day`, `week`, `month`.
- `isDefaultLauncher()`: Check if WebLauncher is the active Home app.
- `openDefaultLauncherSettings()`: Opens the "Default Apps" system page.

## 🔔 2. Notification System
- `getNotifications()`: Returns a list of all active status bar notifications.
- `cancelNotification(key)`: Dismisses a specific notification.
- `clearAllNotifications()`: Clears all dismissible notifications.
- `openNotificationDrawer()`: Native swipe-down to show the shade.
- `getUnreadNotificationCount(pkg)`: Returns the number of active notifications for an app.

## 🌐 3. Network & Connectivity
- `getWifiStatus()`: Returns SSID, Link Speed, and Connection state.
- `setWifiEnabled(bool)`: Toggles WiFi (Triggers settings panel on Android 10+).
- `getBluetoothStatus()`: Returns `boolean`.
- `setBluetoothEnabled(bool)`: Toggles Bluetooth.
- `getMobileDataStatus()`: Returns cellular network details.
- `getAirPlaneModeStatus()`: Returns `boolean`.

## 🔋 4. Hardware & Sensors
- `getBatteryStatus()`: Level, isCharging, Health, Temperature, and PluggedType (AC/USB).
- `getStorageMetrics()`: Total, Free, and Used bytes + RAM status (Total/Available).
- `getDeviceInfo()`: Model, Manufacturer, Android Version, and Screen Resolution.
- `vibrate(duration)`: Triggers a haptic pulse.
- `toggleTorch(enabled)`: Turns the camera flashlight On/Off.

## 🔊 5. Media & Display
- `getVolumeLevels()`: Current levels for `media`, `ring`, `alarm`, and `notification`.
- `setVolumeLevel(type, level)`: Sets volume for a specific stream.
- `setBrightness(level)`: Sets screen brightness (0.0 to 1.0).
- `getDisplayOrientation()`: Returns `portrait` or `landscape`.
- `isDarkModeEnabled()`: Returns `boolean`.

## 🖼️ 6. System Personalization
- `getSystemWallpaper()`: Returns the current system wallpaper as a **Base64** encoded JPEG.
- `setSystemWallpaper(base64, target)`: Changes wallpaper. Target: `home`, `lock`, or `both`.
- `setPersistentData(key, value)` / `getPersistentData(key)`: Save/Load settings for your Web UI.

---

## 📡 7. Real-time Native Events
Subscribe to these using `window.addEventListener('native:EVENT_NAME', ...)`:

| Event Name | Payload Description |
| :--- | :--- |
| `onAppListChanged` | Fired when an app is installed or removed. |
| `onBatteryStatusChanged` | Real-time level and charging updates. |
| `onNetworkStateChanged` | Fired when WiFi/Data connection toggles. |
| `onNotificationReceived` | Payload contains the full notification object. |
| `onVolumeChanged` | Fired when physical volume keys are pressed. |
| `onScreenStateChanged` | Fired when the screen turns On or Off. |
| `onHomeButtonPressed` | Fired when the user taps the Home button. |

---

## 🛠️ 8. Developer & Debugging
- `reloadEngine()`: Forces the WebView to reload the current project.
- **Remote Debugging**: Connect via USB and use Chrome PC (`chrome://inspect`) to debug.
- **Diagnostic Dashboard**: Access the native 🐞 icon in settings to view real-time Console logs and Network traffic.
- **Fail-Safe**: Press **Volume Up + Volume Down** simultaneously to bypass the Web UI and enter native Settings.
