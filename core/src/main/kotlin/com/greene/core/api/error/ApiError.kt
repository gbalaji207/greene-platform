package com.greene.core.api.error

data class ApiError(
    val error: ErrorBody,
) {
    data class ErrorBody(
        val code: String,
        val message: String,
        val details: List<ErrorDetail> = emptyList(),
    )

    companion object {
        fun of(code: String, message: String): ApiError =
            ApiError(ErrorBody(code = code, message = message))

        fun validation(message: String, details: List<ErrorDetail>): ApiError =
            ApiError(ErrorBody(code = "VALIDATION_ERROR", message = message, details = details))
    }
}