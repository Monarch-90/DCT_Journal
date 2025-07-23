import sys
import os
from PyQt6.QtWidgets import (QApplication, QMainWindow, QVBoxLayout,
                             QWidget, QListWidget, QLabel, QListWidgetItem, QMessageBox, QInputDialog,
                             QMenu) # <--- Добавлен QMenu
from PyQt6.QtCore import QThread, pyqtSignal, Qt, QVariant
from PyQt6.QtGui import QFont, QAction, QClipboard, QTextDocument # <--- Добавлены QAction, QClipboard, QTextDocument
import socketio
import datetime
import requests
import json
import warnings

from urllib3.exceptions import InsecureRequestWarning
warnings.simplefilter('ignore', InsecureRequestWarning)

ADMIN_LOGIN_PREFIX = "ADM-SWH-"
ADMIN_DISPLAY_NAME = "Администратор"
LOG_FONT_SIZE = 12

# --- Поток для работы с Socket.IO (без изменений) ---
class SocketIOThread(QThread):
    connected_signal = pyqtSignal()
    disconnected_signal = pyqtSignal()
    connect_error_signal = pyqtSignal(object)
    new_log_signal = pyqtSignal(str, dict)

    def __init__(self, server_url):
        super().__init__()
        self.server_url = server_url
        self.sio = socketio.Client(logger=False, engineio_logger=False, ssl_verify=False)
        self._is_running = True
        self._setup_event_handlers()

    def _setup_event_handlers(self):
        @self.sio.event
        def connect(): self.connected_signal.emit()
        @self.sio.event
        def connect_error(data): self.connect_error_signal.emit(data)
        @self.sio.event
        def disconnect(): self.disconnected_signal.emit()
        @self.sio.on('new_log_entry')
        def on_new_log_entry(data): self.new_log_signal.emit('new_log_entry', data)
        @self.sio.on('deregistration_log')
        def on_deregistration_log(data): self.new_log_signal.emit('deregistration_log', data)
        @self.sio.on('device_registration_attempt')
        def on_device_registration_attempt(data): self.new_log_signal.emit('device_registration_attempt', data)

    def run(self):
        print(f"SocketIOThread: Попытка подключения к {self.server_url}")
        try:
            if self._is_running:
                self.sio.connect(self.server_url, transports=['websocket'])
                if self.sio.connected and self._is_running: self.sio.wait()
        except socketio.exceptions.ConnectionError as e:
            if self._is_running: self.connect_error_signal.emit(str(e))
        except Exception as e:
            if self._is_running: self.connect_error_signal.emit(f"Неожиданная ошибка в SocketIOThread: {str(e)}")
        finally:
            if self.sio.connected: self.sio.disconnect()
            print("SocketIOThread: Метод run завершен.")

    def stop(self):
        print("SocketIOThread: Остановка...")
        self._is_running = False
        if self.sio.connected: self.sio.disconnect()
        self.quit(); self.wait()
        print("SocketIOThread: Остановлен.")

