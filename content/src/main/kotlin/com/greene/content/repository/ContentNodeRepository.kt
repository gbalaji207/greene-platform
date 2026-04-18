package com.greene.content.repository

import com.greene.content.domain.ContentNode
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.UUID

interface ContentNodeRepository : JpaRepository<ContentNode, UUID> {

    fun findByLibraryIdOrderBySortOrderAsc(libraryId: UUID): List<ContentNode>

    fun findByParentIdOrderBySortOrderAsc(parentId: UUID): List<ContentNode>

    fun findByLibraryId(libraryId: UUID): List<ContentNode>

    fun findAllByIdIn(nodeIds: List<UUID>): List<ContentNode>

    fun deleteAllByIdIn(nodeIds: List<UUID>)

    @Query(value = """
        WITH RECURSIVE tree AS (
            SELECT * FROM content_nodes WHERE library_id = :libraryId AND parent_id IS NULL
            UNION ALL
            SELECT cn.* FROM content_nodes cn INNER JOIN tree t ON cn.parent_id = t.id
        )
        SELECT * FROM tree ORDER BY depth, sort_order
    """, nativeQuery = true)
    fun fetchTreeByLibraryId(@Param("libraryId") libraryId: UUID): List<ContentNode>

    @Query(value = """
        WITH RECURSIVE descendants AS (
            SELECT id FROM content_nodes WHERE id = :nodeId
            UNION ALL
            SELECT cn.id FROM content_nodes cn INNER JOIN descendants d ON cn.parent_id = d.id
        )
        SELECT id FROM descendants
    """, nativeQuery = true)
    fun findAllDescendantIds(@Param("nodeId") nodeId: UUID): List<UUID>
}

