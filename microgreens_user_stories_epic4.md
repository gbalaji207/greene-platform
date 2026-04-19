# EPIC 4 — Content Management (Redesigned)
**Version:** 1.4
**Date:** April 2026
**Basis:** Brainstorming session — generic, reusable content module
**Backend:** Spring Boot Kotlin (:content module)
**Status Legend:** `[ ]` To Do | `[-]` In Progress | `[x]` Done

---

## Changelog

| Version | Changes                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
|---|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| 1.0 | Initial stories — E4-US1 through E4-US10                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |
| 1.1 | E4-US3 updated: ARTICLE content saved via `POST /api/v1/nodes/{id}/content` (JSON body) instead of multipart file upload. `POST /api/v1/nodes/{id}/files` now VIDEO-only. Embedded video in article HTML explicitly out of scope for Phase 1. E4-US4 updated to match.                                                                                                                                                                                                                                                                                     |
| 1.2 | E4-US3 wording fixes: removed platform-specific references — design is platform-agnostic. Added deferred blog platform features section.                                                                                                                                                                                                                                                                                                                                                                                                                   |
| 1.3 | Added E4-US11 (Client Lists Entitled Libraries) and E4-US12 (Client Navigates Folder Tree — lazy load). E4-US8 updated: full tree fetch is admin/staff only; client navigation uses children endpoints. Deferred section updated: variant mapping (format × locale) belongs in :training not :content — documented architecture decision and training_content_variants table pattern. D7 (wording fix note) removed — resolved in v1.2. Implementation order updated.                                                                                      |
| 1.4 | E4-US1 and E4-US2 marked complete — verified against implementation. Story map Flyway corrected: V7 → V8 (actual migration filename is V8__create_content_tables.sql). E4-US8 reverted: `GET /api/v1/libraries/{id}/tree` serves all authenticated roles — CLIENT gets entitlement-gated filtered response (PUBLISHED nodes only), not 403. This matches the implementation and is simpler than a separate children-only client path. E4-US12 children endpoints remain useful for lazy-loading UX but are no longer the exclusive client navigation path. |
| 1.5 | E4-US3 complete. `updatedAt` in response sourced from `content_files.created_at`. Upsert uses delete+insert pattern (no unique constraint on node_id+file_role). `NodeController` created separately from `ContentItemController` to avoid base-path conflict. New error codes: NODE_TYPE_MISMATCH, NO_PRIMARY_FILE. No Flyway migration needed.                                                                                                                                                                                                           |

---

## Overview

EPIC 4 implements a **generic, domain-agnostic content management system** inside the `:content` Gradle module. It replaces the original crop-specific content stories with a reusable library/folder/item hierarchy.

Domain modules (`:training`, `:farming`) reference content libraries by ID and interact exclusively through the `ContentAccessPort` interface — the `:content` module never imports them.

**Client discovery flow (lazy-loaded):**
```
GET /api/v1/libraries/me              → list entitled libraries (courses)
GET /api/v1/libraries/{id}/children   → root-level folders
GET /api/v1/nodes/{id}/children       → subfolders and items per level
GET /api/v1/training/content-variants → available format + locale per item
GET /api/v1/nodes/{id}/url            → signed URL or HTML for selected variant
```

---

## Story Map

| Story | Title | Flyway | Status |
|---|---|---|--------|
| E4-US1 | Content Library CRUD | V8 | `[x]`  |
| E4-US2 | Folder & Node Tree Management | — | `[x]`  |
| E4-US3 | Content Item — Article | — | `[x]`  |
| E4-US4 | Content Item — Video Upload | — | `[ ]`  |
| E4-US5 | Inline Image Upload for Articles | — | `[x]`  |
| E4-US6 | Content Entitlement & Access Port | — | `[ ]`  |
| E4-US7 | Staff Marks Training as Complete | — | `[ ]`  |
| E4-US8 | Full Tree (all roles) | — | `[ ]`  |
| E4-US9 | Client Fetches Signed URL | — | `[ ]`  |
| E4-US10 | Orphan Cleanup Job | — | `[ ]`  |
| E4-US11 | Client Lists Entitled Libraries | — | `[ ]`  |
| E4-US12 | Client Navigates Folder Tree | — | `[ ]`  |

---

## E4-US1: Content Library CRUD ✅

**As an** admin,
**I want** to create and manage named content libraries,
**So that** I can organise content for different batches and crops independently.

### Acceptance Criteria

- [x] `POST /api/v1/libraries` — `ADMIN`, `STAFF`, `SUPER_ADMIN` only
  - Request: `name` (required, max 255), `description` (optional)
  - Response `201`: `id`, `name`, `description`, `status: DRAFT`, `createdAt`, `updatedAt`
  - Returns `400 VALIDATION_ERROR` if `name` is blank
- [x] `GET /api/v1/libraries` — `ADMIN`, `STAFF`, `SUPER_ADMIN` only; paginated
  - `?page` (default 1, min 1), `?pageSize` (default 20, max 50)
  - Optional filter: `?status=DRAFT|PUBLISHED|ARCHIVED`
  - Each item: `id`, `name`, `description`, `status`, `createdAt`, `updatedAt`
- [x] `GET /api/v1/libraries/{id}` — `ADMIN`, `STAFF`, `SUPER_ADMIN` only
  - Returns `404 LIBRARY_NOT_FOUND` if not found
