# /app/routes.py

import datetime
import json
import logging
import os
import psycopg2
from psycopg2.extras import RealDictCursor
from flask import request, jsonify, Blueprint, current_app
from base64 import b64encode

# Импортируем все утилиты и объекты из нашего файла utils.py
from .utils import (
    get_db_connection, decrypt_aes, encrypt_aes, is_admin_user,
    send_tsd_error_response, emit_and_log, fio_manager
)

# Создаем Blueprint
main_bp = Blueprint('main', __name__)


# --- МАРШРУТЫ ---

@main_bp.route("/")
def home():
    return "Сервер Flask с SocketIO для Журнала регистрации работает! (рефакторинг v1.4 - полный)"


@main_bp.route("/scan", methods=["POST"])
def scan_barcode():
    request_time = datetime.datetime.now()
    formatted_request_time = request_time.strftime("%d.%m.%Y %H:%M")
    data = request.get_json()
    android_id_from_request = data.get("androidId") if data else None

    if not data:
        logging.warning("/scan: Нет данных JSON в запросе.")
        payload = {"overall_success": False, "status": "bad_request_scan", "errorMessageForWindows": "Нет данных JSON", "serverTimestamp": formatted_request_time, "androidId": android_id_from_request}
        emit_and_log('new_log_entry', payload)
        return send_tsd_error_response(400, "bad_request", "Нет данных JSON", formatted_request_time)

    encrypted_barcode = data.get("barcode")
    client_iv_b64 = data.get("iv")

    if not all([encrypted_barcode, android_id_from_request, client_iv_b64]):
        logging.warning(f"/scan: Отсутствуют параметры. Barcode: {bool(encrypted_barcode)}, AndroidID: {bool(android_id_from_request)}, IV: {bool(client_iv_b64)}")
        payload = {"overall_success": False, "status": "missing_params_scan", "errorMessageForWindows": "Отсутствуют параметры", "androidId": android_id_from_request, "serverTimestamp": formatted_request_time}
        emit_and_log('new_log_entry', payload)
        return send_tsd_error_response(400, "missing_params", "Отсутствуют параметры", formatted_request_time)

    barcode_from_tsd = decrypt_aes(encrypted_barcode, client_iv_b64)
    if barcode_from_tsd is None:
        logging.error(f"/scan: Ошибка расшифровки ШК. AndroidID: {android_id_from_request}")
        payload = {"overall_success": False, "status": "decrypt_error_scan", "errorMessageForWindows": "Ошибка расшифровки ШК", "androidId": android_id_from_request, "serverTimestamp": formatted_request_time}
        emit_and_log('new_log_entry', payload)
        return send_tsd_error_response(500, "decrypt_error", "Ошибка расшифровки ШК", formatted_request_time)

    logging.info(f"/scan: Принят. AndroidID='{android_id_from_request}', Расш.ШК='{barcode_from_tsd}', Время={formatted_request_time}")

    data_for_windows = {
        "userLogin": None, "userFullName": None, "androidId": android_id_from_request,
        "orderNumber": None, "deviceIdentifierForDisplay": android_id_from_request,
        "serverTimestamp": formatted_request_time, "rawAndroidId": android_id_from_request,
        "overall_success": False, "wms_launch_allowed": False, "status": "unknown_scan_error",
        "messageForTsd": "Неизвестная ошибка", "errorMessageForWindows": "Неизвестная ошибка сканирования"
    }

    if not barcode_from_tsd.startswith("20"):
        logging.warning(f"/scan: Неверный формат ШК '{barcode_from_tsd}'. AndroidID='{android_id_from_request}'")
        data_for_windows.update({"status": "invalid_prefix_scan", "userLoginAttempt": barcode_from_tsd, "errorMessageForWindows": "Неверный формат ШК", "messageForTsd": "Неверный формат ШК"})
        emit_and_log('new_log_entry', data_for_windows)
        return send_tsd_error_response(200, "user_invalid_prefix", "Неверный формат ШК", formatted_request_time, user_login=barcode_from_tsd, device_identifier_display=android_id_from_request)

    user_login_from_scan = barcode_from_tsd[2:]
    user_full_name = fio_manager.get_fio(user_login_from_scan)
    is_admin_scan = is_admin_user(user_login_from_scan)
    logging.info(f"/scan: Логин '{user_login_from_scan}' (ФИО: {user_full_name or 'не найдено'}), Admin: {is_admin_scan}")
    
    data_for_windows["userLogin"] = user_login_from_scan
    data_for_windows["userFullName"] = user_full_name

    connection = None
    final_success_for_wms = False
    
    try:
        connection = get_db_connection()
        cursor = connection.cursor(cursor_factory=RealDictCursor)

        if not is_admin_scan:
            cursor.execute("SELECT order_number, android_id FROM devices WHERE current_user_login = %s", (user_login_from_scan,))
            active_tsd_for_user = cursor.fetchone()
            if active_tsd_for_user and active_tsd_for_user.get('android_id') != android_id_from_request:
                locked_tsd_order_number = active_tsd_for_user.get('order_number', "N/A")
                error_msg = f"Вы не дерегистрировали ТСД №{locked_tsd_order_number}"
                logging.warning(f"/scan: Пользователь '{user_login_from_scan}' уже активен на ТСД №{locked_tsd_order_number}. Отказ для '{android_id_from_request}'.")
                data_for_windows.update({"status": "user_already_active_elsewhere", "errorMessageForWindows": error_msg, "messageForTsd": error_msg, "lockedTsdOrder": locked_tsd_order_number})
                emit_and_log('new_log_entry', data_for_windows)
                return send_tsd_error_response(200, "user_locked_elsewhere", error_msg, formatted_request_time, user_login=user_login_from_scan, device_identifier_display=android_id_from_request)

        cursor.execute("SELECT * FROM devices WHERE android_id = %s", (android_id_from_request,))
        current_tsd_info_from_db = cursor.fetchone()

        if current_tsd_info_from_db:
            data_for_windows["orderNumber"] = current_tsd_info_from_db.get("order_number")
            data_for_windows["deviceIdentifierForDisplay"] = current_tsd_info_from_db.get("order_number", android_id_from_request)
            
            occupying_user = current_tsd_info_from_db.get("current_user_login")
            if occupying_user and occupying_user != user_login_from_scan and not is_admin_scan:
                msg = f"ТСД №{data_for_windows['orderNumber']} уже используется: {occupying_user}"
                data_for_windows.update({"status": "device_occupied_scan", "errorMessageForWindows": msg, "messageForTsd": msg, "occupyingUser": occupying_user})
                final_success_for_wms = False
            else:
                cursor.execute(
                    "UPDATE devices SET current_user_login = %s, last_login_timestamp = %s WHERE android_id = %s",
                    (user_login_from_scan, request_time, android_id_from_request)
                )
                connection.commit()
                display_name = user_full_name if user_full_name else user_login_from_scan
                msg_tsd_success = f"Исполнитель: {display_name}\nТСД №{data_for_windows['orderNumber']}\n{formatted_request_time}"
                data_for_windows.update({"overall_success": True, "wms_launch_allowed": True, "status": "ok_scan_registered_device", "messageForTsd": msg_tsd_success})
                final_success_for_wms = True
        else:
            logging.info(f"/scan: ТСД с AndroidID='{android_id_from_request}' не зарегистрирован, но ШК пользователя '{user_login_from_scan}' верный. Разрешаем работу.")
            display_name = user_full_name if user_full_name else user_login_from_scan
            msg_tsd_unregistered = f"Исполнитель: {display_name}\nТСД ID: {android_id_from_request[:10]}...\n{formatted_request_time}"
            data_for_windows.update({
                "overall_success": True, "wms_launch_allowed": True,
                "status": "ok_scan_unregistered_device", "orderNumber": None,
                "deviceIdentifierForDisplay": android_id_from_request,
                "messageForTsd": msg_tsd_unregistered
            })
            final_success_for_wms = True
        
        emit_and_log('new_log_entry', data_for_windows)
        
        user_login_for_payload_tsd = current_app.config['MASKED_ADMIN_BARCODE'] if is_admin_scan else user_login_from_scan
        response_payload_tsd = {
            "status": "ok" if final_success_for_wms else data_for_windows.get("status", "error_final").replace("_scan",""),
            "userLogin": user_login_for_payload_tsd,
            "deviceIdentifier": data_for_windows["deviceIdentifierForDisplay"],
            "serverTimestamp": formatted_request_time,
            "message": data_for_windows["messageForTsd"]
        }
        server_iv_final_bytes = os.urandom(16)
        server_iv_b64_final = b64encode(server_iv_final_bytes).decode('utf-8')
        encrypted_response_final = encrypt_aes(json.dumps(response_payload_tsd), server_iv_final_bytes)
        
        if encrypted_response_final is None:
            return jsonify({"success": False}), 500
        return jsonify({"success": final_success_for_wms, "message": encrypted_response_final, "iv": server_iv_b64_final}), 200

    except psycopg2.Error as db_err:
        logging.error(f"/scan: Ошибка PostgreSQL: {db_err}", exc_info=True)
        return send_tsd_error_response(500, "db_error", "Ошибка сервера БД", formatted_request_time)
    except Exception as e:
        logging.error(f"/scan: Непредвиденная ошибка: {e}", exc_info=True)
        return send_tsd_error_response(500, "server_error", "Внутренняя ошибка сервера", formatted_request_time)
    finally:
        if connection and not connection.closed:
            connection.close()


