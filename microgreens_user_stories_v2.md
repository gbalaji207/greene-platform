# Microgreens Training & Farming Platform — User Stories v2.0

**Backend:** Spring Boot (Kotlin) | **Frontend:** Flutter PWA  
**Last Updated:** 2025  
**Version:** 2.0 — Single company platform, revised after stakeholder clarification  
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

## EPIC 1 — Project Foundation & Architecture

### E1-US1: System Architecture Document
**As a** tech lead,  
**I want** a finalized architecture diagram and document,  
**So that** all developers build consistently against a shared blueprint.

**Acceptance Criteria:**
- [ ] Architecture diagram covers Flutter PWA ↔ Spring Boot ↔ PostgreSQL ↔ Firebase
- [ ] API contract standards defined (REST conventions, error format, pagination)
- [ ] Data flow documented for key scenarios (auth, batch booking, content unlock, journal, support ticket)
- [ ] Role and permission matrix documented
- [ ] Document reviewed and signed off before development begins

---

### E1-US2: Backend Gradle Multi-Module Setup
**As a** backend developer,  
**I want** a Gradle multi-module project structure,  
**So that** each domain module is independently buildable and reusable across projects.

**Acceptance Criteria:**
- [ ] Root project with submodules: `core`, `training`, `farming`, `journal`, `support`, `notifications`, `content`
- [ ] Shared dependencies declared in version catalog (`libs.versions.toml`)
- [ ] Each module has its own `build.gradle.kts`
- [ ] `core` module compiles independently with no cross-module dependencies
- [ ] CI builds all modules and runs tests per module

---

### E1-US3: Flutter Project Setup
**As a** frontend developer,  
**I want** a clean Flutter project with folder structure, routing, and state management configured,  
**So that** all developers follow consistent patterns from day one.

**Acceptance Criteria:**
- [ ] Folder structure: `features/`, `core/`, `shared/`, `config/`
- [ ] GoRouter configured with named routes and role-based auth guards
- [ ] Bloc/Cubit set up with example feature
- [ ] Flavor configuration for dev, staging, production environments
- [ ] Lint rules and formatting enforced via `flutter analyze` and `dart format`

---

### E1-US4: API Contract Standards
**As a** developer,  
**I want** standardized API request/response formats,  
**So that** Flutter and Spring Boot teams integrate without ambiguity.

**Acceptance Criteria:**
- [ ] Standard success response envelope: `{ data, meta }`
- [ ] Standard error response: `{ code, message, details }`
- [ ] Pagination format: `{ items, page, pageSize, total }`
- [ ] OpenAPI/Swagger spec auto-generated from Spring Boot annotations
- [ ] Swagger UI accessible in dev and staging environments

---

### E1-US5: Environment Configuration
**As a** developer,  
**I want** separate dev, staging, and production environment configurations,  
**So that** changes can be tested safely before going live.

**Acceptance Criteria:**
- [ ] Spring Boot `application-dev.yml`, `application-staging.yml`, `application-prod.yml` configured
- [ ] Flutter flavor-based `config.dart` per environment
- [ ] Secrets never committed to repository (`.gitignore` enforced)
- [ ] Environment switching documented for all team members

---

## EPIC 2 — Infrastructure & DevOps

### E2-US1: VPS Provisioning
**As a** DevOps engineer,  
**I want** a production-ready VPS set up,  
**So that** all services can be deployed reliably.

**Acceptance Criteria:**
- [ ] VPS provisioned (Hetzner CX21 or equivalent — minimum 2GB RAM)
- [ ] SSH key-based access configured, password login disabled
- [ ] Firewall rules: only ports 22, 80, 443 open
- [ ] Swap space configured (2GB minimum)
- [ ] Server timezone set to IST

---

### E2-US2: Docker & Docker Compose Setup
**As a** DevOps engineer,  
**I want** all services containerized with Docker Compose,  
**So that** the full stack can be started and stopped with a single command.

**Acceptance Criteria:**
- [ ] `docker-compose.yml` includes: Spring Boot app, PostgreSQL, Redis, Nginx
- [ ] Named volumes configured for PostgreSQL data persistence
- [ ] Health checks defined for each service
- [ ] `.env.example` provided with all required variables
- [ ] `docker-compose up` starts full stack locally without errors

---

### E2-US3: CI/CD Pipeline
**As a** developer,  
**I want** automated build, test, and deploy pipelines via GitHub Actions,  
**So that** every push is validated and deployable without manual steps.

**Acceptance Criteria:**
- [ ] PR pipeline: build + unit tests + lint for both backend and Flutter
- [ ] Merge to `main`: auto-deploy to staging
- [ ] Manual trigger: deploy to production with confirmation step
- [ ] Notification on pipeline failure (email or Slack)
- [ ] Pipeline runtime under 10 minutes for standard builds

---

### E2-US4: PostgreSQL Setup & Backup
**As a** DevOps engineer,  
**I want** PostgreSQL configured with automated backups,  
**So that** data is never permanently lost.