- [x] `PATCH /api/v1/libraries/{id}` — `ADMIN`, `STAFF`, `SUPER_ADMIN` only; patch semantics
  - Editable fields: `name`, `description`, `status`
  - Valid status transitions: `DRAFT → PUBLISHED`, `PUBLISHED → DRAFT`, `PUBLISHED → ARCHIVED`
  - `ARCHIVED → *` returns `422 LIBRARY_ARCHIVED`
  - Empty request body returns `400 AT_LEAST_ONE_FIELD_REQUIRED`
- [x] Library `status` lifecycle: `DRAFT → PUBLISHED → ARCHIVED`
  - `DRAFT`: not visible to clients
  - `PUBLISHED`: accessible to entitled clients
  - `ARCHIVED`: read-only; no nodes can be added or modified
- [x] Flyway migration: `V8__create_content_tables.sql` in `:content` module
  - Tables: `content_libraries`, `content_nodes`, `content_item_details`, `content_files`, `content_entitlements`
  - All indexes included in V8 (see LLD-Backend for full schema)
  - **Note:** Migration runs as V8 — V7 was used by a prior story in another module

### New Error Codes
| Code | HTTP | Scenario |
|---|---|---|
| `LIBRARY_NOT_FOUND` | 404 | Library ID does not exist |
| `LIBRARY_ARCHIVED` | 422 | Attempt to modify an ARCHIVED library or its nodes |

---

## E4-US2: Folder & Node Tree Management ✅

**As an** admin,
**I want** to create folders inside a library and organise them into a hierarchy,
**So that** content is structured and easy to navigate.

### Acceptance Criteria

- [x] `POST /api/v1/libraries/{id}/nodes` — `ADMIN`, `STAFF`, `SUPER_ADMIN` only
  - Request: `nodeType` (required: `FOLDER` | `ITEM`), `title` (required, max 255), `parentId` (optional UUID — null = root level), `sortOrder` (optional, default 0)
  - If `nodeType = ITEM`: `itemType` (required: `VIDEO` | `ARTICLE`), `summary` (optional, max 500)
  - Response `201`: full node fields including `depth`, `status`, `libraryId`
  - Returns `404 LIBRARY_NOT_FOUND` if library does not exist
  - Returns `422 LIBRARY_ARCHIVED` if library is `ARCHIVED`
  - Returns `404 NODE_NOT_FOUND` if `parentId` provided but not found
  - Returns `422 INVALID_PARENT_NODE` if `parentId` references a node in a different library, or if parent is an `ITEM` node
  - Returns `422 MAX_DEPTH_EXCEEDED` if creating the node would result in `depth > 3`
  - Depth computed by service: `parent.depth + 1`. Root-level nodes have `depth = 1`.
  - `FOLDER` nodes default to `PUBLISHED` status; `ITEM` nodes default to `DRAFT`
- [x] `PATCH /api/v1/nodes/{id}` — `ADMIN`, `STAFF`, `SUPER_ADMIN` only; patch semantics
  - Editable fields: `title`, `summary` (ITEM only), `status` (`DRAFT`|`PUBLISHED`, ITEM only), `durationSeconds` (VIDEO only)
  - Empty body returns `400 AT_LEAST_ONE_FIELD_REQUIRED`
  - `FOLDER` nodes do not have `status` — `status` field ignored for folders
  - Returns `404 NODE_NOT_FOUND` if node does not exist
  - Returns `422 LIBRARY_ARCHIVED` if parent library is `ARCHIVED`
- [x] `PATCH /api/v1/nodes/{id}/move` — `ADMIN`, `STAFF`, `SUPER_ADMIN` only
  - Request: `newParentId` (nullable — null = move to root), `sortOrder`
  - Validates new parent is in the same library — returns `422 MOVE_CROSS_LIBRARY` if not
  - Recomputes `depth` for moved node and all descendants
  - Returns `422 MAX_DEPTH_EXCEEDED` if move would cause any descendant to exceed depth 3
  - Returns `422 INVALID_PARENT_NODE` if new parent is an ITEM node
- [x] `PATCH /api/v1/libraries/{id}/nodes/reorder` — `ADMIN`, `STAFF`, `SUPER_ADMIN` only
  - Request: `parentId` (nullable for root-level), `orderedNodeIds: [uuid, uuid, ...]`
  - All node IDs must belong to the same parent and library — returns `400 VALIDATION_ERROR` otherwise
  - Updates `sortOrder` for all provided nodes in a single transaction
- [x] `DELETE /api/v1/nodes/{id}` — `ADMIN`, `STAFF`, `SUPER_ADMIN` only
  - Cascade deletes all descendant nodes
  - Deletes all associated `content_files` rows
  - Deletes associated files from B2/MinIO (best-effort — log failures, do not block response)
  - Response `204`

### New Error Codes
| Code | HTTP | Scenario |
|---|---|---|
| `NODE_NOT_FOUND` | 404 | Node ID does not exist |
| `MAX_DEPTH_EXCEEDED` | 422 | Node creation or move would exceed depth 3 |
| `INVALID_PARENT_NODE` | 422 | Parent is an ITEM node, or belongs to a different library |
| `MOVE_CROSS_LIBRARY` | 422 | Moving a node to a parent in a different library |

