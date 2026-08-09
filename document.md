# Web-based Engine Launcher Framework - Developer Documentation

Welcome to the **Web-based Engine Launcher** framework. This documentation provides everything you need to build custom, high-performance Web UIs that integrate deeply with the Android operating system.

## 1. Architecture Overview

The launcher consists of a **Native Kotlin Engine** and a **Web UI**. They communicate via:
- **JavaScript Bridge**: JS calls native methods via `window.LauncherEngine`.
- **Event System**: Native engine emits real-time events to JS via `window.LauncherEngine.emitEvent`.
- **Secure Asset Loading**: All files (including local SAF folders) are served via `https://appassets.androidplatform.net/` using `WebViewAssetLoader`.

## 2. JavaScript API Reference (`LauncherEngine`)

### App Management
- `getAppsList(): string`: Returns a JSON array of installed launcher apps.
  - **Return Format**: `[ { "name": string, "packageName": string, "iconUrl": string } ]`
  - **Note**: `iconUrl` uses the custom `app-icon://` protocol (see below).
- `launchApp(packageName: string)`: Launches the specified application.

### Device Control
- `setScreenBrightness(level: number)`: Sets the current window brightness (0.0 to 1.0).
- `triggerHapticFeedback(effectType: string)`: Triggers native haptics.
  - **Types**: `"click"`, `"heavy"`, `"double_click"`.
- `toggleTorch(enabled: boolean)`: Toggles the device flashlight.
- `getBatteryStatus(): string`: Returns `{ "level": number, "isCharging": boolean }`.
- `getStorageMetrics(): string`: Returns `{ "totalBytes": number, "freeBytes": number }`.

### Persistence
- `setPersistentData(key: string, value: string)`: Stores data in Android DataStore.
- `getPersistentData(key: string): string`: Retrieves stored data.

### System Actions
- `openLauncherSettings()`: Opens the native Settings UI.
- `reloadEngine()`: Reloads the current WebView.

## 3. Custom Protocols

### `app-icon://<packageName>`
To load high-quality app icons without heavy Base64 strings, use the `app-icon` path:
```html
<img src="https://appassets.androidplatform.net/app-icon/com.android.chrome" />
```

## 4. Native -> JS Event System

Your Web UI should implement a listener for native events.
**Event Names**:
- `appInstalled`: `{ "packageName": string }`
- `appUninstalled`: `{ "packageName": string }`
- `batteryStateChanged`: `{ "level": number, "isCharging": boolean }`
- `networkStateChanged`: `{ "available": boolean }`

**Example Implementation**:
```javascript
window.LauncherEngine = window.LauncherEngine || {};
window.LauncherEngine.emitEvent = (name, payload) => {
    console.log(`Received native event: ${name}`, payload);
    document.dispatchEvent(new CustomEvent(name, { detail: payload }));
};

// Usage
document.addEventListener('batteryStateChanged', (e) => {
    updateBatteryUI(e.detail.level);
});
```

## 5. Safety Policy & Fail-Safe

- **Mandatory Entry Point**: Every custom UI **MUST** include a button or gesture that calls `LauncherEngine.openLauncherSettings()`. The engine scans your files and will warn if this is missing.
- **Hardware Fail-Safe**: If you get locked out of your UI, press **Volume Up + Volume Down** simultaneously to force-open the native settings.

## 6. TypeScript Definitions & Helper

Save this as `launcher-engine.d.ts` in your project:

```typescript
interface AppInfo {
    name: string;
    packageName: string;
    iconUrl: string;
}

interface LauncherEngine {
    getAppsList(): string;
    launchApp(packageName: string): void;
    setScreenBrightness(level: number): void;
    triggerHapticFeedback(effectType: "click" | "heavy" | "double_click"): void;
    toggleTorch(enabled: boolean): void;
    setPersistentData(key: string, value: string): void;
    getPersistentData(key: string): string;
    openLauncherSettings(): void;
    reloadEngine(): void;
    
    // Event dispatcher provided by Native
    emitEvent?(name: string, payload: any): void;
}

declare interface Window {
    LauncherEngine: LauncherEngine;
}
```