# --- Главное окно приложения ---
class MainWindow(QMainWindow):
    def __init__(self, server_url_for_thread_and_http):
        super().__init__()
        self.setWindowTitle("Журнал Регистрации ТСД (Клиент)")
        self.setGeometry(100, 100, 900, 700)
        
        self.server_base_url = server_url_for_thread_and_http
        self.is_socket_io_connected = False

        self.central_widget = QWidget()
        self.setCentralWidget(self.central_widget)
        self.layout = QVBoxLayout(self.central_widget)

        self.status_label = QLabel("Статус: Нет подключения к серверу") # <--- ИЗМЕНЕНИЕ: Начальный статус
        self.layout.addWidget(self.status_label)

        self.log_list_widget = QListWidget()
        list_font = self.log_list_widget.font()
        list_font.setPointSize(LOG_FONT_SIZE)
        self.log_list_widget.setFont(list_font)
        # --- ИЗМЕНЕНИЕ: Включаем политику контекстного меню ---
        self.log_list_widget.setContextMenuPolicy(Qt.ContextMenuPolicy.CustomContextMenu)
        self.log_list_widget.customContextMenuRequested.connect(self.show_log_context_menu)
        # ----------------------------------------------------
        self.layout.addWidget(self.log_list_widget)

        self.load_logs_from_server() # Загружаем логи

        self.socket_io_thread = SocketIOThread(server_url_for_thread_and_http)
        self.socket_io_thread.connected_signal.connect(self.handle_socket_connected)
        self.socket_io_thread.disconnected_signal.connect(self.handle_socket_disconnected)
        self.socket_io_thread.connect_error_signal.connect(self.handle_socket_connect_error)
        self.socket_io_thread.new_log_signal.connect(self.handle_new_log_received)
        self.socket_io_thread.start()

    def _mask_admin_login(self, login_str): # (без изменений)
        if login_str and login_str.startswith(ADMIN_LOGIN_PREFIX):
            return ADMIN_DISPLAY_NAME
        return login_str

    def _format_log_entry(self, event_name, data_dict, is_archived=False): # (без изменений)
        final_html_output = None
        is_successful_event = data_dict.get("overall_success", True) 
        status_from_data = data_dict.get("status", "N/A")
        server_time = data_dict.get("serverTimestamp", datetime.datetime.now().strftime("%d.%m.%Y %H:%M:%S"))

        if event_name == 'new_log_entry':
            if not is_successful_event: return None, False 
            user_login_raw = data_dict.get("userLogin", "N/A")
            user_display = self._mask_admin_login(user_login_raw)
            order_num = data_dict.get("orderNumber", None)
            raw_android_id = data_dict.get("rawAndroidId", "N/A")
            log_html_main = f"<font color='green'>Зарегистрировался</font>: {user_display}<br>"
            tsd_identifier = str(order_num) if order_num else raw_android_id
            log_html_main += f"ТСД: {tsd_identifier}<br>"
            log_html_main += f"{server_time}" 
            final_html_output = log_html_main
            if status_from_data == 'ok_scan_unregistered_device' and data_dict.get("rawAndroidId") and not is_archived:
                current_raw_id = data_dict.get("rawAndroidId")
                action_link_html = (f"<a href='action:add_device:{current_raw_id}' "
                                    f"style='color:black; font-weight:bold; text-decoration:underline;'>"
                                    f"[НАЖМИТЕ ЗДЕСЬ, чтобы добавить]</a>")
                info_message_html = f"<br><br>INFO: ТСД не зарегистрирован в базе данных. {action_link_html}"
                final_html_output += info_message_html
        
        elif event_name == 'deregistration_log':
            admin_action = data_dict.get("adminAction", False)
            deregistered_user_raw = data_dict.get("deregisteredUser", None) 
            attempting_user_raw = data_dict.get("userLoginAttempt", "N/A") 
            order_num_display = data_dict.get("orderNumber", "-")
            is_successful_specific_dereg = status_from_data in ["admin_deregistered", "user_deregistered", "ok_deregistered"]
            is_successful_event = is_successful_specific_dereg 
            if is_successful_specific_dereg:
                base_text = ""
                if admin_action:
                    actor_display_name = ADMIN_DISPLAY_NAME 
                    if attempting_user_raw == deregistered_user_raw: 
                        base_text = f"<font color='red'>Дерегистрировался</font>: {actor_display_name}<br>ТСД: {order_num_display}"
                    else: 
                        deregistered_user_display = self._mask_admin_login(deregistered_user_raw)
                        base_text = f"{deregistered_user_display} был <font color='red'>дерегистрирован</font> {actor_display_name}<br>ТСД: {order_num_display}"
                else: 
                    attempting_user_display = self._mask_admin_login(attempting_user_raw)
                    base_text = f"<font color='red'>Дерегистрировался</font>: {attempting_user_display}<br>ТСД: {order_num_display}"
                final_html_output = f"{base_text}<br>{server_time}"
            else:
                if status_from_data in ["already_free_dereg", "device_not_found_dereg", "permission_denied_dereg"]:
                     final_html_output = None 
                else: 
                    message_content = data_dict.get("messageForTsd", data_dict.get("errorMessageForWindows", ""))
                    attempting_user_display = self._mask_admin_login(attempting_user_raw)
                    text_content = f"[{server_time}] Событие: {event_name} (статус: {status_from_data})\n"
                    text_content += f"  Попытка от: {attempting_user_display}, ТСД №: {order_num_display}\n"
                    if message_content: text_content += f"  Сообщение: {message_content}"
                    final_html_output = text_content.replace("\n", "<br>")
        
        elif event_name == 'device_registration_attempt':
            android_id = data_dict.get("androidId", "N/A")
            order_num = data_dict.get("orderNumber", "N/A")
            if data_dict.get("overall_success"):
                final_html_output = f"ТСД - {android_id} добавлен в базу данных под номером {order_num}<br>{server_time}"
                is_successful_event = True
            else: 
                message = data_dict.get("message", "Ошибка обработки на сервере.")
                final_html_output = f"Ошибка добавления ТСД ({android_id}, №{order_num}): {message}<br>{server_time}"
                is_successful_event = False
        
        elif event_name in ["client_action_cancelled_add_device"]:
            msg = data_dict.get("message", str(data_dict))
            client_timestamp = data_dict.get("clientTimestamp", server_time)
            final_html_output = f"INFO: {msg.replace(chr(10), '<br>') if isinstance(msg, str) else str(msg)}<br>{client_timestamp}"
        
        else: 
            if isinstance(data_dict, str) and data_dict.startswith("INFO:"): # Только для наших INFO сообщений
                final_html_output = data_dict 
            elif event_name not in ["client_add_device_success", "client_add_device_error", "client_http_error_add_device", "client_network_error_add_device", "client_unknown_error_add_device"]:
                final_html_output = f"[{server_time}] Событие: {event_name}<br>Данные: {str(data_dict).replace(chr(10), '<br>')}"
            
        return final_html_output, is_successful_event

    def handle_socket_connected(self):
        self.status_label.setText("Статус: Подключено к серверу") # <--- ИЗМЕНЕНИЕ
        # self.add_log_message_to_gui("INFO: Успешно подключено к серверу.", True, as_html=False) # <--- УДАЛЕНО ИЗ ЛОГА
        self.is_socket_io_connected = True

    def handle_socket_disconnected(self):
        self.status_label.setText("Статус: Нет подключения к серверу") # <--- ИЗМЕНЕНИЕ
        self.add_log_message_to_gui("INFO: Отключено от сервера.", True, as_html=False) # Это оставим, полезно
        self.is_socket_io_connected = False

    def handle_socket_connect_error(self, error_data):
        error_message = str(error_data)
        self.status_label.setText("Статус: Нет подключения к серверу") # <--- ИЗМЕНЕНИЕ
        self.add_log_message_to_gui(f"ОШИБКА ПОДКЛЮЧЕНИЯ: {error_message}", False, as_html=False) # Это оставим
        self.is_socket_io_connected = False

    def handle_new_log_received(self, event_name, data_dict):
        html_to_display, is_success = self._format_log_entry(event_name, data_dict, is_archived=False)

        if html_to_display:
            self.add_log_message_to_gui(html_to_display, is_success, as_html=True)

        # --- ИЗМЕНЕНИЕ: Обновляем status_label только по статусу соединения, не по содержимому лога ---
        status_from_data = data_dict.get("status", "N/A")
        if event_name == 'new_log_entry' and status_from_data == 'ok_scan_unregistered_device' and data_dict.get("rawAndroidId"):
            # Можно временно показать ID в статусе, но потом он должен вернуться к статусу соединения
            # Или лучше вообще не трогать status_label здесь, он только для соединения.
            # current_raw_id = data_dict.get("rawAndroidId")
            # self.status_label.setText(f"Статус: Обнаружен незарегистрированный ТСД ID: {current_raw_id}.") # <-- УБИРАЕМ
            pass # Статус соединения не меняется от этого
        
        # Обновляем статус в соответствии с текущим состоянием соединения
        if self.is_socket_io_connected:
            self.status_label.setText("Статус: Подключено к серверу")
        else:
            self.status_label.setText("Статус: Нет подключения к серверу")


    def on_log_link_activated(self, link_url): # (без изменений)
        print(f"Link activated: {link_url}")
        if link_url.startswith("action:add_device:"):
            try:
                android_id = link_url.split(":")[2]
                if android_id:
                    self.prompt_and_add_device(android_id)
            except IndexError:
                print(f"Ошибка парсинга ссылки: {link_url}")

    def prompt_and_add_device(self, android_id_to_add): # (без изменений)
        order_number_str, ok = QInputDialog.getText(self, "Добавить ТСД", 
                                                 f"Введите порядковый номер для ТСД с ID:\n{android_id_to_add}")
        if ok and order_number_str:
            try:
                int(order_number_str)
                self.send_add_device_request(android_id_to_add, order_number_str)
            except ValueError:
                QMessageBox.warning(self, "Ошибка ввода", "Порядковый номер должен быть числом.")
        else:
            client_time = datetime.datetime.now().strftime("%d.%m.%Y %H:%M:%S")
            cancel_msg = f"Добавление ТСД (ID: {android_id_to_add}) отменено пользователем."
            self.add_log_message_to_gui(f"INFO: {cancel_msg}<br>{client_time}", True, as_html=True)

    def send_add_device_request(self, android_id_to_add, order_number_to_add):
        add_device_url = f"{self.server_base_url}/add_device"
        payload = {"androidId": android_id_to_add, "orderNumber": order_number_to_add}
        # --- ИЗМЕНЕНИЕ: Не меняем status_label на "Отправка запроса..." ---
        # self.status_label.setText(f"Статус: Отправка запроса на добавление ТСД ID: {android_id_to_add}...")
        client_time = datetime.datetime.now().strftime("%d.%m.%Y %H:%M:%S")
        
        try:
            response = requests.post(add_device_url, json=payload, verify=False, timeout=10)
            response.raise_for_status()
            response_data = response.json()
            message_from_server = response_data.get("message", "Ответ от сервера не содержит сообщения.")
            if response_data.get("success"):
                QMessageBox.information(self, "Успех", message_from_server)
            else:
                QMessageBox.warning(self, "Ошибка добавления", message_from_server)
        except requests.exceptions.HTTPError as http_err:
            err_msg_display = f"HTTP ошибка: {http_err}. Ответ: {http_err.response.text if http_err.response else 'Нет ответа'}"
            self.add_log_message_to_gui(f"{err_msg_display}<br>{client_time}", False, as_html=True)
            QMessageBox.critical(self, "HTTP Ошибка", str(http_err))
        except requests.exceptions.RequestException as req_err:
            err_msg_display = f"Ошибка сети: {req_err}"
            self.add_log_message_to_gui(f"{err_msg_display}<br>{client_time}", False, as_html=True)
            QMessageBox.critical(self, "Сетевая Ошибка", str(req_err))
        except Exception as e:
            err_msg_display = f"Неизвестная ошибка: {e}"
            self.add_log_message_to_gui(f"{err_msg_display}<br>{client_time}", False, as_html=True)
            QMessageBox.critical(self, "Ошибка", str(e))
        # --- ИЗМЕНЕНИЕ: finally блок больше не нужен для status_label здесь, т.к. он обновляется по сигналам соединения ---

    def add_separator_if_needed(self): # (без изменений)
        if self.log_list_widget.count() > 0:
            separator_text = "\n" + ("─" * 70) + "\n" 
            sep_item = QListWidgetItem(separator_text)
            sep_item.setFlags(sep_item.flags() & ~Qt.ItemFlag.ItemIsSelectable & ~Qt.ItemFlag.ItemIsEnabled)
            font = sep_item.font()
            font.setPointSize(LOG_FONT_SIZE)
            sep_item.setFont(font)
            self.log_list_widget.addItem(sep_item)

    def add_log_message_to_gui(self, message_content, is_success, as_html=False): # (без изменений)
        self.add_separator_if_needed()
        item = QListWidgetItem() 
        self.log_list_widget.addItem(item) 
        log_display_widget = QLabel(message_content)
        label_font = log_display_widget.font()
        label_font.setPointSize(LOG_FONT_SIZE)
        log_display_widget.setFont(label_font)
        log_display_widget.setContentsMargins(10, 2, 2, 2) 
        if as_html:
            log_display_widget.setTextFormat(Qt.TextFormat.RichText)
            log_display_widget.setOpenExternalLinks(False) 
            log_display_widget.linkActivated.connect(self.on_log_link_activated)
        log_display_widget.setWordWrap(True)
        item.setSizeHint(log_display_widget.minimumSizeHint()) 
        self.log_list_widget.setItemWidget(item, log_display_widget) 
        self.log_list_widget.scrollToBottom()
    
    def load_logs_from_server(self):
        log_endpoint = f"{self.server_base_url}/get_master_log"
        # --- ИЗМЕНЕНИЕ: Удаляем начальное INFO сообщение о загрузке из списка логов ---
        # self.add_log_message_to_gui(f"INFO: Загрузка истории логов с сервера: {log_endpoint}", True, as_html=False)
        print(f"INFO: Загрузка истории логов с сервера: {log_endpoint}") # Оставим в консоли для отладки
        
        try:
            response = requests.get(log_endpoint, verify=False, timeout=15) 
            response.raise_for_status() 
            log_content = response.text
            if not log_content.strip():
                # self.add_log_message_to_gui("INFO: История логов на сервере пуста.", True, as_html=False) # <--- УДАЛЕНО
                print("INFO: История логов на сервере пуста.")
                return

            loaded_entries_count = 0
            temp_formatted_logs = []
            for line in log_content.splitlines():
                if not line.strip(): continue
                try:
                    saved_log_entry = json.loads(line)
                    event_name = saved_log_entry.get('event_name')
                    data_dict = saved_log_entry.get('log_data') 
                    if event_name and data_dict:
                        if 'serverTimestamp' not in data_dict and 'timestamp_event' in saved_log_entry:
                            data_dict['serverTimestamp'] = saved_log_entry['timestamp_event']
                        elif 'serverTimestamp' not in data_dict and 'timestamp_saved_utc' in saved_log_entry:
                             try:
                                dt_utc = datetime.datetime.fromisoformat(saved_log_entry['timestamp_saved_utc'].replace('Z','+00:00'))
                                dt_local = dt_utc.astimezone(datetime.datetime.now().astimezone().tzinfo)
                                data_dict['serverTimestamp'] = dt_local.strftime("%d.%m.%Y %H:%M:%S")
                             except:
                                data_dict['serverTimestamp'] = "Время из архива (UTC): " + saved_log_entry['timestamp_saved_utc']
                        html_to_display, is_success = self._format_log_entry(event_name, data_dict, is_archived=True)
                        if html_to_display:
                            temp_formatted_logs.append((html_to_display, is_success, True))
                            loaded_entries_count +=1
                except json.JSONDecodeError: print(f"Ошибка декодирования JSON из серверного лога: {line.strip()}")
                except Exception as e_inner: print(f"Ошибка обработки строки из серверного лога ('{line.strip()}'): {e_inner}")
            
            if loaded_entries_count > 0:
                 for msg, success, is_html_flag in temp_formatted_logs:
                    self.add_log_message_to_gui(msg, success, is_html_flag)
                 # self.add_log_message_to_gui("INFO: Загрузка истории завершена.", True, is_html=False) # <--- УДАЛЕНО
                 print("INFO: Загрузка истории завершена.")
            else:
                 # self.add_log_message_to_gui("INFO: Архив на сервере не содержит корректных записей для отображения.", True, as_html=False) # <--- УДАЛЕНО
                 print("INFO: Архив на сервере не содержит корректных записей для отображения.")
        # ... (обработка ошибок в load_logs_from_server остается) ...
        except requests.exceptions.HTTPError as http_err:
            err_msg = f"HTTP ошибка при загрузке логов: {http_err}"
            print(err_msg)
            self.add_log_message_to_gui(f"ОШИБКА СЕРВЕРА: Не удалось загрузить историю логов. {err_msg}", False, as_html=False)
        except requests.exceptions.RequestException as req_err:
            err_msg = f"Сетевая ошибка при загрузке логов: {req_err}"
            print(err_msg)
            self.add_log_message_to_gui(f"ОШИБКА СЕТИ: Не удалось загрузить историю логов. {err_msg}", False, as_html=False)
        except Exception as e:
            print(f"Критическая ошибка загрузки логов с сервера: {e}")
            self.add_log_message_to_gui(f"SYSTEM_ERROR: Не удалось загрузить историю логов: {e}", False, as_html=False)

    # --- ИЗМЕНЕНИЕ: Новый метод для контекстного меню ---
    def show_log_context_menu(self, position):
        item = self.log_list_widget.itemAt(position)
        if not item:
            return

        # Проверяем, является ли элемент разделителем (они не должны быть выбираемыми/копируемыми)
        if not (item.flags() & Qt.ItemFlag.ItemIsSelectable):
            return

        menu = QMenu()
        copy_action = QAction("Копировать выделенное", self) # QAction импортирован
        copy_action.triggered.connect(lambda: self.copy_selected_log_item(item))
        menu.addAction(copy_action)
        menu.exec(self.log_list_widget.mapToGlobal(position))

    # --- ИЗМЕНЕНИЕ: Новый метод для копирования текста ---
    def copy_selected_log_item(self, item):
        widget = self.log_list_widget.itemWidget(item)
        text_to_copy = ""
        if widget and isinstance(widget, QLabel):
            html_text = widget.text() # QLabel.text() для RichText возвращает HTML
            # Конвертируем HTML в простой текст для копирования
            doc = QTextDocument()
            doc.setHtml(html_text)
            text_to_copy = doc.toPlainText().strip()
        elif item.text(): # Для простых текстовых элементов (например, разделители, если бы они были выбираемы)
            text_to_copy = item.text().strip()
        
        if text_to_copy:
            clipboard = QApplication.clipboard() # QClipboard импортирован
            clipboard.setText(text_to_copy)
            # Статус обновлять не будем, чтобы он показывал только состояние соединения
            # self.status_label.setText("Статус: Текст скопирован в буфер обмена") 
            print("Текст скопирован в буфер обмена.")


    def closeEvent(self, event): # (без изменений)
        print("MainWindow: Получено событие закрытия окна.")
        self.socket_io_thread.stop()
        event.accept()

if __name__ == "__main__": # (без изменений)
    SERVER_IP = "10.42.0.1"
    SERVER_PORT = 5000
    USE_SSL = True
    server_url_main = f"{'https' if USE_SSL else 'http'}://{SERVER_IP}:{SERVER_PORT}"

    app = QApplication(sys.argv)
    main_app_window = MainWindow(server_url_main)
    main_app_window.show()
    sys.exit(app.exec())
