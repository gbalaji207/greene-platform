# Microgreens Training & Farming Platform — User Stories v3.15

**Backend:** Spring Boot (Kotlin)
**Last Updated:** April 2026
**Version:** 3.16 — Backend-only. Flutter ACs moved to `microgreens_user_stories_frontend.md`. Deployment/infra EPICs moved to `microgreens_user_stories_deployment.md`. EPIC 4 stories moved to `microgreens_user_stories_epic4.md`.
**Status Legend:** `[ ]` To Do | `[-]` In Progress | `[x]` Done

---

## Platform Summary

- Single company platform (not multi-tenant SaaS)
- **Roles:** Super Admin (dev/audit, no UI needed), Admin (business owner), Staff (operations), Client (grower)
- **Client Journey:** Register → Browse & Book Batch → Staff Confirms → Attend Offline Classes → Staff Unlocks Premium Content → Start Growing Journey (app-guided) → Ongoing Support via Tickets
- **Content Model:** Free (pre-enrollment info, crop library) | Premium (crop plans, checklists, guides — unlocked per batch by staff)
- **Payments:** Out of scope for Phase 1 & 2
- **Marketplace:** Out of scope for Phase 1 & 2
- **Offline Classes:** Conducted at fixed location, not recorded — platform tracks attendance and training status only
- **Support:** Ticket-based (not instant messaging), one-to-one between client and staff

---

## Development Phases

| Phase | EPICs | Focus |
|---|---|---|
| **Phase 1 — Local Dev & APIs** | EPIC 1–10 | Foundation + all backend domain APIs |
| **Phase 2 — Flutter App** | See `microgreens_user_stories_frontend.md` | Admin/staff panel, client UI, testing, PWA |
| **Phase 3 — Pre-Launch / Deploy** | See `microgreens_user_stories_deployment.md` | Infra provisioning, security hardening, launch |

---

## EPIC 1 — Project Foundation & Architecture

### E1-US1: System Architecture Document ✅
**As a** tech lead,
**I want** a finalized architecture diagram and document,
**So that** all developers build consistently against a shared blueprint.

**Acceptance Criteria:**
- [x] Architecture diagram covers Flutter PWA ↔ Spring Boot ↔ PostgreSQL ↔ Firebase
- [x] API contract standards defined (REST conventions, error format, pagination)
- [x] Data flow documented for key scenarios (auth, batch booking, content unlock, journal, support ticket)
- [x] Role and permission matrix documented
- [x] Document reviewed and signed off before development begins

---

### E1-US2: Backend Gradle Multi-Module Setup ✅
**As a** backend developer,
**I want** a Gradle multi-module project structure,
**So that** each domain module is independently buildable and reusable across projects.

**Acceptance Criteria:**
- [x] Root project with submodules: `core`, `training`, `farming`, `journal`, `support`, `notifications`, `content`
- [x] Shared dependencies declared in version catalog (`libs.versions.toml`)
- [x] Each module has its own `build.gradle.kts`
- [x] `core` module compiles independently with no cross-module dependencies
- [x] CI builds all modules and runs tests per module

---

### E1-US3: Flutter Project Setup ✅
**As a** frontend developer,
**I want** a clean Flutter project with folder structure, routing, and state management configured,
**So that** all developers follow consistent patterns from day one.

> **Note:** Flutter setup tracked here for completeness. Implementation detail is in `microgreens_user_stories_frontend.md`.

**Acceptance Criteria:**
- [x] Folder structure: `features/`, `core/`, `shared/`, `config/`
- [x] GoRouter configured with named routes and role-based auth guards
- [x] Bloc/Cubit set up with example feature
- [x] Flavor configuration for dev, staging, production environments
- [x] Lint rules and formatting enforced via `flutter analyze` and `dart format`

---

### E1-US4: API Contract Standards ✅
**As a** developer,
**I want** standardized API request/response formats,
**So that** Flutter and Spring Boot teams integrate without ambiguity.

**Acceptance Criteria:**
- [x] Standard success response envelope: `{ data, meta }`
- [x] Standard error response: `{ code, message, details }`
- [x] Pagination format: `{ items, page, pageSize, total }`
- [x] OpenAPI/Swagger spec auto-generated from Spring Boot annotations
- [x] Swagger UI accessible in dev and staging environments

---

### E1-US5: Environment Configuration ✅
**As a** developer,
**I want** separate dev, staging, and production environment configurations,
**So that** changes can be tested safely before going live.

**Acceptance Criteria:**
- [x] Spring Boot `application-dev.yml`, `application-staging.yml`, `application-prod.yml` configured
- [x] Flutter flavor-based `config.dart` per environment
- [x] Secrets never committed to repository (`.gitignore` enforced)
- [x] Environment switching documented for all team members

---

## EPIC 2 — Authentication & User Management

### E2-US1: Client Self-Registration ✅
**As a** prospective client,
**I want** to register on the platform myself,
**So that** I can browse batches and begin my enrollment journey.

