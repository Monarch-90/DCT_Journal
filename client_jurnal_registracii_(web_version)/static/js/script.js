// static/js/script.js

document.addEventListener('DOMContentLoaded', () => {
    // --- DOM Элементы ---
    const statusIndicator = document.getElementById('status-indicator');
    const statusText = document.getElementById('status-text');
    const logBody = document.getElementById('log-body');
    const mainContent = document.getElementById('main-content');
    const searchBox = document.getElementById('search-box');
    const pageSubtitle = document.getElementById('page-subtitle');
    const tableHeaderSticky = document.querySelector('.table-header-sticky');

    let currentView = { type: 'latest' };
    const ADMIN_DISPLAY_NAME = "Администратор";

    function updateStatus(state, text) { statusIndicator.className = 'status-indicator'; statusIndicator.classList.add(state); statusText.textContent = text; }
    function adjustHeaderForScrollbar() { if (!mainContent || !tableHeaderSticky) return; const scrollbarWidth = mainContent.offsetWidth - mainContent.clientWidth; tableHeaderSticky.style.paddingRight = `${scrollbarWidth + 20}px`; }
    function scrollToBottom() { if (currentView.type === 'latest') { setTimeout(() => mainContent.scrollTop = mainContent.scrollHeight, 100); } }
    function formatTimestamp(isoString) { if (!isoString) return ''; const date = new Date(isoString); const day = String(date.getDate()).padStart(2, '0'); const month = String(date.getMonth() + 1).padStart(2, '0'); const year = date.getFullYear(); const hours = String(date.getHours()).padStart(2, '0'); const minutes = String(date.getMinutes()).padStart(2, '0'); return `${day}.${month}.${year} ${hours}:${minutes}`; }
    function formatUser(session) { if (session.is_admin) return ADMIN_DISPLAY_NAME; return session.user_fullName || session.user_login; }
    function formatDuration(ms) { if (ms <= 0) return "00:00"; const totalSeconds = Math.floor(ms / 1000); const hours = Math.floor(totalSeconds / 3600); const minutes = Math.floor((totalSeconds % 3600) / 60); return `${String(hours).padStart(2, '0')}:${String(minutes).padStart(2, '0')}`; }
    
    function calculateActivity(events) {
        let totalMilliseconds = 0;
        const regs = events.filter(e => e.type === 'registration').sort((a,b) => new Date(a.timestamp) - new Date(b.timestamp));
        const deregs = events.filter(e => e.type === 'deregistration').sort((a,b) => new Date(a.timestamp) - new Date(b.timestamp));
        const regsCopy = [...regs];
        deregs.forEach(deregEvent => {
            let matchingRegIndex = -1;
            for (let i = regsCopy.length - 1; i >= 0; i--) { if (regsCopy[i] && new Date(regsCopy[i].timestamp) < new Date(deregEvent.timestamp)) { matchingRegIndex = i; break; } }
            if (matchingRegIndex !== -1) { totalMilliseconds += (new Date(deregEvent.timestamp) - new Date(regsCopy[matchingRegIndex].timestamp)); regsCopy.splice(matchingRegIndex, 1); }
        });
        return formatDuration(totalMilliseconds);
    }
    
    function createSystemEventRow(systemEvent) {
        const eventTime = new Date(systemEvent.timestamp_utc);
        const message = systemEvent.data.message || 'Системное событие';
        const content = `<span class="system-event-time">${formatTimestamp(eventTime)}</span> <span class="system-event-prefix"> Инфо: </span> <span class="system-event-message">${message}</span>`;
        return `<tr class="system-event-row"><td colspan="4">${content}</td></tr>`;
    }

    function createSessionBlockHTML(session, buttonMap) {
        let finalHTML = '';
        const totalActivity = calculateActivity(session.events);
        const headerSearchableText = `${formatUser(session)} ${session.device_androidId}`.toLowerCase();
        const sessionHeaderContent = `<div class="session-header-content"><span><strong>${formatUser(session)}</strong></span><span>Активность за смену: ${totalActivity}</span></div>`;
        finalHTML += `<tr class="session-header-row" data-searchable-text="${headerSearchableText}"><td colspan="4">${sessionHeaderContent}</td></tr>`;
        const regs = session.events.filter(e => e.type === 'registration').sort((a,b) => new Date(a.timestamp) - new Date(b.timestamp));
        const deregs = session.events.filter(e => e.type === 'deregistration').sort((a,b) => new Date(a.timestamp) - new Date(b.timestamp));
        const eventRows = regs.map(r => ({ regEvent: r, deregEvent: null }));
        deregs.forEach(deregEvent => {
            let matchingRegIndex = -1;
            for (let i = eventRows.length - 1; i >= 0; i--) { if (new Date(eventRows[i].regEvent.timestamp) < new Date(deregEvent.timestamp) && eventRows[i].deregEvent === null) { matchingRegIndex = i; break; } }
            if (matchingRegIndex !== -1) { eventRows[matchingRegIndex].deregEvent = deregEvent; }
        });
        eventRows.forEach((row, i) => {
            const adminNote = (row.deregEvent && row.deregEvent.admin_login && row.deregEvent.admin_login !== session.user_login) ? ` <span class="admin-note-dereg">(Администратором)</span>` : '';
            const perRowActivity = row.deregEvent ? formatDuration(new Date(row.deregEvent.timestamp) - new Date(row.regEvent.timestamp)) : '';
            const eventDeviceAndroidId = row.regEvent.device_androidId;
            const deviceOrderNumber = row.regEvent.device_orderNumber;
            let deviceHTML = `<span>${deviceOrderNumber ? `№${deviceOrderNumber}` : eventDeviceAndroidId}</span>`;
            const requiredTimestamp = buttonMap.get(eventDeviceAndroidId);
            if (requiredTimestamp && row.regEvent.timestamp === requiredTimestamp) { deviceHTML += `<button class="add-device-btn" data-android-id="${eventDeviceAndroidId}">Добавить в базу</button>`; }
            const tsdHTML = `<div class="tsd-cell-content">${deviceHTML}</div>`;
            finalHTML += `<tr class="session-event-row" data-searchable-text="${headerSearchableText}"><td><span class="timestamp-reg">${formatTimestamp(row.regEvent.timestamp)}</span></td><td>${row.deregEvent ? `<span class="timestamp-dereg">${formatTimestamp(row.deregEvent.timestamp)}</span>` : ''}${adminNote}</td><td>${perRowActivity}</td><td>${tsdHTML}</td></tr>`;
        });
        return finalHTML;
    }

    function getShiftInfo(date) { const hour = date.getHours(); let shiftDate = new Date(date); if (hour < 6) { shiftDate.setDate(shiftDate.getDate() - 1); } const day = String(shiftDate.getDate()).padStart(2, '0'); const month = String(shiftDate.getMonth() + 1).padStart(2, '0'); const year = shiftDate.getFullYear(); const dateString = `${day}.${month}.${year}`; if (hour >= 18 || hour < 6) { return { key: `night-${dateString}`, header: `Ночная смена ${dateString}` }; } else { return { key: `day-${dateString}`, header: `Дневная смена ${dateString}` }; } }

    async function fetchAndDisplayLogs(type, year = null, month = null) {
        currentView = { type, year, month };
        let url;
        if (type === 'latest') { url = `${SERVER_URL}/api/logs?period=latest`; } 
        else if (type === 'archive') { url = `${SERVER_URL}/api/logs?year=${year}&month=${month}`; } 
        else { return; }
        
        logBody.innerHTML = `<tr><td colspan="4" style="text-align:center;padding:20px;">Загрузка...</td></tr>`;
        try {
            const [shiftsResponse, unregisteredResponse] = await Promise.all([fetch(url), fetch(`${SERVER_URL}/api/unregistered_devices`)]);
            if (!shiftsResponse.ok) throw new Error(`HTTP ${shiftsResponse.status} при загрузке логов`);
            const allShifts = await shiftsResponse.json();
            let unregisteredIds = new Set();
            if (unregisteredResponse.ok) { unregisteredIds = new Set(await unregisteredResponse.json()); } else { console.error("Не удалось получить список незарегистрированных устройств."); }
            if (allShifts.length === 0) { logBody.innerHTML = `<tr><td colspan="4" style="text-align:center;padding:20px;">Записи не найдены</td></tr>`; adjustHeaderForScrollbar(); return; }
            const buttonEventMap = new Map();
            if (currentView.type === 'latest') {
                const allEvents = allShifts.flatMap(s => s.sessions).flatMap(s => s.events.map(e => ({...e, sessionAndroidId: s.device_androidId })));
                allEvents.sort((a,b) => new Date(b.timestamp) - new Date(a.timestamp));
                allEvents.forEach(event => { if (event.type === 'registration') { const deviceId = event.device_androidId || event.sessionAndroidId; if (event.status === 'ok_scan_unregistered_device' && unregisteredIds.has(deviceId) && !buttonEventMap.has(deviceId)) { buttonEventMap.set(deviceId, event.timestamp); } } });
            }
            const taggedItems = allShifts.flatMap(shift => [...shift.sessions, ...shift.system_events]).map(item => { const timestamp = (item.events && item.events.length > 0) ? item.events[0].timestamp : item.timestamp_utc; const itemDate = new Date(timestamp); const shiftInfo = getShiftInfo(itemDate); return { ...shiftInfo, timestamp: itemDate, originalItem: item }; });
            taggedItems.sort((a, b) => a.timestamp - b.timestamp);
            let finalHTML = '';
            let lastDisplayedShiftKey = null;
            taggedItems.forEach(item => {
                // --- ИЗМЕНЕНИЕ: Строка ниже закомментирована, чтобы убрать заголовок смены ---
                // if (item.key !== lastDisplayedShiftKey) { finalHTML += `<tr class="shift-header-row"><td colspan="4">${item.header}</td></tr>`; lastDisplayedShiftKey = item.key; }
                const originalItem = item.originalItem;
                if (originalItem.event_type === 'user_session') { finalHTML += createSessionBlockHTML(originalItem, buttonEventMap); } else if (originalItem.event_type === 'system_event') { finalHTML += createSystemEventRow(originalItem); }
            });
            logBody.innerHTML = finalHTML;
            scrollToBottom();
            adjustHeaderForScrollbar();
        } catch (error) {
            console.error('Критическая ошибка:', error);
            logBody.innerHTML = `<tr><td colspan="4" style="text-align:center;padding:20px;color:red;">Критическая ошибка: ${error.message}</td></tr>`;
            adjustHeaderForScrollbar();
        }
    }
    
    const socket = io(SERVER_URL, { transports: ['websocket'], rejectUnauthorized: false });
    socket.on('connect', () => updateStatus('connected', 'Подключен'));
    socket.on('disconnect', () => updateStatus('disconnected', 'Нет подключения'));
    socket.on('connect_error', (err) => { updateStatus('disconnected', 'Ошибка'); console.error('Ошибка подключения Socket.IO:', err); });
    ['session_updated', 'system_event_logged'].forEach(event => { socket.on(event, (data) => { if (currentView.type === 'latest' && !currentView.year) { fetchAndDisplayLogs('latest'); } }); });
    
    async function initialize() {
        updateStatus('connecting', 'Подключение...');
        
        await setupSidebar('sessions');

        const urlParams = new URLSearchParams(window.location.search);
        const year = urlParams.get('year');
        const month = urlParams.get('month');

        if (year && month) {
            pageSubtitle.textContent = `Архив: ${MONTH_NAMES[month - 1]} ${year}`;
            const activeLink = document.querySelector(`#main-nav a[data-year="${year}"][data-month="${month}"]`);
            if (activeLink) {
                document.querySelectorAll('#main-nav a').forEach(a => a.classList.remove('active-link'));
                activeLink.classList.add('active-link');
                let parent = activeLink.closest('.archive-months');
                if (parent && parent.classList.contains('collapsed')) {
                    const parentToggler = document.querySelector(`.archive-toggler[data-target="${parent.id}"]`);
                    if (parentToggler) { parent.classList.remove('collapsed'); parentToggler.classList.remove('collapsed'); }
                }
                let grandParent = parent?.closest('#archive-wrapper');
                if (grandParent && grandParent.classList.contains('collapsed')) {
                     const grandParentToggler = document.querySelector(`.archive-toggler[data-target="${grandParent.id}"]`);
                     if (grandParentToggler) { grandParent.classList.remove('collapsed'); grandParentToggler.classList.remove('collapsed'); }
                }
            }
            await fetchAndDisplayLogs('archive', year, month);
        } else {
            pageSubtitle.textContent = 'Последние 30 дней';
            await fetchAndDisplayLogs('latest');
        }
        
        adjustHeaderForScrollbar();
        window.addEventListener('resize', adjustHeaderForScrollbar);
    }

    document.body.addEventListener('click', (event) => {
        if (event.target.matches('.add-device-btn')) {
            event.preventDefault();
            promptAndAddDevice(event.target.getAttribute('data-android-id'));
        }
    });

    function promptAndAddDevice(android_id) { const order_num = prompt(`Введите порядковый номер для ТСД с ID:\n${android_id}`); if (order_num && order_num.trim() !== '' && !isNaN(order_num)) { sendAddDeviceRequest(android_id, order_num.trim()); } else if (order_num !== null) { alert("Ошибка ввода: Порядковый номер должен быть числом."); } }
    
    async function sendAddDeviceRequest(android_id, order_number) { 
        try { 
            const response = await fetch(`${SERVER_URL}/add_device`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ "androidId": android_id, "orderNumber": order_number }) }); 
            const data = await response.json(); 
            alert(data.message || "Неизвестный ответ от сервера."); 
            if (response.ok) { fetchAndDisplayLogs(currentView.type, currentView.year, currentView.month); } 
        } catch (error) { 
            alert("Критическая ошибка сети при добавлении ТСД. См. консоль (F12)."); 
            console.error("Ошибка запроса /add_device:", error); 
        } 
    }
    
    searchBox.addEventListener('input', (e) => {
        const searchTerm = e.target.value.toLowerCase().trim();
        const allRows = Array.from(logBody.querySelectorAll('tr'));
        if (!searchTerm) { allRows.forEach(row => { row.style.display = ''; }); return; }
        const blocks = [];
        let currentBlock = null;
        allRows.forEach(row => {
            if (row.classList.contains('shift-header-row') || row.classList.contains('session-header-row') || row.classList.contains('system-event-row')) { currentBlock = { header: row, events: [] }; blocks.push(currentBlock); } 
            else if (currentBlock && row.classList.contains('session-event-row')) { currentBlock.events.push(row); }
        });
        blocks.forEach(block => {
            const { header, events } = block;
            if (header.classList.contains('shift-header-row')) { header.style.display = 'none'; return; }
            if (header.classList.contains('system-event-row')) { header.style.display = header.textContent.toLowerCase().includes(searchTerm) ? '' : 'none'; return; }
            if (header.classList.contains('session-header-row')) {
                if (header.textContent.toLowerCase().includes(searchTerm)) { header.style.display = ''; events.forEach(event => event.style.display = ''); } 
                else {
                    let hasVisibleEvents = false;
                    events.forEach(event => { if (event.textContent.toLowerCase().includes(searchTerm)) { event.style.display = ''; hasVisibleEvents = true; } else { event.style.display = 'none'; } });
                    header.style.display = hasVisibleEvents ? '' : 'none';
                }
            }
        });
    });

    initialize();
});

