# WebLauncher - Web-based Engine Launcher

A high-performance Android Launcher framework that allows developers to build their Home screen UI using modern Web technologies (HTML/JS/CSS) while retaining deep native OS integration.

## 🚀 Key Features

- **Web-to-Native Bridge**: A robust JavaScript interface (`LauncherEngine`) providing access to apps, battery status, storage metrics, and hardware controls.
- **Flexible UI Sourcing**:
    - **Bundled Assets**: Package your UI directly within the APK.
    - **Local Projects**: Load and iterate on your UI from any folder on your device using Android's **Storage Access Framework (SAF)**.
    - **Remote Updates**: Download and auto-extract ZIP-based Web UIs from GitHub or any remote URL.
- **Deep OS Integration**:
    - **Real-time Events**: Native listeners for app installs/uninstalls, battery changes, and network status emitted directly to JS.
    - **Hardware Control**: Control screen brightness, trigger haptic feedback, and toggle the flashlight from the Web UI.
- **Performance Optimized**:
    - **Custom Asset Protocol**: High-performance app icon loading via `https://appassets.androidplatform.net/app-icon/` protocol.
    - **Persistent Storage**: Native Key-Value store for Web UI settings that survive cache clears.
- **Safety First**:
    - **Hardware Fail-Safe**: Press **Volume Up + Volume Down** simultaneously to immediately return to native settings.
    - **Safety Overlay**: Optional floating button to ensure you never get locked inside a custom UI.

## 🛠️ Getting Started

1. **Build and Install**: Open the project in Android Studio and deploy to your device.
2. **Set as Default**: In your Android System Settings, set "WebLauncher" as your default Home app.
3. **Customize**: Open **Launcher Settings** (via the UI or Volume key combo) and select your preferred UI source.

## 📖 Developer Documentation

For detailed information on building your own Web UI, API signatures, and event system integration, please refer to the comprehensive [**Developer Documentation (document.md)**](document.md).

## 📄 License

This project is licensed under the MIT License - see the LICENSE file for details.