**Acceptance Criteria:**
- [x] `POST /api/v1/auth/register` — accepts name, email, phone, city
- [x] Email OTP verification on registration (`POST /api/v1/auth/verify-otp`)
- [x] Duplicate email/phone rejected with `409` error
- [x] Account created with `ROLE_CLIENT` and status `PENDING_VERIFICATION` on registration; set to `ACTIVE` on OTP verification
- [x] Welcome email dispatched via Amazon SES on successful verification
- [x] OTP stored as BCrypt hash in `otp_tokens` table; plaintext in Redis with TTL for resend support
- [x] Rate limit: 3 OTP requests per 10 minutes per email address

---

### E2-US2: Staff Account Creation by Admin ✅
**As an** admin,
**I want** to create staff accounts from the admin panel,
**So that** staff can log in and manage clients without self-registering.

**Acceptance Criteria:**
- [x] `POST /api/v1/staff` — admin creates staff account with name, email, phone, role (`STAFF`)
- [x] Staff receives email OTP to verify and activate account on first login
- [x] `PATCH /api/v1/staff/{id}/status` — admin can deactivate or reactivate a staff account
- [x] Deactivated staff cannot log in (`403 ACCOUNT_SUSPENDED`)

---

### E2-US3: Token Refresh & Logout ✅
**As a** registered user,
**I want** to stay logged in securely across sessions,
**So that** I don't have to re-authenticate every time.

**Acceptance Criteria:**
- [x] JWT access token (15 min expiry) + refresh token (30 days) returned on login
- [x] `POST /api/v1/auth/refresh` — refresh token rotated on each use
- [x] `POST /api/v1/auth/logout` — refresh token invalidated in DB
- [x] All active refresh tokens invalidated on account suspension or role change

---

### E2-US4: Role-Based Access Control ✅
**As a** system,
**I want** role-based access enforced on all API endpoints,
**So that** clients cannot access staff/admin features and vice versa.

**Acceptance Criteria:**
- [x] Roles defined: `SUPER_ADMIN`, `ADMIN`, `STAFF`, `CLIENT`
- [x] Spring Security method-level annotations applied (`@PreAuthorize`)
- [x] Unauthenticated access returns `401`; authenticated but insufficient role returns `403`
- [x] Role stored in JWT claims; filter prepends `ROLE_` when building `GrantedAuthority`
- [x] `PATCH /api/v1/users/{id}/role` — admin can change a user's role (except Super Admin)

---

### E2-US5: Client Profile Management ✅
**As a** client,
**I want** to view and update my profile,
**So that** my information stays current.

**Acceptance Criteria:**
- [x] `GET /api/v1/users/me` — returns current user profile
- [x] `PATCH /api/v1/users/me/profile` — update name and city
- [x] `POST /api/v1/users/me/profile/photo` — upload profile photo to object storage
- [x] Email is read-only (not editable via profile update)
- [x] Phone/email change requires OTP verification (OTP flow already implemented)
- [x] Storage key format: `profile-photos/{userId}/{uuid}.{ext}`
- [x] File type validated by magic bytes; size limit enforced (413 if exceeded)

---

## EPIC 3 — Batch Management

### E3-US1: Admin Creates a Batch ✅
**As an** admin,
**I want** to create a new training batch,
**So that** clients can browse and book it.

**Acceptance Criteria:**
- [x] Only `ADMIN`, `STAFF`, and `SUPER_ADMIN` can create and manage batches
- [x] `POST /api/v1/batches` — fields: `name` (required), `description` (optional), `startDateTime` (required, ISO 8601 with offset e.g. `2026-05-01T09:00:00+05:30`), `endDateTime` (optional, must be after `startDateTime` if provided), `location` (optional), `topics` (optional), `maxSeats` (optional, informational only)
- [x] Batch status lifecycle: `DRAFT → OPEN → CLOSED → COMPLETED`. Defaults to `DRAFT` if status omitted
- [x] `DRAFT` batches not visible to clients; only `OPEN` batches appear in client-facing listings
- [x] Batch fields editable only while status is `DRAFT` or `OPEN`; `CLOSED` and `COMPLETED` are read-only
- [x] `GET /api/v1/batches/{id}` — returns full batch detail (ADMIN, STAFF, SUPER_ADMIN only)
- [x] `startDateTime` / `endDateTime` stored as `TIMESTAMPTZ` in IST
- [x] `trainingStatus` always `null` on creation; set to `COMPLETED` in E3-US5
- [x] Flyway migration: `V4__create_batch_tables.sql` in `:training` module

---

### E3-US2: Client Browses and Books a Batch ✅
**As a** client,
**I want** to browse available batches and book a slot,
**So that** I can enroll in the training program that suits me.

