package com.greene.content.dto

import com.greene.content.domain.NodeStatus

data class UpdateNodeRequest(
    val title: String? = null,
    val status: NodeStatus? = null,
)