@main_bp.route("/deregister", methods=["POST"])
def deregister_device():
    request_time = datetime.datetime.now()
    formatted_request_time = request_time.strftime("%d.%m.%Y %H:%M")
    data = request.get_json()
    android_id_from_request = data.get("androidId") if data else None

    if not data: return send_tsd_error_response(400, "bad_request", "Нет данных JSON", formatted_request_time)
    
    encrypted_barcode = data.get("barcode")
    client_iv_b64 = data.get("iv")
    if not all([encrypted_barcode, android_id_from_request, client_iv_b64]): return send_tsd_error_response(400, "missing_params", "Отсутствуют параметры", formatted_request_time)
    
    barcode_for_dereg = decrypt_aes(encrypted_barcode, client_iv_b64)
    if barcode_for_dereg is None: return send_tsd_error_response(500, "decrypt_error", "Ошибка расшифровки ШК", formatted_request_time)
    
    if not barcode_for_dereg.startswith("20"): return send_tsd_error_response(200, "user_invalid_prefix", "Неверный формат ШК", formatted_request_time)

    login_attempting_dereg = barcode_for_dereg[2:]
    attempting_user_fio = fio_manager.get_fio(login_attempting_dereg)
    is_admin_attempt = is_admin_user(login_attempting_dereg)
    
    data_for_windows = {
        "androidId": android_id_from_request, "serverTimestamp": formatted_request_time,
        "overall_success": False, "status": "unknown_dereg_error",
        "messageForTsd": "Ошибка дерегистрации", "errorMessageForWindows": "Неизвестная ошибка дерегистрации",
        "userLoginAttempt": login_attempting_dereg, "attemptingUserFullName": attempting_user_fio,
        "adminAction": is_admin_attempt, "deregisteredUser": None, "deregisteredUserFullName": None
    }

    connection = None
    try:
        connection = get_db_connection()
        cursor = connection.cursor(cursor_factory=RealDictCursor)
        
        cursor.execute("SELECT * FROM devices WHERE android_id = %s", (android_id_from_request,))
        current_tsd_info_from_db = cursor.fetchone()

        if not current_tsd_info_from_db:
            msg = "ТСД для дерегистрации не найден в системе"
            data_for_windows.update({"status": "device_not_found_dereg", "messageForTsd": msg, "errorMessageForWindows": msg})
            emit_and_log('deregistration_log', data_for_windows)
            return send_tsd_error_response(200, "device_not_found", msg, formatted_request_time, user_login=login_attempting_dereg, device_identifier_display=android_id_from_request)

        user_on_device = current_tsd_info_from_db.get("current_user_login")
        order_num_on_device = current_tsd_info_from_db.get("order_number")
        data_for_windows['orderNumber'] = order_num_on_device
        data_for_windows['deregisteredUser'] = user_on_device

        # ИСПРАВЛЕНИЕ 2: Добавляем поиск ФИО для дерегистрируемого пользователя
        if user_on_device:
            deregistered_user_fio = fio_manager.get_fio(user_on_device)
            data_for_windows['deregisteredUserFullName'] = deregistered_user_fio

        if user_on_device is None:
            msg = f"ТСД №{order_num_on_device} уже свободен"
            data_for_windows.update({"overall_success": True, "status": "already_free_dereg", "messageForTsd": msg, "deregisteredUser": None, "errorMessageForWindows": msg})
            emit_and_log('deregistration_log', data_for_windows)
            
            response_payload_tsd = {"status": "ok_already_free", "message": msg, "userLogin": login_attempting_dereg, "deviceIdentifier": order_num_on_device, "serverTimestamp": formatted_request_time}
            server_iv_final_bytes = os.urandom(16)
            server_iv_b64_final = b64encode(server_iv_final_bytes).decode('utf-8')
            encrypted_response_final = encrypt_aes(json.dumps(response_payload_tsd), server_iv_final_bytes)
            if encrypted_response_final is None: return jsonify({"success": False}), 500
            return jsonify({"success": True, "message": encrypted_response_final, "iv": server_iv_b64_final}), 200

        can_deregister = is_admin_attempt or (user_on_device == login_attempting_dereg)

        if can_deregister:
            cursor.execute("UPDATE devices SET current_user_login = NULL, last_login_timestamp = %s WHERE android_id = %s", (request_time, android_id_from_request))
            connection.commit()
            data_for_windows["overall_success"] = True
            
            if is_admin_attempt:
                data_for_windows["status"] = "admin_deregistered"
                data_for_windows["messageForTsd"] = f"ТСД №{order_num_on_device} дерегистрирован Администратором"
                data_for_windows["errorMessageForWindows"] = f"ТСД №{order_num_on_device} (пользователь {user_on_device}) дерегистрирован администратором {login_attempting_dereg}"
            else:
                data_for_windows["status"] = "user_deregistered"
                data_for_windows["messageForTsd"] = f"ТСД №{order_num_on_device} дерегистрирован"
                data_for_windows["errorMessageForWindows"] = f"Пользователь {login_attempting_dereg} дерегистрировал ТСД №{order_num_on_device}"

            logging.info(f"/deregister: {data_for_windows['errorMessageForWindows']}")
            emit_and_log('deregistration_log', data_for_windows)
            
            response_payload_tsd = {"status": "ok_deregistered", "message": data_for_windows["messageForTsd"], "userLogin": login_attempting_dereg, "deviceIdentifier": order_num_on_device, "serverTimestamp": formatted_request_time}
            server_iv_final_bytes = os.urandom(16)
            server_iv_b64_final = b64encode(server_iv_final_bytes).decode('utf-8')
            encrypted_response_final = encrypt_aes(json.dumps(response_payload_tsd), server_iv_final_bytes)
            if encrypted_response_final is None: return jsonify({"success": False}), 500
            return jsonify({"success": True, "message": encrypted_response_final, "iv": server_iv_b64_final}), 200
        else:
            data_for_windows["status"] = "permission_denied_dereg"
            data_for_windows["messageForTsd"] = "Этот ТСД занят другим пользователем"
            data_for_windows["errorMessageForWindows"] = f"ТСД №{order_num_on_device} занят {user_on_device}. Отказ в дерегистрации для {login_attempting_dereg}."
            emit_and_log('deregistration_log', data_for_windows)
            return send_tsd_error_response(200, "permission_denied", data_for_windows["messageForTsd"], formatted_request_time, user_login=login_attempting_dereg, device_identifier_display=order_num_on_device)

    except psycopg2.Error as db_err:
        logging.error(f"/deregister: Ошибка PostgreSQL: {db_err}", exc_info=True)
        return send_tsd_error_response(500, "db_error", "Ошибка БД", formatted_request_time)
    except Exception as e:
        logging.error(f"/deregister: Непредвиденная ошибка: {e}", exc_info=True)
        return send_tsd_error_response(500, "server_error", "Ошибка сервера", formatted_request_time)
    finally:
        if connection and not connection.closed:
            connection.close()


