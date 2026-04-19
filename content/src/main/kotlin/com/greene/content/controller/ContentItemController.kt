package com.greene.content.controller

import com.greene.content.dto.SaveArticleContentRequest
import com.greene.content.dto.SaveArticleContentResponse
import com.greene.content.service.ContentItemService
import com.greene.core.api.response.ApiResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/nodes")
class ContentItemController(
    private val contentItemService: ContentItemService,
) {

    @PostMapping("/{id}/content")
    fun saveArticleContent(
        @PathVariable id: UUID,
        @RequestBody @Valid request: SaveArticleContentRequest,
        authentication: Authentication,
    ): ResponseEntity<ApiResponse<SaveArticleContentResponse>> {
        val actor = UUID.fromString(authentication.principal as String)
        val response = contentItemService.saveArticleContent(id, request, actor)
        return ResponseEntity.ok(ApiResponse.of(response))
    }
}