**Acceptance Criteria:**
- [x] `GET /api/v1/batches` — public endpoint (no JWT required); paginated. Unauthenticated / `CLIENT` callers see only `OPEN` batches (trimmed projection). `ADMIN`, `STAFF`, `SUPER_ADMIN` see all statuses (full detail). `?status=` filter applies to admin/staff only — silently ignored for client/public callers.
- [x] Client / public batch list projection fields: `id`, `name`, `description`, `startDateTime`, `endDateTime`, `location`, `topics`, `maxSeats`
- [x] Pagination: `?page` (default 1, min 1) and `?pageSize` (default 20, max 50). Returns `400 VALIDATION_ERROR` if page < 1 or pageSize > 50.
- [x] `POST /api/v1/batches/{id}/bookings` — authenticated `CLIENT` only; returns `201` with created booking payload
- [x] Booking response fields: `id`, `batchId`, `clientId`, `status: PENDING`, `createdAt`
- [x] Booking status set to `PENDING` on submission
- [x] Client cannot book a `DRAFT` batch — returns `404 BATCH_NOT_FOUND` (DRAFT is invisible to clients)
- [x] Client cannot book a `CLOSED` or `COMPLETED` batch — returns `422 BATCH_NOT_BOOKABLE`
- [x] Client cannot book the same batch twice (any existing booking status) — returns `409 BOOKING_ALREADY_EXISTS`
- [x] `ADMIN`, `STAFF`, `SUPER_ADMIN` cannot call `POST /api/v1/batches/{id}/bookings` — returns `403 FORBIDDEN`
- [x] Flyway migration `V5` — bookings table in `:training` module; includes `note`, `training_complete`, `training_completed_at` columns upfront for E3-US3 and E3-US5

---

### E3-US3: Staff Confirms or Rejects Booking ✅
**As a** staff member,
**I want** to review and confirm or reject client booking requests,
**So that** only appropriate clients are enrolled in each batch.

**Acceptance Criteria:**
- [x] `GET /api/v1/bookings` — `ADMIN`, `STAFF`, `SUPER_ADMIN` only; paginated list of bookings with client details
- [x] Supports optional filters: `?status=PENDING|CONFIRMED|REJECTED`, `?batchId=<uuid>`; combinable
- [x] Booking list item fields: `id`, `batchId`, `batchName`, `clientId`, `clientName`, `clientEmail`, `clientPhone`, `status`, `note`, `createdAt`
- [x] Pagination: `?page` (default 1, min 1), `?pageSize` (default 20, max 50) — returns `400 VALIDATION_ERROR` if violated
- [x] `PATCH /api/v1/bookings/{id}` — `ADMIN`, `STAFF`, `SUPER_ADMIN` only; confirms or rejects with optional note
- [x] Request body: `{ "status": "CONFIRMED" | "REJECTED", "note": "optional" }` — `PENDING` is not a valid target status (`400`)
- [x] Allowed transitions: `PENDING → CONFIRMED`, `PENDING → REJECTED`, `CONFIRMED → REJECTED`, `REJECTED → CONFIRMED`
- [x] Same-status transition (`CONFIRMED → CONFIRMED`, `REJECTED → REJECTED`) returns `422 INVALID_STATUS_TRANSITION`
- [x] Booking not found returns `404 BOOKING_NOT_FOUND`
- [x] `GET /api/v1/batches/{id}/bookings` not implemented — covered by `GET /api/v1/bookings?batchId=`
- [x] FCM notifications deferred — not part of this story

---

### E3-US4: Admin Closes a Batch ✅
**As an** admin,
**I want** to manually close a batch when seats are filled,
**So that** no further bookings are accepted.

**Acceptance Criteria:**
- [x] `PATCH /api/v1/batches/{id}/details` — `ADMIN`, `STAFF`, `SUPER_ADMIN` only; updates editable batch fields (patch semantics)
- [x] Editable fields: `name`, `description`, `location`, `topics`, `maxSeats`, `startDateTime`, `endDateTime` — all optional
- [x] Only `DRAFT` and `OPEN` batches are editable — `CLOSED` batch returns `422 BATCH_NOT_EDITABLE`
- [x] Empty request body returns `400 AT_LEAST_ONE_FIELD_REQUIRED`
- [x] `maxSeats` < 1 returns `400 VALIDATION_ERROR`
- [x] `endDateTime` before `startDateTime` returns `400 VALIDATION_ERROR` (both provided or only endDateTime before existing start)
- [x] `PATCH /api/v1/batches/{id}/status` — `ADMIN`, `STAFF`, `SUPER_ADMIN` only; transitions batch status
- [x] Request body: `{ "status": "OPEN" | "CLOSED" }` — `DRAFT` not a valid target (`400 VALIDATION_ERROR`)
- [x] Allowed transitions: `DRAFT → OPEN`, `OPEN → CLOSED`, `CLOSED → OPEN`
- [x] Disallowed transitions return `422 INVALID_BATCH_STATUS_TRANSITION`
- [x] On `OPEN → CLOSED`: all `PENDING` bookings auto-rejected with note `"Auto Rejected as batch closed."`; `CONFIRMED` bookings unaffected
- [x] Closed batch no longer returned in client-facing `GET /api/v1/batches`
- [x] Every status transition logged to `batch_status_logs`: `batchId`, `fromStatus`, `toStatus`, `changedBy`, `changedAt`
- [x] `COMPLETED` removed from `BatchStatus` enum — training completion tracked via `training_complete` on booking record
- [x] Flyway migration `V6` — `batch_status_logs` table
- [x] FCM notifications deferred — not part of this story