---

## E4-US3: Content Item — Article

**As an** admin,
**I want** to write and publish rich HTML articles as content items,
**So that** clients get detailed, well-formatted growing guidance.

### Design Note — Why JSON body, not file upload
The client editor produces an HTML string in memory. Sending it as a JSON body is the natural fit — no temp file, no multipart overhead, simpler on both sides regardless of platform (Flutter, React, iOS native, etc.). The backend stores it as `primary.html` in B2/MinIO internally; the caller never needs to know that. File upload (`multipart/form-data`) is reserved for binary uploads (VIDEO, inline images).

### Endpoint Split
| Endpoint | Payload | Purpose |
|---|---|---|
| `POST /api/v1/nodes/{id}/content` | `application/json` | Save ARTICLE HTML content |
| `POST /api/v1/nodes/{id}/files` | `multipart/form-data` | VIDEO binary upload only |
| `POST /api/v1/nodes/{id}/files/inline-image` | `multipart/form-data` | Inline images within articles |

### Acceptance Criteria

- [X] An `ARTICLE` node is created via `POST /api/v1/libraries/{id}/nodes` with `nodeType: ITEM`, `itemType: ARTICLE`
  - Node created in `DRAFT` status — client application opens the editor with the returned `nodeId`
- [X] `POST /api/v1/nodes/{id}/content` — `ADMIN`, `STAFF`, `SUPER_ADMIN` only; ARTICLE nodes only
  - `Content-Type: application/json`
  - Request body:
    ```json
    {
      "htmlContent": "<h1>Week 1 Guide</h1><p>...</p><img src=\"content/{nodeId}/images/{uuid}.jpg\">",
      "summary": "Overview of week 1 setup and watering"
    }
    ```
  - `htmlContent` required, max 500 KB (configurable via `content.max-article-size-kb`)
  - `summary` optional, max 500 chars — updates `content_item_details.summary`
  - Backend converts `htmlContent` string to bytes, uploads to B2/MinIO at key: `content/{nodeId}/primary.html`
  - Upserts `content_files` row: `file_role = PRIMARY`, `mime_type = text/html`
  - Response `200`: `{ nodeId, hasFile: true, updatedAt }`
  - Calling again overwrites the existing `primary.html` in B2/MinIO (upsert — idempotent)
  - Returns `400 VALIDATION_ERROR` if `htmlContent` is blank
  - Returns `413 FILE_TOO_LARGE` if `htmlContent` exceeds size limit
  - Returns `404 NODE_NOT_FOUND` if node does not exist
  - Returns `422 NODE_TYPE_MISMATCH` if node is not an ARTICLE ITEM
  - Returns `422 LIBRARY_ARCHIVED` if parent library is ARCHIVED
- [X] Inline images in the HTML are referenced by their B2/MinIO `fileKey` as the `src` attribute
  - Example: `<img src="content/{nodeId}/images/{uuid}.jpg">`
  - These keys are uploaded separately via `POST /api/v1/nodes/{id}/files/inline-image` (E4-US5)
  - Backend does **not** validate image key references in the HTML on save — lenient on write, resolved on read (E4-US9)
- [X] Embedded video in article HTML is **out of scope for Phase 1**
  - Articles may reference standalone VIDEO nodes via their `/url` endpoint
  - Direct `<video>` tags with B2 signed URLs are not supported in this phase — see Deferred D4
- [X] `hasFile` field on node response: `true` once `POST /api/v1/nodes/{id}/content` has been called at least once
- [X] Publishing (`PATCH /api/v1/nodes/{id}` `{ status: PUBLISHED }`) without prior content save returns `422 NO_PRIMARY_FILE`
- [X] ARTICLE nodes do not have a `durationSeconds` field — ignored if provided in any request

### New Error Codes
| Code | HTTP | Scenario |
|---|---|---|
| `NODE_TYPE_MISMATCH` | 422 | Content save or file upload attempted on wrong node type |
| `NO_PRIMARY_FILE` | 422 | Attempt to publish an ITEM with no content saved yet |

---

## E4-US4: Content Item — Video Upload

**As an** admin,
**I want** to upload video files as content items,
**So that** clients can watch instructional videos within the app.

### Acceptance Criteria

- [ ] A `VIDEO` node is created via `POST /api/v1/libraries/{id}/nodes` with `nodeType: ITEM`, `itemType: VIDEO`
  - Node created in `DRAFT` status
  - Optional fields on creation: `summary`, `durationSeconds`
- [ ] `POST /api/v1/nodes/{id}/files` — `ADMIN`, `STAFF`, `SUPER_ADMIN` only; **VIDEO nodes only**
  - `Content-Type: multipart/form-data` with a single file field
  - Accepted MIME type: `video/mp4` only
  - File validated by magic bytes (`ftyp` box at byte 4–7 for MP4) — not extension alone
  - File stored at key: `content/{nodeId}/primary.mp4`
  - Upserts `content_files` row: `file_role = PRIMARY`, `mime_type = video/mp4`
  - Response `201`: `{ fileId, fileKey, mimeType, sizeBytes }`
  - Calling again overwrites the existing `primary.mp4` in B2/MinIO (upsert — idempotent)
  - Returns `415 INVALID_FILE_TYPE` if file is not a valid MP4
  - Returns `413 FILE_TOO_LARGE` if file exceeds `content.max-video-size-mb` in `application.yml`
  - Returns `404 NODE_NOT_FOUND` if node does not exist
  - Returns `422 NODE_TYPE_MISMATCH` if called on an ARTICLE node or FOLDER node
  - Returns `422 LIBRARY_ARCHIVED` if parent library is ARCHIVED
