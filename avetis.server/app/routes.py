# /app/routes.py

import datetime
import json
import logging
import os
import psycopg2
from psycopg2.extras import RealDictCursor
from flask import request, jsonify, Blueprint, current_app
from base64 import b64encode

from .utils import (
    get_db_connection, decrypt_aes, encrypt_aes, is_admin_user,
    send_tsd_error_response, fio_manager, get_shift_info,
    update_session_log, log_system_event, emit_event
)

main_bp = Blueprint('main', __name__)

# --- Маршруты без изменений ---

@main_bp.route("/")
def home():
    return "Сервер Flask с SocketIO для Журнала Регистраций v2.2 (FIXED) работает!"


@main_bp.route("/scan", methods=["POST"])
def scan_barcode():
    request_time = datetime.datetime.now().astimezone()
    formatted_request_time = request_time.strftime("%d.%m.%Y %H:%M")
    data = request.get_json()
    android_id_from_request = data.get("androidId") if data else None

    if not data or not all([data.get("barcode"), data.get("iv"), android_id_from_request]):
        logging.warning("/scan: Отсутствуют данные или обязательные параметры.")
        return send_tsd_error_response(400, "missing_params", "Отсутствуют параметры", formatted_request_time)

    barcode_from_tsd = decrypt_aes(data["barcode"], data["iv"])
    if barcode_from_tsd is None:
        return send_tsd_error_response(500, "decrypt_error", "Ошибка расшифровки ШК", formatted_request_time)

    logging.info(f"/scan: Принят. AndroidID='{android_id_from_request}', Расш.ШК='{barcode_from_tsd}'")

    if not barcode_from_tsd.startswith("20"):
        return send_tsd_error_response(200, "user_invalid_prefix", "Неверный формат ШК", formatted_request_time, user_login=barcode_from_tsd)

    user_login_from_scan = barcode_from_tsd[2:]
    user_full_name = fio_manager.get_fio(user_login_from_scan)
    is_admin_scan = is_admin_user(user_login_from_scan)

    connection = None
    try:
        connection = get_db_connection()
        cursor = connection.cursor(cursor_factory=RealDictCursor)

        if not is_admin_scan:
            cursor.execute("SELECT order_number FROM devices WHERE current_user_login = %s AND android_id != %s", (user_login_from_scan, android_id_from_request))
            active_tsd = cursor.fetchone()
            if active_tsd:
                error_msg = f"Вы не дерегистрировали ТСД №{active_tsd['order_number']}"
                logging.warning(f"/scan: Пользователь '{user_login_from_scan}' уже активен на ТСД №{active_tsd['order_number']}.")
                return send_tsd_error_response(200, "user_locked_elsewhere", error_msg, formatted_request_time, user_login=user_login_from_scan)

        cursor.execute("SELECT * FROM devices WHERE android_id = %s", (android_id_from_request,))
        tsd_info = cursor.fetchone()
        
        device_order_number = tsd_info.get("order_number") if tsd_info else None
        device_identifier_display = device_order_number or android_id_from_request

        if tsd_info and tsd_info.get("current_user_login") and tsd_info["current_user_login"] != user_login_from_scan and not is_admin_scan:
            msg = f"ТСД №{device_order_number} уже используется"
            return send_tsd_error_response(200, "device_occupied", msg, formatted_request_time, user_login=user_login_from_scan, device_identifier_display=device_identifier_display)

        cursor.execute(
            "UPDATE devices SET current_user_login = %s, last_login_timestamp = %s WHERE android_id = %s",
            (user_login_from_scan, request_time, android_id_from_request)
        )
        connection.commit()

        shift_info = get_shift_info(request_time)
        session_id = f"{user_login_from_scan}-{shift_info['start_time'].isoformat()}"
        
        event_status = "ok_scan_unregistered_device" if not device_order_number else "ok_scan_registered_device"
        
        session_event = {
            "type": "registration",
            "timestamp": request_time.isoformat(),
            "status": event_status,
            "device_androidId": android_id_from_request,
            "device_orderNumber": device_order_number
        }
        
        session_data = {
            "event_type": "user_session", "session_id": session_id,
            "shift_start": shift_info['start_time'].isoformat(), "shift_type": shift_info['type'],
            "user_login": user_login_from_scan, "user_fullName": user_full_name,
            "is_admin": is_admin_scan, "device_orderNumber": device_order_number,
            "device_androidId": android_id_from_request, "events": [session_event],
            "last_updated": datetime.datetime.now(datetime.timezone.utc).isoformat()
        }
        update_session_log(session_data)
        emit_event('session_updated', {'reason': 'new_scan'})

        display_name = user_full_name or user_login_from_scan
        msg_tsd_success = f"Исполнитель: {display_name}\nТСД №{device_order_number if device_order_number else '??'}\n{formatted_request_time}"
        response_payload = {
            "status": "ok", "message": msg_tsd_success, "serverTimestamp": formatted_request_time,
            "userLogin": current_app.config['MASKED_ADMIN_BARCODE'] if is_admin_scan else user_login_from_scan,
            "deviceIdentifier": device_identifier_display
        }
        server_iv_bytes = os.urandom(16)
        encrypted_response = encrypt_aes(json.dumps(response_payload), server_iv_bytes)
        return jsonify({"success": True, "message": encrypted_response, "iv": b64encode(server_iv_bytes).decode('utf-8')}), 200
    except psycopg2.Error as db_err:
        logging.error(f"/scan: Ошибка PostgreSQL: {db_err}", exc_info=True)
        return send_tsd_error_response(500, "db_error", "Ошибка сервера БД", formatted_request_time)
    except Exception as e:
        logging.error(f"/scan: Непредвиденная ошибка: {e}", exc_info=True)
        return send_tsd_error_response(500, "server_error", "Внутренняя ошибка сервера", formatted_request_time)
    finally:
        if connection: connection.close()

