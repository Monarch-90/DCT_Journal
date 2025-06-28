import http.server
import ssl
import os
import socketserver # Добавим для ясности, хотя HTTPServer его использует под капотом

# --- Настройки ---
HOST = "0.0.0.0"
PORT = 8000
CERT_FILE = "cert.pem" 
KEY_FILE = "key.pem"
# -----------------

if not os.path.exists(CERT_FILE) or not os.path.exists(KEY_FILE):
    print(f"Ошибка: Не найдены файлы сертификатов '{CERT_FILE}' и '{KEY_FILE}'")
    print("Пожалуйста, скопируйте их в папку с этим скриптом.")
    exit()

# Создаем обработчик запросов
handler = http.server.SimpleHTTPRequestHandler

# Создаем контекст SSL/TLS
context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
context.load_cert_chain(certfile=CERT_FILE, keyfile=KEY_FILE)

# Создаем HTTPS-сервер (ИСПРАВЛЕНО)
httpd = http.server.HTTPServer((HOST, PORT), handler)

# "Оборачиваем" сокет сервера, используя созданный контекст
httpd.socket = context.wrap_socket(httpd.socket, server_side=True)

print(f"Запущен безопасный веб-сервер для клиента по адресу: https://{HOST}:{PORT}")
print("Откройте этот адрес в браузере.")
httpd.serve_forever()