- [ ] `durationSeconds` updatable separately via `PATCH /api/v1/nodes/{id}` — admin enters manually
- [ ] Same publish guard as ARTICLE: `422 NO_PRIMARY_FILE` if no file uploaded before publish
- [ ] VIDEO node response includes `durationSeconds` (nullable until set)

---

## E4-US5: Inline Image Upload for Articles

**As an** admin,
**I want** to upload images while writing an article,
**So that** the WYSIWYG editor can embed them in the article HTML.

### Acceptance Criteria

- [x] POST /api/v1/nodes/{id}/files/inline-image -- ADMIN, STAFF, SUPER_ADMIN only
  - [x] Accepts multipart/form-data, single file field named "file"
  - [x] Accepted MIME types: image/jpeg, image/png, image/gif
  - [x] Validated by magic bytes via shared ImageTypeDetector utility in :core (FF D8 FF for JPEG, 89 50 4E 47 for PNG, 47 49 46 38 for GIF)
  - [x] Extension derived from MIME type: jpeg->jpg, png->png, gif->gif
  - [x] Stored at: content/{nodeId}/images/{uuid}.{ext}
  - [x] Inserts content_files row: file_role = INLINE_IMAGE, mime_type, size_bytes
  - [x] Response 201: { fileId, fileKey, mimeType, sizeBytes }
  - [x] fileKey is raw B2/MinIO object key (not signed URL)
  - [x] Returns 415 INVALID_FILE_TYPE if magic bytes do not match
  - [x] Returns 413 FILE_TOO_LARGE if exceeds content.max-image-size-kb (default 2048)
  - [x] Returns 404 NODE_NOT_FOUND
  - [x] Returns 422 NODE_TYPE_MISMATCH if nodeType is FOLDER or itemType != ARTICLE
  - [x] Returns 422 LIBRARY_ARCHIVED
- [x] Multiple inline images allowed -- each inserts its own content_files row (INSERT, not upsert)
- [x] No Flyway migration -- content_files + INLINE_IMAGE already exist (V8)
- [x] No DELETE endpoint -- orphan cleanup deferred to E4-US10

---

## E4-US6: Content Entitlement & Access Port

**As a** platform,
**I want** a clean entitlement system with a port interface,
**So that** other modules can grant content access without coupling to :content internals.

### Acceptance Criteria

- [ ] `ContentAccessPort` interface defined in `:content` module:
  ```kotlin
  interface ContentAccessPort {
      fun grantAccess(userId: UUID, libraryId: UUID, grantedBy: UUID)
      fun bulkGrantAccess(userIds: List<UUID>, libraryId: UUID, grantedBy: UUID)
      fun revokeAccess(userId: UUID, libraryId: UUID)
      fun hasAccess(userId: UUID, libraryId: UUID): Boolean
  }
  ```
- [ ] `ContentAccessPortImpl` implements the interface inside `:content`
  - `grantAccess`: upsert into `content_entitlements` — if row exists (active or revoked), reset `revoked_at = null`, update `granted_by` and `granted_at`. If no row, insert.
  - `bulkGrantAccess`: single transaction batch upsert for all `userIds`
  - `revokeAccess`: sets `revoked_at = now()` where `user_id = userId` and `library_id = libraryId` and `revoked_at IS NULL`
  - `hasAccess`: `SELECT EXISTS` where `user_id = userId` AND `library_id = libraryId` AND `revoked_at IS NULL`
- [ ] `:training` module receives `ContentAccessPort` via constructor injection (wired in `:app` module)
  - `:content` is NOT imported by `:training` — port interface only
- [ ] `content_entitlements` table: unique constraint on `(user_id, library_id)`
- [ ] `ADMIN`, `STAFF`, `SUPER_ADMIN` roles always bypass entitlement checks — checked by role in service layer, not via entitlement table
- [ ] No REST endpoints exposed for entitlement management in Phase 1 — port is internal only

---

## E4-US7: Staff Marks Training as Complete

**As a** staff member,
**I want** to mark a batch's training as complete,
**So that** all enrolled clients automatically get access to the batch's content library.

### Acceptance Criteria

- [ ] `PATCH /api/v1/bookings/{id}/training` — `ADMIN`, `STAFF`, `SUPER_ADMIN` only
  - Request: `{ "trainingComplete": true }`
  - Sets `training_complete = true` and `training_completed_at = now()` on the booking record
  - Returns `404 BOOKING_NOT_FOUND` if booking does not exist
  - Returns `422 TRAINING_ALREADY_COMPLETE` if `training_complete` is already `true`
  - Returns `422 BOOKING_NOT_CONFIRMED` if booking status is not `CONFIRMED`