---

### E3-US5: Client Views Their Enrollment Status
**As a** client,
**I want** to see the status of my batch booking and training,
**So that** I know where I am in the journey.

**Acceptance Criteria:**
- [ ] `GET /api/v1/bookings/me` — returns client's current and past bookings with status
- [ ] Booking record includes: batch name, startDateTime, endDateTime, location, booking status, trainingStatus
- [ ] Client notified via FCM on every booking status change
- [ ] Past completed batch enrollments included in response

---

## EPIC 4 — Content Management

> **Note:** EPIC 4 has been fully redesigned as a generic, domain-agnostic content module.
> All stories have been moved to a dedicated file: **`microgreens_user_stories_epic4.md`**
>
> The original crop-specific stories (E4-US1 through E4-US6) are superseded and removed from this document.

### Story Summary

| Story | Title | Status |
|---|---|--------|
| E4-US1 | Content Library CRUD | `[x]`  |
| E4-US2 | Folder & Node Tree Management | `[x]`  |
| E4-US3 | Content Item — Article | `[x]`  |
| E4-US4 | Content Item — Video Upload | `[ ]`  |
| E4-US5 | Inline Image Upload for Articles | `[x]`  |
| E4-US6 | Content Entitlement & Access Port | `[ ]`  |
| E4-US7 | Staff Marks Training as Complete | `[ ]`  |
| E4-US8 | Client Browses Content | `[ ]`  |
| E4-US9 | Client Fetches Signed URL | `[ ]`  |
| E4-US10 | Orphan Cleanup Job | `[ ]`  |

### Key Design Decisions
- Generic library → folder → item hierarchy (adjacency list, max depth 3)
- Content types: `VIDEO` and `ARTICLE` (rich HTML — text, images, embedded video)
- All files stored in private B2/MinIO bucket — signed URL delivery (15 min expiry)
- `ContentAccessPort` interface for cross-module entitlement grants (ADR-001 pattern)
- `:content` module imports only `:core` — zero domain coupling
- Flyway migration: `V7__create_content_tables.sql`

---

## EPIC 5 — Crop Checklist & Reminder System

### E5-US1: Admin Creates Crop Checklist Templates
**As an** admin,
**I want** to define a day-by-day checklist template for each crop,
**So that** clients get precise, crop-specific guidance throughout their growing journey.

**Acceptance Criteria:**
- [ ] `POST /api/v1/crops/{cropId}/checklist-template` — one entry per day: stage name, tasks, guidance text, tips
- [ ] Task types: `CHECKBOX`, `NUMBER_INPUT`, `PHOTO_UPLOAD`
- [ ] Templates versioned — editing does not affect clients already on that template version
- [ ] `GET /api/v1/crops/{cropId}/checklist-template` — full day-by-day plan

---

### E5-US2: Daily Task Generation per Client
**As a** client,
**I want** my daily tasks auto-generated based on my crop and current day of journey,
**So that** I know exactly what to do without guessing.

**Acceptance Criteria:**
- [ ] Scheduled job runs at midnight IST for all clients with `ACTIVE` journeys
- [ ] Day number calculated from `journey.startDate`
- [ ] Task records generated in DB linked to client + tray + day number
- [ ] `GET /api/v1/trays/{trayId}/tasks/today` — returns today's tasks with day number and stage name
- [ ] Returns empty list (not 404) if template has no entry for that day

---

### E5-US3: Stage-Aware Smart Reminders
**As a** client,
**I want** reminders specific to my current growth stage,
**So that** I receive contextually relevant nudges.

**Acceptance Criteria:**
- [ ] Morning FCM reminder at 8:00 AM IST — content pulled from that day's checklist guidance text
- [ ] No reminder sent if all tasks for the day are already completed
- [ ] Second FCM reminder at 6:00 PM IST if tasks remain incomplete
- [ ] `PATCH /api/v1/users/me/preferences` — client can store custom reminder time

---

### E5-US4: Checklist Completion Tracking
**As a** client,
**I want** to mark tasks as complete and log required values,
**So that** my progress is tracked and visible to staff.

**Acceptance Criteria:**
- [ ] `PATCH /api/v1/tasks/{id}` — mark task complete: `{ completed: true, value?, photoUrl? }`
- [ ] Completion timestamp recorded per task
- [ ] `NUMBER_INPUT` tasks require a value; `PHOTO_UPLOAD` tasks require a `photoUrl`
- [ ] `GET /api/v1/trays/{trayId}/tasks/today` — returns daily completion percentage
- [ ] Staff can access checklist history: `GET /api/v1/clients/{clientId}/trays/{trayId}/tasks`

