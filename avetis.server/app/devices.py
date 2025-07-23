# /app/devices.py

import logging
import datetime
import os
from flask import request, jsonify, Blueprint, send_from_directory, current_app
from psycopg2.extras import RealDictCursor
from . import socketio
from .utils import get_db_connection

devices_bp = Blueprint('devices', __name__)
connected_devices = {}

# --- НАЧАЛО ИЗМЕНЕНИЙ: Новый маршрут для раздачи файлов ---
@devices_bp.route('/updates/<path:filename>')
def download_update_file(filename):
    """
    Отдает запрошенный файл из папки 'updates', находящейся в корне проекта.
    """
    # Определяем путь к папке 'updates' относительно папки 'app'
    updates_dir = os.path.join(current_app.root_path, '..', 'updates')
    logging.info(f"Попытка отдать файл: {filename} из директории: {updates_dir}")
    try:
        return send_from_directory(updates_dir, filename, as_attachment=True)
    except FileNotFoundError:
        logging.error(f"Файл обновления не найден: {filename}")
        return jsonify({"error": "File not found"}), 404
# --- КОНЕЦ ИЗМЕНЕНИЙ ---


@socketio.on('connect')
def handle_connect():
    android_id = request.args.get('deviceId')
    session_id = request.sid

    if android_id:
        connected_devices[android_id] = session_id
        logging.info(f"ТСД подключен: AndroidID='{android_id}', SessionID='{session_id}'")
        try:
            conn = get_db_connection()
            with conn.cursor() as cursor:
                cursor.execute(
                    "UPDATE devices SET status = %s, last_seen_at = %s WHERE android_id = %s",
                    ('online', datetime.datetime.now(datetime.timezone.utc), android_id)
                )
                conn.commit()
            conn.close()
            socketio.emit('device_status_changed', {'androidId': android_id, 'status': 'online'})
        except Exception as e:
            logging.error(f"Ошибка БД при обновлении статуса 'online' для {android_id}: {e}")
    else:
        logging.info(f"Веб-клиент подключен: SessionID='{session_id}'")

@socketio.on('disconnect')
def handle_disconnect():
    session_id = request.sid
    disconnected_android_id = next((aid for aid, sid in connected_devices.items() if sid == session_id), None)

    if disconnected_android_id:
        del connected_devices[disconnected_android_id]
        logging.warning(f"ТСД отключен: AndroidID='{disconnected_android_id}'")
        try:
            conn = get_db_connection()
            with conn.cursor() as cursor:
                cursor.execute(
                    "UPDATE devices SET status = %s, last_seen_at = %s WHERE android_id = %s",
                    ('offline', datetime.datetime.now(datetime.timezone.utc), disconnected_android_id)
                )
                conn.commit()
            conn.close()
            socketio.emit('device_status_changed', {'androidId': disconnected_android_id, 'status': 'offline'})
        except Exception as e:
            logging.error(f"Ошибка БД при обновлении статуса 'offline' для {disconnected_android_id}: {e}")
    else:
        logging.info(f"Веб-клиент отключен: SessionID='{session_id}'")

@devices_bp.route('/api/v1/devices', methods=['GET'])
def get_all_devices():
    try:
        conn = get_db_connection()
        with conn.cursor(cursor_factory=RealDictCursor) as cursor:
            cursor.execute("SELECT * FROM devices ORDER BY order_number NULLS LAST, order_number ASC")
            devices = cursor.fetchall()
        conn.close()
        return jsonify(devices)
    except Exception as e:
        logging.error(f"Ошибка получения списка ТСД: {e}", exc_info=True)
        return jsonify({"error": "Ошибка сервера при получении списка устройств"}), 500