- [ ] `PATCH /api/v1/batches/{id}/training` — `ADMIN`, `STAFF`, `SUPER_ADMIN` only — bulk action
  - Request: `{ "trainingComplete": true }`
  - Marks `training_complete = true` for **all** `CONFIRMED` bookings in the batch
  - Skips bookings already marked complete (no error — idempotent)
  - If the batch has a `content_library_id`: calls `ContentAccessPort.bulkGrantAccess()` for all newly marked clients
  - If `content_library_id` is null on the batch: training marked complete but no content grant triggered (no error)
  - Response `200`: `{ "markedComplete": N, "alreadyComplete": M, "contentAccessGranted": true|false }`
  - Returns `404 BATCH_NOT_FOUND` if batch does not exist
- [ ] FCM notification dispatched to each newly entitled client — deferred to `:notif` module story
- [ ] `training_complete` and `training_completed_at` columns already exist in `bookings` table (V5 migration)
- [ ] No new Flyway migration required for this story

### New Error Codes
| Code | HTTP | Scenario |
|---|---|---|
| `TRAINING_ALREADY_COMPLETE` | 422 | Training already marked complete for this booking |
| `BOOKING_NOT_CONFIRMED` | 422 | Booking must be CONFIRMED before training can be marked complete |

---

## E4-US8: Full Content Tree

**As any** authenticated user,
**I want** to fetch the full content tree for a library in one call,
**So that** I can see the complete structure — admin/staff for management, clients for browsing.

### Design Decision
`GET /api/v1/libraries/{id}/tree` serves all authenticated roles. The response is role-aware:
- **CLIENT** — entitlement gated; receives PUBLISHED nodes only; `status` field omitted
- **ADMIN/STAFF/SUPER_ADMIN** — no entitlement gate; receives all nodes (DRAFT + PUBLISHED); `status` field included

Clients may also use the lazy-loaded children endpoints (E4-US12) for better mobile performance, but the full tree endpoint remains available to them.

### Acceptance Criteria

- [ ] `GET /api/v1/libraries/{id}/tree` — all authenticated roles; unauthenticated returns `401`
  - Executes `WITH RECURSIVE` CTE on `content_nodes`
  - Returns `404 LIBRARY_NOT_FOUND` if library does not exist
  - Response is nested JSON tree — each node includes `children: []`
- [ ] **CLIENT behaviour:**
  - `CLIENT` requesting a `DRAFT` or `ARCHIVED` library: returns `404 LIBRARY_NOT_FOUND`
  - `CLIENT` without active entitlement: returns `403 CONTENT_LOCKED`
  - `CLIENT` with active entitlement: returns `PUBLISHED` ITEM nodes only; all FOLDER nodes included
  - `status` field omitted from CLIENT response
- [ ] **ADMIN/STAFF/SUPER_ADMIN behaviour:**
  - No entitlement check — always returns full tree
  - Returns all nodes (DRAFT + PUBLISHED)
  - `status` field included in response
- [ ] Tree response node fields:
  - `id`, `nodeType`, `title`, `sortOrder`, `depth`
  - For ITEM: `itemType`, `summary`, `durationSeconds` (VIDEO only), `hasFile`
  - `status` — included for ADMIN/STAFF/SUPER_ADMIN, omitted for CLIENT
  - `children: []` for recursive structure

### New Error Codes
| Code | HTTP | Scenario |
|---|---|---|
| `CONTENT_LOCKED` | 403 | Client requests content without active entitlement |

---

## E4-US9: Client Fetches Signed URL

**As a** client,
**I want** to get a time-limited URL to view a content item,
**So that** I can watch a video or read an article securely within the app.

### Acceptance Criteria

- [ ] `GET /api/v1/nodes/{id}/url` — all authenticated roles
  - `CLIENT`: entitlement check required — returns `403 CONTENT_LOCKED` if not entitled
  - `ADMIN`/`STAFF`/`SUPER_ADMIN`: bypass entitlement check
  - Returns `404 NODE_NOT_FOUND` if node does not exist
  - Returns `422 NODE_TYPE_MISMATCH` if node is a FOLDER (folders have no file)
  - Returns `422 NO_PRIMARY_FILE` if ITEM has no PRIMARY file uploaded
  - Returns `404 LIBRARY_NOT_FOUND` if parent library is DRAFT or ARCHIVED and caller is CLIENT
- [ ] For **VIDEO** nodes:
  - Generates B2/MinIO pre-signed URL for `content/{nodeId}/primary.mp4`
  - Signed URL expiry: 15 minutes (configurable via `content.signed-url-expiry-minutes`)
  - Response: `{ "itemType": "VIDEO", "videoUrl": "<signed-url>", "expiresAt": "<ISO 8601>" }`
- [ ] For **ARTICLE** nodes:
  - Fetches `content/{nodeId}/primary.html` from B2/MinIO internally (key never exposed to client)
  - Loads all `INLINE_IMAGE` file rows for the node from `content_files`
  - Generates a signed URL for each inline image (same 15-minute expiry)
  - Rewrites all occurrences of each `fileKey` in the HTML string with its signed URL
  - Response: `{ "itemType": "ARTICLE", "htmlContent": "<rewritten-html>", "expiresAt": "<ISO 8601>" }`
- [ ] Signed URL generation uses the platform's existing `StorageService`
  - Add `generatePresignedUrl(key: String, expiryMinutes: Int): String` method to `StorageService` interface in `:core`

---

## E4-US10: Orphan Cleanup Job