---

### E5-US5: Streak Tracking
**As a** client,
**I want** to see my checklist completion streak,
**So that** I stay motivated to tend to my trays consistently.

**Acceptance Criteria:**
- [ ] Streak evaluated end-of-day: full completion = streak +1; any missed task = streak reset to 0
- [ ] `GET /api/v1/users/me/streak` — returns current streak and longest streak
- [ ] Streak and longest streak stored on client record
- [ ] Staff can view client streak via `GET /api/v1/clients/{clientId}/streak`

---

### E5-US6: Growing Journey Completion
**As a** client,
**I want** to be notified when my growing journey reaches harvest day,
**So that** I know to log my harvest.

**Acceptance Criteria:**
- [ ] Final day checklist includes harvest tasks (flagged in template)
- [ ] On completing final day: FCM notification dispatched to client
- [ ] `POST /api/v1/trays/{trayId}/harvest` — client logs harvest: yield (grams), quality rating, notes, photo
- [ ] Journey status set to `COMPLETED` after harvest log submitted
- [ ] Staff notified via FCM of client's first successful harvest

---

## EPIC 6 — Tray & Batch Tracking

### E6-US1: Client Creates a Tray
**As a** client starting their growing journey,
**I want** to register my tray on the platform,
**So that** the system can track it and generate the right daily tasks.

**Acceptance Criteria:**
- [ ] `POST /api/v1/trays` — fields: crop (from journey), tray size, start date, optional label
- [ ] Expected harvest date auto-calculated from crop template harvest day count
- [ ] Tray status set to `ACTIVE` on creation
- [ ] Growing plan and checklist template immediately associated to tray
- [ ] Client can create a new tray after prior tray reaches `HARVESTED` status

---

### E6-US2: Growth Stage Tracking
**As a** client,
**I want** to see my tray's current growth stage,
**So that** I know where I am in the cycle at a glance.

**Acceptance Criteria:**
- [ ] `GET /api/v1/trays/{id}` — returns current stage derived from day number and crop template
- [ ] Stage name included in daily reminder text
- [ ] Staff can view current stage for all client trays: `GET /api/v1/clients/{clientId}/trays`

---

### E6-US3: Harvest Logging
**As a** client,
**I want** to log my harvest when I cut my microgreens,
**So that** I have a record of my yield.

**Acceptance Criteria:**
- [ ] `POST /api/v1/trays/{id}/harvest` — date, yield in grams, quality rating (1–5), notes, photo
- [ ] Tray status set to `HARVESTED` after logging
- [ ] `GET /api/v1/users/me/harvest-summary` — running yield total across all trays
- [ ] Staff can view harvest log via `GET /api/v1/clients/{clientId}/trays/{trayId}/harvest`

---

### E6-US4: Tray History
**As a** client,
**I want** to view all my past trays and their outcomes,
**So that** I can learn from each cycle and improve.

**Acceptance Criteria:**
- [ ] `GET /api/v1/trays?clientId=me` — all trays with crop, start date, harvest date, yield, status
- [ ] `GET /api/v1/trays/{id}` — full tray detail including checklist completion per day, journal entries, harvest log
- [ ] Staff can access full tray history for any client

---

## EPIC 7 — Tray Journal

### E7-US1: Client Creates Journal Entry
**As a** client,
**I want** to log daily observations about my tray,
**So that** I have a growing diary and can share progress with staff.

**Acceptance Criteria:**
- [ ] `POST /api/v1/trays/{trayId}/journal-entries` — date, text, one or more photo URLs
- [ ] Multiple entries allowed per day
- [ ] Photos stored in object storage; key format: `journal/{userId}/{trayId}/{uuid}.{ext}`

---

### E7-US2: Client Views Journal Feed
**As a** client,
**I want** to scroll through my tray's journal entries chronologically,
**So that** I can see how my tray has progressed.

**Acceptance Criteria:**
- [ ] `GET /api/v1/trays/{trayId}/journal-entries` — paginated, reverse chronological
- [ ] Each entry includes: date, text, photo URLs, any staff comments
- [ ] Returns empty list with `200` (not `404`) when no entries yet

---

### E7-US3: Staff Views Client Journal
**As a** staff member,
**I want** to view a client's tray journal,
**So that** I have full context before responding to queries.

**Acceptance Criteria:**
- [ ] `GET /api/v1/clients/{clientId}/trays/{trayId}/journal-entries` — full journal feed accessible to ADMIN, STAFF, SUPER_ADMIN
- [ ] Supports `?from=&to=` date range filter
- [ ] Returns checklist completion data alongside journal entries in same response

---

### E7-US4: Staff Comments on Journal Entry
**As a** staff member,
**I want** to comment on a client's journal entry,
**So that** I can give specific feedback on their observation.

