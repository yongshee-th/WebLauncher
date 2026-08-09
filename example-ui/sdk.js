/**
 * WebLauncher SDK Helper
 * Provides a cleaner interface for interacting with the Native Engine.
 */
window.LauncherEngine = window.LauncherEngine || {};

// Native -> JS Event Bridge
window.LauncherEngine.emitEvent = (name, payload) => {
    console.log(`[Native Event] ${name}:`, payload);
    const event = new CustomEvent(`native:${name}`, { detail: payload });
    window.dispatchEvent(event);
};

const WL = {
    // Apps
    getAppsList: () => JSON.parse(window.LauncherEngine.getAppsList() || "[]"),
    launchApp: (packageName) => window.LauncherEngine.launchApp(packageName),

    // System Metrics
    getBatteryStatus: () => JSON.parse(window.LauncherEngine.getBatteryStatus() || "{}"),
    getStorageMetrics: () => JSON.parse(window.LauncherEngine.getStorageMetrics() || "{}"),

    // Hardware
    setBrightness: (level) => window.LauncherEngine.setScreenBrightness(level),
    toggleTorch: (enabled) => window.LauncherEngine.toggleTorch(enabled),
    hapticFeedback: (type) => window.LauncherEngine.triggerHapticFeedback(type),

    // Persistence
    setData: (key, value) => window.LauncherEngine.setPersistentData(key, value),
    getData: (key) => window.LauncherEngine.getPersistentData(key),

    // Navigation/Engine
    openSettings: () => window.LauncherEngine.openLauncherSettings(),
    reload: () => window.LauncherEngine.reloadEngine()
};

window.WL = WL;