**Acceptance Criteria:**
- [ ] PostgreSQL running in Docker with persistent volume
- [ ] Daily automated backup to object storage (Backblaze B2 or S3-compatible)
- [ ] Backup retention: 30 days
- [ ] Restore procedure documented and tested
- [ ] Backup success/failure alerts configured

---

### E2-US5: Nginx Reverse Proxy & SSL
**As a** DevOps engineer,  
**I want** Nginx configured as a reverse proxy with SSL termination,  
**So that** all traffic is served securely over HTTPS.

**Acceptance Criteria:**
- [ ] Nginx proxies `/api/*` to Spring Boot and `/` to Flutter PWA static files
- [ ] SSL certificate issued via Let's Encrypt (Certbot)
- [ ] Auto-renewal configured for SSL certificate
- [ ] HTTP redirects to HTTPS
- [ ] Gzip compression enabled for static assets

---

### E2-US6: Logging & Monitoring
**As a** DevOps engineer,  
**I want** centralized logging and uptime monitoring,  
**So that** issues are detected and diagnosed quickly.

**Acceptance Criteria:**
- [ ] Spring Boot logs shipped to Grafana Loki or Papertrail
- [ ] Uptime monitoring configured (UptimeRobot free tier minimum)
- [ ] Alert triggered if service down for more than 2 minutes
- [ ] Error rate dashboard available
- [ ] Log retention policy: 90 days

---

## EPIC 3 — Authentication & User Management

### E3-US1: Client Self-Registration
**As a** prospective client,  
**I want** to register on the platform myself,  
**So that** I can browse batches and begin my enrollment journey.

**Acceptance Criteria:**
- [ ] Registration form: name, email, phone, city
- [ ] Phone OTP verification on registration
- [ ] Duplicate email/phone rejected with clear error message
- [ ] Account created with `ROLE_CLIENT` on successful registration
- [ ] Welcome email sent with next steps (browse batches)

---

### E3-US2: Staff Account Creation by Admin
**As an** admin,  
**I want** to create staff accounts from the admin panel,  
**So that** staff can log in and manage clients without self-registering.

**Acceptance Criteria:**
- [ ] Admin creates staff account: name, email, phone, role (`STAFF`)
- [ ] Staff receives email with temporary password and login link
- [ ] Staff prompted to change password on first login
- [ ] Admin can deactivate a staff account
- [ ] Deactivated staff cannot log in

---

### E3-US3: Login & JWT Auth
**As a** registered user,  
**I want** to log in securely and stay logged in,  
**So that** I don't have to re-authenticate every session.

**Acceptance Criteria:**
- [ ] Login via email + password or phone + OTP
- [ ] JWT access token (15 min expiry) + refresh token (30 days) returned
- [ ] Refresh token rotation on each use
- [ ] Refresh token invalidated on logout
- [ ] Flutter stores tokens in `flutter_secure_storage`

---

### E3-US4: Role-Based Access Control
**As a** system,  
**I want** role-based access enforced on all API endpoints,  
**So that** clients cannot access staff/admin features and vice versa.

**Acceptance Criteria:**
- [ ] Roles defined: `SUPER_ADMIN`, `ADMIN`, `STAFF`, `CLIENT`
- [ ] Spring Security method-level annotations applied (`@PreAuthorize`)
- [ ] Unauthorized access returns `403` with clear message
- [ ] Role visible in JWT claims
- [ ] Admin can change a user's role (except Super Admin)

---

### E3-US5: Password Reset
**As a** user,  
**I want** to reset my password if I forget it,  
**So that** I can regain access without contacting support.

**Acceptance Criteria:**
- [ ] "Forgot password" initiates email OTP
- [ ] OTP valid for 10 minutes, single use
- [ ] Password reset requires OTP + new password confirmation
- [ ] All active sessions invalidated after password reset
- [ ] Flutter: forgot password screen with OTP input and new password form

---

### E3-US6: Client Profile Management
**As a** client,  
**I want** to view and update my profile,  
**So that** my information stays current.

**Acceptance Criteria:**
- [ ] View and edit: name, city, profile photo
- [ ] Phone/email change requires OTP verification
- [ ] Profile photo uploaded to object storage
- [ ] Changes saved with success confirmation
- [ ] Flutter: profile screen with edit mode

---

## EPIC 4 — Batch Management

### E4-US1: Admin Creates a Batch
**As an** admin,  
**I want** to create a new training batch,  
**So that** clients can browse and book it.

**Acceptance Criteria:**
- [ ] Batch creation form: name, description, start date, end date, location, crop focus, max seats (for reference only — not auto-enforced)
- [ ] Batch status: `OPEN`, `CLOSED`, `COMPLETED`
- [ ] Batch visible to clients immediately on creation
- [ ] Admin can edit batch details before it is closed
- [ ] Admin can create multiple concurrent batches

---

### E4-US2: Client Browses and Books a Batch
**As a** client,  
**I want** to browse available batches and book a slot,  
**So that** I can enroll in the training program that suits me.