**Acceptance Criteria:**
- [ ] `POST /api/v1/journal-entries/{id}/comments` — staff adds comment with text
- [ ] Client notified via FCM when staff comments
- [ ] `PATCH /api/v1/journal-entries/{entryId}/comments/{commentId}` — staff can edit own comment
- [ ] `DELETE /api/v1/journal-entries/{entryId}/comments/{commentId}` — staff can delete own comment

---

## EPIC 8 — Support Ticket System

### E8-US1: Client Raises a Support Ticket
**As a** client,
**I want** to raise a support query linked to my tray,
**So that** staff can help me with a specific problem in context.

**Acceptance Criteria:**
- [ ] `POST /api/v1/tickets` — subject, description, optional photos, optional `trayId`
- [ ] Ticket status lifecycle: `OPEN → IN_PROGRESS → RESOLVED → CLOSED`
- [ ] Max 3 open tickets per client enforced (`422 MAX_OPEN_TICKETS_EXCEEDED` if exceeded)
- [ ] Staff notified via FCM on new ticket
- [ ] `GET /api/v1/tickets?clientId=me` — client's ticket history with current status

---

### E8-US2: Staff Views Ticket with Tray Context
**As a** staff member,
**I want** to see a client's tray context alongside their support ticket,
**So that** I can give an accurate response without asking for repeated information.

**Acceptance Criteria:**
- [ ] `GET /api/v1/tickets/{id}` — ticket detail fetches context via `SupportContextPort`: tray snapshot, last 5 journal entries, 7-day checklist compliance (per ADR-001)
- [ ] Ticket auto-assigned to first staff member who opens it
- [ ] `PATCH /api/v1/tickets/{id}/assignee` — staff can reassign to another staff member

---

### E8-US3: Staff Responds to Ticket
**As a** staff member,
**I want** to respond to a client's support ticket,
**So that** the client gets help and the conversation is tracked.

**Acceptance Criteria:**
- [ ] `POST /api/v1/tickets/{id}/messages` — staff response with optional image attachment
- [ ] Ticket status set to `IN_PROGRESS` on first staff response
- [ ] Client notified via FCM on new message
- [ ] Internal notes supported: `{ isInternal: true }` — not visible to client

---

### E8-US4: Client Replies to Ticket
**As a** client,
**I want** to reply to a staff response within the same ticket,
**So that** the conversation stays in one place.

**Acceptance Criteria:**
- [ ] `POST /api/v1/tickets/{id}/messages` — client reply with text and optional photo
- [ ] Staff notified via FCM on client reply
- [ ] `PATCH /api/v1/tickets/{id}` `{ status: RESOLVED }` — client can mark ticket as resolved
- [ ] Auto-close: scheduled job closes tickets with no activity for 7 days; FCM notification sent

---

### E8-US5: Staff Closes or Resolves Ticket
**As a** staff member,
**I want** to mark a ticket as resolved or closed,
**So that** the support queue stays clean.

**Acceptance Criteria:**
- [ ] `PATCH /api/v1/tickets/{id}` — staff sets status to `RESOLVED` or `CLOSED` with optional closing note
- [ ] Client notified via FCM
- [ ] Client can reopen a `RESOLVED` ticket within 3 days (`422` after 3 days)
- [ ] Closed tickets returned in archived view; not deleted

---

### E8-US6: Staff Ticket Dashboard
**As a** staff member,
**I want** a dashboard view of all open tickets,
**So that** I can prioritize and manage client queries efficiently.

**Acceptance Criteria:**
- [ ] `GET /api/v1/tickets` — all tickets accessible to ADMIN, STAFF, SUPER_ADMIN
- [ ] Filter support: `?status=&assigneeId=&from=&to=`
- [ ] Default sort: oldest first
- [ ] `GET /api/v1/tickets/counts` — returns ticket count grouped by status

---

## EPIC 9 — FAQ & Broadcast

### E9-US1: Admin Manages FAQ Library
**As an** admin,
**I want** to maintain a searchable FAQ library,
**So that** clients can resolve common questions without raising a ticket.

**Acceptance Criteria:**
- [ ] `POST /api/v1/faq` — admin creates article: question, answer, category, optional photos, status (`DRAFT`/`PUBLISHED`)
- [ ] `PUT /api/v1/faq/{id}` — edit article
- [ ] `DELETE /api/v1/faq/{id}` — archive (soft delete)
- [ ] `GET /api/v1/faq` — returns published articles to all authenticated users

---

### E9-US2: Client Searches FAQ
**As a** client,
**I want** to search the FAQ before raising a support ticket,
**So that** I can get instant answers to common problems.

**Acceptance Criteria:**
- [ ] `GET /api/v1/faq?q={searchTerm}` — full-text search across published FAQ articles
- [ ] `POST /api/v1/faq/{id}/feedback` — client submits `{ helpful: true|false }`
- [ ] `GET /api/v1/faq?sort=popular` — most viewed FAQs returned first

