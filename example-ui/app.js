document.addEventListener('DOMContentLoaded', () => {
    // Initial Load
    updateSystemInfo();
    loadAppList();

    // Event Listeners
    window.addEventListener('native:batteryStateChanged', (e) => {
        logEvent('batteryStateChanged', e.detail);
        const data = e.detail;
        document.getElementById('battery-val').innerText = `${data.level}% ${data.isCharging ? '⚡' : ''}`;
    });

    window.addEventListener('native:networkStateChanged', (e) => {
        logEvent('networkStateChanged', e.detail);
    });

    window.addEventListener('native:appInstalled', (e) => {
        logEvent('appInstalled', e.detail);
        loadAppList();
    });

    window.addEventListener('native:appUninstalled', (e) => {
        logEvent('appUninstalled', e.detail);
        loadAppList();
    });
});

function updateSystemInfo() {
    const battery = WL.getBatteryStatus();
    document.getElementById('battery-val').innerText = `${battery.level}% ${battery.isCharging ? '⚡' : ''}`;

    const storage = WL.getStorageMetrics();
    const freeGB = (storage.freeBytes / (1024 * 1024 * 1024)).toFixed(2);
    const totalGB = (storage.totalBytes / (1024 * 1024 * 1024)).toFixed(2);
    document.getElementById('storage-val').innerText = `${freeGB} / ${totalGB} GB Free`;
}

function loadAppList() {
    const apps = WL.getAppsList();
    const grid = document.getElementById('app-grid');
    grid.innerHTML = '';

    apps.sort((a, b) => a.name.localeCompare(b.name));

    apps.forEach(app => {
        const item = document.createElement('div');
        item.className = 'app-item';
        item.onclick = () => {
            WL.hapticFeedback('click');
            WL.launchApp(app.packageName);
        };

        const icon = document.createElement('img');
        icon.className = 'app-icon';
        icon.src = app.iconUrl;

        const name = document.createElement('div');
        name.className = 'app-name';
        name.innerText = app.name;

        item.appendChild(icon);
        item.appendChild(name);
        grid.appendChild(item);
    });
}

function saveData() {
    const key = document.getElementById('kv-key').value;
    const val = document.getElementById('kv-value').value;
    if (key) {
        WL.setData(key, val);
        document.getElementById('kv-result').innerText = `Saved ${key}`;
    }
}

function loadData() {
    const key = document.getElementById('kv-key').value;
    if (key) {
        const val = WL.getData(key);
        document.getElementById('kv-result').innerText = `Value: ${val}`;
    }
}

function logEvent(name, payload) {
    const log = document.getElementById('event-log');
    const entry = document.createElement('div');
    entry.className = 'log-entry';
    const ts = new Date().toLocaleTimeString();
    entry.innerHTML = `<span class="timestamp">[${ts}]</span> <b>${name}:</b> ${JSON.stringify(payload)}`;
    log.prepend(entry);
}

function clearLogs() {
    document.getElementById('event-log').innerHTML = '';
}