@main_bp.route("/deregister", methods=["POST"])
def deregister_device():
    request_time = datetime.datetime.now().astimezone()
    formatted_request_time = request_time.strftime("%d.%m.%Y %H:%M")
    data = request.get_json()
    android_id_from_request = data.get("androidId") if data else None

    if not data or not all([data.get("barcode"), data.get("iv"), android_id_from_request]):
        return send_tsd_error_response(400, "missing_params", "Отсутствуют параметры", formatted_request_time)

    barcode_for_dereg = decrypt_aes(data["barcode"], data["iv"])
    if barcode_for_dereg is None:
        return send_tsd_error_response(500, "decrypt_error", "Ошибка расшифровки ШК", formatted_request_time)

    if not barcode_for_dereg.startswith("20"):
        return send_tsd_error_response(200, "user_invalid_prefix", "Неверный формат ШК", formatted_request_time)

    login_attempting_dereg = barcode_for_dereg[2:]
    is_admin_attempt = is_admin_user(login_attempting_dereg)

    connection = None
    try:
        connection = get_db_connection()
        cursor = connection.cursor(cursor_factory=RealDictCursor)
        cursor.execute("SELECT * FROM devices WHERE android_id = %s", (android_id_from_request,))
        tsd_info = cursor.fetchone()

        if not tsd_info:
            return send_tsd_error_response(200, "device_not_found", "ТСД не найден", formatted_request_time)

        user_on_device = tsd_info.get("current_user_login")
        order_num = tsd_info.get("order_number")

        if user_on_device is None:
            msg = f"ТСД №{order_num} уже свободен"
            return send_tsd_error_response(200, "ok_already_free", msg, formatted_request_time)
        
        can_deregister = is_admin_attempt or (user_on_device == login_attempting_dereg)
        if not can_deregister:
            msg = "Этот ТСД занят другим пользователем"
            return send_tsd_error_response(200, "permission_denied", msg, formatted_request_time)
            
        cursor.execute("UPDATE devices SET current_user_login = NULL, last_login_timestamp = %s WHERE android_id = %s", (request_time, android_id_from_request))
        connection.commit()

        user_to_update_fio = fio_manager.get_fio(user_on_device)
        shift_info = get_shift_info(request_time)
        session_id = f"{user_on_device}-{shift_info['start_time'].isoformat()}"
        
        session_event = {
            "type": "deregistration", "timestamp": request_time.isoformat(),
            "admin_login": login_attempting_dereg if is_admin_attempt and user_on_device != login_attempting_dereg else None
        }
        session_data = {
            "event_type": "user_session", "session_id": session_id,
            "shift_start": shift_info['start_time'].isoformat(), "shift_type": shift_info['type'],
            "user_login": user_on_device, "user_fullName": user_to_update_fio,
            "is_admin": is_admin_user(user_on_device), "device_orderNumber": order_num,
            "device_androidId": android_id_from_request, "events": [session_event],
            "last_updated": datetime.datetime.now(datetime.timezone.utc).isoformat()
        }
        update_session_log(session_data)
        emit_event('session_updated', {'reason': 'deregistration'})
        
        msg_success = f"ТСД №{order_num} дерегистрирован"
        if is_admin_attempt and user_on_device != login_attempting_dereg:
            msg_success += " Администратором"

        response_payload = {"status": "ok_deregistered", "message": msg_success, "serverTimestamp": formatted_request_time}
        server_iv_bytes = os.urandom(16)
        encrypted_response = encrypt_aes(json.dumps(response_payload), server_iv_bytes)
        return jsonify({"success": True, "message": encrypted_response, "iv": b64encode(server_iv_bytes).decode('utf-8')}), 200
    except psycopg2.Error as db_err:
        logging.error(f"/deregister: Ошибка PostgreSQL: {db_err}", exc_info=True)
        return send_tsd_error_response(500, "db_error", "Ошибка БД", formatted_request_time)
    except Exception as e:
        logging.error(f"/deregister: Непредвиденная ошибка: {e}", exc_info=True)
        return send_tsd_error_response(500, "server_error", "Ошибка сервера", formatted_request_time)
    finally:
        if connection: connection.close()

