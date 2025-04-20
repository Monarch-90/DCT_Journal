package com.dct_journal

object Constants {

    /** Use case constants */
    const val OK = "ok"
    const val DEVICE_NOT_FOUND = "device_not_found"
    const val USER_NOT_FOUND = "user_not_found"
    const val USER_INVALID_PREFIX = "user_invalid_prefix"
    const val ERROR = "error" // Общая ошибка

    /** MainActivity constants */
    // Действие Intent, указанное в настройках DataWedge вашего MC330M
    const val DATAWEDGE_SCAN_ACTION = "com.dxexample.ACTION"

    // Стандартный ключ для получения данных сканирования из Intent Extras
    const val DATAWEDGE_DATA_STRING_KEY = "com.symbol.datawedge.data_string"

    // Задержка перед запуском ВМС приложения (2 секунды)
    const val WMS_APP_LAUNCH_DELAY = 2000L

    // !!! ВАЖНО: Замените "com.your.wms.package.name" на реальный пакет вашего ВМС приложения !!!
    const val WMS_APP_PACKAGE_NAME = "AZ.Terminal" // ЗАМЕНИТЬ!!!

    /** Api endpoints */
    const val API_SERVER_ENDPOINT_SCAN = "scan"
    const val API_SERVER_ENDPOINT_ADD_DEVICE = "add_device"

    const val SECRET_BARCODE = "A7X4-D9LQ-"
}