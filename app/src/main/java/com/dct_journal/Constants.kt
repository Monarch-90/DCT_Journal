package com.dct_journal

object Constants {

    /** Use case constants */
    const val SCAN_STATUS_OK = "ok"
    const val SCAN_STATUS_USER_LOCKED_ELSEWHERE = "user_locked_elsewhere"
    const val SCAN_STATUS_DEVICE_OCCUPIED = "device_occupied_scan"
    const val SCAN_STATUS_USER_INVALID_PREFIX = "user_invalid_prefix"


    /** hardwareScanner (MainActivity) constants */
    // Действие Intent
    const val DATAWEDGE_SCAN_ACTION = "com.dwexample.ACTION" // ZEBRA-MC330M
    const val POINT_MOBILE_SCAN_ACTION = "device.scanner.EVENT" // Point Mobile-PM451

    // Стандартный ключ для получения данных сканирования из Intent Extras
    const val DATAWEDGE_DATA_STRING_KEY = "com.symbol.datawedge.data_string" // ZEBRA-MC330M
    const val POINT_MOBILE_DATA_KEY = "EXTRA_EVENT_DECODE_VALUE" // Point Mobile-PM451


    /** MainActivity */
    const val DEREGISTRATION_ACTIVITY_LAUNCH_DELAY = 2000L

    // Расположение пакета ВМС
    const val WMS_APP_PACKAGE_NAME = "AZ.Terminal"


    /** Префиксы ШК из ВМС */
    val PREFIX_USER_BARCODE = listOf("20", "S-", "UID-") // Бейдж сотрудника


    /** Intent Extras */
    const val EXTRA_USER_LOGIN_DISPLAY = "extra_user_login_display" // Логин пользователя для отображения
    const val EXTRA_ANDROID_ID = "extra_android_id_for_dereg" // Android ID текущего ТСД
    const val EXTRA_ORDER_NUMBER_DISPLAY = "extra_order_number_display" // Порядковый номер текущего ТСД


    /** SharedPreferencesManager */
    const val PREFS_NAME = "dct_journal_prefs"
    const val KEY_DEREG_MODE_ACTIVE = "key_deregistration_mode_active"
    const val KEY_USER_BARCODE_TO_DEREGISTER = "key_user_barcode_to_deregister"
    const val KEY_ANDROID_ID_FOR_DEREG = "key_android_id_for_dereg"
    const val KEY_ORDER_NUMBER_FOR_DEREG = "key_order_number_for_dereg"


    /** Api endpoints */
    const val API_SERVER_ENDPOINT_SCAN = "scan"
    const val API_SERVER_ENDPOINT_ADD_DEVICE = "add_device"
    const val API_SERVER_ENDPOINT_DEREGISTER = "deregister"

    /** My notebook ip_address */
    const val MY_IP_ADDRESS = "http://10.42.0.1:5000/"


    /** DeviceManagementService */
    const val NOTIFICATION_ID = 101
    const val NOTIFICATION_CHANNEL_ID = "DeviceManagementChannel"


    /** ШК для настроек */
    const val SECRET_BARCODE = "A7X4-D9LQ-"
    const val ADMIN_BARCODE = "ADM-SWH-"
}