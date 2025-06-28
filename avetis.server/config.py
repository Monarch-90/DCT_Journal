# /config.py

import os
from base64 import b64decode

class Config:
    """
    Класс для хранения всех конфигурационных переменных приложения.
    """
    # --- КЛЮЧИ ШИФРОВАНИЯ ---
    # ВАЖНО: В реальном проекте эти ключи лучше хранить в переменных окружения,
    # а не прямо в коде.
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
    ADMIN_BARCODE = "20ADM-SWH-" # Это, кажется, не используется, но оставлю на всякий случай
    MASKED_ADMIN_BARCODE = "Администратор"
    FIO_CSV_FILE = "WMSMANAGER.txt"

    # --- ПАРАМЕТРЫ ЗАПУСКА СЕРВЕРА ---
    SERVER_HOST = "0.0.0.0"
    SERVER_PORT = 5000
    CERT_FILE = "cert.pem"
    KEY_FILE = "key.pem"