---

### E9-US3: Staff Sends Broadcast to Clients
**As a** staff member,
**I want** to send a broadcast message to all clients or a specific batch,
**So that** I can share tips, reminders, or updates efficiently.

**Acceptance Criteria:**
- [ ] `POST /api/v1/broadcasts` — title, message body, optional image, target: `ALL_CLIENTS`, `BATCH:{id}`, `GROWING_CLIENTS`
- [ ] Delivered as FCM push notification + stored in client notification inbox
- [ ] `GET /api/v1/broadcasts` — broadcast history with delivery count (ADMIN, STAFF, SUPER_ADMIN only)
- [ ] Clients cannot reply to broadcasts

---

## EPIC 10 — Analytics & Reporting

### E10-US1: Admin Dashboard — Platform Overview
**As an** admin,
**I want** a dashboard showing key platform metrics,
**So that** I can monitor the health of the business at a glance.

**Acceptance Criteria:**
- [ ] `GET /api/v1/analytics/overview` — total registered clients, active growing clients, clients in training, open/closed batch counts
- [ ] New signups this week vs last week
- [ ] Open ticket count and average resolution time
- [ ] Top crops being grown
- [ ] Supports `?from=&to=` date range filter

---

### E10-US2: Staff Dashboard — Client Overview
**As a** staff member,
**I want** to see an overview of all clients and their current status,
**So that** I can identify who needs attention today.

**Acceptance Criteria:**
- [ ] `GET /api/v1/clients` — list with name, batch, current status, last checklist date, open ticket count
- [ ] Filter: `?status=&batchId=`
- [ ] Flag clients with no checklist activity in last 3 days
- [ ] Flag clients with open tickets older than 48 hours
- [ ] Search: `?q={name or phone}`

---

### E10-US3: Client Dashboard — Personal Progress
**As a** client,
**I want** to see my personal growing metrics,
**So that** I feel motivated and can track my journey.

**Acceptance Criteria:**
- [ ] `GET /api/v1/users/me/dashboard` — current streak, total days on journey, total yield to date, trays completed
- [ ] Today's checklist completion percentage included
- [ ] Current tray stage and day number included

---

### E10-US4: Staff Views Individual Client Report
**As a** staff member,
**I want** to view a detailed report for any individual client,
**So that** I can assess their progress and give personalized support.

**Acceptance Criteria:**
- [ ] `GET /api/v1/clients/{clientId}/report` — enrollment history, training status, active tray info, checklist compliance rate
- [ ] Yield history per tray
- [ ] Journal entry count and last entry date
- [ ] Support ticket history with resolution times

---

## EPIC 13 — Testing (Backend)

> Flutter widget tests and E2E tests are tracked in `microgreens_user_stories_frontend.md`.

### E13-US1: Backend Unit Tests
**As a** backend developer,
**I want** unit tests for all service-layer business logic,
**So that** regressions are caught automatically before deployment.

**Acceptance Criteria:**
- [ ] JUnit 5 + MockK for all unit tests
- [ ] Minimum 70% code coverage across service classes
- [ ] Tests cover: happy path, edge cases, error conditions
- [ ] Tests run in CI on every PR

---

### E13-US2: Backend Integration Tests
**As a** backend developer,
**I want** integration tests for all API endpoints,
**So that** the full request-response cycle is validated including DB interactions.

**Acceptance Criteria:**
- [ ] Spring Boot Test with TestContainers (PostgreSQL)
- [ ] All CRUD endpoints covered
- [ ] Auth flow tested end-to-end
- [ ] Tests isolated — each test resets DB state
- [ ] Integration tests run in CI on merge to `main`

---

### E13-US5: Load Testing
**As a** DevOps engineer,
**I want** load tests run before launch,
**So that** the system can handle expected concurrent users without degradation.

**Acceptance Criteria:**
- [ ] Load test tool: k6
- [ ] Scenario: 200 concurrent users, 10-minute sustained load
- [ ] Endpoints tested: login, home/checklist fetch, journal submit, ticket create
- [ ] Acceptance: p95 response time < 500ms, error rate < 1%
- [ ] Load test report documented with findings and tuning applied

---

## Changelog

