# /app/utils.py

import os
import json
import datetime
import logging
import csv
import psycopg2
from base64 import b64decode, b64encode
from Crypto.Cipher import AES
from Crypto.Util.Padding import pad, unpad
from cryptography.fernet import Fernet
from flask import current_app, jsonify

# ИЗМЕНЕНО: SocketIO импортируется из __init__.py нашего пакета 'app'
from . import socketio

# --- ИЗМЕНЕНИЕ: УБИРАЕМ ИНИЦИАЛИЗАЦИЮ ОТСЮДА ---
# Оставляем "пустые" переменные, которые будут инициализированы в create_app
fernet_cipher = None
fio_manager = None

# --- МЕНЕДЖЕР ФИО (Класс остается без изменений) ---
class FIOManager:
    # ... (Здесь ваш код класса FIOManager без изменений) ...
    # Копипаст вашего класса FIOManager сюда целиком
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

# --- Функции остаются прежними, они будут использовать инициализированные объекты ---

def get_current_log_filename():
    # ... (код без изменений) ...
    current_year = datetime.datetime.now().year
    return f"master_event_log_{current_year}.jsonl"

def append_to_master_log(event_name, data_dict):
    # ... (код без изменений) ...
    log_file = get_current_log_filename()
    try:
        log_entry = {
            "timestamp_event": data_dict.get("serverTimestamp", datetime.datetime.now().strftime("%d.%m.%Y %H:%M")),
            "timestamp_logged_utc": datetime.datetime.utcnow().isoformat() + "Z",
            "event_name": event_name,
            "log_data": dict(data_dict)
        }
        with open(log_file, "a", encoding="utf-8") as f:
            f.write(json.dumps(log_entry, ensure_ascii=False) + "\n")
    except Exception as e:
        logging.error(f"КРИТИЧЕСКАЯ ОШИБКА: Не удалось записать в главный лог ({log_file}): {e}", exc_info=True)

def emit_and_log(event_name_socketio, data_to_emit_and_log, event_name_for_log_file=None):
    # ... (код без изменений) ...
    socketio.emit(event_name_socketio, data_to_emit_and_log)
    log_event_name = event_name_for_log_file if event_name_for_log_file else event_name_socketio
    append_to_master_log(log_event_name, data_to_emit_and_log)

def get_db_connection():
    # ... (код без изменений) ...
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
    # ... (код без изменений) ...
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
    # ... (код без изменений) ...
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
    # ... (код без изменений) ...
    return login_without_prefix.startswith(current_app.config['ADMIN_BARCODE_PREFIX'])

def send_tsd_error_response(status_code_http, error_status_key, error_message_text, server_timestamp_str, user_login=None, device_identifier_display=None):
    # ... (код без изменений) ...
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
