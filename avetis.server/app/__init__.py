# /app/__init__.py

import logging
from flask import Flask
from flask_cors import CORS
from flask_socketio import SocketIO
from config import Config
from cryptography.fernet import Fernet
import fcntl # Для блокировки файлов

# Создаем экземпляры расширений БЕЗ привязки к приложению
cors = CORS()
socketio = SocketIO(
    cors_allowed_origins="*",
    ping_interval=200,
    ping_timeout=10,
    async_mode='gevent'
)

# Импортируем сам модуль utils, а не объекты из него
from . import utils

def create_app(config_class=Config):
    """
    Фабрика для создания экземпляра приложения Flask.
    """
    # --- НАЧАЛО ИЗМЕНЕНИЯ ---
    # Убираем настройку логирования отсюда, так как она переехала в run.py
    # logging.basicConfig(...) - УДАЛЕНО
    # logging.getLogger(...) - УДАЛЕНО
    # --- КОНЕЦ ИЗМЕНЕНИЯ ---

    app = Flask(__name__)
    app.config.from_object(config_class)

    # Инициализируем расширения с приложением
    cors.init_app(app)
    socketio.init_app(app)

    # Инициализируем вспомогательные утилиты в контексте приложения
    with app.app_context():
        try:
            utils.fernet_cipher = Fernet(app.config['FERNET_KEY'])
            utils.fio_manager = utils.FIOManager(app.config['FIO_CSV_FILE'])
            logging.info("Вспомогательные утилиты (Fernet, FIOManager) успешно инициализированы.")
        except Exception as e:
            logging.critical(f"КРИТИЧЕСКАЯ ОШИБКА при инициализации утилит: {e}", exc_info=True)

    # Регистрируем основной Blueprint для логов
    from .routes import main_bp
    app.register_blueprint(main_bp)

    # Регистрируем новый Blueprint для управления ТСД
    from .devices import devices_bp
    app.register_blueprint(devices_bp)

    logging.info("Приложение Flask успешно создано и сконфигурировано со всеми маршрутами.")
    
    return app

