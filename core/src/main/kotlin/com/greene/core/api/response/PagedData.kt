package com.greene.core.api.response

data class PagedData<T>(
    val items: List<T>,
    val page: Int,
    val pageSize: Int,
    val total: Long,
)
