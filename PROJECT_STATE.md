# Greene Platform — Project State Reference

**Last Updated:** April 2026  
**Purpose:** Persistent reference for continuing development across chat sessions.  
Always check this file at the start of a new chat to get up to speed quickly.

---

## Current State Summary

| Item | Value                                    |
|---|------------------------------------------|
| User Stories Doc | v3.16                                    |
| Last Completed EPIC | EPIC 3 — Batch Management (in progress)  |
| Last Completed Story | E4-US5: Inline Image Upload for Articles |
| Next Story | E4-US4: Content Item — Video Upload      |
| Next Flyway Migration | V9                                       |
| Phase | Phase 1 — Local Dev & APIs               |
| Overall Progress | ~22% (~18 of 81 stories done)            |

---

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Spring Boot 3.4.5, Kotlin 2.1.20, JDK 21, Gradle 9.2.1 |
| Project structure | Gradle multi-module monolith |
| Database | PostgreSQL 15 (local install, not Docker), Flyway migrations |
| Cache / Rate limiting | Redis 7 |
| Email (dev) | Mailpit (localhost:1025, UI at localhost:8025) |
| Email (prod) | Amazon SES |
| Object storage (dev) | MinIO (localhost:9000, UI at localhost:9001) |
| Object storage (prod) | Backblaze B2 / AWS S3 |
| Auth | OTP-only via email, JWT RS256 access token + opaque refresh token |
| API docs | Swagger UI at /swagger-ui/index.html |
| Frontend | Flutter PWA (Phase 2 — not started) |

---

## Gradle Modules

| Module | Purpose |
|---|---|
| `:core` | Auth, JWT, RBAC, users, profile, shared DTOs, exceptions, storage |
| `:training` | Batch management, booking workflow |
| `:farming` | Tray tracking, crop checklists, reminders, streak |
| `:journal` | Daily journal entries, photos, staff comments |
| `:content` | Crop library, premium content, session materials |
| `:notif` | Push notification dispatch, FCM integration |
| `:support` | Tickets, messages, FAQ (isolated — ADR-001) |
| `:app` | Spring Boot entry point, wires all modules |

---

## Local Dev Containers

| Container | Purpose | Ports |
|---|---|---|
| `greene-redis` | Redis 7 | 6379 |
| `greene-mailpit` | Email catcher (dev only) | SMTP: 1025, UI: 8025 |
| `greene-minio` | Object storage (dev only) | API: 9000, UI: 9001 |

> PostgreSQL 15 runs as a local install, not a container.

**Start containers:**
```powershell
docker start greene-redis greene-mailpit greene-minio
```

**Stop containers:**
```powershell
docker stop greene-redis greene-mailpit greene-minio
```

**MinIO credentials (dev):**
- User: `greeneminio`
- Password: `greeneminio123`
- Bucket: `greene-dev`

---

## Flyway Migration History

| Version | File | Description |
|---|---|---|
| V1 | `V1__initial_schema.sql` | Baseline / initial schema |
| V2 | `V2__create_auth_tables.sql` | users, otp_tokens, refresh_tokens |
| V3 | `V3__add_profile_photo_url.sql` | profile_photo_url column on users |
| V4 | `V4__create_batch_tables.sql` | batches table (EPIC 3) |
| V5 | `V5__create_booking_tables.sql` | bookings table (E3-US2) — includes note, training_complete, training_completed_at for E3-US3/E3-US5 |
| V6 | `V6__create_batch_status_logs.sql` | batch_status_logs table (E3-US4) |
| V7 | `V7__add_booking_client_created_index.sql` | index on bookings(client_id, created_at) (E3-US5) |
| V8 | `V8__create_content_tables.sql` | content_libraries, content_nodes, content_item_details, content_files, content_entitlements + all indexes (E4-US1) |
| **V9** | **next migration** | **TBD** |

---

## API Conventions

**Base path:** `/api/v1/`

**Success envelope:**
```json
{ "data": { }, "meta": { "timestamp": "...", "version": "1.0" } }
```

