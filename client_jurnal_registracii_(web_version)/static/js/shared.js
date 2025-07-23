// static/js/shared.js

// Глобальные настройки для всех страниц
const SERVER_URL = "http://10.42.0.1:5000";
const MONTH_NAMES = ["Январь", "Февраль", "Март", "Апрель", "Май", "Июнь", "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"];

/**
 * Генерирует боковую панель навигации, корректно подсвечивая активную страницу.
 * @param {string} activePage - Название активной страницы ('sessions' или 'devices').
 */
async function setupSidebar(activePage) {
    const sidebarContainer = document.getElementById('sidebar');
    if (!sidebarContainer) return;

    try {
        const response = await fetch(`${SERVER_URL}/api/logs/archive-index`);
        if (!response.ok) throw new Error("Не удалось получить индекс архива");
        const years = await response.json();

        const sessionsLinkClass = activePage === 'sessions' ? 'class="active-link"' : '';
        const devicesLinkClass = activePage === 'devices' ? 'class="active-link"' : '';

        let navHtml = `
            <div class="sidebar-header"><h2>Навигация</h2></div>
            <nav id="main-nav">
                <div class="nav-section-title">Основное</div>
                <ul>
                    <li><a href="index.html" ${sessionsLinkClass}>Журнал регистрации</a></li>
                    <li><a href="devices.html" ${devicesLinkClass}>Управление ТСД</a></li>
                </ul>`;

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
        
        navHtml += `</nav>`;
        sidebarContainer.innerHTML = navHtml;

        // Единый обработчик для всей боковой панели
        sidebarContainer.addEventListener('click', (event) => {
            const target = event.target;

            // Логика для сворачивания/разворачивания
            const toggler = target.closest('.archive-toggler');
            if (toggler) {
                const targetId = toggler.dataset.target;
                const targetElement = document.getElementById(targetId);
                if (targetElement) {
                    toggler.classList.toggle('collapsed');
                    targetElement.classList.toggle('collapsed');
                }
                return; // Выходим, чтобы не обрабатывать клик как ссылку
            }

            // Логика для перехода по ссылкам архива
            const link = target.closest('a');
            if (link && link.dataset.type === 'archive') {
                event.preventDefault();
                const year = link.dataset.year;
                const month = link.dataset.month;
                // Всегда перенаправляем на index.html с параметрами
                window.location.href = `index.html?year=${year}&month=${month}`;
            }
        });

    } catch (error) {
        console.error("Ошибка загрузки индекса архива:", error);
        sidebarContainer.innerHTML = `<div class="sidebar-header"><h2>Навигация</h2></div><div style="padding: 15px; color: #c0392b;">Ошибка загрузки архива</div>`;
    }
}

