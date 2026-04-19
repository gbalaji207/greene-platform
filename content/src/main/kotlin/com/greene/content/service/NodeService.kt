package com.greene.content.service

import com.greene.content.domain.NodeStatus
import com.greene.content.domain.NodeType
import com.greene.content.domain.FileRole
import com.greene.content.dto.UpdateNodeRequest
import com.greene.content.repository.ContentFileRepository
import com.greene.content.repository.ContentNodeRepository
import com.greene.core.exception.PlatformException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.UUID

@Service
class NodeService(
    private val contentNodeRepository: ContentNodeRepository,
    private val contentFileRepository: ContentFileRepository,
) {

    @Transactional
    fun updateNode(nodeId: UUID, request: UpdateNodeRequest): Unit {
        val node = contentNodeRepository.findById(nodeId)
            .orElseThrow {
                PlatformException(
                    "NODE_NOT_FOUND",
                    "Content node with id $nodeId not found",
                    HttpStatus.NOT_FOUND
                )
            }

        // Publish guard — ITEM nodes must have a PRIMARY file before they can be published
        if (request.status == NodeStatus.PUBLISHED && node.nodeType == NodeType.ITEM) {
            if (!contentFileRepository.existsByNodeIdAndFileRole(node.id, FileRole.PRIMARY)) {
                throw PlatformException(
                    "NO_PRIMARY_FILE",
                    "Cannot publish a content item before saving content",
                    HttpStatus.UNPROCESSABLE_ENTITY
                )
            }
        }

        if (request.title != null) node.title = request.title
        if (request.status != null) node.status = request.status

        node.updatedAt = OffsetDateTime.now()
        contentNodeRepository.save(node)
    }
}