**Error envelope:**
```json
{ "error": { "code": "...", "message": "...", "details": [] } }
```

**Pagination envelope:**
```json
{ "data": { "items": [], "page": 1, "pageSize": 20, "total": 148 }, "meta": {} }
```

**HTTP status conventions:**
- `200` GET, PUT, PATCH success
- `201` POST that creates a resource
- `204` DELETE success
- `400` Validation failure
- `401` Missing or invalid JWT
- `403` Valid JWT but insufficient role
- `404` Resource not found
- `409` Duplicate resource
- `413` File too large
- `415` Invalid file type
- `422` Business rule violation
- `429` Rate limit exceeded
- `500` Unhandled exception

---

## Key Implementation Patterns

**Request DTOs:**
- All fields nullable with `null` default
- Use `@field:` prefix on all validation annotations
- Example: `val email: String? = null` with `@field:NotBlank @field:Email`

**Error handling:**
- Throw `PlatformException(code, message, httpStatus)` from service layer
- `GlobalExceptionHandler` maps to error envelope
- `DataIntegrityViolationException` caught as safety net for DB constraint violations
- `InvalidFormatException` (Jackson) handled in `handleUnreadableMessage` — returns field-level details for bad enum values

**Security:**
- `JwtAuthenticationFilter` sets userId (UUID string) as principal in SecurityContext
- Extract caller: `UUID.fromString(SecurityContextHolder.getContext().authentication.principal as String)`
- `@PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")` on admin endpoints
- Always include `SUPER_ADMIN` in every `hasAnyRole()` expression
- STAFF now has full batch management access — use `hasAnyRole('ADMIN', 'STAFF', 'SUPER_ADMIN')` for all batch endpoints

**Object storage:**
- Storage key format: `{folder}/{userId}/{uuid}.{ext}`
- Validate file type by magic bytes, not extension
- `MinioStorageService` for `@Profile("dev")`
- `S3StorageService` for `@Profile("staging", "prod")`

**OTP:**
- Stored as BCrypt hash in `otp_tokens` table
- Plaintext stored temporarily in Redis with TTL matching OTP expiry
- Key format: `otp:plaintext:{userId}`
- Rate limit key: `otp:ratelimit:{email}`

**Datetime:**
- Batch datetimes stored as `TIMESTAMPTZ` in IST
- Accept full ISO 8601 offset strings: `"2026-05-01T09:00:00+05:30"`
- Use `OffsetDateTime` in Kotlin entity and DTOs

**Copilot prompts:**
- Split into 3–5 phases to avoid response length limit
- Attach relevant spec files (`api-spec.md`, `error-scenarios.md`) to each phase
- Always specify: Spring Boot version, Kotlin version, existing classes available

---

## Completed Stories

### EPIC 1 — Project Foundation ✅
- [x] E1-US1 System Architecture Document
- [x] E1-US2 Backend Gradle Multi-Module Setup
- [x] E1-US3 Flutter Project Setup
- [x] E1-US4 API Contract Standards
- [x] E1-US5 Environment Configuration

### EPIC 2 — Authentication & User Management ✅
- [x] E2-US1 Client Self-Registration
- [x] E2-US2 Staff Account Creation by Admin
- [x] E2-US3 Token Refresh & Logout
- [x] E2-US4 Role-Based Access Control
- [x] E2-US5 Client Profile Management

### EPIC 3 — Batch Management (in progress)
- [x] E3-US1 Admin Creates a Batch
- [x] E3-US2 Client Browses and Books a Batch
- [x] E3-US3 Staff Confirms or Rejects Booking
- [x] E3-US4 Admin Closes a Batch
- [x] E3-US5 Client Views Their Enrollment Status (was E3-US6)