**As a** platform,
**I want** orphaned draft content nodes cleaned up automatically,
**So that** incomplete content from abandoned editing sessions does not accumulate.

### Acceptance Criteria

- [ ] `OrphanCleanupJob` — Spring `@Scheduled` job in `:content` module
  - Schedule: weekly, configurable via `content.orphan-cleanup-cron` in `application.yml` (default: `0 0 2 * * MON` — 2 AM every Monday)
  - Target: `ContentNode` records where:
    - `node_type = ITEM`
    - `status = DRAFT`
    - `created_at < now() - 7 days` (configurable via `content.orphan-threshold-days`, default 7)
    - No `PRIMARY` file exists in `content_files` for this node
  - For each orphan:
    1. Load all `content_files` rows for the node (may include INLINE_IMAGE files)
    2. Delete files from B2/MinIO (best-effort — log individual failures, continue)
    3. Delete `content_files` rows
    4. Delete `content_item_details` row
    5. Delete `content_node` row
  - Log total orphans found and deleted at `INFO` level
  - Log individual B2 deletion failures at `WARN` level — do not rethrow
- [ ] Cleanup does not touch `FOLDER` nodes or `PUBLISHED` nodes — only DRAFT ITEM nodes with no PRIMARY file
- [ ] Cleanup is skipped for nodes younger than threshold — avoids deleting in-progress editing sessions
- [ ] Job is `@ConditionalOnProperty(name = "content.orphan-cleanup-enabled", havingValue = "true", matchIfMissing = true)` — can be disabled in test environments

---

## E4-US11: Client Lists Entitled Libraries

**As a** client who has completed training,
**I want** to see the content libraries I have access to,
**So that** I can find and open the course relevant to my training.

### Acceptance Criteria

- [ ] `GET /api/v1/libraries/me` — `CLIENT` only
  - Returns all libraries where the calling client has an active (non-revoked) entitlement in `content_entitlements`
  - Only `PUBLISHED` libraries returned — `DRAFT` and `ARCHIVED` excluded even if entitlement exists
  - Response: paginated list
    - `?page` (default 1, min 1), `?pageSize` (default 20, max 50)
  - Each item: `id`, `name`, `description`, `updatedAt`
  - Returns empty list (not 404) if client has no active entitlements
  - `ADMIN`, `STAFF`, `SUPER_ADMIN` calling this endpoint returns `403 FORBIDDEN` — use `GET /api/v1/libraries` instead
- [ ] Response is ordered by `content_entitlements.granted_at` descending — most recently unlocked library first
- [ ] Entitlement check is performed live — not cached

---

## E4-US12: Client Navigates Folder Tree

**As a** client,
**I want** to browse the content inside a library one level at a time,
**So that** the app loads quickly and I can drill into the folders I need.

### Design Note — Why lazy-load, not full tree
The full recursive tree (`GET /api/v1/libraries/{id}/tree`) is reserved for admin/staff management use. For clients, fetching the entire tree upfront over-fetches data — a library with 50+ items would return a large payload on every screen open. Instead, the client fetches one level at a time as the user taps into folders. Each API call is small, fast, and targeted.

### Acceptance Criteria

- [ ] `GET /api/v1/libraries/{id}/children` — entitled `CLIENT`, `ADMIN`, `STAFF`, `SUPER_ADMIN`
  - Returns root-level nodes only (`depth = 1`, `parent_id IS NULL`) for the given library
  - `CLIENT` entitlement check: returns `403 CONTENT_LOCKED` if not entitled
  - `CLIENT` requesting a `DRAFT` or `ARCHIVED` library: returns `404 LIBRARY_NOT_FOUND`
  - `CLIENT` response: `PUBLISHED` ITEM nodes only + all FOLDER nodes
  - `ADMIN`/`STAFF`/`SUPER_ADMIN` response: all nodes regardless of status
  - Ordered by `sort_order` ascending
  - Returns `404 LIBRARY_NOT_FOUND` if library does not exist
- [ ] `GET /api/v1/nodes/{id}/children` — entitled `CLIENT`, `ADMIN`, `STAFF`, `SUPER_ADMIN`
  - Returns immediate children of the given FOLDER node only — not recursive
  - Returns `404 NODE_NOT_FOUND` if node does not exist
  - Returns `422 INVALID_PARENT_NODE` if node is an ITEM (items cannot have children)
  - `CLIENT` entitlement check: checks entitlement against the node's parent library
  - `CLIENT` response: `PUBLISHED` ITEM nodes only + all FOLDER nodes
  - `ADMIN`/`STAFF`/`SUPER_ADMIN` response: all nodes regardless of status
  - Ordered by `sort_order` ascending
- [ ] Node response fields (both endpoints):
  - For FOLDER: `id`, `nodeType: FOLDER`, `title`, `sortOrder`, `depth`, `hasChildren` (boolean — true if node has any children)
  - For ITEM: `id`, `nodeType: ITEM`, `itemType`, `title`, `summary`, `sortOrder`, `depth`, `hasFile`, `durationSeconds` (VIDEO only)
  - `status` field omitted from CLIENT response; included for ADMIN/STAFF/SUPER_ADMIN
- [ ] `hasChildren` on FOLDER nodes: allows the client to show an expand arrow without fetching children prematurely
  - For CLIENT: `hasChildren = true` if folder has at least one PUBLISHED ITEM descendant or any FOLDER child
  - For ADMIN/STAFF: `hasChildren = true` if folder has any child regardless of status