**Acceptance Criteria:**
- [ ] Client sees list of open batches: name, dates, location, crop focus, description
- [ ] Client selects a batch and submits a booking request (no payment needed)
- [ ] Booking status set to `PENDING` on submission
- [ ] Client sees confirmation screen: "Your booking is under review. Staff will confirm shortly."
- [ ] Staff notified of new booking request via push notification

---

### E4-US3: Staff Confirms or Rejects Booking
**As a** staff member,  
**I want** to review and confirm or reject client booking requests,  
**So that** only appropriate clients are enrolled in each batch.

**Acceptance Criteria:**
- [ ] Staff sees list of pending booking requests with client details
- [ ] Staff can confirm or reject with an optional note
- [ ] On confirmation: booking status set to `CONFIRMED`, client notified
- [ ] On rejection: booking status set to `REJECTED`, client notified with reason
- [ ] Confirmed clients appear in batch client list

---

### E4-US4: Admin Closes a Batch
**As an** admin,  
**I want** to manually close a batch when seats are filled,  
**So that** no further bookings are accepted.

**Acceptance Criteria:**
- [ ] Admin can set batch status to `CLOSED` from admin panel
- [ ] Closed batch no longer visible in client's batch browse list
- [ ] Pending bookings at time of closure are auto-rejected with notification
- [ ] Admin can reopen a closed batch if needed
- [ ] Batch status change logged with timestamp and admin ID

---

### E4-US5: Staff Marks Training as Complete
**As a** staff member,  
**I want** to mark a batch's offline training as complete,  
**So that** all enrolled clients automatically get access to premium content.

**Acceptance Criteria:**
- [ ] Staff selects batch and marks training status as `COMPLETED`
- [ ] All confirmed clients under that batch instantly get premium content access
- [ ] Clients notified via push notification: "Your training is complete — your growing journey begins!"
- [ ] Individual client can also be marked complete separately if they missed a session and caught up
- [ ] Training completion date recorded per client

---

### E4-US6: Client Views Their Enrollment Status
**As a** client,  
**I want** to see the status of my batch booking and training,  
**So that** I know where I am in the journey.

**Acceptance Criteria:**
- [ ] Client sees current status: Booking Pending / Confirmed / Training Ongoing / Training Complete / Growing
- [ ] Status shown as a visual step indicator on home screen
- [ ] Batch details visible: name, dates, location, staff contact
- [ ] Client notified on every status change
- [ ] Past completed batches visible in enrollment history

---

## EPIC 5 — Content Management

### E5-US1: Crop Library
**As a** client,  
**I want** to browse a library of supported crop varieties,  
**So that** I can understand what I will be growing.

**Acceptance Criteria:**
- [ ] Crop list with: name, photo, difficulty level, days to harvest, yield estimate
- [ ] Filter by difficulty (beginner, intermediate, advanced)
- [ ] Crop detail page: description, ideal conditions, common problems
- [ ] Crop library accessible before and after enrollment (free content)
- [ ] Admin manages crop library from admin panel

---

### E5-US2: Admin Manages Premium Content
**As an** admin,  
**I want** to upload and organize premium content for each crop,  
**So that** clients get structured, high-quality guidance after training.

**Acceptance Criteria:**
- [ ] Admin can create content items: title, description, type (video, article, PDF, image)
- [ ] Content linked to a specific crop
- [ ] Content ordered within a crop (drag-and-drop reorder)
- [ ] Content saved as draft or published immediately
- [ ] Published premium content only accessible to clients who have completed training

---

### E5-US3: Staff Uploads Session Materials
**As a** staff member,  
**I want** to upload materials for a batch after offline sessions,  
**So that** clients can refer to session-specific resources in the app.

**Acceptance Criteria:**
- [ ] Staff selects batch and uploads materials: PDFs, images, reference documents
- [ ] Materials tagged to specific session number (e.g., Session 2 Notes)
- [ ] Materials visible only to clients enrolled in that batch
- [ ] Client notified when new material is uploaded
- [ ] Staff can delete or replace uploaded materials

---

### E5-US4: Client Accesses Premium Content
**As a** client who has completed training,  
**I want** to access premium growing content for my crop,  
**So that** I can follow detailed instructions during my growing journey.

**Acceptance Criteria:**
- [ ] Premium content section locked until training marked complete
- [ ] On unlock: client sees full content library for their enrolled crop
- [ ] Content consumed in order (next item unlocks after current is viewed)
- [ ] Video watch progress tracked (resume from last position)
- [ ] Locked content shows a clear message: "Complete your training to unlock"

---

### E5-US5: Client Selects Crop to Start Growing Journey
**As a** client,  
**I want** to select my crop when I begin my growing journey,  
**So that** the platform generates the right checklist and plan for me from day 1.

**Acceptance Criteria:**
- [ ] Crop selection screen shown after training completion notification
- [ ] Client selects from crops covered in their batch
- [ ] On selection: growing plan and checklist templates auto-assigned
- [ ] Client can start journey immediately after selection
- [ ] Crop selection locked after first checklist is completed (cannot change mid-journey)

---

## EPIC 6 — Crop Checklist & Reminder System

