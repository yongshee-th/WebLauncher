document.addEventListener('DOMContentLoaded', () => {
    console.log('WebLauncher UI Loaded');

    // Setup event listener bridge
    window.LauncherEngine = window.LauncherEngine || {};
    window.LauncherEngine.emitEvent = (name, payload) => {
        console.log(`Native Event: ${name}`, payload);
        document.dispatchEvent(new CustomEvent(name, { detail: payload }));
    };

    updateStatus();
    loadApps();

    // Usage of real-time events
    document.addEventListener('batteryStateChanged', (e) => {
        const battery = e.detail;
        document.getElementById('battery').innerText = `Battery: ${battery.level}% ${battery.isCharging ? '(Charging)' : ''}`;
    });

    document.addEventListener('appInstalled', () => loadApps());
    document.addEventListener('appUninstalled', () => loadApps());
});

function updateStatus() {
    if (typeof LauncherEngine !== 'undefined') {
        const batteryStr = LauncherEngine.getBatteryStatus();
        const battery = JSON.parse(batteryStr);
        document.getElementById('battery').innerText = `Battery: ${battery.level}% ${battery.isCharging ? '(Charging)' : ''}`;

        const storageStr = LauncherEngine.getStorageMetrics();
        const storage = JSON.parse(storageStr);
        const freeGB = (storage.freeBytes / (1024 * 1024 * 1024)).toFixed(2);
        document.getElementById('storage').innerText = `Storage: ${freeGB} GB free`;
    }
}

function loadApps() {
    if (typeof LauncherEngine !== 'undefined') {
        try {
            const appsStr = LauncherEngine.getAppsList();
            const apps = JSON.parse(appsStr);
            const grid = document.getElementById('app-grid');
            grid.innerHTML = '';

            apps.sort((a, b) => a.name.localeCompare(b.name));

            apps.forEach(app => {
                const item = document.createElement('div');
                item.className = 'app-item';
                item.onclick = () => {
                    LauncherEngine.triggerHapticFeedback('click');
                    LauncherEngine.launchApp(app.packageName);
                };

                const icon = document.createElement('img');
                icon.className = 'app-icon';
                icon.src = app.iconUrl; // Using the new custom protocol URL

                const name = document.createElement('div');
                name.className = 'app-name';
                name.innerText = app.name;

                item.appendChild(icon);
                item.appendChild(name);
                grid.appendChild(item);
            });
        } catch (e) {
            console.error('Error loading apps:', e);
        }
    }
}
