# /app/__init__.py

import logging
from flask import Flask
from flask_cors import CORS
from flask_socketio import SocketIO
from config import Config
from cryptography.fernet import Fernet

# Создаем экземпляры расширений БЕЗ привязки к приложению
cors = CORS()
socketio = SocketIO(cors_allowed_origins="*")

# ИЗМЕНЕНО: Импортируем сам модуль utils, а не объекты из него
from . import utils

def create_app(config_class=Config):
    """
    Фабрика для создания экземпляра приложения Flask.
    """
    # Настраиваем базовое логирование
    logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')

    # Создаем и конфигурируем приложение
    app = Flask(__name__)
    app.config.from_object(config_class)

    # Инициализируем расширения с помощью созданного приложения
    cors.init_app(app)
    socketio.init_app(app)

    # --- ИЗМЕНЕНИЕ: ИНИЦИАЛИЗИРУЕМ ОБЪЕКТЫ ВНУТРИ КОНТЕКСТА ПРИЛОЖЕНИЯ ---
    with app.app_context():
        try:
            # Заполняем "пустые" переменные в модуле utils
            utils.fernet_cipher = Fernet(app.config['FERNET_KEY'])
            utils.fio_manager = utils.FIOManager(app.config['FIO_CSV_FILE'])
            logging.info("Вспомогательные утилиты (Fernet, FIOManager) успешно инициализированы.")
        except Exception as e:
            logging.critical(f"КРИТИЧЕСКАЯ ОШИБКА при инициализации утилит: {e}", exc_info=True)


    # --- РЕГИСТРАЦИЯ BLUEPRINTS ---
    # Импортируем Blueprint здесь, чтобы избежать циклических импортов
    from .routes import main_bp
    app.register_blueprint(main_bp)

    logging.info("Приложение Flask успешно создано и сконфигурировано.")
    
    return app
