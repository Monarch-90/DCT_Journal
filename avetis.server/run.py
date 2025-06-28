# /run.py

import os
import logging
from app import create_app, socketio

# Создаем приложение с помощью нашей фабрики
app = create_app()

if __name__ == "__main__":
    # Копируем ваш блок запуска отсюда
    config = app.config
    ssl_args = {}
    
    if os.path.exists(config['CERT_FILE']) and os.path.exists(config['KEY_FILE']):
        logging.info(f"Найдены файлы SSL: {config['CERT_FILE']}, {config['KEY_FILE']}. Запуск с HTTPS.")
        ssl_args['certfile'] = config['CERT_FILE']
        ssl_args['keyfile'] = config['KEY_FILE']
    else:
        logging.warning(f"Файлы SSL сертификата не найдены! Сервер SocketIO будет запущен по HTTP.")

    try:
        logging.info("Запуск Flask-SocketIO сервера...")
        socketio.run(
            app,
            host=config['SERVER_HOST'],
            port=config['SERVER_PORT'],
            debug=False,
            use_reloader=False, # Важно для продакшена
            **ssl_args
        )
    except Exception as run_err:
        logging.critical(f"Критическая ошибка запуска Flask-SocketIO сервера: {run_err}", exc_info=True)
