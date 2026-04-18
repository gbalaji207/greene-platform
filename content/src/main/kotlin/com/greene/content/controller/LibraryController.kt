package com.greene.content.controller

import com.greene.content.domain.LibraryStatus
import com.greene.content.dto.CreateLibraryRequest
import com.greene.content.dto.LibraryResponse
import com.greene.content.dto.UpdateLibraryRequest
import com.greene.content.service.LibraryService
import com.greene.core.api.response.ApiResponse
import com.greene.core.api.response.PagedData
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import org.springframework.http.HttpStatus
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/libraries")
@PreAuthorize("hasAnyRole('ADMIN', 'STAFF', 'SUPER_ADMIN')")
class LibraryController(
    private val libraryService: LibraryService
) {

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createLibrary(
        @Valid @RequestBody req: CreateLibraryRequest,
        authentication: Authentication
    ): ApiResponse<LibraryResponse> {
        val callerId = UUID.fromString(authentication.principal as String)
        return ApiResponse.of(libraryService.createLibrary(req, callerId))
    }

    @GetMapping
    fun listLibraries(
        @RequestParam(required = false) status: LibraryStatus?,
        @RequestParam(defaultValue = "1") @Min(1) page: Int,
        @RequestParam(defaultValue = "20") @Min(1) @Max(50) pageSize: Int
    ): ApiResponse<PagedData<LibraryResponse>> {
        return ApiResponse.of(libraryService.listLibraries(status, page, pageSize))
    }

    @GetMapping("/{id}")
    fun getLibrary(@PathVariable id: UUID): ApiResponse<LibraryResponse> {
        return ApiResponse.of(libraryService.getLibrary(id))
    }

    @PatchMapping("/{id}")
    fun updateLibrary(
        @PathVariable id: UUID,
        @Valid @RequestBody req: UpdateLibraryRequest
    ): ApiResponse<LibraryResponse> {
        return ApiResponse.of(libraryService.updateLibrary(id, req))
    }
}

