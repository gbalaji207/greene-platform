package com.greene.content.controller

import com.greene.content.dto.InlineImageUploadResponse
import com.greene.content.dto.SaveArticleContentRequest
import com.greene.content.dto.SaveArticleContentResponse
import com.greene.content.service.ContentItemService
import com.greene.core.api.response.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
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

    @Operation(summary = "Upload an inline image for an article")
    @PostMapping("/{id}/files/inline-image", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun uploadInlineImage(
        @PathVariable id: UUID,
        @RequestPart("file") file: MultipartFile,
    ): ApiResponse<InlineImageUploadResponse> {
        val result = contentItemService.uploadInlineImage(id, file)
        return ApiResponse.of(result)
    }
}

