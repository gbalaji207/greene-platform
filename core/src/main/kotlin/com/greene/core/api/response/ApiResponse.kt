package com.greene.core.api.response

data class ApiResponse<T>(
    val data: T,
    val meta: Meta = Meta(),
) {
    companion object {
        fun <T> of(data: T): ApiResponse<T> = ApiResponse(data = data)
    }
}
