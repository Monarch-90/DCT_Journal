# /config.py

import os
from base64 import b64decode

class Config:
    """
    Класс для хранения всех конфигурационных переменных приложения.
    """
    # --- КЛЮЧИ ШИФРОВАНИЯ ---
    FERNET_KEY = b"wFVzjaJd2H9MlwEsAXxxZ1vUDIK5rfRpOsOgczPvtsY="
    AES_SECRET_KEY = b64decode("dGhpc2lzbXlzZWNyZXRrZXlmb3JhcGVz")

    # --- ПАРАМЕТРЫ ПОДКЛЮЧЕНИЯ К БД ---
    DB_NAME = "user_data"
    DB_USER = "postgres"
    DB_PASSWORD = "12345"
    DB_HOST = "localhost"
    DB_PORT = "5432"

    # --- КОНСТАНТЫ ПРИЛОЖЕНИЯ ---
    ADMIN_BARCODE_PREFIX = "ADM-SWH-"
    ADMIN_BARCODE = "20ADM-SWH-"
    MASKED_ADMIN_BARCODE = "Администратор"
    FIO_CSV_FILE = "WMSMANAGER.txt"
    # НОВОЕ: Шаблон для имени файла логов сессий
    SESSION_LOG_FILENAME_TEMPLATE = "master_session_log_{year}.jsonl"

    # --- ПАРАМЕТРЫ ЗАПУСКА СЕРВЕРА ---
    SERVER_HOST = "0.0.0.0"
    SERVER_PORT = 5000
    CERT_FILE = "cert.pem"
    KEY_FILE = "key.pem"
