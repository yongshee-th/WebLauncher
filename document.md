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
  - **Return Format**: `[ { "name": "Chrome", "packageName": "com.android.chrome", "iconUrl": "https://..." } ]`
- `launchApp(packageName: string)`: Launches the specified application.

### Device Control
- `setScreenBrightness(level: number)`: Sets the current window brightness (0.0 to 1.0).
- `triggerHapticFeedback(effectType: "click" | "heavy" | "double_click")`: Triggers native haptics.
- `toggleTorch(enabled: boolean)`: Toggles the device flashlight.
- `getBatteryStatus(): string`: Returns `{ "level": number, "isCharging": boolean }`.
- `getStorageMetrics(): string`: Returns `{ "totalBytes": number, "freeBytes": number }`.

### Persistence
- `setPersistentData(key: string, value: string)`: Stores data in Android DataStore.
- `getPersistentData(key: string): string`: Retrieves stored data.

### System Actions
- `openLauncherSettings()`: Opens the native Settings UI.
- `reloadEngine()`: Reloads the current WebView.

## 3. Custom Protocols & Asset Loading

### App Icons
Use the high-performance native handler to load icons. Do **not** use Base64 icons for large grids.
```html
<img src="https://appassets.androidplatform.net/app-icon/com.android.chrome" />
```

### Local Project Structure
When using the "Local Folder" source via SAF, ensure your project follows this structure:
```text
/SelectedFolder
  ├── index.html (Mandatory)
  ├── style.css
  ├── main.js
  └── assets/
```
In your HTML, use absolute-looking paths like `/local_project/assets/image.png` or relative paths like `./assets/image.png`.

## 4. Native -> JS Event System

The engine emits events for real-time system changes. Your UI must implement a global handler.

### Subscribable Events:
- `appInstalled`: `{ "packageName": string }`
- `appUninstalled`: `{ "packageName": string }`
- `batteryStateChanged`: `{ "level": number, "isCharging": boolean }`
- `networkStateChanged`: `{ "available": boolean }`

### Boilerplate JS Wrapper:
```javascript
// sdk.js
window.LauncherEngine = window.LauncherEngine || {};
window.LauncherEngine.emitEvent = (name, payload) => {
    const event = new CustomEvent(`native:${name}`, { detail: payload });
    window.dispatchEvent(event);
};

// Usage in your app
window.addEventListener('native:batteryStateChanged', (e) => {
    console.log('New battery level:', e.detail.level);
});
```

## 5. Security & Safety Policies

### Mandatory Entry Point
Every custom UI **MUST** include a button or gesture that calls `LauncherEngine.openLauncherSettings()`. 
> [!CAUTION]
> The engine scans your `index.html` and linked scripts. If this call is missing, the app will display a warning and may block the project in future versions.

### Hardware Fail-Safe
If you get locked out of your UI, press **Volume Up + Volume Down simultaneously** to force-open settings.

## 6. Debugging Your Web UI

You can debug your Launcher UI just like a regular website:
1. Enable **Developer Options** and **USB Debugging** on your Android device.
2. Connect your device to your computer.
3. Open Chrome and go to `chrome://inspect/#devices`.
4. Find **WebLauncher** in the list and click **inspect**.

## 7. TypeScript Definitions (`launcher-engine.d.ts`)

```typescript
interface AppInfo {
    name: string;
    packageName: string;
    iconUrl: string;
}

interface LauncherEngine {
    getAppsList(): string; // Returns JSON string of AppInfo[]
    launchApp(packageName: string): void;
    setScreenBrightness(level: number): void;
    triggerHapticFeedback(effectType: "click" | "heavy" | "double_click"): void;
    toggleTorch(enabled: boolean): void;
    getBatteryStatus(): string; // Returns JSON string
    getStorageMetrics(): string; // Returns JSON string
    setPersistentData(key: string, value: string): void;
    getPersistentData(key: string): string;
    openLauncherSettings(): void;
    reloadEngine(): void;
    emitEvent?(name: string, payload: any): void; // Native use only
}

declare interface Window {
    LauncherEngine: LauncherEngine;
}
```
