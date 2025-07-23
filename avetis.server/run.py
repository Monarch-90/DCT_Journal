# /run.py

# --- НАЧАЛО КЛЮЧЕВОГО ИСПРАВЛЕНИЯ ---
# 1. "Патчим" стандартные библиотеки ДО импорта всего остального.
from gevent import monkey
monkey.patch_all()

# 2. Настраиваем логирование в самом начале, чтобы его никто не переопределил.
import logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - [%(name)s] - %(message)s') # 👈 Удалить

import os
from app import create_app, socketio

# Теперь create_app будет использовать уже настроенный логгер
app = create_app()

if __name__ == "__main__":
    config = app.config
    ssl_args = {}
    
    if os.path.exists(config['CERT_FILE']) and os.path.exists(config['KEY_FILE']):
        logging.info(f"Найдены файлы SSL: {config['CERT_FILE']}, {config['KEY_FILE']}. Запуск с HTTPS.")
        ssl_args['certfile'] = config['CERT_FILE']
        ssl_args['keyfile'] = config['KEY_FILE']
    else:
        logging.warning(f"Файлы SSL сертификата не найдены! Сервер SocketIO будет запущен по HTTP.")

    try:
        logging.info(f"Запуск Flask-SocketIO сервера в режиме 'eventlet'...")
        socketio.run(
            app,
            host=config['SERVER_HOST'],
            port=config['SERVER_PORT'],
            debug=False,
            use_reloader=False,
            **ssl_args
        )
    except Exception as run_err:
        logging.critical(f"Критическая ошибка запуска Flask-SocketIO сервера: {run_err}", exc_info=True)

