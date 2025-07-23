package com.dct_journal.data.network.model

import com.google.gson.annotations.SerializedName

// Этот класс будет отправляться на сервер
data class StatusUpdateRequest(
    @SerializedName("deviceId")
    val deviceId: String,

    @SerializedName("androidVersion")
    val androidVersion: String,

    @SerializedName("appVersion")
    val appVersion: String,

    @SerializedName("wmsVersion")
    val wmsVersion: String, // Пока оставим пустой, если не знаем как получить

    @SerializedName("batteryLevel")
    val batteryLevel: Int
)