@main_bp.route("/add_device", methods=["POST"])
def add_device():
    data = request.get_json()
    android_id = data.get("androidId")
    order_number = data.get("orderNumber")

    if not all([android_id, order_number]):
        return jsonify({"success": False, "message": "Не указаны androidId или orderNumber"}), 400
    try:
        order_number_int = int(order_number)
    except (ValueError, TypeError):
        return jsonify({"success": False, "message": "orderNumber должен быть целым числом"}), 400
        
    connection = None
    try:
        connection = get_db_connection()
        cursor = connection.cursor()
        
        cursor.execute("SELECT 1 FROM devices WHERE order_number = %s AND android_id != %s", (order_number_int, android_id))
        if cursor.fetchone():
            msg = f"Номер терминала {order_number_int} уже присвоен другому устройству"
            return jsonify({"success": False, "message": msg}), 409

        insert_query = "INSERT INTO devices (android_id, order_number) VALUES (%s, %s) ON CONFLICT (android_id) DO UPDATE SET order_number = EXCLUDED.order_number;"
        cursor.execute(insert_query, (android_id, order_number_int))
        connection.commit()
        
        status_message = f"ТСД {android_id} добавлен под №{order_number_int}"
        logging.info(f"/add_device: {status_message}")
        
        log_system_event({
            "event": "device_added_updated", "message": status_message,
            "androidId": android_id, "orderNumber": order_number_int,
            "serverTimestamp": datetime.datetime.now().strftime("%d.%m.%Y %H:%M:%S")
        })
        emit_event('system_event_logged', {'reason': 'add_device'})

        return jsonify({"success": True, "message": "Устройство успешно добавлено/обновлено"}), 200
    except psycopg2.Error as db_err:
        logging.error(f"Ошибка PostgreSQL при обработке устройства {android_id}: {db_err}", exc_info=True)
        return jsonify({"success": False, "message": "Ошибка БД при добавлении ТСД"}), 500
    except Exception as e:
        logging.exception(f"Непредвиденная ошибка при обработке устройства {android_id}")
        return jsonify({"success": False, "message": "Внутренняя ошибка сервера"}), 500
    finally:
        if connection: connection.close()

# --- ФИНАЛЬНОЕ ИСПРАВЛЕНИЕ v2 ---
@main_bp.route("/api/unregistered_devices", methods=["GET"])
def get_unregistered_devices():
    all_ids_from_logs = set()
    try:
        # ШАГ 1: Собрать все записи из лог-файлов за последние 30 дней (как в /api/logs)
        all_entries_in_period = []
        now_utc = datetime.datetime.now(datetime.timezone.utc)
        thirty_days_ago = now_utc - datetime.timedelta(days=30)
        years_to_check = sorted(list(set([now_utc.year, thirty_days_ago.year])), reverse=True)

        for year in years_to_check:
            log_file = current_app.config['SESSION_LOG_FILENAME_TEMPLATE'].format(year=year)
            if os.path.exists(log_file):
                with open(log_file, 'r', encoding='utf-8') as f:
                    for line in f:
                        try:
                            entry = json.loads(line)
                            ts_str = entry.get("shift_start") or entry.get("last_updated") or entry.get("timestamp_utc")
                            if not ts_str: continue
                            
                            entry_ts = datetime.datetime.fromisoformat(ts_str.replace('Z', '')).replace(tzinfo=datetime.timezone.utc)
                            
                            if entry_ts >= thirty_days_ago:
                                all_entries_in_period.append(entry)
                        except (json.JSONDecodeError, KeyError, ValueError): 
                            continue
        
        # ШАГ 2: Искать кандидатов ВНУТРИ событий каждой сессии
        for entry in all_entries_in_period:
            if entry.get("event_type") == "user_session":
                # Проверяем каждое событие в сессии
                for event in entry.get("events", []):
                    # Нас интересуют только события регистрации без номера
                    if event.get("type") == "registration" and event.get("device_orderNumber") is None:
                        device_id = event.get("device_androidId")
                        if device_id: 
                            all_ids_from_logs.add(device_id)

        if not all_ids_from_logs: 
            return jsonify([])

        # ШАГ 3: Сравнить с базой данных и вернуть разницу
        conn = get_db_connection()
        cursor = conn.cursor()
        cursor.execute("SELECT android_id FROM devices;")
        registered_ids_from_db = {item[0] for item in cursor.fetchall()}
        conn.close()

        truly_unregistered_ids = list(all_ids_from_logs - registered_ids_from_db)
        
        return jsonify(truly_unregistered_ids)
    except Exception as e:
        logging.error(f"Ошибка в /api/unregistered_devices: {e}", exc_info=True)
        return jsonify({"error": "Internal Server Error"}), 500