@main_bp.route("/add_device", methods=["POST"])
def add_device():
    # ИСПРАВЛЕНИЕ 1: Добавляем serverTimestamp
    request_time = datetime.datetime.now()
    formatted_request_time = request_time.strftime("%d.%m.%Y %H:%M")

    data = request.get_json()
    android_id_from_payload = data.get("androidId") if data else None
    order_number_from_payload = data.get("orderNumber") if data else None

    # ИСПРАВЛЕНИЕ 1: Инициализируем объект для лога с таймстэмпом
    data_for_windows = {
        "overall_success": False, "status": "add_device_failed_initial",
        "androidId": android_id_from_payload, "orderNumber": order_number_from_payload,
        "serverTimestamp": formatted_request_time, "message": "Ошибка добавления устройства"
    }

    if not all([android_id_from_payload, order_number_from_payload is not None]):
        data_for_windows.update({"status": "missing_params_add", "message": "Не указаны androidId или orderNumber"})
        emit_and_log('device_registration_attempt', data_for_windows)
        return jsonify({"success": False, "message": "Не указаны androidId или orderNumber"}), 400

    try:
        order_number_int = int(order_number_from_payload)
    except (ValueError, TypeError):
        data_for_windows.update({"status": "invalid_ordernum_add", "message": "orderNumber должен быть числом"})
        emit_and_log('device_registration_attempt', data_for_windows)
        return jsonify({"success": False, "message": "orderNumber должен быть целым числом"}), 400
        
    connection = None
    try:
        connection = get_db_connection()
        cursor = connection.cursor()
        
        cursor.execute("SELECT 1 FROM devices WHERE order_number = %s AND android_id != %s", (order_number_int, android_id_from_payload))
        if cursor.fetchone():
            msg = f"Номер терминала {order_number_int} уже присвоен другому устройству"
            data_for_windows.update({"status": "ordernum_exists_add", "message": msg})
            emit_and_log('device_registration_attempt', data_for_windows)
            return jsonify({"success": False, "message": msg}), 409

        insert_query = """
            INSERT INTO devices (android_id, order_number) VALUES (%s, %s)
            ON CONFLICT (android_id) DO UPDATE SET order_number = EXCLUDED.order_number;
        """
        cursor.execute(insert_query, (android_id_from_payload, order_number_int))
        connection.commit()
        
        status_message = cursor.statusmessage or "OK"
        msg_success = f"Данные ТСД (№{order_number_int}, ID:{android_id_from_payload}) обработаны ({status_message})"
        logging.info(f"/add_device: {msg_success}")
        
        data_for_windows.update({"overall_success": True, "status": "device_added_updated", "message": msg_success})
        emit_and_log('device_registration_attempt', data_for_windows)
        
        return jsonify({"success": True, "message": msg_success}), 200

    except psycopg2.Error as db_err:
        logging.error(f"Ошибка PostgreSQL при обработке устройства {android_id_from_payload}: {db_err}", exc_info=True)
        data_for_windows.update({"status": "db_error_add", "message": "Ошибка БД"})
        emit_and_log('device_registration_attempt', data_for_windows)
        return jsonify({"success": False, "message": "Ошибка БД при добавлении ТСД"}), 500
    except Exception as e:
        logging.exception(f"Непредвиденная ошибка при обработке устройства {android_id_from_payload}")
        data_for_windows.update({"status": "server_error_add", "message": "Внутренняя ошибка сервера"})
        emit_and_log('device_registration_attempt', data_for_windows)
        return jsonify({"success": False, "message": "Внутренняя ошибка сервера"}), 500
    finally:
        if connection and not connection.closed:
            connection.close()


