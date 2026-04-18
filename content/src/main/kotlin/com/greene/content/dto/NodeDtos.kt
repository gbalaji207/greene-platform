package com.greene.content.dto

import com.fasterxml.jackson.annotation.JsonInclude
import com.greene.content.domain.ItemType
import com.greene.content.domain.NodeStatus
import com.greene.content.domain.NodeType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime
import java.util.UUID

data class CreateNodeRequest(
    @field:NotNull(message = "nodeType is required")
    val nodeType: NodeType? = null,

    @field:NotBlank(message = "title is required")
    @field:Size(max = 255, message = "title must not exceed 255 characters")
    val title: String? = null,

    val parentId: UUID? = null,

    val sortOrder: Int? = null,

    // Required when nodeType = ITEM; cross-field validation done in service
    val itemType: ItemType? = null,

    @field:Size(max = 500, message = "summary must not exceed 500 characters")
    val summary: String? = null
)

data class UpdateNodeRequest(
    @field:Size(max = 255, message = "title must not exceed 255 characters")
    val title: String? = null,

    @field:Size(max = 500, message = "summary must not exceed 500 characters")
    val summary: String? = null,

    val status: NodeStatus? = null,

    val durationSeconds: Int? = null
)

data class MoveNodeRequest(
    val newParentId: UUID? = null,
    val sortOrder: Int? = null
)

data class ReorderNodesRequest(
    val parentId: UUID? = null,

    @field:NotEmpty(message = "orderedNodeIds is required")
    val orderedNodeIds: List<UUID>? = null
)

data class NodeResponse(
    val id: UUID,
    val libraryId: UUID,
    val parentId: UUID?,
    val nodeType: NodeType,
    val title: String,
    val sortOrder: Int,
    val depth: Int,
    val status: NodeStatus,
    val itemType: ItemType?,
    val summary: String?,
    val durationSeconds: Int?,
    val hasFile: Boolean,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class TreeNodeResponse(
    val id: UUID,
    val nodeType: NodeType,
    val title: String,
    val sortOrder: Int,
    val depth: Int,
    val status: NodeStatus?,
    val itemType: ItemType?,
    val summary: String?,
    val durationSeconds: Int?,
    val hasFile: Boolean?,
    val children: List<TreeNodeResponse>
)