### E6-US1: Admin Creates Crop Checklist Templates
**As an** admin,  
**I want** to define a day-by-day checklist template for each crop from day 1 to harvest,  
**So that** clients get precise, crop-specific guidance throughout their growing journey.

**Acceptance Criteria:**
- [ ] Admin creates checklist template per crop: one entry per day
- [ ] Each day entry includes: stage name, tasks, guidance text, tips, what to watch for
- [ ] Task types supported: checkbox, number input (e.g., water ml), photo upload
- [ ] Templates versioned — editing a template does not affect clients already on that template
- [ ] Admin can preview the full day-by-day plan before publishing

---

### E6-US2: Daily Task Generation per Client
**As a** client,  
**I want** my daily tasks auto-generated based on my crop and current day of journey,  
**So that** I know exactly what to do without guessing.

**Acceptance Criteria:**
- [ ] Tasks generated at midnight for each active growing client
- [ ] Tasks derived from crop checklist template for that day number
- [ ] Day number calculated from journey start date
- [ ] Tasks grouped clearly on home screen with day number and stage name
- [ ] "No tasks today" shown gracefully if template has no entry for that day

---

### E6-US3: Stage-Aware Smart Reminders
**As a** client,  
**I want** reminders that are specific to my current growth stage,  
**So that** I receive contextually relevant nudges and not generic alerts.

**Acceptance Criteria:**
- [ ] Reminder content pulled from that day's checklist guidance text
- [ ] Example: Day 3 reminder — "Time to check germination — open your tray and take a photo"
- [ ] Default reminder time: 8:00 AM IST daily
- [ ] Client can customize reminder time from settings
- [ ] No reminder sent if all tasks for the day are already completed
- [ ] Second reminder at 6:00 PM if tasks remain incomplete

---

### E6-US4: Checklist Completion Tracking
**As a** client,  
**I want** to mark tasks as complete and log required values,  
**So that** my progress is tracked and visible to staff.

**Acceptance Criteria:**
- [ ] Tap to complete checkbox tasks
- [ ] Number input tasks require a value before marking complete
- [ ] Photo tasks open camera — photo attached to that day's log
- [ ] Completion timestamp recorded per task
- [ ] Daily completion percentage shown (e.g., "4 of 6 tasks done today")
- [ ] Staff can view checklist completion history per client

---

### E6-US5: Streak Tracking
**As a** client,  
**I want** to see my checklist completion streak,  
**So that** I stay motivated to tend to my trays consistently.

**Acceptance Criteria:**
- [ ] Streak count shown on home screen (e.g., "🔥 7 day streak")
- [ ] Streak increments when all tasks for the day are completed
- [ ] Streak resets if tasks are missed for a full day
- [ ] Longest streak recorded and shown on profile
- [ ] Staff sees client streak on client detail screen

---

### E6-US6: Growing Journey Completion
**As a** client,  
**I want** to be notified when my growing journey reaches harvest day,  
**So that** I know my first cycle is complete and can log my harvest.

**Acceptance Criteria:**
- [ ] Final day checklist includes harvest tasks
- [ ] On completing final day: congratulations screen shown
- [ ] Client prompted to log harvest (yield, photo, notes)
- [ ] Journey marked as `COMPLETED` after harvest log submitted
- [ ] Staff notified of client's first successful harvest

---

## EPIC 7 — Tray & Batch Tracking

### E7-US1: Client Creates a Tray
**As a** client starting their growing journey,  
**I want** to register my tray on the platform,  
**So that** the system can track it and generate the right daily tasks.

**Acceptance Criteria:**
- [ ] Tray creation form: crop (pre-selected from journey crop), tray size, start date, optional name/label
- [ ] Expected harvest date auto-calculated from crop template
- [ ] Tray status set to `ACTIVE` on creation
- [ ] Growing plan and checklist immediately associated to tray
- [ ] Client can create a second tray for a new cycle after first is harvested

---

### E7-US2: Growth Stage Tracking
**As a** client,  
**I want** to see my tray's current growth stage,  
**So that** I know where I am in the cycle at a glance.

**Acceptance Criteria:**
- [ ] Stages derived from crop checklist template: e.g., Soaking → Sowing → Germination → Blackout → Light → Harvest
- [ ] Current stage auto-calculated from journey day number
- [ ] Stage shown as visual progress timeline on tray detail screen
- [ ] Stage name included in daily reminder text
- [ ] Staff sees current stage for all client trays

---

### E7-US3: Harvest Logging
**As a** client,  
**I want** to log my harvest when I cut my microgreens,  
**So that** I have a record of my yield and can share it with staff.

**Acceptance Criteria:**
- [ ] Harvest log form: date, yield in grams, quality rating (1–5 stars), notes, photo
- [ ] Tray status set to `HARVESTED` after logging
- [ ] Harvest log visible to staff on client detail screen
- [ ] Running yield total shown on client dashboard
- [ ] Client can view all past harvest logs

---

### E7-US4: Tray History
**As a** client,  
**I want** to view all my past trays and their outcomes,  
**So that** I can learn from each cycle and improve.