### New Error Codes
| Code | HTTP | Scenario |
|---|---|---|
| `CONTENT_LOCKED` | 403 | Client requests content without active entitlement (already defined in E4-US8) |

---

## Summary of New Error Codes (EPIC 4)

| Code | HTTP | Story |
|---|---|---|
| `LIBRARY_NOT_FOUND` | 404 | E4-US1 |
| `LIBRARY_ARCHIVED` | 422 | E4-US1 |
| `NODE_NOT_FOUND` | 404 | E4-US2 |
| `MAX_DEPTH_EXCEEDED` | 422 | E4-US2 |
| `INVALID_PARENT_NODE` | 422 | E4-US2, E4-US12 |
| `MOVE_CROSS_LIBRARY` | 422 | E4-US2 |
| `CONTENT_LOCKED` | 403 | E4-US8, E4-US9, E4-US12 |
| `NODE_TYPE_MISMATCH` | 422 | E4-US3, E4-US4, E4-US5, E4-US9 |
| `NO_PRIMARY_FILE` | 422 | E4-US3, E4-US4, E4-US9 |
| `TRAINING_ALREADY_COMPLETE` | 422 | E4-US7 |
| `BOOKING_NOT_CONFIRMED` | 422 | E4-US7 |

---

## Flyway Migration: V8

**File:** `V8__create_content_tables.sql`
**Module:** `:content`
**Includes:** All 5 tables + all indexes in a single migration
**Status:** ✅ Applied

Tables created:
- `content_libraries`
- `content_nodes`
- `content_item_details`
- `content_files`
- `content_entitlements`

**Note:** Migration runs as V8 — V7 was used by a prior story in another module.

See LLD-Backend document for full schema.

---

## Implementation Order (Recommended)

| Order | Story | Why |
|---|---|---|
| 1 | E4-US1 | V8 migration — creates all tables ✅ done |
| 2 | E4-US6 | ContentAccessPort — needed by E4-US7 |
| 3 | E4-US2 | Tree management ✅ done |
| 4 | E4-US3 | Article content save |
| 5 | E4-US5 | Inline image upload (depends on article nodes) |
| 6 | E4-US4 | Video upload |
| 7 | E4-US7 | Training complete — depends on port from E4-US6 |
| 8 | E4-US8 | Admin full tree — depends on nodes from E4-US2 |
| 9 | E4-US11 | Client library list — depends on entitlements from E4-US6 |
| 10 | E4-US12 | Client folder navigation — depends on nodes from E4-US2 |
| 11 | E4-US9 | Signed URL — depends on files from E4-US3/4/5 |
| 12 | E4-US10 | Orphan cleanup — standalone, any order after E4-US3 |

---

## Deferred — Blog Platform Capabilities

> **Context:** The `:content` module is intended to be reusable as a general-purpose blog/content platform (similar to Medium or WordPress). The Phase 1 stories above implement the foundational layer. The features below are identified gaps between the current design and a full blog platform capability. They are **not in scope for Phase 1** but must be addressed before the module can be considered blog-complete.

---

### D1 — Article Metadata (Blog Essentials)

These fields are standard on every blog platform and are missing from `content_item_details`:

| Field | Type | Notes |
|---|---|---|
| `authorId` | UUID | Who wrote the article. Plain UUID ref — no FK constraint (cross-module safe). Displayed as author name + avatar on the article. |
| `publishedAt` | TIMESTAMPTZ | Set automatically when status transitions to `PUBLISHED` for the first time. Immutable after first publish — not overwritten on re-publish. |
| `readingTimeMinutes` | INT | Auto-calculated server-side on content save. Formula: word count ÷ 200 (average reading speed). Not a user input. |
| `slug` | VARCHAR(255) | URL-safe identifier auto-generated from title (e.g. `week-1-watering-guide`). Unique per library. Enables SEO-friendly URLs. |

**Implementation note:** All four fields can be added in a single Flyway migration on `content_item_details`. No breaking changes to existing ACs — all are additive columns.

---

### D2 — Cover Image

Every blog article has a hero/banner image displayed at the top. Currently the `content_files` table supports `file_role = PRIMARY | INLINE_IMAGE`. A third role is needed:

- `file_role = COVER_IMAGE` — one per ARTICLE node
- New endpoint: `POST /api/v1/nodes/{id}/files/cover-image`
  - Validation: `image/jpeg`, `image/png`, magic bytes
  - Stored at: `content/{nodeId}/cover.{ext}`
- Cover image returned as a signed URL in the `GET /api/v1/nodes/{id}/url` response alongside `htmlContent`
- Cover image signed URL included in `GET /api/v1/libraries/{id}/children` and `GET /api/v1/nodes/{id}/children` responses for article card display

---

### D3 — Tags

Tags enable content discovery and filtering. Two options were evaluated:

| Option | Description | Verdict |
|---|---|---|
| **A — Free-form tags** | Admin types any string. Stored as text array on article. Like Medium. | Recommended for Phase 1 when ready |
| **B — Managed tag library** | Admin pre-creates tags, assigns to articles. Like WordPress categories. | Better for large content libraries |

