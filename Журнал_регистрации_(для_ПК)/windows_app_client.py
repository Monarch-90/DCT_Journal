import socketio
import time

# --- НАСТРОЙКИ КЛИЕНТА ---
SERVER_IP = "10.42.0.1"
SERVER_PORT = 5000
USE_SSL = True

server_url = f"{'https' if USE_SSL else 'http'}://{SERVER_IP}:{SERVER_PORT}"
# Итоговый URL будет: https://10.42.0.1:5000
# --------------------------------------------------------------------

# Создаем экземпляр клиента Socket.IO
# logger и engineio_logger полезны для отладки

# Пытаемся передать ssl_verify напрямую, если engineio_options вызывает TypeError
try:
    sio = socketio.Client(logger=True, engineio_logger=True, ssl_verify=False)
except TypeError as e:
    # Если и это не сработало, возможно, версия очень старая или имеет другую структуру.
    # В этом случае можно попробовать без опций SSL на клиенте, если сервер это позволит
    # (маловероятно при HTTPS с самоподписанным сертификатом).
    print(f"Ошибка инициализации клиента с ssl_verify: {e}")
    print("Пробуем инициализировать клиент без явных SSL опций (может не сработать для самоподписанного HTTPS)")
    sio = socketio.Client(logger=True, engineio_logger=True)


@sio.event
def connect():
    print(f"Успешно подключились к серверу: {server_url}")

@sio.event
def connect_error(data):
    print(f"Ошибка подключения к {server_url}!")
    error_message = str(data) if data else "Нет дополнительных данных об ошибке."
    if isinstance(data, dict) and 'message' in data:
        error_message = data['message']
    elif hasattr(data, 'strerror'):
        error_message = data.strerror
    print(f"Детали ошибки: {error_message}")
    print("Проверьте, что сервер запущен, доступен по указанному IP и порту, и что брандмауэр не блокирует соединение.")
    if USE_SSL:
        print("Если используете HTTPS, убедитесь, что на сервере корректно настроен SSL.")

@sio.event
def disconnect():
    print(f"Отключились от сервера: {server_url}")

@sio.on('new_log_entry')
def on_new_log_entry(data):
    print("\n--- Получен новый лог (new_log_entry) ---")
    print(data)
    print("----------------------------------------\n")

@sio.on('deregistration_log')
def on_deregistration_log(data):
    print("\n--- Получен лог дерегистрации (deregistration_log) ---")
    print(data)
    print("-----------------------------------------------------\n")

@sio.on('device_registration_attempt')
def on_device_registration_attempt(data):
    print("\n--- Получен лог попытки регистрации ТСД (device_registration_attempt) ---")
    print(data)
    print("---------------------------------------------------------------------------\n")

if __name__ == '__main__':
    print(f"Клиент пытается подключиться к: {server_url}")
    try:
        sio.connect(server_url, transports=['websocket'])
        sio.wait()
    except socketio.exceptions.ConnectionError as e:
        print(f"Не удалось подключиться (socketio.exceptions.ConnectionError): {e}")
    except Exception as e:
        error_type = type(e).__name__
        error_details = str(e)
        print(f"Произошла неожиданная ошибка в клиенте: {error_type} - {error_details}")
    finally:
        if hasattr(sio, 'connected') and sio.connected: # Проверка, что sio был успешно инициализирован
            print("Отключаемся от сервера...")
            sio.disconnect()
        print("Клиент завершил работу.")