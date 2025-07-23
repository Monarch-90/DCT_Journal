import http.server
import os
import socketserver

# --- Настройки ---
HOST = "0.0.0.0"
PORT = 8000
# -----------------

# Создаем обработчик запросов
handler = http.server.SimpleHTTPRequestHandler

# Создаем ОБЫЧНЫЙ HTTP-сервер
httpd = http.server.HTTPServer((HOST, PORT), handler)

# --- Вся логика, связанная с HTTPS, теперь удалена ---

print(f"Запущен ОБЫЧНЫЙ веб-сервер для клиента по адресу: http://{HOST}:{PORT}")
print("Откройте этот адрес в браузере.")
httpd.serve_forever()