@main_bp.route("/api/unregistered_devices", methods=["GET"])
def get_unregistered_devices():
    conn = None
    all_ids_from_logs = set()
    try:
        now = datetime.datetime.now(datetime.timezone.utc)
        thirty_days_ago = now - datetime.timedelta(days=30)
        years_to_check = list(set([now.year, thirty_days_ago.year]))

        for year in years_to_check:
            log_file = f"master_event_log_{year}.jsonl"
            if os.path.exists(log_file):
                with open(log_file, 'r', encoding='utf-8') as f:
                    for line in f:
                        try:
                            entry = json.loads(line)
                            entry_ts = datetime.datetime.fromisoformat(entry["timestamp_logged_utc"].replace('Z', '+00:00'))
                            if entry_ts < thirty_days_ago: continue
                            if entry.get("event_name") == "new_log_entry" and entry.get("log_data", {}).get("status") == "ok_scan_unregistered_device":
                                device_id = entry.get("log_data", {}).get("rawAndroidId")
                                if device_id: all_ids_from_logs.add(device_id)
                        except (json.JSONDecodeError, KeyError, TypeError):
                            continue
        
        if not all_ids_from_logs: return jsonify([])

        conn = get_db_connection()
        cursor = conn.cursor()
        cursor.execute("SELECT android_id FROM devices;")
        registered_ids_from_db = {item[0] for item in cursor.fetchall()}

        truly_unregistered_ids = list(all_ids_from_logs - registered_ids_from_db)
        return jsonify(truly_unregistered_ids)
    except Exception as e:
        logging.error(f"Ошибка в /api/unregistered_devices: {e}", exc_info=True)
        return jsonify({"error": "Internal Server Error"}), 500
    finally:
        if conn and not conn.closed: conn.close()