**Acceptance Criteria:**
- [ ] List of all trays: crop, start date, harvest date, yield, status
- [ ] Tap tray to view full history: checklist completion per day, journal entries, harvest log
- [ ] Average yield shown across all completed trays
- [ ] Staff can view full tray history per client

---

## EPIC 8 — Tray Journal

### E8-US1: Client Creates Journal Entry
**As a** client,  
**I want** to log daily observations about my tray as journal entries,  
**So that** I have a growing diary and can share progress with staff for guidance.

**Acceptance Criteria:**
- [ ] Journal entry form: date (defaults to today), text observation, one or more photos
- [ ] Entry linked to specific tray
- [ ] Multiple entries allowed per day
- [ ] Photos compressed before upload to save storage
- [ ] Journal accessible from tray detail screen

---

### E8-US2: Client Views Journal Feed
**As a** client,  
**I want** to scroll through my tray's journal entries chronologically,  
**So that** I can see how my tray has progressed day by day.

**Acceptance Criteria:**
- [ ] Journal feed shown in reverse chronological order (latest first)
- [ ] Each entry shows: date, text, photos, any staff comments
- [ ] Photos viewable in full screen on tap
- [ ] Entries from day 1 to current day all accessible
- [ ] Empty state shown if no entries yet with prompt to add first entry

---

### E8-US3: Staff Views Client Journal
**As a** staff member,  
**I want** to view a client's tray journal,  
**So that** I have full context of their growing progress before responding to queries.

**Acceptance Criteria:**
- [ ] Staff accesses journal from client detail screen, per tray
- [ ] Full journal feed visible: all entries, photos, dates
- [ ] Staff sees checklist completion alongside journal (same timeline view)
- [ ] Staff can filter journal by date range
- [ ] Journal entries clearly distinguish client text from staff comments

---

### E8-US4: Staff Comments on Journal Entry
**As a** staff member,  
**I want** to comment on a client's journal entry,  
**So that** I can give specific feedback or encouragement on their observation.

**Acceptance Criteria:**
- [ ] Staff adds comment directly on a journal entry
- [ ] Comment shows staff name and timestamp
- [ ] Client notified via push notification when staff comments
- [ ] Client can see comment inline in their journal feed
- [ ] Staff can edit or delete their own comment

---

## EPIC 9 — Support Ticket System

### E9-US1: Client Raises a Support Ticket
**As a** client,  
**I want** to raise a support query linked to my tray,  
**So that** staff can help me with a specific problem in context.

**Acceptance Criteria:**
- [ ] Ticket form: subject, description, optional photos, linked tray (optional)
- [ ] Ticket status: `OPEN`, `IN_PROGRESS`, `RESOLVED`, `CLOSED`
- [ ] Client can raise multiple tickets but only 3 open at a time
- [ ] Staff notified of new ticket via push notification
- [ ] Client sees ticket in their support history with current status

---

### E9-US2: Staff Views Ticket with Tray Context
**As a** staff member,  
**I want** to see a client's tray journal and checklist history alongside their support ticket,  
**So that** I can give an accurate and informed response without asking for repeated information.

**Acceptance Criteria:**
- [ ] Ticket detail screen shows: ticket description, linked tray info, recent journal entries (last 5), checklist completion for last 7 days
- [ ] Staff can navigate to full journal or checklist history from ticket screen
- [ ] All context loaded in a single screen without excessive navigation
- [ ] Ticket assigned to staff member who opens it (auto-assign)
- [ ] Staff can reassign ticket to another staff member

---

### E9-US3: Staff Responds to Ticket
**As a** staff member,  
**I want** to respond to a client's support ticket,  
**So that** the client gets help and the conversation is tracked.

**Acceptance Criteria:**
- [ ] Staff types response with optional image attachment
- [ ] Response visible to client in ticket thread
- [ ] Client notified via push notification on new response
- [ ] Ticket status updated to `IN_PROGRESS` on first staff response
- [ ] Staff can add internal notes (not visible to client) for context

---

### E9-US4: Client Replies to Ticket
**As a** client,  
**I want** to reply to a staff response within the same ticket,  
**So that** the conversation stays in one place and is easy to follow.

**Acceptance Criteria:**
- [ ] Client can add reply with text and optional photo
- [ ] Staff notified of client reply via push notification
- [ ] Full conversation thread shown chronologically in ticket
- [ ] Client can mark ticket as resolved if their issue is addressed
- [ ] Auto-close ticket after 7 days of no activity with notification

---

### E9-US5: Staff Closes or Resolves Ticket
**As a** staff member,  
**I want** to mark a ticket as resolved or closed,  
**So that** the support queue stays clean and manageable.

**Acceptance Criteria:**
- [ ] Staff can mark ticket as `RESOLVED` or `CLOSED` with optional closing note
- [ ] Client notified when ticket is resolved/closed
- [ ] Client can reopen a resolved ticket within 3 days
- [ ] Closed tickets move to archived view, not deleted
- [ ] Staff sees open ticket count on dashboard header

---

### E9-US6: Staff Ticket Dashboard
**As a** staff member,  
**I want** a dashboard view of all open tickets,  
**So that** I can prioritize and manage client queries efficiently.