@main_bp.route("/api/logs/archive-index", methods=["GET"])
def get_archive_index():
    try:
        files = os.listdir('.')
        log_files = [f for f in files if f.startswith('master_session_log_') and f.endswith('.jsonl')]
        years = sorted(list(set([int(f.split('_')[3].split('.')[0]) for f in log_files])), reverse=True)
        return jsonify(years)
    except Exception as e:
        logging.error(f"Ошибка при создании индекса архива: {e}", exc_info=True)
        return jsonify({"error": "Не удалось получить индекс архива"}), 500


@main_bp.route("/api/logs", methods=["GET"])
def get_logs():
    period = request.args.get('period')
    year_str = request.args.get('year')
    month_str = request.args.get('month')
    all_entries = []

    try:
        now_utc = datetime.datetime.now(datetime.timezone.utc)
        
        if period == 'latest':
            thirty_days_ago = now_utc - datetime.timedelta(days=30)
            years_to_check = sorted(list(set([now_utc.year, thirty_days_ago.year])), reverse=True)
        elif year_str:
            years_to_check = [int(year_str)]
        else:
            return jsonify({"error": "Неверные параметры запроса"}), 400

        for year in years_to_check:
            log_file = current_app.config['SESSION_LOG_FILENAME_TEMPLATE'].format(year=year)
            if os.path.exists(log_file):
                with open(log_file, 'r', encoding='utf-8') as f:
                    for line in f:
                        try: all_entries.append(json.loads(line))
                        except json.JSONDecodeError: continue

        filtered_entries = []
        if period == 'latest':
            thirty_days_ago_utc = now_utc - datetime.timedelta(days=30)
            for entry in all_entries:
                ts_str = entry.get("shift_start") or entry.get("last_updated") or entry.get("timestamp_utc")
                if not ts_str: continue
                entry_ts = datetime.datetime.fromisoformat(ts_str.replace('Z', '')).replace(tzinfo=datetime.timezone.utc)
                if entry_ts >= thirty_days_ago_utc:
                    filtered_entries.append(entry)
        elif year_str and month_str:
            year, month = int(year_str), int(month_str)
            for entry in all_entries:
                ts_str = entry.get("shift_start") or entry.get("last_updated") or entry.get("timestamp_utc")
                if not ts_str: continue
                entry_ts = datetime.datetime.fromisoformat(ts_str.replace('Z', '')).replace(tzinfo=datetime.timezone.utc)
                if entry_ts.year == year and entry_ts.month == month:
                    filtered_entries.append(entry)
        
        shifts = {}
        for entry in filtered_entries:
            if entry.get("event_type") == "user_session":
                shift_start_iso = entry["shift_start"]
                shift_key = shift_start_iso
                
                if shift_key not in shifts:
                    shift_dt = datetime.datetime.fromisoformat(shift_start_iso)
                    shifts[shift_key] = {
                        "shift_start_iso": shift_start_iso,
                        "shift_display_name": f"{entry['shift_type']} - {shift_dt.astimezone().strftime('%d.%m.%Y %H:%M')}",
                        "sessions": [], "system_events": []
                    }
                shifts[shift_key]["sessions"].append(entry)

            elif entry.get("event_type") == "system_event":
                event_time_utc = datetime.datetime.fromisoformat(entry["timestamp_utc"].replace('Z', '')).replace(tzinfo=datetime.timezone.utc)
                shift_info = get_shift_info(event_time_utc)
                shift_key = shift_info['start_time'].isoformat()

                if shift_key not in shifts:
                        shifts[shift_key] = {
                            "shift_start_iso": shift_key,
                            "shift_display_name": f"{shift_info['type']} - {shift_info['start_time'].astimezone().strftime('%d.%m.%Y %H:%M')}",
                            "sessions": [], "system_events": []
                        }
                shifts[shift_key]["system_events"].append(entry)

        sorted_shifts = sorted(shifts.values(), key=lambda x: x['shift_start_iso'])
        
        return jsonify(sorted_shifts)
    except Exception as e:
        logging.error(f"Ошибка при получении логов ({request.args}): {e}", exc_info=True)
        return jsonify({"error": "Не удалось получить логи"}), 500