@devices_bp.route('/api/v1/terminal/status', methods=['POST'])
def receive_terminal_status():
    data = request.get_json()
    android_id = data.get('deviceId')
    if not android_id:
        return jsonify({"success": False, "message": "deviceId is required"}), 400

    logging.info(f"Получен статус от ТСД {android_id}: {data}")
    try:
        conn = get_db_connection()
        with conn.cursor() as cursor:
            cursor.execute(
                """
                UPDATE devices SET
                    android_version = %(android_version)s,
                    app_version = %(app_version)s,
                    wms_version = %(wms_version)s,
                    battery_level = %(battery_level)s,
                    last_seen_at = %(last_seen_at)s
                WHERE android_id = %(android_id)s
                """,
                {
                    "android_version": data.get('androidVersion'),
                    "app_version": data.get('appVersion'),
                    "wms_version": data.get('wmsVersion'),
                    "battery_level": data.get('batteryLevel'),
                    "last_seen_at": datetime.datetime.now(datetime.timezone.utc),
                    "android_id": android_id
                }
            )
            conn.commit()
        conn.close()
        socketio.emit('device_data_updated', data)
        return jsonify({"success": True}), 200
    except Exception as e:
        logging.error(f"Ошибка обновления статуса ТСД {android_id} в БД: {e}", exc_info=True)
        return jsonify({"success": False, "message": "Database error"}), 500

@devices_bp.route('/api/v1/devices/command/request_status_all', methods=['POST'])
def command_request_status_all():
    if not connected_devices:
        logging.info("Нет онлайн ТСД для запроса статуса.")
        return jsonify({"success": True, "message": "Нет устройств в сети для опроса."})

    online_device_count = len(connected_devices)
    logging.info(f"Отправка команды 'get_status' на {online_device_count} устройств(о).")
    
    for sid in connected_devices.values():
        socketio.emit('command:get_status', room=sid)

    return jsonify({"success": True, "message": f"Команда запроса статуса отправлена на {online_device_count} устройств."})

@devices_bp.route('/api/v1/devices/<string:android_id>/command/update', methods=['POST'])
def command_update_app(android_id):
    data = request.get_json()
    apk_url = data.get('apkUrl')
    if not apk_url:
        return jsonify({"error": "apkUrl is required"}), 400

    target_sid = connected_devices.get(android_id)
    if target_sid:
        socketio.emit('command:update_app', {'apkUrl': apk_url}, to=target_sid)
        logging.info(f"Отправлена команда 'update_app' на ТСД {android_id} с URL: {apk_url}")
        return jsonify({"success": True, "message": "Команда на обновление отправлена."})
    else:
        logging.warning(f"Попытка отправить команду на оффлайн ТСД {android_id}")
        return jsonify({"success": False, "message": "ТСД оффлайн. Невозможно отправить команду."}), 404

@devices_bp.route('/api/v1/devices/<string:android_id>', methods=['PUT'])
def update_device_info(android_id):
    data = request.get_json()
    work_status = data.get('work_status')
    repair_cost_str = data.get('total_repair_cost')

    if work_status is None and repair_cost_str is None:
        return jsonify({"error": "Не предоставлено данных для обновления"}), 400
    
    try:
        conn = get_db_connection()
        with conn.cursor() as cursor:
            if work_status is not None:
                cursor.execute("UPDATE devices SET work_status = %s WHERE android_id = %s", (work_status, android_id))
            if repair_cost_str is not None:
                repair_cost = float(repair_cost_str)
                cursor.execute("UPDATE devices SET total_repair_cost = %s WHERE android_id = %s", (repair_cost, android_id))
            conn.commit()
        conn.close()
        
        socketio.emit('device_data_updated', {'deviceId': android_id, **data})
        logging.info(f"Обновлены данные для ТСД {android_id} из веб-интерфейса: {data}")
        return jsonify({"success": True, "message": "Данные ТСД обновлены"})
    except (ValueError, TypeError):
        return jsonify({"error": "total_repair_cost должен быть числом"}), 400
    except Exception as e:
        logging.error(f"Ошибка обновления данных ТСД {android_id} из веба: {e}", exc_info=True)
        return jsonify({"error": "Ошибка БД"}), 500