@main_bp.route("/api/logs/archive-index", methods=["GET"])
def get_archive_index():
    try:
        files = os.listdir('.')
        log_files = [f for f in files if f.startswith('master_event_log_') and f.endswith('.jsonl')]
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
    log_entries = []
    try:
        if period == 'latest':
            now = datetime.datetime.now(datetime.timezone.utc)
            thirty_days_ago = now - datetime.timedelta(days=30)
            years_to_check = list(set([now.year, thirty_days_ago.year]))
            for year in years_to_check:
                log_file = f"master_event_log_{year}.jsonl"
                if os.path.exists(log_file):
                    with open(log_file, 'r', encoding='utf-8') as f:
                        for line in f:
                            try:
                                entry = json.loads(line)
                                entry_ts_str = entry.get("timestamp_logged_utc")
                                if entry_ts_str:
                                    entry_ts = datetime.datetime.fromisoformat(entry_ts_str.replace('Z', '+00:00'))
                                    if thirty_days_ago <= entry_ts <= now:
                                        log_entries.append(entry)
                            except (json.JSONDecodeError, KeyError): continue
        elif year_str and month_str:
            year = int(year_str)
            month = int(month_str)
            log_file = f"master_event_log_{year}.jsonl"
            if os.path.exists(log_file):
                with open(log_file, 'r', encoding='utf-8') as f:
                    for line in f:
                        try:
                            entry = json.loads(line)
                            entry_ts_str = entry.get("timestamp_logged_utc")
                            if entry_ts_str:
                                entry_ts = datetime.datetime.fromisoformat(entry_ts_str.replace('Z', '+00:00'))
                                if entry_ts.year == year and entry_ts.month == month:
                                    log_entries.append(entry)
                        except (json.JSONDecodeError, KeyError): continue
        log_entries.sort(key=lambda x: x.get('timestamp_logged_utc', ''))
        return jsonify(log_entries)
    except Exception as e:
        logging.error(f"Ошибка при получении логов ({request.args}): {e}", exc_info=True)
        return jsonify({"error": "Не удалось получить логи"}), 500
