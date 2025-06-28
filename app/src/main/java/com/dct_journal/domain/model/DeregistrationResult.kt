package com.dct_journal.domain.model

sealed class DeregistrationResult {
    enum class SuccessType { DEREGISTERED, ALREADY_FREE }
    enum class FailureType { DEVICE_NOT_FOUND, PERMISSION_DENIED, INVALID_BARCODE, INVALID_FORMAT, UNKNOWN, EXCEPTION }

    data class Success(val message: String, val serverTimestamp: String) : DeregistrationResult()
    data class Failure(
        val message: String,
        val serverTimestamp: String?,
        val invalidFormat: FailureType
    ) : DeregistrationResult()
}