**Recommended approach when implementing:** Option A (free-form). Stored as a `text[]` PostgreSQL array column on `content_item_details` or a simple `content_node_tags(node_id, tag)` join table.

New query parameter needed: `GET /api/v1/nodes/{id}/children?tag=watering` — filters returned items to those tagged.

---

### D4 — Embedded Video in Articles

Currently `POST /api/v1/nodes/{id}/content` stores article HTML as-is. Inline `<video>` tags referencing B2 assets are not signed — E4-US9 only rewrites `INLINE_IMAGE` file keys.

To support embedded video within article body:
- New `file_role = INLINE_VIDEO` in `content_files`
- New endpoint: `POST /api/v1/nodes/{id}/files/inline-video` — uploads short MP4 clips
- Stored at: `content/{nodeId}/videos/{uuid}.mp4`
- E4-US9 extended to also rewrite `INLINE_VIDEO` keys to signed URLs in the HTML

**Note:** Distinct from standalone VIDEO nodes. Standalone videos are full recordings. Inline videos are short clips embedded mid-article.

---

### D5 — Multi-Format and Multilingual Content (Architecture Decision)

> **Decision recorded: variant mapping belongs in the consuming domain module (:training), not in :content.**

**Background:** Two use cases were identified during brainstorming:
1. Same content available in both HTML and Video format — client can toggle
2. Same content available in multiple languages (English, Tamil) — client sees their preferred language

**Why NOT in :content:**
- Format and language relationships are **domain concerns**, not content storage concerns
- A news module or blog module using `:content` would never need this complexity
- Putting variants in `:content` would couple a generic module to domain-specific business rules
- The `:content` module's job is storage and access control — nothing else
- New use cases in future modules should not require changes to `:content`

**Correct architecture — variant mapping table in :training:**

```sql
-- In :training module (not :content)
training_content_variants (
  id           UUID PK,
  node_id      UUID,          -- plain UUID ref to content_nodes — no FK constraint
  title        VARCHAR(255),  -- logical content title e.g. "Seeding Guide"
  format       VARCHAR(10),   -- HTML | VIDEO
  locale       VARCHAR(10),   -- BCP 47: en, ta, hi — nullable = language-neutral
  sort_order   INT,
  created_at   TIMESTAMPTZ,
  UNIQUE (node_id, format, locale)
)
```

**How it works:**
- Admin creates one `:content` node per physical variant ("Seeding Guide - EN HTML", "Seeding Guide - TA HTML", etc.)
- `:training` orchestrates node creation + mapping insert in a single API call — admin does one action
- Client calls `GET /api/v1/training/content-variants?nodeGroupTitle=Seeding Guide&libraryId={id}` → gets available format + locale combinations
- Client picks preferred format + locale → calls `GET /api/v1/nodes/{nodeId}/url`
- Fallback logic (e.g. fall back to English if Tamil not available) lives entirely in `:training` — `:content` is unaffected

**Admin journey (orchestrated by :training):**
```
POST /api/v1/training/content-variants
{
  "title": "Seeding Guide",
  "libraryId": "{id}",
  "parentNodeId": "{day1-folder-id}",
  "format": "HTML",
  "locale": "en"
}
→ :training internally creates the :content node + inserts mapping row
→ Returns { variantId, nodeId }
```

**Stories needed (in :training EPIC, not here):**
- Staff creates content variant (orchestrates node creation + mapping)
- Client fetches available variants for a content group
- Fallback logic when preferred locale/format not available

---

### D6 — Reader Engagement (Phase 2+)

| Feature | Notes |
|---|---|
| **View count** | Increment on every `GET /api/v1/nodes/{id}/url` call by a CLIENT. Simple counter on `content_item_details`. |
| **Reactions / likes** | `POST /api/v1/nodes/{id}/reactions` — CLIENT toggles a like. `content_reactions(node_id, user_id)` table. |
| **Comments** | `POST /api/v1/nodes/{id}/comments` — CLIENT adds a comment. Needs moderation workflow. Separate `content_comments` table. |

---

### D7 — SEO & Discoverability (Phase 3)

| Feature | Notes |
|---|---|
| **Open Graph meta** | `GET /api/v1/nodes/{id}/meta` — returns `og:title`, `og:description`, `og:image` (cover image signed URL) for social sharing previews. |
| **Article series** | Link related articles with `previousNodeId` / `nextNodeId` on `content_item_details`. Enables "Part 2 of 3" navigation. |
| **Full-text search** | PostgreSQL `tsvector` column on article content. `GET /api/v1/libraries/{id}/search?q=watering` returns matching items. |
| **Revision history** | Store previous versions of `primary.html` in B2 with timestamp suffix. `GET /api/v1/nodes/{id}/revisions` returns version list. High storage cost — evaluate before implementing. |

---

### D8 — Video Proxy Streaming (Phase 3)

Currently videos are delivered via short-lived B2/MinIO pre-signed URLs (15 min). For stronger content protection, Phase 3 should implement backend proxy streaming:

- `GET /api/v1/nodes/{id}/url` returns a stream URL pointing to the backend instead of a direct B2 URL
- Backend streams bytes from B2 to client with Range request support (for seek/resume)
- B2 URL never leaves the server
- Tradeoff: Hetzner VPS handles video bandwidth — evaluate with expected video library size before implementing