### EPIC 4 — Content Management (in progress)
- [x] E4-US1 Content Library CRUD
- [x] E4-US2 Folder & Node Tree Management
- [x] E4-US3 Content Item — Article
- [ ] E4-US4 Content Item — Video Upload
- [x] E4-US5 Inline Image Upload for Articles
- [ ] E4-US6 Content Entitlement & Access Port
- [ ] E4-US7 Staff Marks Training as Complete
- [ ] E4-US8 Client Browses Content
- [ ] E4-US9 Client Fetches Signed URL
- [ ] E4-US10 Orphan Cleanup Job

---

## Implemented API Endpoints

### Auth (public)
- `POST /api/v1/auth/identify`
- `POST /api/v1/auth/register`
- `POST /api/v1/auth/verify-otp`
- `POST /api/v1/auth/resend-otp`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`

### Staff (ADMIN, SUPER_ADMIN)
- `POST /api/v1/staff`
- `PATCH /api/v1/staff/{id}/status`

### Users (ADMIN, SUPER_ADMIN)
- `PATCH /api/v1/users/{id}/role`

### Profile (any authenticated user)
- `GET /api/v1/users/me`
- `PATCH /api/v1/users/me/profile`
- `POST /api/v1/users/me/profile/photo`

### Batches (public + ADMIN, STAFF, SUPER_ADMIN)
- `POST /api/v1/batches`
- `GET /api/v1/batches` ← public; role-aware response shape
- `GET /api/v1/batches/{id}`
- `PATCH /api/v1/batches/{id}/details`
- `PATCH /api/v1/batches/{id}/status`

### Bookings (CLIENT)
- `POST /api/v1/batches/{id}/bookings`

### Libraries (ADMIN, STAFF, SUPER_ADMIN)
- `POST /api/v1/libraries`
- `GET /api/v1/libraries`
- `GET /api/v1/libraries/{id}`
- `PATCH /api/v1/libraries/{id}`

### Nodes (ADMIN, STAFF, SUPER_ADMIN + entitled CLIENT for tree)
- `POST /api/v1/libraries/{id}/nodes`
- `POST /api/v1/nodes/{id}/content` (ADMIN, STAFF, SUPER_ADMIN)
- `POST /api/v1/nodes/{id}/files/inline-image` (ADMIN, STAFF, SUPER_ADMIN)
- `PATCH /api/v1/nodes/{id}`
- `PATCH /api/v1/nodes/{id}/move`
- `PATCH /api/v1/libraries/{id}/nodes/reorder`
- `DELETE /api/v1/nodes/{id}`
- `GET /api/v1/libraries/{id}/tree`

---

## Error Codes Implemented

| Code | HTTP | Story |
|---|---|---|
| `VALIDATION_ERROR` | 400 | E2-US1 |
| `INVALID_OTP` | 400 | E2-US1 |
| `OTP_EXPIRED` | 400 | E2-US1 |
| `OTP_ALREADY_USED` | 400 | E2-US1 |
| `OTP_NOT_FOUND` | 400 | E2-US1 |
| `RESEND_TOO_SOON` | 400 | E2-US1 |
| `MAX_RESEND_ATTEMPTS` | 400 | E2-US1 |
| `AT_LEAST_ONE_FIELD_REQUIRED` | 400 | E2-US5 |
| `INVALID_BATCH_STATUS` | 400 | E3-US1 |
| `ACCOUNT_NOT_FOUND` | 404 | E2-US1 |
| `USER_NOT_FOUND` | 404 | E2-US4 |
| `STAFF_NOT_FOUND` | 404 | E2-US2 |
| `BATCH_NOT_FOUND` | 404 | E3-US1 |
| `BOOKING_ALREADY_EXISTS` | 409 | E3-US2 |
| `BATCH_NOT_BOOKABLE` | 422 | E3-US2 |
| `BOOKING_NOT_FOUND` | 404 | E3-US3 |
| `BATCH_NOT_EDITABLE` | 422 | E3-US4 |
| `INVALID_BATCH_STATUS_TRANSITION` | 422 | E3-US4 |
| `EMAIL_ALREADY_ACTIVE` | 409 | E2-US1 |
| `EMAIL_ALREADY_REGISTERED` | 409 | E2-US2 |
| `PHONE_ALREADY_REGISTERED` | 409 | E2-US1 |
| `INVALID_STATUS_TRANSITION` | 422 | E2-US2 |
| `INVALID_ROLE_CHANGE` | 422 | E2-US4 |
| `FILE_TOO_LARGE` | 413 | E2-US5 |
| `INVALID_FILE_TYPE` | 415 | E2-US5 |
| `RATE_LIMIT_EXCEEDED` | 429 | E2-US1 |
| `REFRESH_TOKEN_EXPIRED` | 401 | E2-US3 |
| `REFRESH_TOKEN_INVALID` | 401 | E2-US3 |
| `UNAUTHORIZED` | 401 | E2-US4 |
| `FORBIDDEN` | 403 | E2-US4 |
| `ACCOUNT_SUSPENDED` | 403 | E2-US2 |
| `LIBRARY_NOT_FOUND` | 404 | E4-US1 |
| `LIBRARY_ARCHIVED` | 422 | E4-US1 |
| `NODE_NOT_FOUND` | 404 | E4-US2 |
| `MAX_DEPTH_EXCEEDED` | 422 | E4-US2 |
| `INVALID_PARENT_NODE` | 422 | E4-US2 |
| `MOVE_CROSS_LIBRARY` | 422 | E4-US2 |
| `CONTENT_LOCKED` | 403 | E4-US2 |

---

## Known Quirks & Decisions

| # | Item |
|---|---|
| 1 | File size over 5MB shows "Failed to fetch" in Swagger — verified working via curl (returns 413 correctly) |
| 2 | OTP plaintext stored in Redis (TTL = OTP expiry) to support resend without regenerating |
| 3 | `PENDING_VERIFICATION` accounts never deleted — re-registration reuses existing record |
| 4 | JWT role claim stores `CLIENT` not `ROLE_CLIENT` — filter prepends `ROLE_` when building `GrantedAuthority` |
| 5 | Phone is required but `phone_verified = false` by default — SMS OTP deferred |
| 6 | Email is not editable via profile update — read-only |
| 7 | Refresh token rotation enforced on every use — single-use tokens |
| 8 | `MaxUploadSizeExceededException` handled in `GlobalExceptionHandler` — maps to `413 FILE_TOO_LARGE` |
| 9 | MinIO requires `forcePathStyle(true)` and `Region.US_EAST_1` (ignored by MinIO but required by SDK) |
| 10 | `:support` module is logically isolated per ADR-001 — no imports from other domain modules |
| 11 | PostgreSQL runs as a local install (not Docker) — to reset a migration, DROP TABLE and DELETE FROM flyway_schema_history WHERE version = 'N' |
| 12 | `InvalidFormatException` for bad enum values handled in `GlobalExceptionHandler.handleUnreadableMessage` — returns field name and valid enum values in details |
| 13 | Batch `startDateTime`/`endDateTime` stored as TIMESTAMPTZ in IST — clients must send full ISO 8601 offset e.g. `2026-05-01T09:00:00+05:30` |
| 14 | STAFF role has full batch management access (create, edit, close, reopen, mark complete) — same as ADMIN |
| 15 | `GET /api/v1/batches` is a public endpoint — `permitAll()` in SecurityConfig. Role detection uses nullable `Authentication?` from SecurityContextHolder to determine response shape (trimmed vs full) |
| 16 | PENDING_VERIFICATION accounts cannot obtain a JWT — `ACCOUNT_NOT_VERIFIED` guard at booking layer is redundant and untestable; protection is enforced at auth layer |
| 17 | Rejected bookings block re-booking — `BOOKING_ALREADY_EXISTS` applies regardless of existing booking status (PENDING, CONFIRMED, or REJECTED) |
| 18 | `GET /api/v1/bookings` uses JPA Specifications for optional filters — `BookingRepository` extends both `JpaRepository` and `JpaSpecificationExecutor` |
| 19 | `GET /api/v1/batches/{id}/bookings` not implemented — the `?batchId=` filter on `GET /api/v1/bookings` covers the same need |
| 20 | All booking status transitions allowed except same-status (CONFIRMED→CONFIRMED, REJECTED→REJECTED) — correction flows explicitly supported |
| 21 | `BatchStatus.COMPLETED` removed from enum — training completion tracked via `training_complete` boolean on booking record, not batch level |
| 22 | Batch status transitions strictly limited to: DRAFT→OPEN, OPEN→CLOSED, CLOSED→OPEN — all others return 422 |
| 23 | `updateBatchStatus` is `@Transactional` — batch update, booking auto-rejections, and audit log write are atomic |
| 24 | `training_status` column in batches table must be inserted as NULL in test data — not `'PENDING'` |
| 25 | psql connection requires `$DB_HOST = "127.0.0.1"` and `$DB_NAME = "greene_db"` — using `"localhost"` or `"greene"` causes connection failure |
| 26 | `:content` module API paths use no `/content/` prefix — resources are `/api/v1/libraries`, `/api/v1/nodes`, `/api/v1/files` — no namespace needed as names are unique across modules |
| 27 | `description` PATCH sentinel: `null` = no change, `""` = clear to null (via `ifEmpty { null }`) — applies to all optional text fields in content module |
| 28 | V7 creates all 5 content tables in a single migration — `content_nodes`, `content_item_details`, `content_files`, `content_entitlements` are empty until E4-US2 through E4-US6 implement them |
| 29 | `content_files` has no `library_id` column — queries filtering by library must join through `content_nodes`: `JOIN content_nodes cn ON cn.id = cf.node_id WHERE cn.library_id = :libraryId` |
| 30 | UUID prefixes in test SQL must use only hex chars `a`–`f` — prefixes `g`–`z` cause `invalid input syntax for type uuid`. Convention: `a`=users, `b`=primary domain entity, `c`=dependent entities, `d`=libraries, `e`=nodes, `f`=item details |
| 31 | `MAX_DEPTH_EXCEEDED` test requires a FOLDER at depth 3 as parent — an ITEM at depth 3 hits `INVALID_PARENT_NODE` first. Create a depth-3 FOLDER during the test or seed one explicitly |
| 32 | `TreeNodeResponse` uses `@JsonInclude(JsonInclude.Include.NON_NULL)` — CLIENT response shape (status omitted) is handled by setting `status = null`, not a separate DTO |
| 33 | `getTree` bulk-loads item details and PRIMARY file presence before tree assembly to avoid N+1 — `findAllByNodeIdIn` and `findNodeIdsWithPrimaryFileByLibraryId` (native query joining through content_nodes) |
| 34 | `PATCH /api/v1/nodes/{id}` lives in `NodeController` (separate from `ContentItemController`) — both share the `/api/v1/nodes` path space; Spring disambiguates by HTTP method + path suffix. Full path on method annotation avoids base-path conflict. |
| 35 | `content_files` has no unique constraint on `(node_id, file_role)` — the service queries `findByNodeIdAndFileRole` before upsert; delete + insert pattern used for overwrite so that `created_at` (source of `updatedAt` in response) reflects the actual re-upload time. |
| 36 | `POST /api/v1/nodes/{id}/content` response `updatedAt` is sourced from `content_files.created_at` of the upserted file row — not from `content_nodes.updated_at`. |
| 37 | ImageTypeDetector extracted from ProfileService into :core/util -- shared by :content and :core; adds GIF support (47 49 46 38) alongside existing JPEG and PNG signatures. ProfileService.detectImageType() private method removed. |
| 38 | INLINE_IMAGE content_files rows are plain INSERT (not upsert/overwrite) -- multiple rows per node_id are expected and valid. No uniqueness constraint on (node_id, file_role) for INLINE_IMAGE. |
---

## Next Steps

1. Next story: E4-US4 Content Item -- Video Upload
2. Next Flyway migration: V9 (first needed migration since V8)
3. FCM notifications for all deferred stories -- defer to dedicated notifications story