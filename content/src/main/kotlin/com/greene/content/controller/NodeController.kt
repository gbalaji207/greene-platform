package com.greene.content.controller

import com.greene.content.dto.UpdateNodeRequest
import com.greene.content.service.NodeService
import com.greene.core.api.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
class NodeController(
    private val nodeService: NodeService,
) {

    @PatchMapping("/api/v1/nodes/{id}")
    fun updateNode(
        @PathVariable id: UUID,
        @RequestBody @Valid request: UpdateNodeRequest,
    ): ResponseEntity<ApiResponse<Unit>> {
        println(">>> NodeController.updateNode called for id=$id request=$request")
        nodeService.updateNode(id, request)
        return ResponseEntity.ok(ApiResponse.of(Unit))
    }
}