**Acceptance Criteria:**
- [ ] List of all open tickets: client name, subject, tray linked, date raised, status
- [ ] Filter by: status, assigned staff, date range
- [ ] Sort by: oldest first (default), newest first
- [ ] Unread/new tickets highlighted
- [ ] Ticket count badges shown by status on dashboard

---

## EPIC 10 — FAQ & Broadcast

### E10-US1: Admin Manages FAQ Library
**As an** admin,  
**I want** to maintain a searchable FAQ library,  
**So that** clients can resolve common questions without raising a support ticket.

**Acceptance Criteria:**
- [ ] Admin creates FAQ articles: question, detailed answer, category, optional photos
- [ ] Categories: germination, watering, mould, blackout, harvest, platform usage
- [ ] FAQ articles publishable or saveable as draft
- [ ] Admin can edit, archive, or delete FAQ articles
- [ ] FAQ visible to all logged-in clients

---

### E10-US2: Client Searches FAQ
**As a** client,  
**I want** to search the FAQ before raising a support ticket,  
**So that** I can get instant answers to common problems.

**Acceptance Criteria:**
- [ ] Full-text search across all FAQ articles
- [ ] Results ranked by relevance
- [ ] Search prompt shown on support ticket creation screen (search before submitting)
- [ ] "Was this helpful?" feedback on each FAQ article
- [ ] Most viewed FAQs shown on support home screen

---

### E10-US3: Staff Sends Broadcast to Clients
**As a** staff member,  
**I want** to send a broadcast message to all clients or a specific batch,  
**So that** I can share tips, reminders, or important updates efficiently.

**Acceptance Criteria:**
- [ ] Broadcast compose: title, message body, optional image
- [ ] Target options: all clients, specific batch, clients in growing phase
- [ ] Delivered as push notification + visible in client's notification inbox
- [ ] Broadcast history visible to staff with delivery count
- [ ] Clients cannot reply to broadcasts (one-way communication)

---

## EPIC 11 — Analytics & Reporting

### E11-US1: Admin Dashboard — Platform Overview
**As an** admin,  
**I want** a dashboard showing key platform metrics,  
**So that** I can monitor the health of the business at a glance.

**Acceptance Criteria:**
- [ ] KPIs: total registered clients, active growing clients, clients in training, batches open/closed
- [ ] New signups this week vs last week
- [ ] Tickets: open count, average resolution time
- [ ] Top crops being grown
- [ ] Date range filter: last 7 days, 30 days, 3 months

---

### E11-US2: Staff Dashboard — Client Overview
**As a** staff member,  
**I want** to see an overview of all clients and their current status,  
**So that** I can identify who needs attention today.

**Acceptance Criteria:**
- [ ] List of all clients with: name, batch, current status, last checklist date, open tickets
- [ ] Filter by: status (booking pending / training / growing / inactive), batch
- [ ] Highlight clients with no checklist activity in last 3 days
- [ ] Highlight clients with open tickets older than 48 hours
- [ ] Search clients by name or phone

---

### E11-US3: Client Dashboard — Personal Progress
**As a** client,  
**I want** to see my personal growing metrics on my home screen,  
**So that** I feel motivated and can track my journey.

**Acceptance Criteria:**
- [ ] KPIs: current streak, total days on journey, total yield to date (grams), trays completed
- [ ] Today's checklist completion percentage
- [ ] Current tray stage shown as progress bar
- [ ] Recent journal entries thumbnail strip
- [ ] Quick links: today's checklist, add journal entry, raise ticket

---

### E11-US4: Staff Views Individual Client Report
**As a** staff member,  
**I want** to view a detailed report for any individual client,  
**So that** I can assess their progress and give personalized support.

**Acceptance Criteria:**
- [ ] Client report: enrollment history, training status, active tray info, checklist compliance rate
- [ ] Yield history per tray shown as table
- [ ] Journal entry count and last entry date
- [ ] Support ticket history with resolution times
- [ ] All data on a single scrollable screen

---

## EPIC 12 — Admin Panel

### E12-US1: Admin Manages Users
**As an** admin,  
**I want** to manage all platform users from the admin panel,  
**So that** I can handle account issues and maintain platform integrity.

**Acceptance Criteria:**
- [ ] List all users: name, role, status, join date, last active
- [ ] Search by name, email, phone
- [ ] Actions: suspend account, activate account, reset password
- [ ] Create new staff accounts with role assignment
- [ ] Suspended users cannot log in and see a clear message

---

### E12-US2: Admin Manages Batches
**As an** admin,  
**I want** to create, edit, and manage all training batches from the admin panel,  
**So that** the enrollment pipeline runs smoothly.

**Acceptance Criteria:**
- [ ] Create, edit, close, and reopen batches
- [ ] View all clients enrolled per batch with booking status
- [ ] Manually confirm or reject bookings from admin panel
- [ ] Mark batch training as complete (triggers premium content unlock for all clients)
- [ ] View batch summary: total enrolled, training complete, now growing

---

