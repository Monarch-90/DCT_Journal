// static/js/devices.js

document.addEventListener('DOMContentLoaded', () => {
    // --- НАСТРОЙКИ ---
    const SERVER_URL = "http://10.42.0.1:5000";

    // --- DOM Элементы ---
    const statusIndicator = document.getElementById('status-indicator');
    const statusText = document.getElementById('status-text');
    const deviceGrid = document.getElementById('device-grid');
    const sidebar = document.getElementById('sidebar');
    const requestStatusAllBtn = document.getElementById('request-status-all-btn');
    const searchBox = document.getElementById('search-box');

    // --- НАЧАЛО ИЗМЕНЕНИЯ: Новая функция для обновления счетчика ---
    function updateDeviceCountSubtitle() {
        const allCards = document.querySelectorAll('.device-card');
        const totalDevices = allCards.length;
        let onlineDevices = 0;

        allCards.forEach(card => {
            if (card.dataset.status === 'online') {
                onlineDevices++;
            }
        });

        const subtitleElement = document.getElementById('page-subtitle');
        if (subtitleElement) {
            subtitleElement.textContent = `Онлайн: ${onlineDevices} из ${totalDevices}`;
        }
    }
    // --- КОНЕЦ ИЗМЕНЕНИЯ ---

    function updateStatus(state, text) {
        statusIndicator.className = 'status-indicator';
        statusIndicator.classList.add(state);
        statusText.textContent = text;
    }

    function createDeviceCard(device) {
        const na = '<span style="color: #999;">n/a</span>';
        const orderNumber = device.order_number || '???';
        const workStatus = device.work_status || 'свободен';
        const battery = device.battery_level;
        let batteryDisplay = na;
        if (battery !== null) {
            let icon = '🔋';
            if (battery < 20) icon = '🔌';
            batteryDisplay = `<span class="battery-icon">${icon}</span> ${battery}%`;
        }

        const card = document.createElement('div');
        card.className = 'device-card';
        card.id = `device-${device.android_id}`;
        card.dataset.status = device.status;
        card.dataset.searchText = `${orderNumber} ${device.android_id}`.toLowerCase();

        card.innerHTML = `
            <div class="card-header">
                <div class="device-name">
                    №${orderNumber}
                    <small>${device.android_id}</small>
                </div>
                <div class="status-indicator-card">${device.status}</div>
            </div>
            <div class="card-body">
                <div class="info-item">
                    <span class="info-label">Статус работы</span>
                    <span class="info-value">
                        <select class="work-status-select">
                            <option value="свободен" ${workStatus === 'свободен' ? 'selected' : ''}>Свободен</option>
                            <option value="в работе" ${workStatus === 'в работе' ? 'selected' : ''}>В работе</option>
                            <option value="повреждён" ${workStatus === 'повреждён' ? 'selected' : ''}>Повреждён</option>
                        </select>
                    </span>
                </div>
                 <div class="info-item">
                    <span class="info-label">Батарея</span>
                    <span class="info-value battery-level">${batteryDisplay}</span>
                </div>
                <div class="info-item">
                    <span class="info-label">Версия Android</span>
                    <span class="info-value android-version">${device.android_version || na}</span>
                </div>
                <div class="info-item">
                    <span class="info-label">Стоимость ремонта (BYN)</span>
                    <span class="info-value">
                        <input type="number" class="repair-cost-input" value="${device.total_repair_cost || 0}" step="0.01">
                    </span>
                </div>
                <div class="info-item">
                    <span class="info-label">Версия Журнала</span>
                    <span class="info-value app-version">${device.app_version || na}</span>
                </div>
                <div class="info-item">
                    <span class="info-label">Версия WMS</span>
                    <span class="info-value wms-version">${device.wms_version || na}</span>
                </div>
            </div>
            <div class="card-footer">
                <button class="card-button update-btn">Обновить ПО</button>
                <button class="card-button save-btn">Сохранить</button>
            </div>
        `;

        card.querySelector('.update-btn').addEventListener('click', () => handleUpdateApp(device.android_id));
        card.querySelector('.save-btn').addEventListener('click', () => handleSaveChanges(device.android_id));
        
        return card;
    }
    
    async function fetchAndRenderDevices() {
        try {
            const response = await fetch(`${SERVER_URL}/api/v1/devices`);
            if (!response.ok) throw new Error('Ошибка сети при загрузке устройств');
            const devices = await response.json();
            deviceGrid.innerHTML = '';
            devices.forEach(device => {
                deviceGrid.appendChild(createDeviceCard(device));
            });
            updateDeviceCountSubtitle(); // 👈 Вызов после начальной загрузки
        } catch (error) {
            console.error(error);
            deviceGrid.innerHTML = `<p style="color: red;">${error.message}</p>`;
        }
    }

    function handleSearch() {
        const searchTerm = searchBox.value.toLowerCase().replace('№', '').trim();
        const allCards = document.querySelectorAll('.device-card');

        allCards.forEach(card => {
            const searchText = card.dataset.searchText || '';
            if (searchText.includes(searchTerm)) {
                card.style.display = '';
            } else {
                card.style.display = 'none';
            }
        });
    }

    function handleRequestStatusAll() {
        requestStatusAllBtn.disabled = true;
        requestStatusAllBtn.textContent = 'Отправка...';
        fetch(`${SERVER_URL}/api/v1/devices/command/request_status_all`, { method: 'POST' })
        .then(res => { if (!res.ok) { console.error('Ошибка при отправке команды запроса статуса'); }})
        .catch(err => { console.error("Ошибка сети при отправке команды.", err); })
        .finally(() => {
            setTimeout(() => {
                requestStatusAllBtn.disabled = false;
                requestStatusAllBtn.textContent = 'Обновить статус';
            }, 2000);
        });
    }

    function handleUpdateApp(androidId) {
        const apkFilename = prompt("Введите имя APK файла (например, app-v2.apk):", "");
        if (!apkFilename || apkFilename.trim() === '') { return; }
        if (!apkFilename.toLowerCase().endsWith('.apk')) { alert("Ошибка: Имя файла должно заканчиваться на .apk"); return; }
        const localApkUrl = `${SERVER_URL}/updates/${apkFilename.trim()}`;
        fetch(`${SERVER_URL}/api/v1/devices/${androidId}/command/update`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ apkUrl: localApkUrl })
        })
        .then(res => res.json())
        .then(data => { alert(data.message || (data.success ? "Команда отправлена" : "Неизвестная ошибка")); })
        .catch(err => { alert("Ошибка сети при отправке команды."); console.error(err); });
    }

    function handleSaveChanges(androidId) {
        const card = document.getElementById(`device-${androidId}`);
        const work_status = card.querySelector('.work-status-select').value;
        const total_repair_cost = card.querySelector('.repair-cost-input').value;
        fetch(`${SERVER_URL}/api/v1/devices/${androidId}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ work_status, total_repair_cost })
        })
        .then(res => res.json())
        .then(data => {
            if (data.success) { alert('Данные сохранены!'); } 
            else { alert(`Ошибка: ${data.error || 'Неизвестная ошибка'}`); }
        })
        .catch(err => { alert("Ошибка сети при сохранении данных."); console.error(err); });
    }

    const socket = io(SERVER_URL, { transports: ['websocket'], rejectUnauthorized: false });
    socket.on('connect', () => updateStatus('connected', 'Подключен'));
    socket.on('disconnect', () => updateStatus('disconnected', 'Нет подключения'));
    socket.on('connect_error', (err) => { updateStatus('disconnected', 'Ошибка'); console.error('Ошибка подключения:', err); });
    
    socket.on('device_status_changed', (data) => {
        const card = document.getElementById(`device-${data.androidId}`);
        if (card) { 
            card.dataset.status = data.status; 
            card.querySelector('.status-indicator-card').textContent = data.status;
            updateDeviceCountSubtitle(); // 👈 Вызов при изменении статуса
        }
    });

    socket.on('device_data_updated', (data) => {
        const card = document.getElementById(`device-${data.deviceId}`);
        if (card) {
             if (data.batteryLevel !== undefined) { card.querySelector('.battery-level').innerHTML = `<span class="battery-icon">🔋</span> ${data.batteryLevel}%`; }
             if (data.androidVersion) card.querySelector('.android-version').textContent = data.androidVersion;
             if (data.appVersion) card.querySelector('.app-version').textContent = data.appVersion;
             if (data.wmsVersion) card.querySelector('.wms-version').textContent = data.wmsVersion;
             if (data.work_status) card.querySelector('.work-status-select').value = data.work_status;
             if (data.total_repair_cost) card.querySelector('.repair-cost-input').value = data.total_repair_cost;
        }
    });

    async function init() {
        updateStatus('Подключение...');
        if (typeof setupSidebar === 'function') { await setupSidebar('devices'); }
        
        await fetchAndRenderDevices();
        requestStatusAllBtn.addEventListener('click', handleRequestStatusAll);
        
        if (searchBox) {
            searchBox.addEventListener('input', handleSearch);
        } else {
            console.error("Поле поиска с ID 'search-box' не найдено!");
        }
    }
    init();
});
