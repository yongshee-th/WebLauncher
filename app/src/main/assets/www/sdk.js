/**
 * WebLauncher Master SDK Helper
 */
window.LauncherEngine = window.LauncherEngine || {};

// Native -> JS Event Bridge
window.LauncherEngine.emitEvent = (name, payload) => {
    const event = new CustomEvent(`native:${name}`, { detail: payload });
    window.dispatchEvent(event);
};

const WL = {
    // Apps
    getAppsList: () => JSON.parse(window.LauncherEngine.getAppsList() || "[]"),
    launchApp: (pkg) => window.LauncherEngine.launchApp(pkg),
    launchWithData: (pkg, data) => window.LauncherEngine.launchAppWithData(pkg, data),
    uninstallApp: (pkg) => window.LauncherEngine.uninstallApp(pkg),
    openAppSettings: (pkg) => window.LauncherEngine.openAppSettings(pkg),
    getUsageStats: (range) => JSON.parse(window.LauncherEngine.getAppUsageStats(range) || "[]"),
    isDefaultLauncher: () => window.LauncherEngine.getDefaultLauncher(),
    openDefaultSettings: () => window.LauncherEngine.openDefaultLauncherSettings(),

    // Notifications
    getNotifications: () => JSON.parse(window.LauncherEngine.getActiveNotifications() || "[]"),
    openDrawer: () => window.LauncherEngine.openNotificationDrawer(),

    // System Metrics
    getBattery: () => JSON.parse(window.LauncherEngine.getBatteryStatus() || "{}"),
    getStorage: () => JSON.parse(window.LauncherEngine.getStorageMetrics() || "{}"),
    getDeviceInfo: () => JSON.parse(window.LauncherEngine.getDeviceInfo() || "{}"),
    getWifi: () => JSON.parse(window.LauncherEngine.getWifiStatus() || "{}"),

    // Controls
    setBrightness: (level) => window.LauncherEngine.setScreenBrightness(level),
    setWifiEnabled: (enabled) => window.LauncherEngine.setWifiEnabled(enabled),
    getVolume: () => JSON.parse(window.LauncherEngine.getVolumeLevels() || "{}"),
    setVolume: (type, level) => window.LauncherEngine.setVolumeLevel(type, level),
    vibrate: (duration) => window.LauncherEngine.vibrate(duration),
    toggleTorch: (enabled) => window.LauncherEngine.toggleTorch(enabled),

    // Themes
    getWallpaper: () => window.LauncherEngine.getSystemWallpaper(),
    isDarkMode: () => window.LauncherEngine.isDarkModeEnabled(),

    // Storage
    setData: (key, val) => window.LauncherEngine.setPersistentData(key, val),
    getData: (key) => window.LauncherEngine.getPersistentData(key),

    // Navigation
    openSettings: () => window.LauncherEngine.openLauncherSettings(),
    openSystemSettings: (type) => window.LauncherEngine.openSystemSettings(type),
    reload: () => window.LauncherEngine.reloadEngine()
};

window.WL = WL;