| Version | Date | Changes |
|---|---|---|
| 1.0 | 2025 | Initial draft — 16 EPICs, 79 stories |
| 2.0 | 2025 | Revised to single-company model. Removed multi-trainer architecture, marketplace, payments. Added EPIC 8 (Tray Journal), revised EPIC 9 (Support Tickets). Updated roles to Super Admin / Admin / Staff / Client. |
| 3.0 | April 2026 | Reordered EPICs into three development phases. Infrastructure & DevOps moved to EPIC 14. Security & Compliance to EPIC 15. All domain EPICs renumbered 2–13. |
| 3.1–3.5 | April 2026 | EPIC 2 implementation iterations: E2-US1 through E2-US5 completed. OTP changed to email-only. Auth model updated to OTP-only. |
| 3.6 | April 2026 | EPIC 2 marked complete. E2-US3 renamed Token Refresh & Logout. E2-US6 (Password Reset) removed — not applicable to OTP-only auth model. |
| 3.7 | April 2026 | E3-US1 complete. ACs fully rewritten to reflect finalized design: DRAFT status, TIMESTAMPTZ datetimes, `topics` free text, STAFF full batch access. |
| 3.8 | April 2026 | **Backend-only split.** Flutter ACs extracted from all EPICs and moved to `microgreens_user_stories_frontend.md`. EPICs 11, 12, and Flutter portions of 13 removed from this doc. EPICs 14, 15, 16 (infra, security, launch) moved to `microgreens_user_stories_deployment.md`. Backend ACs in EPICs 2–10 rewritten to be API-endpoint-focused. EPIC 13 retains only backend test stories (US1, US2, US5). |
| 3.9 | April 2026 | E3-US2 complete. ACs rewritten to reflect finalised design: public `GET /api/v1/batches` (no JWT required), role-aware response shape (trimmed vs full), pagination validation, `DRAFT` batch returns 404 for clients, `ACCOUNT_NOT_VERIFIED` AC removed (untestable — PENDING_VERIFICATION accounts cannot obtain a JWT). FCM notification AC removed from this story — deferred to future notifications story. New error codes: `BATCH_NOT_BOOKABLE`, `BOOKING_ALREADY_EXISTS`. V5 migration includes all booking columns for E3-US3 and E3-US5 upfront. |
| 3.10 | April 2026 | E3-US3 complete. ACs rewritten to reflect finalised design: `GET /api/v1/bookings` with optional `?status=` and `?batchId=` filters, role-aware (ADMIN/STAFF/SUPER_ADMIN only), paginated with validation. `PATCH /api/v1/bookings/{id}` supports all correction transitions except same-status. `GET /api/v1/batches/{id}/bookings` dropped — covered by batchId filter. FCM deferred. New error code: `BOOKING_NOT_FOUND`. |
| 3.11 | April 2026 | E3-US4 complete. ACs rewritten to reflect finalised design: split into two endpoints (`/details` for field updates, `/status` for transitions). `COMPLETED` removed from `BatchStatus` enum — training completion is booking-level only. Auto-rejection on close with fixed note. Full audit log via `batch_status_logs` table (V6 migration). FCM deferred. New error codes: `BATCH_NOT_EDITABLE`, `INVALID_BATCH_STATUS_TRANSITION`. |
| 3.12 | April 2026 | E3-US5 (Staff Marks Training as Complete) moved to EPIC 4 as E4-US4 — content access logic depends on content model not yet defined. Existing E4-US4 and E4-US5 renumbered to E4-US5 and E4-US6. E3-US6 renumbered to E3-US5. |
| 3.13 | April 2026 | EPIC 4 fully redesigned as a generic, domain-agnostic content module. All 6 original crop-specific stories (E4-US1 through E4-US6) replaced with 10 new stories. Full stories moved to `microgreens_user_stories_epic4.md`. Main doc retains summary reference block only. BRD, unified HLD, LLD-Backend, and LLD-Frontend produced. |
| 3.14 | April 2026 | E4-US1 complete. ACs finalised: no `/content/` prefix in API paths (resources are `/api/v1/libraries`); `description` PATCH sentinel (`null` = no change, `""` = clear); `DRAFT→ARCHIVED` returns `INVALID_STATUS_TRANSITION`; `ARCHIVED→*` returns `LIBRARY_ARCHIVED` (checked first); `created_by` included in response. FCM deferred. New error codes: `LIBRARY_NOT_FOUND` (404), `LIBRARY_ARCHIVED` (422). V8 migration — all 5 content tables + all indexes. |
| 3.15 | April 2026 | E4-US2 complete. ACs rewritten to reflect finalised design: `sortOrder` uses array-index on reorder; `LIBRARY_ARCHIVED` guard added to DELETE and move; ARCHIVED library readable by staff on tree; `TreeNodeResponse` uses `@JsonInclude(NON_NULL)` for dual response shapes; `MAX_DEPTH_EXCEEDED` requires FOLDER at depth 3 as parent; `content_files` joins through `content_nodes` for library-scoped queries. No new Flyway migration. New error codes: `NODE_NOT_FOUND` (404), `MAX_DEPTH_EXCEEDED` (422), `INVALID_PARENT_NODE` (422), `MOVE_CROSS_LIBRARY` (422), `CONTENT_LOCKED` (403). |
| v3.16 | April 2026 | E4-US5 complete. ACs rewritten to reflect finalised design: ImageTypeDetector extracted from ProfileService into shared :core utility (adds GIF support); response shape extended to include mimeType and sizeBytes matching E4-US4 pattern; INSERT not upsert for INLINE_IMAGE rows; no DELETE endpoint -- orphan cleanup deferred to E4-US10; no new Flyway migration. No new error codes. |