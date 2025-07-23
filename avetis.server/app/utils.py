# /app/utils.py

import os
import json
import datetime
import logging
import csv
import psycopg2
import fcntl
from base64 import b64decode, b64encode
from Crypto.Cipher import AES
from Crypto.Util.Padding import pad, unpad
from cryptography.fernet import Fernet
from flask import current_app, jsonify

from . import socketio

# "Пустые" переменные, которые будут инициализированы в create_app
fernet_cipher = None
fio_manager = None
log_file_lock = None


class FIOManager:
    def __init__(self, filepath):
        self.filepath = filepath
        self.employee_fio_map = {}
        self.last_mod_time = 0
        self._load_data()

    def _load_data(self):
        if not os.path.exists(self.filepath):
            logging.warning(f"Файл с ФИО '{self.filepath}' не найден. Карта ФИО будет пустой.")
            self.employee_fio_map = {}
            return
        try:
            current_mod_time = os.path.getmtime(self.filepath)
            if current_mod_time == self.last_mod_time:
                return
            logging.info(f"Обнаружено изменение в файле '{self.filepath}'. Перезагрузка данных ФИО...")
            temp_map = {}
            with open(self.filepath, mode='r', encoding='cp1251', newline='') as csvfile:
                reader = csv.reader(csvfile, delimiter=';')
                try:
                    next(reader, None)
                except StopIteration:
                    logging.warning(f"Файл '{self.filepath}' пуст.")
                    return
                for i, row in enumerate(reader, 1):
                    if not row: continue
                    if len(row) >= 3:
                        login = row[1].strip()
                        fio = row[2].strip()
                        if login:
                            temp_map[login] = fio
                    else:
                        logging.warning(f"Неверный формат строки {i+1} в файле '{self.filepath}': недостаточно столбцов.")
            self.employee_fio_map = temp_map
            self.last_mod_time = current_mod_time
            logging.info(f"Данные ФИО успешно загружены. Записей: {len(self.employee_fio_map)}.")
        except Exception as e:
            logging.error(f"КРИТИЧЕСКАЯ ОШИБКА при чтении файла ФИО '{self.filepath}': {e}", exc_info=True)

    def get_fio(self, login):
        if not login:
            return None
        self._load_data()
        return self.employee_fio_map.get(login)

# --- НОВАЯ ЛОГИКА СМЕН ---

def get_shift_info(timestamp):
    """
    Определяет информацию о смене для заданной временной метки.
    ВАЖНО: timestamp должен быть 'aware' (с часовым поясом).
    """
    # Убедимся, что на входе aware-объект
    if timestamp.tzinfo is None:
        # Если пришел naive, считаем его локальным и делаем aware
        timestamp = timestamp.astimezone()

    day_shift_start_hour, day_shift_start_minute = 7, 30
    night_shift_start_hour, night_shift_start_minute = 19, 30
    day_reg_open_hour = 6
    night_reg_open_hour = 18

    today_at_midnight = timestamp.replace(hour=0, minute=0, second=0, microsecond=0)

    day_shift_start = today_at_midnight.replace(hour=day_shift_start_hour, minute=day_shift_start_minute)
    night_shift_start = today_at_midnight.replace(hour=night_shift_start_hour, minute=night_shift_start_minute)
    
    day_reg_open = today_at_midnight.replace(hour=day_reg_open_hour)
    night_reg_open = today_at_midnight.replace(hour=night_reg_open_hour)

    if day_reg_open <= timestamp < day_shift_start:
        return {"type": "Дневная смена", "start_time": day_shift_start}
    elif day_shift_start <= timestamp < night_reg_open:
        return {"type": "Дневная смена", "start_time": day_shift_start}
    elif night_reg_open <= timestamp < night_shift_start:
        return {"type": "Ночная смена", "start_time": night_shift_start}
    elif timestamp >= night_shift_start:
        return {"type": "Ночная смена", "start_time": night_shift_start}
    else: # timestamp < day_reg_open (раннее утро, относится к прошлой ночной смене)
        yesterday_night_shift_start = (today_at_midnight - datetime.timedelta(days=1)).replace(hour=night_shift_start_hour, minute=night_shift_start_minute)
        return {"type": "Ночная смена", "start_time": yesterday_night_shift_start}


def get_current_log_filename():
    """Возвращает имя файла лога для текущего года."""
    current_year = datetime.datetime.now().year
    return current_app.config['SESSION_LOG_FILENAME_TEMPLATE'].format(year=current_year)


