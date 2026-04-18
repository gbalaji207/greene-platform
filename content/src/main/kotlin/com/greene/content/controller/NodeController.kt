package com.greene.content.controller

import com.greene.content.dto.*
import com.greene.content.service.NodeService
import com.greene.core.api.response.ApiResponse
import com.greene.core.exception.PlatformException
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
class NodeController(private val nodeService: NodeService) {

    @PostMapping("/api/v1/libraries/{id}/nodes")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'SUPER_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    fun createNode(
        @PathVariable id: UUID,
        @Valid @RequestBody req: CreateNodeRequest
    ): ApiResponse<NodeResponse> =
        ApiResponse.of(nodeService.createNode(id, req))

    @PatchMapping("/api/v1/nodes/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'SUPER_ADMIN')")
    fun updateNode(
        @PathVariable id: UUID,
        @Valid @RequestBody req: UpdateNodeRequest
    ): ApiResponse<NodeResponse> =
        ApiResponse.of(nodeService.updateNode(id, req))

    @PatchMapping("/api/v1/nodes/{id}/move")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'SUPER_ADMIN')")
    fun moveNode(
        @PathVariable id: UUID,
        @RequestBody req: MoveNodeRequest
    ): ApiResponse<NodeResponse> =
        ApiResponse.of(nodeService.moveNode(id, req))

    @PatchMapping("/api/v1/libraries/{id}/nodes/reorder")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'SUPER_ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun reorderNodes(
        @PathVariable id: UUID,
        @Valid @RequestBody req: ReorderNodesRequest
    ) = nodeService.reorderNodes(id, req)

    @DeleteMapping("/api/v1/nodes/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'SUPER_ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteNode(@PathVariable id: UUID) = nodeService.deleteNode(id)

    @GetMapping("/api/v1/libraries/{id}/tree")
    fun getTree(
        @PathVariable id: UUID,
        authentication: Authentication?
    ): ApiResponse<List<TreeNodeResponse>> {
        val callerId = authentication?.principal?.let { UUID.fromString(it as String) }
            ?: throw PlatformException("UNAUTHORIZED", "Authentication required", HttpStatus.UNAUTHORIZED)
        val isClient = authentication.authorities
            ?.none { it.authority in setOf("ROLE_ADMIN", "ROLE_STAFF", "ROLE_SUPER_ADMIN") } ?: true
        return ApiResponse.of(nodeService.getTree(id, callerId, isClient))
    }
}

