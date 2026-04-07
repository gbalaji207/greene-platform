# Microgreens Training & Farming Platform — System Architecture

**Version:** 1.1
**Date:** April 2026
**Status:** Draft — Pending Sign-Off
**Backend:** Spring Boot (Kotlin) | **Frontend:** Flutter PWA
**Platform Type:** Single-Company | **Deployment:** VPS (Hetzner CX21+)

---

## Sign-Off

| Role | Name | Date |
|---|---|---|
| Tech Lead | | |
| Product Owner | | |
| DevOps Engineer | | |

---

## Table of Contents

1. [Platform Overview](#1-platform-overview)
2. [System Architecture](#2-system-architecture)
3. [API Contract Standards](#3-api-contract-standards)
4. [Key Data Flows](#4-key-data-flows)
5. [Role & Permission Matrix](#5-role--permission-matrix)
6. [Infrastructure & Deployment](#6-infrastructure--deployment)
7. [Security Baseline](#7-security-baseline)
8. [PWA & Cross-Platform Readiness](#8-pwa--cross-platform-readiness)
9. [Open Items](#9-open-items)
10. [Changelog](#10-changelog)

---

## 1. Platform Overview

The Microgreens Training & Farming Platform is a single-company, single-deployment system designed to manage the end-to-end client journey from enrollment through a guided growing experience. It is not a multi-tenant SaaS product.

### 1.1 Technology Stack

| Layer | Technology |
|---|---|
| Frontend | Flutter PWA (Dart) — targets Browser, Android Chrome, iOS Safari |
| Backend | Spring Boot 3.x (Kotlin) — Gradle multi-module monolith |
| Database | PostgreSQL 15 — primary store, schema migrations via Flyway |
| Cache / Sessions | Redis — rate limiting, session state, task queuing |
| Push Notifications | Firebase Cloud Messaging (FCM) |
| Email (all) | Amazon SES — OTP, welcome emails, notifications, ticket updates |
| Object Storage | Backblaze B2 / S3-compatible — media files, DB backups |
| Gateway | Nginx — reverse proxy, SSL termination (Let's Encrypt), gzip |
| Hosting | VPS (Hetzner CX21 minimum — 2 vCPU, 4 GB RAM) |
| CI/CD | GitHub Actions — build, test, staging deploy, production deploy |
| Monitoring | Grafana Loki / Papertrail + UptimeRobot |

### 1.2 User Roles

| Role | Description |
|---|---|
| `SUPER_ADMIN` | Developer / auditor access. No UI required. Full DB and log access. |
| `ADMIN` | Business owner. Manages batches, content, crops, users, and announcements. |
| `STAFF` | Operations team. Confirms bookings, manages tickets, marks training complete, uploads materials. |
| `CLIENT` | Grower. Registers, books batches, follows growing journey, raises support tickets. |

### 1.3 Client Journey

```
Register → Browse & Book Batch → Staff Confirms → Attend Offline Classes
→ Staff Unlocks Premium Content → Select Crop → Start Growing Journey (app-guided)
→ Daily Checklists & Journal → Ongoing Support via Tickets → Harvest
```

---

## 2. System Architecture

### 2.1 High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        CLIENT TIER                          │
│  Flutter PWA          Flutter PWA          Flutter PWA      │
│  (Browser/Android)    (Admin/Staff)        (iOS Safari)     │
└──────────────────────────────┬──────────────────────────────┘
                               │ HTTPS
┌──────────────────────────────▼──────────────────────────────┐
│                       GATEWAY TIER                          │
│           Nginx — reverse proxy, SSL termination            │
│     /api/* → Spring Boot  |  / → Flutter PWA static        │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│              SPRING BOOT BACKEND (Kotlin)                   │
│                  Gradle multi-module monolith               │
│  ┌──────────┬───────────┬──────────┬─────────┬──────────┐  │
│  │  :core   │ :training │ :farming │:journal │:content  │  │
│  ├──────────┴───────────┴──────────┴─────────┴──────────┤  │
│  │  :notif                                               │  │
│  ├───────────────────────────────────────────────────────┤  │
│  │  :support  ← logically isolated (see ADR-001)         │  │
│  └───────────────────────────────────────────────────────┘  │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│                        DATA TIER                            │
│  PostgreSQL 15   Redis 7   Object Storage   Firebase FCM   │
│  (primary store) (cache)   (B2/S3 media)   (push notif)   │
│                                                             │
│  Amazon SES (all outbound email — OTP, welcome, updates)   │
└─────────────────────────────────────────────────────────────┘
```

| Tier | Components |
|---|---|
| Client Tier | Flutter PWA (Browser / Android Chrome / iOS Safari). Admin and Staff share the same PWA with role-gated UI. |
| Gateway Tier | Nginx reverse proxy. Routes `/api/*` to Spring Boot, `/` to Flutter PWA static files. Handles SSL termination, gzip, and HTTP→HTTPS redirect. |
| Backend Tier | Spring Boot Kotlin monolith structured as a Gradle multi-module project. Modules: `core`, `training`, `farming`, `journal`, `content`, `notif`, `support` (isolated). |
| Data Tier | PostgreSQL (primary store), Redis (cache/rate-limit), Object Storage B2/S3 (media + backups), Firebase FCM (push notifications), Amazon SES (all email). |

### 2.2 Gradle Multi-Module Structure

```
:core          — Auth, JWT, RBAC, shared DTOs, base exceptions
:training      — Batch management, booking workflow
:farming       — Tray tracking, crop checklists, reminders, streak
:journal       — Daily journal entries, photos, staff comments
:content       — Crop library, premium content, session materials
:notif         — Push notification dispatch, broadcast, FCM integration
:support       — Tickets, messages, FAQ  [ISOLATED — see ADR-001]
```

Each module is independently buildable. The `:core` module has no cross-module dependencies. All other modules depend on `:core` only.

### 2.3 Support Module Isolation (ADR-001)

> **Status:** Accepted | **Date:** April 2026

#### Decision

The `:support` module is designed as if it were a standalone microservice, even though it runs inside the monolith. This enables future extraction with minimal refactoring.

#### Rationale

The support module has broad reuse potential across projects. Keeping it logically isolated now means extraction later is a matter of adding an HTTP layer around what is already an isolated unit — not untangling domain dependencies.

#### Isolation Rules

- `:support` may only import `:core`. Never `:training`, `:farming`, `:journal`, or `:content`.
- Cross-module data is accessed exclusively through the `SupportContextPort` interface, implemented by `SupportContextAdapter` in the `:app` layer.
- Snapshot DTOs (`TraySnapshot`, `JournalEntrySnapshot`, `ChecklistComplianceSummary`) are defined inside `:support` — they are not JPA entities from other modules.
- Database tables are prefixed `support_*` with no foreign key constraints pointing to domain tables. Linked IDs (e.g. `tray_id`) are stored as plain `UUID` columns.
- The port interface remains stable; the adapter behind it is swappable (local impl → HTTP client on extraction).

#### Interface Contract

```kotlin
interface SupportContextPort {
    fun getClientTraySnapshot(clientId: UUID, trayId: UUID): TraySnapshot?
    fun getRecentJournalEntries(clientId: UUID, trayId: UUID, limit: Int): List<JournalEntrySnapshot>
    fun getChecklistComplianceLast7Days(clientId: UUID, trayId: UUID): ChecklistComplianceSummary
}
```

```kotlin
// Snapshot DTOs — defined inside :support, not shared JPA entities
data class TraySnapshot(val trayId: UUID, val cropName: String, val stage: String, val dayNumber: Int)
data class JournalEntrySnapshot(val date: LocalDate, val text: String, val photoUrls: List<String>)
data class ChecklistComplianceSummary(val completedDays: Int, val totalDays: Int)
```

#### Future Extraction Path

When extraction to a standalone service is needed:
1. Move `:support` module into its own repository — it already has no domain imports
2. Replace `SupportContextAdapter` with HTTP client calls to the main platform's internal API
3. `SupportContextPort` interface stays identical — `TicketService` does not change

---

## 3. API Contract Standards

### 3.1 Response Envelope

All API responses use a consistent envelope. The Flutter client must always unwrap from `data` before using the payload.

**Success:**
```json
{
  "data": { },
  "meta": { "timestamp": "2026-04-06T10:00:00Z", "version": "1.0" }
}
```

**Paginated:**
```json
{
  "data": {
    "items": [],
    "page": 1,
    "pageSize": 20,
    "total": 148
  },
  "meta": {}
}
```

**Error:**
```json
{
  "error": {
    "code": "BATCH_NOT_FOUND",
    "message": "Batch with id X not found",
    "details": {}
  }
}
```

### 3.2 HTTP Status Codes

| Status | Usage |
|---|---|
| `200 OK` | Successful GET, PUT, PATCH |
| `201 Created` | Successful POST that creates a resource |
| `204 No Content` | Successful DELETE |
| `400 Bad Request` | Validation failure — includes field-level details in `error.details` |
| `401 Unauthorized` | Missing or invalid JWT |
| `403 Forbidden` | Authenticated but insufficient role/permission |
| `404 Not Found` | Resource does not exist |
| `409 Conflict` | Duplicate resource (e.g. email already registered) |
| `422 Unprocessable` | Business rule violation (e.g. batch already closed) |
| `429 Too Many Requests` | Rate limit exceeded — includes `Retry-After` header |
| `500 Internal Server Error` | Unhandled exception — logged, never exposes stack trace |

### 3.3 REST Conventions

- Base path: `/api/v1/`
- Resource names are plural nouns: `/batches`, `/tickets`, `/trays`, `/crops`
- Nested resources for strong ownership: `/trays/{id}/journal-entries`, `/tickets/{id}/messages`
- Filters via query params: `?status=OPEN&clientId=...` — never in path for filtering
- Dates: ISO 8601 (`yyyy-MM-dd`). Timestamps: ISO 8601 UTC with `Z` suffix.
- UUIDs for all resource IDs. Never expose sequential integer IDs.
- OpenAPI/Swagger spec auto-generated from Spring Boot annotations. Accessible at `/swagger-ui` in dev and staging.

---

## 4. Key Data Flows

### 4.1 Authentication Flow

| Step | Description |
|---|---|
| 1 | Client submits registration form (name, email, phone, city) → `POST /api/v1/auth/register` |
| 2 | Backend validates input, checks for duplicate email/phone (409 if duplicate) |
| 3 | Email OTP dispatched via Amazon SES. Account created with status `PENDING_VERIFICATION`, role `CLIENT` |
| 4 | Client submits OTP → `POST /api/v1/auth/verify-otp`. Account status set to `ACTIVE` |
| 5 | Login: `POST /api/v1/auth/login` with email+password or email OTP |
| 6 | Backend returns JWT access token (15 min expiry) + refresh token (30 days, stored hashed in DB) |
| 7 | Flutter stores tokens in `flutter_secure_storage` |
| 8 | On 401: Flutter auto-calls `POST /api/v1/auth/refresh`. Refresh token rotated on each use |
| 9 | Logout: `POST /api/v1/auth/logout`. Refresh token invalidated in DB |

### 4.2 Batch Booking & Content Unlock Flow

| Step | Description |
|---|---|
| 1 | Client browses open batches → `GET /api/v1/batches?status=OPEN` |
| 2 | Client books a batch → `POST /api/v1/batches/{id}/bookings`. Status: `PENDING` |
| 3 | Staff receives push notification (FCM) of new booking |
| 4 | Staff confirms or rejects → `PATCH /api/v1/bookings/{id}` `{ status: CONFIRMED | REJECTED, note }` |
| 5 | Client notified via push + booking status updated. Confirmed clients appear in batch roster |
| 6 | Offline training conducted. Staff marks batch complete → `PATCH /api/v1/batches/{id}` `{ trainingStatus: COMPLETED }` |
| 7 | Backend sets `content_access_granted = true` for all confirmed clients in that batch |
| 8 | Push notification dispatched to all affected clients via FCM |
| 9 | Client selects crop → `POST /api/v1/journeys`. Growing plan + checklist template auto-assigned |

### 4.3 Daily Checklist & Reminder Flow

| Step | Description |
|---|---|
| 1 | Scheduled job runs at midnight IST for all active growing clients |
| 2 | Derives day number from journey start date. Fetches crop checklist template entry for that day |
| 3 | Generates task records in DB linked to client + tray + day |
| 4 | Morning reminder at 8:00 AM IST dispatched via FCM with that day's checklist guidance text |
| 5 | Client completes tasks → `PATCH /api/v1/tasks/{id}` `{ completed: true, value?, photoUrl? }` |
| 6 | Completion timestamp recorded. Daily completion % calculated and exposed on client dashboard |
| 7 | If tasks remain incomplete by 6:00 PM: second reminder dispatched via FCM |
| 8 | End of day: streak logic evaluated. Full completion = streak +1; missed = streak reset to 0 |

### 4.4 Support Ticket Flow

| Step | Description |
|---|---|
| 1 | Client raises ticket → `POST /api/v1/tickets` `{ subject, description, trayId?, photos[] }` |
| 2 | Ticket created with status `OPEN`. Max 3 open tickets enforced per client (422 if exceeded) |
| 3 | Staff notified via FCM. Ticket auto-assigned to first staff member who opens it |
| 4 | Ticket detail fetches context via `SupportContextPort`: tray snapshot, last 5 journal entries, 7-day checklist compliance |
| 5 | Staff responds → `POST /api/v1/tickets/{id}/messages`. Ticket status → `IN_PROGRESS` |
| 6 | Client notified via FCM. Client replies → `POST /api/v1/tickets/{id}/messages` |
| 7 | Staff or client marks resolved. Client can reopen within 3 days |
| 8 | Auto-close: if no activity for 7 days, ticket closed automatically with notification |

---

## 5. Role & Permission Matrix

`Y` = Full access | `N` = No access | `R` = Read only

| Action | SUPER_ADMIN | ADMIN | STAFF | CLIENT |
|---|:---:|:---:|:---:|:---:|
| **Auth & Users** | | | | |
| Register account (self) | Y | Y | Y | Y |
| Create staff accounts | Y | Y | N | N |
| Suspend / activate users | Y | Y | N | N |
| Change user role | Y | Y | N | N |
| View all users | Y | Y | Y | N |
| **Batches** | | | | |
| Create / edit batch | Y | Y | N | N |
| Close / reopen batch | Y | Y | N | N |
| Confirm / reject booking | Y | Y | Y | N |
| Mark training complete | Y | Y | Y | N |
| Browse open batches | Y | Y | Y | Y |
| Book a batch | N | N | N | Y |
| **Content** | | | | |
| Manage crop library (CRUD) | Y | Y | N | N |
| Manage checklist templates | Y | Y | N | N |
| Upload premium content | Y | Y | N | N |
| Access premium content | Y | Y | Y | Y* |
| Upload session materials | Y | Y | Y | N |
| **Farming & Journal** | | | | |
| Create tray | N | N | N | Y |
| Complete checklist tasks | N | N | N | Y |
| Write journal entries | N | N | N | Y |
| Comment on journal entries | Y | Y | Y | N |
| View any client journal | Y | Y | Y | N |
| Log harvest | N | N | N | Y |
| **Support** | | | | |
| Raise support ticket | N | N | N | Y |
| Respond to ticket | Y | Y | Y | N |
| Close / resolve ticket | Y | Y | Y | Y |
| View all tickets | Y | Y | Y | N |
| Add internal ticket note | Y | Y | Y | N |
| Send broadcast | Y | Y | Y | N |
| Manage FAQ | Y | Y | N | N |
| **Analytics & Admin** | | | | |
| View platform analytics | Y | Y | N | N |
| View client overview | Y | Y | Y | N |
| View individual client report | Y | Y | Y | N |
| Send platform announcements | Y | Y | N | N |
| Access audit logs | Y | N | N | N |

> \* `CLIENT` access to premium content is gated on `content_access_granted = true` per batch enrollment. Staff and Admin bypass this gate.

---

## 6. Infrastructure & Deployment

### 6.1 VPS Configuration

| Parameter | Value |
|---|---|
| Provider | Hetzner Cloud (CX21 minimum — 2 vCPU, 4 GB RAM, 40 GB SSD) |
| OS | Ubuntu 24.04 LTS |
| Open Ports | 22 (SSH, key-based only), 80 (HTTP→HTTPS redirect), 443 (HTTPS) |
| Swap | 2 GB minimum configured |
| Timezone | Asia/Kolkata (IST) |
| SSH | Key-based authentication only. Password login disabled. |

### 6.2 Docker Compose Services

| Service | Notes |
|---|---|
| `spring-boot-app` | Built from Dockerfile. Depends on `postgres`, `redis`. Health check on `/api/v1/health`. |
| `postgres` | PostgreSQL 15. Named volume for data persistence. Flyway migrations run on startup. |
| `redis` | Redis 7. Used for rate limiting (INCR/EXPIRE) and session store. |
| `nginx` | Reverse proxy + static file server. SSL termination. Certbot-managed certs. |

### 6.3 Nginx Routing

```nginx
location /api/ {
    proxy_pass http://spring-boot-app:8080;
}

location / {
    root /var/www/app;
    try_files $uri /index.html;   # Required for Flutter web routing
}
```

All Flutter web routes return `index.html`. HTTP → HTTPS redirect enforced. Gzip compression enabled for static assets.

### 6.4 CI/CD Pipeline (GitHub Actions)

| Trigger | Action |
|---|---|
| PR opened | Build + unit tests + lint for backend (Kotlin) and Flutter |
| Merge to `main` | Auto-deploy to staging environment |
| Manual trigger | Deploy to production with confirmation step required |
| Pipeline failure | Notification via email or Slack |

Target pipeline runtime: under 10 minutes for standard builds.

### 6.5 Database Backup Strategy

- Daily automated backup of PostgreSQL to object storage (B2/S3)
- Retention period: 30 days
- Restore procedure must be documented and tested before go-live
- Backup success/failure alerts configured via monitoring integration

---

## 7. Security Baseline

### 7.1 Authentication & Token Management

- JWT access tokens: 15-minute expiry, signed with RS256
- Refresh tokens: 30-day expiry, stored hashed in DB, rotated on every use
- All active refresh tokens invalidated on password reset or logout
- Flutter: tokens stored in `flutter_secure_storage` (never `localStorage`)

### 7.2 Rate Limiting

| Endpoint | Limit |
|---|---|
| `POST /auth/login` | 5 attempts per 15 min per IP |
| `POST /auth/otp` | 3 requests per 10 min per email address |
| All authenticated endpoints | 100 requests per minute per user |

Rate limit exceeded returns `429 Too Many Requests` with `Retry-After` header.

### 7.3 Input Validation & Data Privacy

- All request DTOs validated via Spring Boot Bean Validation (`@Valid`)
- SQL injection prevented via JPA parameterized queries only — no raw SQL with string concatenation
- File uploads: type whitelist (`jpg`, `png`, `pdf`, `mp4`) + size limits enforced server-side
- XSS prevention: all user-generated content HTML-escaped before storage and on output
- Sensitive fields (phone, email) encrypted at rest
- Client account deletion anonymises personal data within 30 days

### 7.4 Pre-Launch Security Checklist

- [ ] OWASP Top 10 checklist reviewed and signed off by tech lead
- [ ] All API endpoints verified to require authentication (no accidental public endpoints)
- [ ] Git history scanned for committed secrets
- [ ] Dependency vulnerability scan run via OWASP Dependency-Check
- [ ] Load test completed: 200 concurrent users, p95 < 500ms, error rate < 1%

---

## 8. PWA & Cross-Platform Readiness

### 8.1 Flutter Web Build

- Production build: `flutter build web --release`
- Web app manifest: name, icons (192px, 512px), theme colour defined
- Service worker registered for caching static assets
- Installable on Android Chrome and iOS Safari (Add to Home Screen)
- Target Lighthouse PWA score: ≥ 85

### 8.2 Offline Checklist

- Today's checklist cached locally via service worker on load
- Task completions stored in IndexedDB when offline
- Changes synced to server on connection restore (background sync)
- Offline indicator displayed clearly in app UI
- No blank screens or unhandled errors when offline

### 8.3 Responsive Breakpoints

| Breakpoint | Width | Layout |
|---|---|---|
| Mobile | < 600px | Single column, bottom navigation bar |
| Tablet | 600–1024px | Two-column layout where appropriate |
| Desktop | > 1024px | Sidebar navigation, wider content area |

All interactive touch targets: minimum 48×48px.

**Tested on:** Android Chrome, iOS Safari, Chrome desktop, Firefox desktop.

---

## 9. Open Items

| # | Item | Owner | Target Date |
|---|---|---|---|
| 1 | Confirm VPS provider and plan (Hetzner CX21 vs CX31) | DevOps | |
| 2 | Confirm object storage provider (Backblaze B2 vs AWS S3) | DevOps | |
| 3 | Firebase project setup and FCM credentials provisioned | Backend Lead | |
| 4 | Domain name finalised and DNS configured | Product Owner | |
| 5 | Amazon SES domain verification and sending limits increase completed before launch | Backend Lead | |
| 6 | Google Play Store developer account created for Play Store submission | Product Owner | |
| 7 | Architecture document reviewed and signed off by all listed roles | Tech Lead | |

---

## 10. Changelog

| Version | Date | Changes |
|---|---|---|
| 1.0 | April 2026 | Initial architecture document. Full stack, API contracts, data flows, role matrix, support module isolation ADR-001, infrastructure baseline, security checklist. |
| 1.1 | April 2026 | OTP delivery changed from phone SMS to email. Amazon SES adopted as the single email provider for all transactional email (OTP, welcome, notifications, ticket updates). OTP rate limit updated to per-email-address. Open items updated: OTP provider row removed, email provider resolved as Amazon SES. |
