package com.greene.content.service

import com.greene.content.domain.ContentLibrary
import com.greene.content.domain.LibraryStatus
import com.greene.content.dto.CreateLibraryRequest
import com.greene.content.dto.LibraryResponse
import com.greene.content.dto.UpdateLibraryRequest
import com.greene.content.repository.ContentLibraryRepository
import com.greene.core.api.response.PagedData
import com.greene.core.exception.PlatformException
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.data.jpa.domain.Specification
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import java.util.UUID

@Service
@Transactional
class LibraryService(
    private val libraryRepository: ContentLibraryRepository
) {

    fun createLibrary(req: CreateLibraryRequest, callerId: UUID): LibraryResponse {
        val library = ContentLibrary(
            name = req.name!!,
            description = req.description,
            createdBy = callerId
        )
        return toResponse(libraryRepository.save(library))
    }

    fun listLibraries(status: LibraryStatus?, page: Int, pageSize: Int): PagedData<LibraryResponse> {
        val spec: Specification<ContentLibrary>? = if (status != null) {
            Specification { root, _, cb -> cb.equal(root.get<LibraryStatus>("status"), status) }
        } else {
            null
        }
        val pageable = PageRequest.of(page - 1, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"))
        val result = libraryRepository.findAll(spec, pageable)
        return PagedData(
            items = result.content.map { toResponse(it) },
            page = page,
            pageSize = pageSize,
            total = result.totalElements
        )
    }

    fun getLibrary(id: UUID): LibraryResponse {
        val library = libraryRepository.findById(id)
            .orElseThrow { PlatformException("LIBRARY_NOT_FOUND", "Library not found", HttpStatus.NOT_FOUND) }
        return toResponse(library)
    }

    fun updateLibrary(id: UUID, req: UpdateLibraryRequest): LibraryResponse {
        val library = libraryRepository.findById(id)
            .orElseThrow { PlatformException("LIBRARY_NOT_FOUND", "Library not found", HttpStatus.NOT_FOUND) }

        if (library.status == LibraryStatus.ARCHIVED) {
            throw PlatformException("LIBRARY_ARCHIVED", "Archived libraries cannot be modified", HttpStatus.UNPROCESSABLE_ENTITY)
        }

        if (req.name == null && req.description == null && req.status == null) {
            throw PlatformException("AT_LEAST_ONE_FIELD_REQUIRED", "At least one field must be provided", HttpStatus.BAD_REQUEST)
        }

        if (req.name != null) library.name = req.name
        if (req.description != null) library.description = req.description.ifEmpty { null }
        if (req.status != null) {
            val allowed = setOf(
                LibraryStatus.DRAFT to LibraryStatus.PUBLISHED,
                LibraryStatus.PUBLISHED to LibraryStatus.DRAFT,
                LibraryStatus.PUBLISHED to LibraryStatus.ARCHIVED
            )
            if (library.status to req.status !in allowed) {
                throw PlatformException("INVALID_STATUS_TRANSITION", "Invalid status transition", HttpStatus.UNPROCESSABLE_ENTITY)
            }
            library.status = req.status
        }

        library.updatedAt = OffsetDateTime.now()
        return toResponse(libraryRepository.save(library))
    }

    private fun toResponse(library: ContentLibrary) = LibraryResponse(
        id = library.id,
        name = library.name,
        description = library.description,
        status = library.status,
        createdBy = library.createdBy,
        createdAt = library.createdAt,
        updatedAt = library.updatedAt
    )
}