### E12-US3: Admin Manages Content & Crops
**As an** admin,  
**I want** to manage all crop data, checklist templates, and premium content from one place,  
**So that** the content library stays accurate and up to date.

**Acceptance Criteria:**
- [ ] CRUD for crops: name, description, photo, difficulty, harvest days
- [ ] CRUD for crop checklist templates (day-by-day)
- [ ] CRUD for premium content items (video, article, PDF) linked to crops
- [ ] Preview mode: see exactly what a client sees for a given crop
- [ ] Publish / unpublish content without deleting

---

### E12-US4: Admin Sends Platform Announcements
**As an** admin,  
**I want** to send platform-wide announcements,  
**So that** all users are informed of important updates or changes.

**Acceptance Criteria:**
- [ ] Compose announcement: title, body, optional image, target (all / clients only / staff only)
- [ ] Delivered as push notification + in-app inbox
- [ ] Announcements history visible in admin panel
- [ ] Clients see announcements in notification inbox
- [ ] Scheduled announcements supported

---

## EPIC 13 — PWA & Cross-Platform Readiness

### E13-US1: Flutter PWA Build Configuration
**As a** developer,  
**I want** Flutter configured to build a production-ready PWA,  
**So that** clients can install and use the app from any browser without an app store.

**Acceptance Criteria:**
- [ ] `flutter build web --release` produces optimized output
- [ ] Web app manifest: name, icons (192px, 512px), theme color
- [ ] Service worker registered for caching static assets
- [ ] App installable on Android Chrome and iOS Safari
- [ ] Lighthouse PWA score ≥ 85

---

### E13-US2: Offline Checklist Access
**As a** client,  
**I want** to view and complete my daily checklist even when offline,  
**So that** poor connectivity doesn't interrupt my growing routine.

**Acceptance Criteria:**
- [ ] Today's checklist cached locally via service worker
- [ ] Completions stored locally when offline
- [ ] Changes synced to server when connection is restored
- [ ] Offline indicator shown in app clearly
- [ ] No blank screens or confusing errors when offline

---

### E13-US3: Responsive Layout
**As a** user,  
**I want** the app to work well on mobile, tablet, and desktop,  
**So that** clients on phones and staff on desktops both have a good experience.

**Acceptance Criteria:**
- [ ] Mobile (< 600px): single column, bottom navigation
- [ ] Tablet (600–1024px): two-column layout where appropriate
- [ ] Desktop (> 1024px): sidebar navigation, wider content area
- [ ] All touch targets minimum 48px
- [ ] Tested on: Android Chrome, iOS Safari, Chrome desktop, Firefox desktop

---

### E13-US4: PWA Deployment via Nginx
**As a** DevOps engineer,  
**I want** the Flutter PWA deployed and served via Nginx,  
**So that** clients can access it from the company's custom domain.

**Acceptance Criteria:**
- [ ] `flutter build web` output served from `/var/www/app`
- [ ] All routes return `index.html` (Flutter web routing fix applied)
- [ ] Cache-Control headers set correctly for assets
- [ ] HTTPS enforced
- [ ] PWA tested end-to-end on production domain before launch

---

## EPIC 14 — Security & Compliance

### E14-US1: Input Validation & Sanitisation
**As a** platform,  
**I want** all API inputs validated and sanitised,  
**So that** injection attacks and malformed data cannot compromise the system.

**Acceptance Criteria:**
- [ ] Spring Boot Bean Validation (`@Valid`) on all request DTOs
- [ ] SQL injection prevented via JPA parameterized queries
- [ ] File upload validation: type whitelist (jpg, png, pdf, mp4), size limits
- [ ] XSS prevention: HTML escaped in all user-generated content
- [ ] Validation errors return `400` with field-level details

---

### E14-US2: Rate Limiting
**As a** platform,  
**I want** API rate limiting enforced,  
**So that** brute-force and abuse attempts are blocked.

**Acceptance Criteria:**
- [ ] Login endpoint: max 5 attempts per 15 minutes per IP
- [ ] OTP endpoint: max 3 requests per 10 minutes per phone
- [ ] General API: max 100 requests per minute per authenticated user
- [ ] Rate limit exceeded returns `429 Too Many Requests`
- [ ] Rate limit headers included in response

---

### E14-US3: Data Privacy
**As a** platform,  
**I want** basic data privacy practices implemented,  
**So that** client data is handled responsibly.

**Acceptance Criteria:**
- [ ] Privacy policy page accessible without login
- [ ] Clients can request account deletion from settings
- [ ] Account deletion anonymises personal data within 30 days
- [ ] Sensitive fields (phone, email) encrypted at rest
- [ ] Data access logged for audit by Super Admin

---

### E14-US4: Pre-Launch Security Checklist
**As a** platform,  
**I want** a security audit completed before production launch,  
**So that** known vulnerabilities are addressed before real clients use the system.

**Acceptance Criteria:**
- [ ] OWASP Top 10 checklist reviewed and signed off
- [ ] All API endpoints require authentication (no accidental public endpoints)
- [ ] Secrets scanned in git history
- [ ] Dependency vulnerability scan run (OWASP Dependency-Check)
- [ ] Security sign-off documented before go-live