def update_session_log(session_data_to_update):
    """
    Находит и обновляет сессию в файле лога или создает новую.
    Финальная, максимально надежная версия с подробным логированием.
    """
    log_file = get_current_log_filename()
    session_id = session_data_to_update["session_id"]
    
    logging.info(f"Начало обновления сессии {session_id} в файле {log_file}")

    lines_to_write = []
    found = False
    
    try:
        # --- Чтение ---
        if os.path.exists(log_file) and os.path.getsize(log_file) > 0:
            with open(log_file, "r", encoding="utf-8") as f:
                current_lines = f.readlines()
        else:
            current_lines = []
            logging.info(f"Файл лога {log_file} не существует или пуст. Будет создан новый.")

        # --- Обработка в памяти ---
        for line in current_lines:
            if not line.strip(): continue # Пропускаем пустые строки
            
            try:
                entry = json.loads(line)
                if entry.get("session_id") == session_id:
                    logging.info(f"Найдена существующая сессия {session_id}. Добавление событий.")
                    entry["events"].extend(session_data_to_update["events"])
                    entry["last_updated"] = session_data_to_update["last_updated"]
                    lines_to_write.append(json.dumps(entry, ensure_ascii=False) + "\n")
                    found = True
                else:
                    lines_to_write.append(line)
            except json.JSONDecodeError:
                logging.warning(f"Пропуск поврежденной строки в логе: {line.strip()}")
                lines_to_write.append(line)

        if not found:
            logging.info(f"Сессия {session_id} не найдена. Создание новой записи.")
            lines_to_write.append(json.dumps(session_data_to_update, ensure_ascii=False) + "\n")

        # --- Запись ---
        with open(log_file, "w", encoding="utf-8") as f:
            f.writelines(lines_to_write)
        
        logging.info(f"Сессия {session_id} успешно записана в {log_file}.")

    except Exception as e:
        logging.critical(f"КРИТИЧЕСКАЯ ОШИБКА при обновлении лога сессий: {e}", exc_info=True)
        logging.error(f"ДАННЫЕ, КОТОРЫЕ НЕ УДАЛОСЬ ЗАПИСАТЬ: {session_data_to_update}")


def log_system_event(event_data):
    """Записывает системное событие в лог-файл."""
    log_file = get_current_log_filename()
    try:
        log_entry = {
            "event_type": "system_event",
            "timestamp_utc": datetime.datetime.utcnow().isoformat() + "Z",
            "data": event_data
        }
        with open(log_file, "a", encoding="utf-8") as f:
            f.write(json.dumps(log_entry, ensure_ascii=False) + "\n")
    except Exception as e:
        logging.error(f"КРИТИЧЕСКАЯ ОШИБКА: Не удалось записать системное событие в лог ({log_file}): {e}", exc_info=True)


def emit_event(event_name, data):
    """Просто отправляет событие через SocketIO без логирования в файл."""
    socketio.emit(event_name, data)


def get_db_connection():
    try:
        conn = psycopg2.connect(
            dbname=current_app.config['DB_NAME'],
            user=current_app.config['DB_USER'],
            password=current_app.config['DB_PASSWORD'],
            host=current_app.config['DB_HOST'],
            port=current_app.config['DB_PORT']
        )
        return conn
    except psycopg2.OperationalError as e:
        logging.error(f"Не удалось подключиться к БД: {e}")
        raise


def decrypt_aes(encrypted_data, iv_b64):
    try:
        iv = b64decode(iv_b64)
        secret_key = current_app.config['AES_SECRET_KEY']
        cipher_obj = AES.new(secret_key, AES.MODE_CBC, iv)
        decrypted_padded = cipher_obj.decrypt(b64decode(encrypted_data))
        decrypted_data = unpad(decrypted_padded, AES.block_size)
        return decrypted_data.decode('utf-8')
    except Exception as e:
        logging.error(f"Ошибка при расшифровке AES: {str(e)}", exc_info=False)
        return None

def encrypt_aes(data_to_encrypt_str, iv_bytes):
    try:
        secret_key = current_app.config['AES_SECRET_KEY']
        cipher_obj = AES.new(secret_key, AES.MODE_CBC, iv_bytes)
        padded_data = pad(data_to_encrypt_str.encode('utf-8'), AES.block_size)
        encrypted_data = cipher_obj.encrypt(padded_data)
        return b64encode(encrypted_data).decode('utf-8')
    except Exception as e:
        logging.error(f"Ошибка при шифровании AES: {str(e)}", exc_info=False)
        return None

def is_admin_user(login_without_prefix):
    return login_without_prefix.startswith(current_app.config['ADMIN_BARCODE_PREFIX'])

def send_tsd_error_response(status_code_http, error_status_key, error_message_text, server_timestamp_str, user_login=None, device_identifier_display=None):
    response_payload_tsd = {"status": error_status_key, "message": error_message_text, "serverTimestamp": server_timestamp_str}
    if user_login:
        response_payload_tsd["userLogin"] = user_login
    if device_identifier_display:
        response_payload_tsd["deviceIdentifier"] = device_identifier_display
    
    server_iv_bytes = os.urandom(16)
    server_iv_b64 = b64encode(server_iv_bytes).decode('utf-8')
    encrypted_response_content = encrypt_aes(json.dumps(response_payload_tsd), server_iv_bytes)
    
    if encrypted_response_content is None:
        return jsonify({"success": False}), 500
    return jsonify({"success": False, "message": encrypted_response_content, "iv": server_iv_b64}), status_code_http
