document.addEventListener('DOMContentLoaded', () => {
    // --- НАСТРОЙКИ ---
    const SERVER_URL = "https://10.42.0.1:5000";
    const ADMIN_LOGIN_PREFIX = "ADM-SWH-";
    const ADMIN_DISPLAY_NAME = "Администратор";
    const MONTH_NAMES = ["Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"];

    // --- DOM Элементы ---
    const statusIndicator = document.getElementById('status-indicator');
    const statusText = document.getElementById('status-text');
    const logBody = document.getElementById('log-body');
    const mainContent = document.getElementById('main-content');
    const searchBox = document.getElementById('search-box');
    const mainNav = document.getElementById('main-nav');
    const pageSubtitle = document.getElementById('page-subtitle');
    // -- ИЗМЕНЕНИЕ 4: Получаем элемент заголовка таблицы
    const tableHeaderSticky = document.querySelector('.table-header-sticky');

    let currentView = { type: 'latest' };
    
    // -- ИЗМЕНЕНИЕ 4: Функция для выравнивания заголовка таблицы --
    function adjustHeaderForScrollbar() {
        if (!mainContent || !tableHeaderSticky) return;
        
        // Вычисляем ширину скроллбара
        const scrollbarWidth = mainContent.offsetWidth - mainContent.clientWidth;
        
        // Применяем эту ширину как правый отступ для контейнера заголовка
        tableHeaderSticky.style.paddingRight = `${scrollbarWidth + 20}px`; // 20px - это исходный горизонтальный отступ
    }
    // -------------------------------------------------------------

    function updateStatus(state, text) {
        statusIndicator.className = 'status-indicator';
        statusIndicator.classList.add(state === 'connected' ? 'connected' : 'disconnected');
        statusText.textContent = text;
    }

    function scrollToBottom() {
        setTimeout(() => {
            if (mainContent) {
                mainContent.scrollTop = mainContent.scrollHeight;
            }
        }, 100);
    }

    function maskAdminLogin(loginStr) {
        if (!loginStr) return "N/A";
        return loginStr.startsWith(ADMIN_LOGIN_PREFIX) ? ADMIN_DISPLAY_NAME : loginStr;
    }

    function formatLogEntry(eventName, data, showButton = false) {
        let timestamp = data.serverTimestamp || new Date().toLocaleString('ru-RU');
        let type = { text: 'N/A', className: '' };
        let userAction_html = 'N/A';
        let device_html = 'N/A';
        let details_node = null;
        let details_html = '';

        const row = document.createElement('tr');
        const androidId = data.rawAndroidId || data.androidId;
        if (androidId) {
            row.dataset.androidId = androidId;
        }

        switch (eventName) {
            case 'new_log_entry':
                if (!data.overall_success && data.status !== 'ok_scan_unregistered_device') return null;
                type = { text: 'Регистрация', className: 'log-type--registration' };
                userAction_html = data.userFullName || maskAdminLogin(data.userLogin);
                device_html = data.orderNumber ? `№${data.orderNumber}` : (data.rawAndroidId || "N/A");

                if (showButton && data.rawAndroidId) {
                    const addButton = document.createElement('button');
                    addButton.className = 'add-device-btn';
                    addButton.textContent = 'Добавить в базу';
                    addButton.dataset.androidId = data.rawAndroidId;
                    details_node = addButton;
                }
                break;
            case 'deregistration_log':
                if (!data.overall_success && data.status !== 'already_free_dereg') return null;
                type = { text: 'Дерегистрация', className: 'log-type--deregistration' };
                device_html = data.orderNumber ? `№${data.orderNumber}` : "-";
                userAction_html = data.deregisteredUserFullName || maskAdminLogin(data.deregisteredUser);
                if (data.adminAction) {
                    details_html = `(Администратором)`;
                }
                break;
            case 'device_registration_attempt':
                if (!data.overall_success) return null;
                type = { text: 'Инфо', className: 'log-type--info' };
                userAction_html = 'Система';
                device_html = data.androidId || "N/A";
                details_html = `Добавлен под № ${data.orderNumber || "N/A"}`;
                break;
            default:
                return null;
        }

        row.innerHTML = `<td>${timestamp}</td><td class="${type.className}">${type.text}</td><td>${userAction_html}</td><td>${device_html}</td><td></td>`;
        const detailsCell = row.cells[4];
        if (details_node) {
            detailsCell.appendChild(details_node);
        } else {
            detailsCell.innerHTML = details_html;
        }
        return row;
    }

    async function fetchAndDisplayLogs(type, year = null, month = null) {
        currentView = { type, year, month };
        let url;
        let title;
        if (type === 'latest') {
            url = `${SERVER_URL}/api/logs?period=latest`;
            title = 'Последние 30 дней';
        } else if (type === 'archive') {
            url = `${SERVER_URL}/api/logs?year=${year}&month=${month}`;
            title = `Архив: ${MONTH_NAMES[month - 1]} ${year}`;
        } else { return; }

        pageSubtitle.textContent = title;
        logBody.innerHTML = `<tr><td colspan="5" style="text-align:center;padding:20px;">Загрузка...</td></tr>`;

        try {
            const [logsResponse, unregisteredResponse] = await Promise.all([
                fetch(url),
                currentView.type === 'latest' ? fetch(`${SERVER_URL}/api/unregistered_devices`) : Promise.resolve(null)
            ]);

            if (!logsResponse.ok) throw new Error(`HTTP ${logsResponse.status} при загрузке логов`);
            const logs = await logsResponse.json();
            
            let trulyUnregisteredIds = new Set();
            if (unregisteredResponse) {
                if (!unregisteredResponse.ok) throw new Error(`HTTP ${unregisteredResponse.status} при загрузке статусов устройств`);
                trulyUnregisteredIds = new Set(await unregisteredResponse.json());
            }

            logBody.innerHTML = '';

            if (logs.length === 0) {
                logBody.innerHTML = `<tr><td colspan="5" style="text-align:center;padding:20px;">Записи не найдены</td></tr>`;
                return;
            }

            const lastLogForButton = new Map();
            if (currentView.type === 'latest') {
                 [...logs].reverse().forEach(log => {
                     const data = log.log_data;
                     const id = data.rawAndroidId;
                     if (log.event_name === 'new_log_entry' && data.status === 'ok_scan_unregistered_device' && trulyUnregisteredIds.has(id)) {
                         if (!lastLogForButton.has(id)) {
                             lastLogForButton.set(id, log); 
                         }
                     }
                 });
            }
            
            logs.forEach(log => {
                const id = log.log_data.rawAndroidId;
                const showButton = (id && lastLogForButton.get(id) === log);
                
                const entry = formatLogEntry(log.event_name, log.log_data, showButton);
                if (entry) {
                    logBody.appendChild(entry);
                }
            });

            scrollToBottom();

        } catch (error) {
            console.error('Критическая ошибка:', error);
            logBody.innerHTML = `<tr><td colspan="5" style="text-align:center;padding:20px;color:red;">${error.message}</td></tr>`;
        }
    }
    
    const socket = io(SERVER_URL, { transports: ['websocket'], rejectUnauthorized: false });
    socket.on('connect', () => updateStatus('connected', 'Подключено'));
    socket.on('disconnect', () => updateStatus('disconnected', 'Отключено'));
    socket.on('connect_error', (err) => { updateStatus('disconnected', 'Ошибка подключения'); console.error('Ошибка подключения Socket.IO:', err); });
    
    ['new_log_entry', 'deregistration_log', 'device_registration_attempt'].forEach(event => {
        socket.on(event, (data) => {
            if (currentView.type === 'latest') {
                fetchAndDisplayLogs('latest');
            }
        });
    });

    // -- ИЗМЕНЕНИЕ 1: Полностью переписанная функция для создания боковой панели --
    async function setupSidebar() {
        try {
            const response = await fetch(`${SERVER_URL}/api/logs/archive-index`);
            if (!response.ok) throw new Error("Не удалось получить индекс архива");
            const years = await response.json();

            let navHtml = `<div class="nav-section-title">Основное</div><ul><li><a href="#" class="active-link" data-type="latest">Последние 30 дней</a></li></ul>`;
            
            if (years.length > 0) {
                navHtml += `<div class="archive-toggler collapsed" data-target="archive-wrapper">Архив</div>`;
                navHtml += `<div id="archive-wrapper" class="archive-content collapsed">`;
                years.forEach(year => {
                    navHtml += `<div class="archive-toggler collapsed" data-target="year-${year}">${year}</div>`;
                    navHtml += `<ul id="year-${year}" class="archive-months collapsed">
                        ${MONTH_NAMES.map((name, index) => `<li><a href="#" data-type="archive" data-year="${year}" data-month="${index + 1}">${name}</a></li>`).join('')}
                    </ul>`;
                });
                navHtml += `</div>`;
            }

            mainNav.innerHTML = navHtml;

            // Добавляем обработчики событий для всех переключателей
            document.querySelectorAll('.archive-toggler').forEach(toggler => {
                toggler.addEventListener('click', (e) => {
                    const targetId = e.currentTarget.dataset.target;
                    const targetElement = document.getElementById(targetId);
                    if (targetElement) {
                        e.currentTarget.classList.toggle('collapsed');
                        targetElement.classList.toggle('collapsed');
                    }
                });
            });

        } catch (error) {
            console.error("Ошибка загрузки индекса архива:", error);
            mainNav.innerHTML = `<div style="padding: 15px; color: #c0392b;">Ошибка загрузки архива</div>`;
        }
    }
    // --------------------------------------------------------------------------

    function promptAndAddDevice(android_id) { const order_num = prompt(`Введите порядковый номер для ТСД с ID:\n${android_id}`); if (order_num && order_num.trim() !== '' && !isNaN(order_num)) { sendAddDeviceRequest(android_id, order_num.trim()); } else if (order_num !== null) { alert("Ошибка ввода: Порядковый номер должен быть числом."); } }
    async function sendAddDeviceRequest(android_id, order_number) { try { const response = await fetch(`${SERVER_URL}/add_device`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ "androidId": android_id, "orderNumber": order_number }) }); const data = await response.json(); alert(data.message || "Неизвестный ответ от сервера."); } catch (error) { alert("Критическая ошибка сети при добавлении ТСД. См. консоль (F12)."); console.error("Ошибка запроса /add_device:", error); } }
    document.body.addEventListener('click', (event) => { if (event.target.matches('.add-device-btn')) { event.preventDefault(); promptAndAddDevice(event.target.getAttribute('data-android-id')); return; } const link = event.target.closest('a'); if (link && link.closest('#main-nav')) { event.preventDefault(); document.querySelectorAll('#main-nav a').forEach(a => a.classList.remove('active-link')); link.classList.add('active-link'); fetchAndDisplayLogs(link.dataset.type, link.dataset.year, link.dataset.month); } });
    searchBox.addEventListener('input', (e) => { const searchTerm = e.target.value.toLowerCase(); logBody.querySelectorAll('tr').forEach(row => { const rowText = row.textContent.toLowerCase(); row.style.display = rowText.includes(searchTerm) ? '' : 'none'; }); });
    
    async function initialize() {
        await setupSidebar();
        await fetchAndDisplayLogs('latest');
        // -- ИЗМЕНЕНИЕ 4: Вызываем функцию выравнивания при инициализации
        adjustHeaderForScrollbar();
        // Убрали автоматическое раскрытие текущего года
    }

    // -- ИЗМЕНЕНИЕ 4: Добавляем обработчик изменения размера окна
    window.addEventListener('resize', adjustHeaderForScrollbar);

    initialize();
});