---

## EPIC 15 — Testing

### E15-US1: Backend Unit Tests
**As a** backend developer,  
**I want** unit tests for all service-layer business logic,  
**So that** regressions are caught automatically before deployment.

**Acceptance Criteria:**
- [ ] JUnit 5 + MockK for all unit tests
- [ ] Minimum 70% code coverage across service classes
- [ ] Tests cover: happy path, edge cases, error conditions
- [ ] Tests run in CI on every PR
- [ ] Test results reported with pass/fail summary

---

### E15-US2: Backend Integration Tests
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

### E15-US3: Flutter Widget Tests
**As a** Flutter developer,  
**I want** widget tests for all key screens,  
**So that** UI regressions are caught without manual testing.

**Acceptance Criteria:**
- [ ] Widget tests cover: login, home, checklist, tray detail, journal, ticket screens
- [ ] Mock API responses using `mocktail`
- [ ] Tests verify: elements rendered, user interactions, state changes
- [ ] Golden tests for critical UI components
- [ ] Tests run in CI pipeline

---

### E15-US4: End-to-End Tests
**As a** QA engineer,  
**I want** end-to-end tests for the critical user journeys,  
**So that** the most important flows are verified in an integrated environment.

**Acceptance Criteria:**
- [ ] E2E flow 1: Client registers → books batch → staff confirms
- [ ] E2E flow 2: Staff marks training complete → client accesses premium content → selects crop
- [ ] E2E flow 3: Client creates tray → completes checklist → adds journal entry → raises ticket → staff responds
- [ ] E2E flow 4: Client completes journey → logs harvest
- [ ] E2E tests run against staging and failure blocks production deployment

---

### E15-US5: Load Testing
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

## EPIC 16 — Launch & Post-Launch

### E16-US1: Pilot with Real Clients on Staging
**As a** product owner,  
**I want** real clients to validate the platform on staging before launch,  
**So that** real-world issues are caught before going live.

**Acceptance Criteria:**
- [ ] 5–10 pilot clients onboarded on staging with at least one active tray each
- [ ] All key flows exercised: booking, checklist, journal, ticket, content access
- [ ] Feedback collected via structured form
- [ ] All critical bugs fixed before production deployment
- [ ] Staff team trained on admin panel and ticket handling before go-live

---

### E16-US2: Production Deployment Runbook
**As a** DevOps engineer,  
**I want** a documented production deployment runbook,  
**So that** deployments are repeatable, safe, and reversible.

**Acceptance Criteria:**
- [ ] Runbook covers: pre-deploy checklist, deploy steps, smoke tests, rollback procedure
- [ ] Database migrations run via Flyway before app deployment
- [ ] Zero-downtime deployment via rolling restart
- [ ] Smoke tests verify: login, home load, checklist fetch, journal submit post-deploy
- [ ] Rollback tested and confirmed to complete within 5 minutes

---

### E16-US3: Play Store Submission
**As a** product owner,  
**I want** the Flutter app submitted to Google Play Store,  
**So that** clients can install it natively on Android for a better experience.

**Acceptance Criteria:**
- [ ] App signed with production keystore
- [ ] Store listing complete: description, screenshots (5+), feature graphic
- [ ] Content rating questionnaire completed
- [ ] Privacy policy URL linked in store listing
- [ ] App reviewed and approved by Google Play

---

### E16-US4: In-App Feedback
**As a** product owner,  
**I want** clients to submit feedback from within the app,  
**So that** I have a continuous stream of insights post-launch.

**Acceptance Criteria:**
- [ ] Feedback option accessible from settings
- [ ] Form: rating (1–5), category (bug / suggestion / praise), description, optional screenshot
- [ ] Submissions visible in admin panel
- [ ] Auto-reply email sent acknowledging feedback
- [ ] Feedback trends visible as a simple chart in admin panel

---

### E16-US5: Bug Triage & Hotfix Process
**As an** engineering lead,  
**I want** a defined process for triaging and fixing post-launch bugs,  
**So that** critical issues are resolved quickly.

**Acceptance Criteria:**
- [ ] Severity levels defined: P0 (platform down), P1 (major feature broken), P2 (minor issue)
- [ ] P0: hotfix deployed within 4 hours
- [ ] P1: fix within 48 hours
- [ ] P2: scheduled in next sprint
- [ ] In-app banner shown for ongoing P0 incidents

---

*Last Updated: 2025 | Version 2.0*  
*Maintained by: [Team Name]*  

---

## Changelog
| Version | Date | Changes |
|---|---|---|
| 1.0 | 2025 | Initial draft — 16 EPICs, 79 stories |
| 2.0 | 2025 | Revised to single-company model. Removed multi-trainer architecture, marketplace (Phase 2), payments (Phase 2), CRM. Added EPIC 8 (Tray Journal), revised EPIC 9 (Support Tickets). Updated roles to Super Admin / Admin / Staff / Client. Revised client journey, content unlock model, batch management. Total: 17 EPICs, 85 user stories |
