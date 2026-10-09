# Thuhina Water – Master Task Breakdown

| Item | Detail |
|---|---|
| Product | Water Distribution Inventory & Sales Management System |
| Document version | 1.2 |
| Created | 05/10/2026 |
| Prepared by | Amritha |
| Based on | BRD v4.0 (`BRD Water Distribution System.md`) · System Architecture & Design v1.1 (`System_Architecture_and_Design.md`) · Prototype (`/prototype`) · Tech stack v1.1 (`TechStack Thuhina Water.md`) |
| Purpose | The build plan. Each module block (M00–M14) is pasted one at a time to build that module. This file is updated after every module with status, ticks and comments. |

> **Living document.** After each step: tick the tasks (`[x]`), update the progress tracker (section 4), and add a line to the module's **Notes & comments** table. Record any change of scope or design decision in section 8 (Decision log).

---

## Contents

1. How to use this document
2. Git branching & release strategy
3. Standard module workflow (steps to follow in every module)
4. Progress tracker
5. Global rules for every module (definition of done, conventions, seed data)
6. Module blocks M00 – M16
7. Milestones & release plan
8. Decision log
9. Change log of this document

---

## 1. How to use this document

1. Build modules **in order** (M00 → M14). Each block lists what it depends on.
2. For **split modules** (M00, M06, M07, M11) paste the **SERVER** part first, then the **CLIENT** part in a separate message. For other modules paste the whole block (or server then client if preferred).
3. Every block contains: header (branch, dependencies, BRD IDs, prototype screen, design sections) · goal & scope · **key logic & rules** · **server tasks** · **client tasks** · **tests** · **acceptance checklist** · **notes & comments**.
4. The **prototype page** is the visual and behaviour spec. The **design document** is the technical spec. **This document** is the order of work and the checklist.
5. When a block is finished: follow steps 9–12 of section 3 (PR → merge → UAT → update this document).

**Message to use when pasting a module:**

```
Build module M05 – Customers, SERVER part, exactly as written in Master_Task_Breakdown.md.
Follow section 3 (standard module workflow) and section 5 (global rules).
Branch: feature/M05-customers
[paste the module block here]
```

**Status legend** (used in the tracker and task lists):

| Mark | Meaning |
|---|---|
| `[ ]` / ☐ To do | Not started |
| ◐ In progress | Branch created, work ongoing |
| `[x]` / ☑ Done | Finished, tested, merged to `dev` |
| ⊘ Moved / skipped | Moved to another module or dropped (say why in comments) |

---

## 2. Git branching & release strategy

### 2.1 Recommended model – simplified Git Flow

`main` **is production**. A separate `prod` branch is **not needed** – it would only duplicate `main`. Each production release is a **tag** on `main` (`v1.0.0`), and a rollback is simply redeploying the previous tag.

```
 feature/M05-customers ──┐
 feature/M06-sales-server┼──► dev ──(release PR, after UAT sign-off)──► main ──tag v1.0.0──► Production
 fix/M07-paper-bill ─────┘       │                                          │
                                 └──► auto-deploy to UAT                    │
                                                                            │
 hotfix/1.0.1-login-lock ◄──────────── branched from main ──────────────────┘
        └──► PR to main (tag v1.0.1 → Production) ──► then merged back into dev
```

| Branch | Purpose | Created from | Merges into | Deploys to |
|---|---|---|---|---|
| `main` | Production code only. Always what is live (or ready to go live). | – | – | **Production** (manual, on tag) |
| `dev` | Integration branch. All finished modules land here. | `main` (once, at start) | `main` via release PR | **UAT** (automatic on every merge) |
| `feature/Mnn-name` | One module (or one half of a split module) | `dev` | `dev` via PR | – (local) |
| `fix/Mnn-name` | Fix for a module already merged to `dev`, not yet in production | `dev` | `dev` | – |
| `hotfix/x.y.z-name` | Urgent fix to production | `main` | `main` (tag x.y.z) **and** `dev` | Production |
| `release/x.y` *(optional)* | Only if UAT fixes must continue while new features go into `dev` | `dev` | `main` and back to `dev` | UAT |

**Flow summary: feature → dev → main (tag) → production.** The integration branch is named `dev`. The production step is a tag on `main`, not a separate `prod` branch.

### 2.2 Branch names for this project

| Module | Branch(es) |
|---|---|
| M00 Foundation | `feature/M00-foundation-server`, `feature/M00-foundation-client` |
| M01 Administration | `feature/M01-administration` |
| M02 Master data | `feature/M02-master-data` |
| M03 Suppliers & factories | `feature/M03-suppliers-factories` |
| M04 Inventory core | `feature/M04-inventory` |
| M05 Customers | `feature/M05-customers` |
| M06 Sales core | `feature/M06-sales-server`, `feature/M06-sales-client` |
| M07 Enter Bills | `feature/M07-enter-bills-server`, `feature/M07-enter-bills-client` |
| M08 Delivery schedule | `feature/M08-delivery` |
| M09 Purchasing | `feature/M09-purchasing` |
| M10 Production | `feature/M10-production` |
| M11 Billing & payments | `feature/M11-billing-server`, `feature/M11-billing-client` |
| M12 Dashboard & reports | `feature/M12-dashboard-reports` |
| M13 WhatsApp | `feature/M13-whatsapp` |
| M14 Migration & go-live | `feature/M14-migration-golive` |

### 2.3 Rules

| Rule | Detail |
|---|---|
| Protected branches | `main` and `dev`: no direct push; merge only by pull request; CI must be green |
| Reviews | Every PR reviewed (by a teammate, or by Amritha with the PR checklist) before merge |
| Merge style | Feature → dev: **squash merge** (one clean commit per module/half). dev → main: **merge commit** (keeps release history) |
| Up to date | Before opening a PR, merge the latest `dev` into the feature branch and re-run tests |
| Branch life | Delete the feature branch after merge |
| Commit messages | `M05: add customer registration validation` · `M07 fix: paper bill duplicate message` |
| Versions | `0.x.0` at each milestone during the build (section 7); `1.0.0` at go-live; `1.0.x` hotfixes; `1.x.0` later features |
| Database | Flyway scripts are never edited after merge to `dev`; changes go in a new script |
| Secrets | Never committed. `.env` files stay local / on servers; `.env.example` is committed |

### 2.4 Pull request template (`.github/pull_request_template.md`)

```
## Module / task
M05 – Customers (SERVER)   Tasks: S1–S9

## What changed
-

## Checklist
- [ ] Follows the module block in Master_Task_Breakdown.md
- [ ] Flyway migration added (not editing old ones)
- [ ] Business rules in services/rules classes only
- [ ] Permissions on every endpoint
- [ ] Audit entries for create/change/approve/reverse
- [ ] Unit + integration tests added and passing; invariants hold
- [ ] Screen checked against the prototype for each role (client PRs)
- [ ] Master_Task_Breakdown.md updated (ticks + comments)
```

---

## 3. Standard module workflow (steps to follow in every module)

| Step | What to do | Output |
|---|---|---|
| 1 | **Read** the module block, the design-document sections it lists, and the prototype page(s). Note any question in the module's comments table before starting. | Clear scope |
| 2 | **Start**: update the progress tracker to ◐; `git checkout dev && git pull`; create the feature branch. | Branch |
| 3 | **Database**: write the Flyway migration (tables, constraints, indexes, sequence rows, settings rows). | `V0nn__…sql` |
| 4 | **Model & repository**: entities (extend `BaseEntity`), repositories, custom queries. | `model/`, `repository/` |
| 5 | **Rules & services**: pure rule classes first (with unit tests), then services (transactions, ledger posting, numbering, audit). | `rules/`, `service/` |
| 6 | **API**: DTOs, mapper, controller, `@PreAuthorize` permissions, validation, error codes. Check in Swagger. | `controller/`, `dto/`, `mapper/` |
| 7 | **Server tests**: unit + integration (Testcontainers) + invariants; run the full test suite. | Green build |
| 8 | **Client** (after the server part is merged, or on the same branch for non-split modules): regenerate API types → feature hooks → pages and components → permissions (view-only hides buttons) → match the prototype. | Screens |
| 9 | **Check**: lint, type-check, all tests; manual run-through as **Admin, Accountant, Delivery Staff**; compare with the prototype page side by side. | Ready |
| 10 | **Pull request** to `dev` with the PR template; fix review comments; squash merge. | Merged |
| 11 | **UAT**: automatic deploy; quick smoke test on UAT with seed data. | On UAT |
| 12 | **Update this document**: tick tasks and acceptance items, set tracker to ☑ with dates, add notes/comments and any decision to section 8. | Document updated |

**If something in the block is unclear or wrong:** stop, write the question in the module's comments table, agree the answer, record it in the decision log (section 8), then continue.

---

## 4. Progress tracker

| Module | Branch(es) | Status | Started | Merged to dev | Release tag | Comments |
|---|---|---|---|---|---|---|
| M00 Foundation | `feature/M00-foundation-server` / `-client` | ☐ To do | | | | |
| M01 Administration | `feature/M01-administration` | ☐ To do | | | | |
| M02 Master data | `feature/M02-master-data` | ☐ To do | | | | |
| M03 Suppliers & factories | `feature/M03-suppliers-factories` | ☐ To do | | | | |
| M04 Inventory core | `feature/M04-inventory` | ☐ To do | | | | |
| M05 Customers | `feature/M05-customers` | ☐ To do | | | | |
| M06 Sales core | `feature/M06-sales-server` / `-client` | ☐ To do | | | | |
| M07 Enter Bills | `feature/M07-enter-bills-server` / `-client` | ☐ To do | | | | Row shows the customer's price; agreed prices marked (v1.2) |
| M08 Delivery schedule | `feature/M08-delivery` | ☐ To do | | | | |
| M09 Purchasing | `feature/M09-purchasing` | ☐ To do | | | | |
| M10 Production | `feature/M10-production` | ☐ To do | | | | |
| M11 Billing & payments | `feature/M11-billing-server` / `-client` | ☐ To do | | | | |
| M12 Dashboard & reports | `feature/M12-dashboard-reports` | ☐ To do | | | | |
| M13 WhatsApp | `feature/M13-whatsapp` | ☐ To do | | | | |
| M14 Migration & go-live | `feature/M14-migration-golive` | ☐ To do | | | | |
| M15 Expenses | `feature/M15-expenses` | ☐ To do | | | | Added 08/10/2026 (client feedback) |
| M16 Customer quotations | `feature/M16-customer-quotations` | ☐ To do | | | | Added 08/10/2026 (client feedback); depends on M05 agreed prices |

**Rough size guide** (one developer working with AI assistance; for planning only, refine after M00): M00 5–7 days · M01 2–3 · M02 4–5 · M03 2–3 · M04 4–6 · M05 5–7 · M06 10–14 · M07 5–7 · M08 4–5 · M09 6–8 · M10 3–4 · M11 7–9 · M12 6–8 · M13 5–6 · M14 5–7 + UAT · M15 2–3 · M16 4–5.

---

## 5. Global rules for every module

### 5.1 Definition of done (applies to every module)

- [ ] Flyway migration with constraints (`CHECK`, `UNIQUE`, `FK`) and indexes
- [ ] Business rules only in `service/` and `rules/` classes (never in controllers or the client)
- [ ] Every stock / customer-bottle change goes through the **Ledger Engine** (design 5.1)
- [ ] Every document number / code comes from the **Numbering Service** (design 5.3)
- [ ] Every endpoint has a permission; view-only roles cannot call write endpoints (tested)
- [ ] Business errors return `422 { code, message, details }` with the prototype wording
- [ ] Audit entry for every create / update / approve / reverse / import
- [ ] Unit tests for rules; integration tests for endpoints; invariants hold (design 13.1)
- [ ] Screens match the prototype for each role; buttons hidden for view-only roles
- [ ] Dates shown DD/MM/YYYY with the calendar picker; money as `Rs. 1,350.00`
- [ ] No console errors; lint and type-check clean
- [ ] This document updated (ticks, tracker, comments)

### 5.2 Conventions (short – full list in design section 14)

- Backend module folders: `controller/ dto/ service/ model/ repository/ mapper/` (+ `rules/` where needed). **No `ServiceImpl`** unless a second implementation is real (design 3.2).
- Money `BigDecimal` / `NUMERIC(12,2)`, `HALF_UP`, 2 decimals. Business dates `LocalDate` / `DATE`. Time zone `Asia/Colombo`.
- API uses ISO dates; the client formats DD/MM/YYYY.
- Client: feature folders `client/src/features/<module>/`; shared components in `components/shared/`.

### 5.3 Seed data (local and UAT only – never production)

Taken from `prototype/assets/js/data.js`: users (6), bottle types (B20, B10, B5 inactive), products (P01–P06), customer types (4), areas (10), price history (incl. the scheduled 10L Factory price from 01/11/2026), American Water brand, suppliers (S01–S06 with items), factories (F01 AquaSeal, F02 Pure Lanka), opening stock, minimum levels, customers C0001–C0040 with opening bottles and balances. Each module seeds its own data in `db/seed/` (Flyway `R__` repeatable seed scripts enabled only in `local`/`uat` profiles). A full demo-history generator is added in M12.

### 5.4 Roles (design 8.2) – reminder

Admin = full. Accountant = Dashboard, Billing (full), Reports (Sales, Purchasing, Outstanding & aging, Profit), view-only Customers / Suppliers & Factories / Purchasing / Filling Factory. Delivery Staff = Daily Delivery List (view + print). Over-limit approval = Admin only.

---

## 6. Module blocks

---

### M00 – Project foundation

| Item | Detail |
|---|---|
| Branches | `feature/M00-foundation-server` → then `feature/M00-foundation-client` |
| Depends on | – (first module) |
| BRD | FR-56 (login), FR-57 (audit), FR-60 (numbering), NFR-05, NFR-07, NFR-09 |
| Prototype | `login.html`, shell/sidebar/top bar (`assets/js/layout.js`, `assets/css/styles.css`), date picker & helpers (`assets/js/ui.js`) |
| Design | 1, 2, 3, 4.1, 4.3 (admin & system tables), 5.3, 5.7, 5.8, 7.1, 8, 12, 14 |

**Goal:** a running skeleton – both projects, Docker, CI, database migrations, login with roles, the app shell with role-based sidebar, the numbering and audit services, and the shared UI components every later module uses.

**Key logic & rules**
- **Login:** username + password (BCrypt). Inactive user → "This account is deactivated. Contact the Admin." Unknown user / wrong password → "Invalid username or password." (same message for both). **5 failed attempts → locked 15 minutes** ("Too many attempts. Try again in 15 minutes.").
- **JWT cookie:** `HttpOnly; Secure; SameSite=Strict`, 12 hours; claims: userId, role, tokenVersion. Each request re-checks the user is active and `token_version` matches (deactivation / password reset logs out immediately).
- **must_change_password** → client forces the change-password screen. New password ≥ 8 characters, not equal to the old one.
- **Landing page:** Admin/Accountant → `/dashboard`; Delivery Staff → `/delivery/daily-list`.
- **`/auth/me`** returns user, role, permissions and the **menu** (modules → pages, with `view` flag and `hidden` flag from settings, e.g. Data Migration hidden).
- **Numbering:** `next(name)` locks the `doc_sequence` row (`SELECT … FOR UPDATE`) in the caller's transaction; formats and resets per design 5.3 (`period_key` = `2026` for yearly, `2610` for monthly).
- **Audit:** explicit `AuditService.log(action, entity, ref, details)`; insert-only.
- **Errors:** 400 VALIDATION (field errors) · 401 not logged in · 403 no permission · 404 · 409 CONFLICT (optimistic lock) · 422 business rule.

#### SERVER tasks
- [ ] **S1 – Repository & branches:** create `dev` from `main`; protect `main` and `dev`; root `README.md`, `.gitignore`, `.editorconfig`, PR template (section 2.4); `deploy/` with `docker-compose.yml` (db, api, web, nginx), `nginx.conf` (`/` → web, `/api` → api), `.env.example`.
- [ ] **S2 – Spring Boot project** in `server/` (Gradle, Java 21): web, security, data-jpa, validation, flyway, postgresql, actuator, springdoc, openpdf, poi (added later where used), test + testcontainers. Profiles `local`, `uat`, `prod`; time zone `Asia/Colombo`; JVM heap setting documented; `Dockerfile` (multi-stage).
- [ ] **S3 – Common package:** `BaseEntity` (id, created_at/by, updated_at/by, version) with JPA auditing from the current user; `BusinessException(code, message, details)`; `GlobalExceptionHandler` (formats above); `PageResponse<T>`; `Money` and `BusinessDates` utils.
- [ ] **S4 – Migration `V001__foundation.sql`:** `app_user`, `audit_log`, `doc_sequence` (rows for every sequence in design 5.3), `app_setting` (company details from BRD: Thuhina Water (Pvt) Ltd, address, phone, email, reg. no; `features.dataMigration=false`), `login_attempt`.
- [ ] **S5 – NumberingService** + unit tests (formats, yearly/monthly reset) + concurrency test (two threads → different numbers, no gaps).
- [ ] **S6 – AuditService** + `AuditLog` entity/repository (insert-only; no update/delete methods exposed).
- [ ] **S7 – Security:** BCrypt encoder; `JwtService`; cookie writer (Secure off only in `local`); `JwtAuthFilter` (active + token_version check); `Permissions` constants and role → permission map exactly as design 8.2; `@EnableMethodSecurity`.
- [ ] **S8 – Auth endpoints:** `POST /auth/login` (lock rule, audit LOGIN, `last_login_at`), `POST /auth/logout` (audit LOGOUT, clear cookie), `GET /auth/me` (user, permissions, menu), `POST /auth/change-password`.
- [ ] **S9 – Settings service:** `GET/PUT /settings/{group}` (company, business, features); first-run bootstrap of the Admin user from environment variables (`ADMIN_USERNAME`, `ADMIN_TEMP_PASSWORD`, must change on first login). Seed users (local/UAT) from the prototype.
- [ ] **S10 – OpenAPI & CI:** Swagger UI in local/UAT only; GitHub Actions workflow `server-ci.yml` (build + tests with Testcontainers on every PR).
- [ ] **S11 – Tests:** login success/fail/lock/inactive, logout, token invalid after deactivation, permission denied (403), error format, numbering.

#### CLIENT tasks
- [ ] **C1 – Next.js project** in `client/` (TypeScript strict, App Router, Tailwind, shadcn/ui init, ESLint, Prettier); `lib/api.ts` (fetch with `credentials: 'include'`, error → toast, 401 → login); TanStack Query provider; `npm run api:types` (openapi-typescript); `Dockerfile`.
- [ ] **C2 – Theme & shell** from the prototype CSS (colours, spacing, cards, tables, badges, banners): `AppShell`, `Sidebar` (built from `/auth/me` menu; single link when a module has one page; group with sub-items for Master Data, Sales & Deliveries, Delivery Schedule; "view" tag; hidden modules omitted), `TopBar` (today DD/MM/YYYY, user name + role, Log out).
- [ ] **C3 – Login page** (as `login.html`, without demo users): username, password, errors; redirect to landing page by role; **Change password** screen when `must_change_password`.
- [ ] **C4 – Route protection:** protected `(app)` layout loads `/auth/me`; "No access" page; `useCan(permission)` hook; `<Can>` wrapper to hide write buttons for view-only roles.
- [ ] **C5 – Shared components:** `DataTable` (server paging, sorting, empty state, row click), `FormDialog` (pop-up; Enter does not submit by accident), `SidePanel` (sheet), `ConfirmDialog`, `ReasonDialog` (required text), `StatusBadge`, `KpiCard`, `StepBar`, `Tabs` (remembers tab in URL hash), toasts.
- [ ] **C6 – `DateField`:** calendar picker (Monday first, highlights today and selected, ‹ › month navigation, **Today** and **Clear**), typing allowed with auto-slash, DD/MM/YYYY display, ISO value, invalid state; opens above the field when there is no room below; follows the field on scroll. `MoneyText` (`Rs. 1,350.00`), `formatDate`, number inputs right-aligned.
- [ ] **C7 – CI & local run:** `client-ci.yml` (lint, type-check, build); `docker compose up` runs db + api + web + nginx locally; README "how to run".

**Tests:** server tests in S11; client: type-check, lint, component test for `DateField` (typing, picking, clearing).

**Acceptance checklist**
- [ ] `docker compose up` starts everything; `http://localhost` shows the login page
- [ ] Admin logs in → Dashboard placeholder with the full sidebar; Accountant → reduced sidebar; Delivery Staff → only Daily Delivery List
- [ ] 5 wrong passwords lock the account for 15 minutes; inactive user cannot log in
- [ ] Opening a page without permission shows "No access"; calling its API returns 403
- [ ] First login with a temporary password forces a password change
- [ ] Numbering service returns correct formats; concurrent calls never duplicate
- [ ] Audit log rows written for login/logout
- [ ] Calendar picker works on a test page (pick, type, clear, today)
- [ ] CI runs on pull requests for both projects

**Notes & comments**

| Date | By | Comment |
|---|---|---|
| | | |

---

### M01 – Administration

| Item | Detail |
|---|---|
| Branch | `feature/M01-administration` |
| Depends on | M00 |
| BRD | FR-56, FR-57, section 5 (roles), NFR-05 |
| Prototype | `admin/administration.html` |
| Design | 6 (Administration), 7.2 (Admin), 8 |

**Goal:** the Admin manages users, sees the roles matrix and the audit log, and maintains company details.

**Key logic & rules**
- **User:** full name, username, role, phone (optional). Username: lowercase letters, numbers, dot, underscore, at least 3 characters ("Username: at least 3 lowercase letters, numbers, dots or underscores."), unique ("Username already taken."). Role ∈ Admin / Accountant / Delivery Staff.
- **Create:** temporary password ≥ 8 characters ("Temporary password must be at least 8 characters."); `must_change_password = true`. No public signup.
- **Reset password:** system generates a temporary password, shows it **once** to the Admin, sets `must_change_password`, bumps `token_version` (logs the user out). Audit "Password reset – temporary password issued".
- **Deactivate / activate:** cannot deactivate yourself ("You cannot deactivate your own account."); deactivation bumps `token_version`. History kept.
- **Role change** is audited ("role Accountant → Admin").
- **Roles & access tab:** read-only, generated from the permission map (same as design 8.2).
- **Audit log:** filters from/to (default last 7 days), user, action, record type, text search; newest first; paged; REVERSE rows highlighted; entries cannot be edited or deleted.
- **Company settings:** name, address, phone, email, registration no., logo (PNG/JPG ≤ 1 MB) – used on all printed documents.

#### SERVER tasks
- [ ] **S1 – UserService:** create, update (name, username, role, phone), activate/deactivate, reset password (random 10-character temp password), validations above; audit each action.
- [ ] **S2 – User endpoints:** `GET/POST /users`, `PUT /users/{id}`, `POST /users/{id}/activate|deactivate|reset-password` (reset returns the temp password once).
- [ ] **S3 – Audit query:** `GET /audit-log?from&to&user&action&entity&q&page&size`; distinct users/actions/entities for the filter dropdowns.
- [ ] **S4 – Roles matrix endpoint:** `GET /roles/matrix` (menu pages × roles → full / view / none), from the same permission map used by security.
- [ ] **S5 – Company settings:** extend `/settings/company` with logo upload (`POST /settings/company/logo`, stored via `FileStorage`), returned as a URL for printing.
- [ ] **S6 – Tests:** username rules, duplicate, self-deactivation blocked, reset logs out (old token rejected), Accountant cannot call user endpoints, audit filters.

#### CLIENT tasks
- [ ] **C1 – Administration page** with tabs **Users · Roles & access · Audit log · Company** (Admin only).
- [ ] **C2 – Users tab:** table (name + "you" badge, username, role badge, phone, last login, status, actions Edit / Reset password / Deactivate|Activate); **New user** and **Edit** pop-ups with validation; temporary-password result pop-up ("Give this to …").
- [ ] **C3 – Roles & access tab:** read-only matrix (Full / View only / –) for the 3 roles.
- [ ] **C4 – Audit log tab:** filters (DateField from/to, user, action, record type, search), paged table (when, user + role, action badge, record + ref, details), Reverse rows highlighted.
- [ ] **C5 – Company tab:** form for company details + logo upload with preview.

**Tests:** server S6; client manual check against the prototype.

**Acceptance checklist**
- [ ] Admin creates a Delivery Staff user; that user logs in, must change password, sees only the Daily Delivery List
- [ ] Reset password shows a temporary password once; the user's old session stops working
- [ ] Admin cannot deactivate their own account
- [ ] Audit log shows logins, user changes, filters work, paging works
- [ ] Roles & access tab matches design 8.2
- [ ] Company details and logo saved (used later by printing)
- [ ] Accountant and Delivery Staff cannot open Administration (UI and API)

**Notes & comments**

| Date | By | Comment |
|---|---|---|
| | | |

---

### M02 – Master data (Bottles & Products)

| Item | Detail |
|---|---|
| Branch | `feature/M02-master-data` |
| Depends on | M00, M01 |
| BRD | FR-01, FR-02, FR-03, FR-04, FR-06, FR-07, BR-01, BR-02, BR-08 (standard prices + Pricing Service; customer agreed prices FR-05 are added in M05) |
| Prototype | `master-data/bottles-products.html` (customer types & areas popup is in `master-data/customers.html`) |
| Design | 4.3 (Master data), 5.5, 6 (Master data), 7.2 (Master data) |

**Goal:** bottle types, other products, customer types, areas, water prices and deposits with dated history, and accepted old-bottle brands.

**Key logic & rules**
- **Bottle type:** name and size (litres) required ("Name and size in litres are required."). **Code generated from the size**: `B` + litres (19 → `B19`; 0.5 → `B0_5`). Duplicate size rejected ("A 19L bottle type already exists (19L Bottle)."). Size cannot be changed after creation. Active/inactive (inactive types are hidden from sales and purchasing). Changes audited (before → after).
- **Product:** code `P01…` generated; name and selling price > 0 required; cost price optional; **opening stock only when creating**; later stock changes only via goods receipt, sales and adjustments; active/inactive.
- **Customer type:** unique name ("Customer type already exists."), description, active. Deactivation blocked while active customers use it (check added in M05: "Active customers use this type. Move them first.").
- **Area:** unique name, active – the delivery route. Managed from the Customers page popup (M05 client) – API here.
- **Prices (FR-04, FR-07, BR-08):** water price per bottle type × customer type; deposit per bottle type. Stored only as dated `price_entry` rows. Change = new entry with **new price > 0**, **effective date** (default tomorrow) and **reason** – all required ("Enter a price greater than zero." / "Enter the effective date." / "Enter the reason for the change."). Audit: "Rs. 350.00 → Rs. 375.00 from 01/11/2026 – reason".
- **Current price** for a date = entry with the latest `effective_from ≤ date`. Future entries are "scheduled" (shown under the current price: "→ Rs. 180 from 01/11/2026"). This is the **standard** price; a customer's agreed price (M05) overrides it for that customer – the Pricing Service is built so M05 only adds step 1 of `waterPrice` (design 5.5).
- **Confirmed vs example prices:** the 20L deposit (Rs. 1,000) is marked **confirmed**; all others show **example** until the client confirms (setting `prices.confirmed` list).
- **Old-bottle brands (FR-06, BR-02):** name, accepted bottle type, note, active ("Stop accepting" / "Accept again"); starts with **American Water (20L)**. "Taken in so far" count comes from sales (M06).

#### SERVER tasks
- [ ] **S1 – Migration `V003__masterdata.sql`:** `bottle_type`, `product`, `customer_type`, `area`, `price_entry` (unique kind+bottle+type+date), `old_bottle_brand`; product `stock_qty CHECK ≥ 0`; doc sequence `product`.
- [ ] **S2 – BottleTypeService + endpoints** (`GET/POST /bottle-types`, `PUT /bottle-types/{code}`) with the rules above. *(Stock rows for a new bottle type are created in M04.)*
- [ ] **S3 – ProductService + endpoints** (`GET/POST /products`, `PUT /products/{id}`).
- [ ] **S4 – CustomerType and Area services + endpoints** (`/customer-types`, `/areas`).
- [ ] **S5 – PricingService:** `currentMatrix(date)` (water by bottle × type, deposits, scheduled next entries, confirmed flags), `history()`, `setPrice()`, `standardPrice(customerTypeId, bottle, date)`, `waterPrice(customer, bottle, date)` (standard only until M05 adds agreed prices), `deposit(bottle, date)`; endpoints `GET /prices/current`, `GET /prices/history`, `POST /prices`.
- [ ] **S6 – OldBottleBrand service + endpoints** (`/old-bottle-brands`).
- [ ] **S7 – Seed (local/UAT):** bottle types, products, customer types, areas, full price history from the prototype (Jan 2026 and Jul 2026 lists, 20L deposit 1,000, 10L deposit 600, 10L prices Household 200 / Shop 190 / Office 185 / Factory 175, scheduled 10L Factory 180 from 01/11/2026), American Water.
- [ ] **S8 – Tests:** code from size, duplicate size, product opening stock only on create, price lookup at date boundaries (day before / on / after effective date), scheduled price, validation messages, Accountant 403.

#### CLIENT tasks
- [ ] **C1 – Bottles & Products page** with tabs **Bottle types · Other products · Prices & deposits · Accepted old bottles** (Admin only).
- [ ] **C2 – Bottle types tab:** table (code, name, size, deposit, in circulation (from M04, show "–" until then), status, Edit) + New/Edit pop-up (code shows "Generated from the size", size read-only on edit).
- [ ] **C3 – Other products tab:** table (product + code, selling price, cost, in stock – red when ≤ 3, status) + pop-up (product code "Generated on save", opening stock only on create).
- [ ] **C4 – Prices & deposits tab:** info banner (example vs confirmed); water price grid (rows = active bottle types, columns = active customer types) – click a price → change pop-up (current price, new price, from date, reason); scheduled price shown under the current one; deposit table; **Price history** pop-up (from, price, amount, reason, by, Current/Scheduled/Old badge); a **Customer agreed prices** card is added in M05 (C8)
- [ ] **C5 – Accepted old bottles tab:** table (brand, for bottle, taken in so far, note, status, Stop accepting/Accept again) + Add brand pop-up.

**Acceptance checklist**
- [ ] Creating a 19L bottle type gives code `B19`; a second 19L is rejected
- [ ] A price change effective tomorrow shows as scheduled; today's price is unchanged
- [ ] Price history lists all entries with Current / Scheduled / Old correctly
- [ ] 20L deposit shows "confirmed", other prices "example"
- [ ] Products: stock not editable after creation
- [ ] American Water accepted for 20L; can be stopped and re-enabled
- [ ] Every change appears in the audit log

**Notes & comments**

| Date | By | Comment |
|---|---|---|
| | | |

---

### M03 – Suppliers & factories

| Item | Detail |
|---|---|
| Branch | `feature/M03-suppliers-factories` |
| Depends on | M02 |
| BRD | FR-14, FR-22, BR-11 |
| Prototype | `master-data/suppliers.html` |
| Design | 4.3 (Partners), 6, 7.2 (Partners) |

**Goal:** suppliers with the items they supply, and one or more filling factories with a charge per bottle type.

**Key logic & rules**
- **Supplier:** code `S01…` generated; name and phone required ("Supplier name and phone are required."); **at least one supplied item** picked from active bottle types (as "Empty 20L Bottle") and active products ("Select at least one item the supplier supplies."); contact, email, address optional; **payment terms from the fixed list: Cash on delivery (0), 7, 14, 30, 45, 60 days**; active/inactive.
- **Factory:** code `F01…` generated; name required ("Enter the factory name."); address, licence, contact, phone, email; payment terms (same list); **charge per active bottle type > 0**; active/inactive. A charge change is saved with today's effective date (history kept); **a new charge applies only to batches sent after saving** (batches copy the charge at dispatch – M10).
- **Owed amounts** and **bottles at factory** columns are filled by M09 and M10 (show "–" until then).

#### SERVER tasks
- [ ] **S1 – Migration `V004__partners.sql`:** `supplier`, `supplier_item`, `factory`, `factory_charge`; sequences `supplier`, `factory`.
- [ ] **S2 – SupplierService + endpoints** (`GET/POST /suppliers`, `PUT /suppliers/{id}`); `GET /suppliers?item=B20` (suppliers of an item – used by M09 auto-tick).
- [ ] **S3 – FactoryService + endpoints** (`GET/POST /factories`, `PUT /factories/{id}`); `currentCharge(factoryId, bottle, date)`.
- [ ] **S4 – Seed (local/UAT):** S01–S06 with items and terms, F01 AquaSeal (B20 60, B10 35, 14 days), F02 Pure Lanka Fillers (B20 62, B10 34, 30 days).
- [ ] **S5 – Tests:** validations, terms list, charge history and current charge, Accountant view-only (GET allowed, POST/PUT 403).

#### CLIENT tasks
- [ ] **C1 – Suppliers & Factories page** with tabs **Suppliers · Filling factories** (Admin full, Accountant view).
- [ ] **C2 – Suppliers tab:** table (supplier + code + address, contact, supplies (item names), payment terms label, owed, status, Edit) + pop-up (code read-only "Generated on save", terms dropdown, **item chips** to tick).
- [ ] **C3 – Filling factories tab:** table (factory + code, contact, charge per bottle "20L Rs. 60 · 10L Rs. 35", terms, at factory now, owed, status) + pop-up (charges per bottle type, terms dropdown, note "A new charge applies to batches sent after saving.").

**Acceptance checklist**
- [ ] Supplier cannot be saved without an item; terms only from the list
- [ ] A third factory can be added with its own charges
- [ ] Accountant sees both tabs without New/Edit buttons; API rejects writes
- [ ] Changes audited

**Notes & comments**

| Date | By | Comment |
|---|---|---|
| | | |

---

### M04 – Inventory core (Ledger Engine & Stock)

| Item | Detail |
|---|---|
| Branch | `feature/M04-inventory` |
| Depends on | M02 |
| BRD | FR-27, FR-28, FR-29 (company part), FR-30, FR-31, BR-05, BR-12, BR-15 |
| Prototype | `inventory/inventory.html` |
| Design | 4.3 (Inventory & ledger), **5.1**, 6 (Inventory), 7.2 (Inventory) |

**Goal:** the **Ledger Engine** used by every later module, stock by status, company damage, stock adjustments, minimum levels and the movement history.

**Key logic & rules**
- **Five buckets per bottle type:** EMPTY (Empty in store), FACTORY (At factory), FILLED (Filled in store), CUSTOMERS (With customers), WRITTEN_OFF (Written off). "In circulation" = EMPTY + FACTORY + FILLED + CUSTOMERS. Product stock = `product.stock_qty`.
- **Ledger Engine `post(source, effects[])`:** lock rows in a fixed order (`stock_balance` by bottle+bucket, then customer bottle rows (M05), then products) with `SELECT … FOR UPDATE`; compute new values; **if any value < 0 → `STOCK_NEGATIVE`** with the message "<Bucket label> (<20L>) cannot go below zero – available X, needed Y. Record a stock adjustment first." (BR-12); apply; insert `ledger_effect` + `stock_movement` rows (date, doc no, description, item, bucket, delta, user). `reverse(source)` = negated effects through the same checks.
- **New bottle type** (from M02) automatically gets its 5 `stock_balance` rows (event/hook); migration back-fills rows for existing bottle types.
- **Company damage (FR-29, BR-05):** location → bucket: "Empty in store" → EMPTY, "Filled in store" → FILLED, "At factory" → FACTORY, "During delivery" → FILLED. Qty ≥ 1, reason required. Effects: bucket −Q, WRITTEN_OFF +Q. Creates `damage_record` (responsibility COMPANY). Audit "… Written off at company cost." *(Customer responsibility is added in M06.)*
- **Stock adjustment (FR-30):** reason required; recorded by the current user.
  - **Physical count:** item + status (Empty / Filled / At factory) + counted qty → delta = counted − system; no change → "Counted quantity matches the system. No adjustment needed."
  - **Lost bottles:** qty ≥ 1 → bucket −Q, WRITTEN_OFF +Q.
  - **Products:** count only (counted − system).
  - Number `ADJ-0001`; audit with "system X → counted Y".
- **Minimum level & alert (FR-31):** minimum filled level per bottle type (≥ 0); alert when FILLED < minimum ("Low stock: only 117 filled 20L bottles in store (minimum 150). 66 are at the factory.").
- **Movements:** every posting visible, filterable by item and text, newest first, paged.

#### SERVER tasks
- [ ] **S1 – Migration `V005__inventory.sql`:** `stock_balance` (PK bottle+bucket, `CHECK qty ≥ 0`), `ledger_effect`, `stock_movement`, `damage_record`, `stock_adjustment`, `min_stock_level`; back-fill stock rows; sequence `adjustment`; indexes.
- [ ] **S2 – Ledger Engine** (`ledger/`): `Effect` (target STOCK / PRODUCT; CUSTOMER_BOTTLE added in M05), `LedgerService.post()` and `reverse()`, bucket labels for messages, movement writer. Hook: create stock rows when a bottle type is created.
- [ ] **S3 – Stock queries:** `GET /stock` (per bottle type: 5 buckets, in circulation, minimum level; product stock), `GET /stock/movements?item&q&page`, `GET /stock/alerts`.
- [ ] **S4 – Company damage:** `POST /stock/damage` (responsibility COMPANY), `GET /stock/damages` (damages + adjustments list for the tab).
- [ ] **S5 – Stock adjustments:** `POST /stock/adjustments` (count / lost / product count).
- [ ] **S6 – Minimum levels:** `PUT /stock/min-levels/{bottle}`; audit "Filled 20L: 150 → 160".
- [ ] **S7 – Seed (local/UAT):** opening stock (B20: empty 30, filled 95, written off 46; B10: empty 18, filled 30, written off 7) as IMPORT postings; minimum levels B20 150, B10 25.
- [ ] **S8 – Tests:** negative stock blocked with the exact message; reverse restores values; **concurrency test** (two threads posting on the same row → no negative, no lost update); adjustment no-change error; product count; low-stock alert.

#### CLIENT tasks
- [ ] **C1 – Stock page** (Admin) with tabs **Stock · Damage & adjustments · Movements**; actions **Record damage** and **Stock adjustment**; low-stock banner with link "Send empties" (enabled in M10).
- [ ] **C2 – Stock tab:** bottle table (5 buckets, filled in red when below minimum, in circulation, **minimum filled editable inline** – saves on change), note on how stock changes; product stock table (red when ≤ 3, cost value). *(The "With customers = held + to collect + owed" line is added in M05.)*
- [ ] **C3 – Record damage pop-up:** responsibility radio (Company now; Customer option appears in M06), date, bottle, qty, where (4 locations), reason; effect note ("The bottles are disposed of and written off. Value: Rs. …").
- [ ] **C4 – Stock adjustment pop-up:** Physical count / Lost bottles; date, item (bottles + products), status (bottles only), system qty (read-only), counted or lost qty, reason; "Recorded by <user>".
- [ ] **C5 – Damage & adjustments tab:** table (date, type badge Damaged/Lost/Count adjustment, item, qty, responsibility, where, customer, reason, by).
- [ ] **C6 – Movements tab:** filters (item, search), table (date, movement + ref, item, ± per bucket in green/red, product ±), paging.

**Acceptance checklist**
- [ ] Recording 999 damaged filled bottles is blocked with the BR-12 message; nothing changes
- [ ] Company damage moves bottles to Written off and appears in the damage list and movements
- [ ] Physical count adjustment sets the bucket to the counted value; lost bottles go to Written off
- [ ] Changing the minimum level updates the low-stock banner immediately
- [ ] Concurrency test passes in CI
- [ ] Every posting appears in Movements with user and reference

**Notes & comments**

| Date | By | Comment |
|---|---|---|
| | | |

---

### M05 – Customers

| Item | Detail |
|---|---|
| Branch | `feature/M05-customers` |
| Depends on | M02, M04 |
| BRD | FR-03 (types popup), FR-05 (agreed prices), FR-08 (details), FR-09, FR-10, FR-11, FR-12, FR-13, BR-08, BR-09, BR-16, BR-18 |
| Prototype | `master-data/customers.html`, `assets/js/customer-panel.js` |
| Design | 4.3 (Customers), 5.4 (first delivery, is due, advance), 6 (Customers), 7.2 (Customers) |

**Goal:** customer list, register/edit pop-up (details, delivery, payment), customer side panel, customer types & areas management, deactivate/reactivate. *(The "bottles given on the registration date" section and the history are completed in M06.)*

**Key logic & rules**
- **Required fields (FR-08):** name, address, phone, customer type, area ("Name, address, phone, customer type and area are required.").
- **Phone:** Sri Lankan format `0XX XXX XXXX` with optional spaces ("Phone must be a Sri Lankan number, e.g. 077 123 4567 or 011 285 4471."). **Duplicate phone** → warning code `DUP_PHONE` "Phone number already used by C0012 – <name>." → user may confirm "Save anyway" (`allowDuplicatePhone=true`).
- **Customer code** `C0001…` generated; shown as "Generated on save".
- **Registration date** (default today, not in the future) = `registered_on`.
- **Delivery (FR-09, BR-16):** delivery day (Mon–Sun), cycle (every 1, 2, 3, 4 weeks), usual quantity per active bottle type. **Next delivery is filled automatically = the very next delivery day after the registration date** and recalculated when the delivery day or registration date changes; it stays editable with the calendar. It must fall on the delivery day ("Next delivery date 13/10/2026 is a Tuesday, but the delivery day is Monday."). On edit, changing the delivery day recalculates from today.
- **Payment (FR-10, BR-09):** Cash / Credit / Monthly bill. Credit and Monthly bill need **credit limit > 0** ("Credit and Monthly bill customers need a credit limit.") and **payment terms from the list 7, 14, 30, 45, 60 days**. Cash → limit 0, terms 0.
- **WhatsApp fields (for M13):** "Send WhatsApp messages" opt-in + WhatsApp number (default = phone if mobile).
- **New customer** gets bottle balance rows (held/to collect/owed = 0) for every active bottle type and an account row (balance 0).
- **Edit audit:** list of changed fields ("phone: 077… → 071…; cycle: 1 → 2 wk; credit limit: Rs. 20,000 → Rs. 25,000").
- **Search & filters (FR-12):** text (name, code, address; phone digits when ≥ 3 digits), area, delivery day, customer type, payment type, status (default Active); paged; count "39 of 40 customers".
- **Customer lookup** (for pickers): exact code (`C0017`), number (`17` → `C0017`), phone digits (≥ 9), or name contains.
- **Side panel (FR-11):** code, address, area, phone; badges (status, type, payment type); balances per bottle type (held, empties to collect – amber when > 0, bottles owed – red when > 0); outstanding; credit limit + terms; delivery ("Monday, every week · next 05/10/2026"), usual quantity, water price per bottle type (from the Pricing Service, **agreed** badge + standard), customer since, notes; banners: inactive (with reason), over credit limit, delivery overdue (next < today). Actions by permission: Edit, Enter bill, First purchase / Extra order, Collect empties, Settle owed, Receive payment, Statement, Deactivate/Reactivate (links become live in M06–M11).
- **Deactivate (FR-13):** reason required ("Enter the reason for deactivating."); history kept; reactivate anytime; inactive customers excluded from sales and delivery lists.
- **Status on the customer screen (client feedback 08/10/2026):** the edit pop-up has a **Status** section (Active / Inactive). Inactive needs **Inactive from** date (not in the future) and a **reason** from a list (Stopped buying, Moved away, Business closed, Went to another supplier, Not paying, Other – Other needs details). New customers always start Active. Each list row also has a **Deactivate / Reactivate** button; the Status column shows "since dd/mm/yyyy". Before deactivating, warn when the customer still holds bottles, has empties to collect, owes bottles or owes money (these stay on the account). Reactivating moves a past next-delivery date to the next delivery day from today. Store `status_date` and `status_reason`; count line shows active / inactive totals.
- **Customer types guard:** cannot deactivate a type used by active customers.
- **Agreed prices (FR-05, BR-08, BR-18 – client feedback 08/10/2026):** an Admin can give a customer an agreed water price per bottle type that overrides the standard price for that customer only. Fields: price > 0, **from** date (may be in the future), optional **until** date, **reason** (required – "Enter the reason for the agreed price (e.g. about 3,500 bottles a month)."), optional link to the customer quotation it came from. Saving closes the agreement in force the day before `from` and cancels later ones; **Back to standard** ends it from a date. Every change audited ("Agreed price Rs. 300.00 → Rs. 270.00 from 01/07/2026 (standard Rs. 300.00) – reason"). Old bills never change (price stored on the bill line).
- **Where prices are set and shown:** register pop-up (optional agreed price per bottle type + reason, applied from the registration date before the first-purchase bill); side panel (water price with **agreed** badge and the standard price; **Prices** button opens the agreed-price dialog with standard, price now, scheduled change, bottles a month, filling charge, difference and margin hints; **Prices** tab = history with In force / Scheduled / Ended / Replaced / Cancelled); edit pop-up shows "Price now" + "Change prices…"; list has a **Price** filter (standard / agreed) and an "agreed price" badge; Bottles & Products → Prices tab lists all agreed prices.
- **Bottle balances are changed only by the Ledger Engine** (CUSTOMER_BOTTLE effects – added here; first used in M06).

#### SERVER tasks
- [ ] **S1 – Migration `V006__customers.sql`:** `customer`, `customer_usual_qty`, `customer_bottle_balance` (`CHECK ≥ 0` on held/to_collect/owed), `customer_account`; indexes (lower(name), phone, area+day+status, next_delivery_date); sequence `customer`.
- [ ] **S2 – Ledger: CUSTOMER_BOTTLE target** (lock order after stock rows; message "Bottles held (20L) for <name> cannot go below zero – currently X."); balance rows created for new bottle types too.
- [ ] **S3 – CustomerService:** create / update with all validations, duplicate-phone flow, code, balance + account rows, audit with changed fields.
- [ ] **S4 – ScheduleService (core):** `firstDelivery(registeredOn, day)`, `isDue(customer, date)`, `nextAligned(customer, after)`, `advance(customer, txnDate)` – unit tests with weekly / 2-weekly / 4-weekly cycles and month boundaries.
- [ ] **S5 – List, filters & lookup:** `GET /customers` (filters, paging, sort, includes balances summary & outstanding), `GET /customers/lookup?q=`.
- [ ] **S6 – Detail:** `GET /customers/{id}` (all panel data incl. water price per bottle from PricingService); `GET /customers/{id}/history` defined now (returns customer events; sales/payments added in M06/M11).
- [ ] **S7 – Deactivate / reactivate** endpoints; complete the customer-type deactivation guard from M02.
- [ ] **S8 – Seed (local/UAT):** customers C0001–C0040 from the prototype (opening bottles and opening balances are posted in M06 seed).
- [ ] **S9 – Tests:** all validation messages, duplicate phone flow, first-delivery rule, next delivery not on delivery day rejected, lookup variants, Accountant view-only.
- [ ] **S10 – Agreed prices:** migration `customer_price` (+ sequence `CP`), `CustomerPriceService.set / end / history / listCurrent`, Pricing Service step 1 (agreed price in force on the date) + `isAgreed`; endpoints `GET/POST /customers/{id}/prices`, `POST /customers/{id}/prices/end`, `GET /customer-prices`, `GET /pricing/water`; register request accepts agreed prices; Admin-only writes; audit.
- [ ] **S11 – Agreed price tests:** in force / scheduled / ended / cancelled / replaced; back to standard; from = until; a bill saved before a change keeps its price; Accountant 403 on writes; seed agreed prices from the prototype (C0004, C0008, C0010 scheduled, C0012 ended, C0014 10L, C0018, C0021, C0030 with end date).

#### CLIENT tasks
- [ ] **C1 – Customers page:** actions **Customer types** and **+ New customer** (Admin); filters (search, area, day, type, payment, status); count line "… · click a row to see details"; table (customer + code + address, phone, area, type, delivery "Mon, weekly · next 05/10/2026", payment badge, bottles "2×20L" + collect/owed badges, balance (red when over limit), status, Edit); row click opens the side panel; `?id=C0012` opens a panel, `?new=1` opens the pop-up.
- [ ] **C2 – New / Edit customer pop-up:** code read-only; name, phone, address, area, type; **Delivery** section (registration date – new only, delivery day, cycle, next delivery DateField with the weekday shown below and a red note if not the delivery day, usual qty per bottle type); **Payment** section (payment type; credit limit + terms dropdown only for Credit/Monthly); WhatsApp opt-in + number; notes; help line with the standard water price for the chosen type; **Water price** section (new: agreed price per bottle type + reason; edit: price now + "Change prices…"). Auto next-delivery rule as above. Duplicate-phone confirm. After save: "Customer added" (the bottles-given section and print are added in M06).
- [ ] **C3 – Customer side panel** (`CustomerPanel` shared component – reused by Billing, Sales): header, banners, action buttons by permission, balance cards, delivery summary, tabs **History · Payments · Invoices** (empty states until M06/M11).
- [ ] **C4 – Customer types & areas pop-up:** list with customer counts, activate/deactivate (guard message), add new type / new area.
- [ ] **C5 – Deactivate / reactivate** from the panel, the list row button and the Status section of the edit pop-up (shared dialog: date, reason list, open-items warning).
- [ ] **C6 – Stock tab update (M04 page):** add the line "With customers (20L) = held X + empties to collect Y + bottles owed Z".
- [ ] **C7 – Agreed prices UI:** `PriceText` (price + agreed badge + standard), `CustomerPriceDialog`, Prices tab in the side panel, price fields in the register pop-up, "Change prices…" in edit, list Price filter and badge.
- [ ] **C8 – Bottles & Products → Prices tab:** "Customer agreed prices" card (in force + scheduled / all; customer, bottle, agreed, standard now and %, from, until, reason, status; links to the customer).

**Acceptance checklist**
- [ ] New customer registered today (Friday) with Monday delivery → next delivery = next Monday; editable
- [ ] Next delivery on a wrong weekday is rejected with the clear message
- [ ] Credit / Monthly bill require a credit limit; terms only from the list
- [ ] Duplicate phone warns and can be saved anyway
- [ ] Search by name, code, phone digits; filters combine; paging works with 10,000 seeded test customers in < 1 s
- [ ] Side panel shows balances, delivery, price; actions follow the role
- [ ] Deactivated customer keeps history and disappears from active lists
- [ ] Accountant sees list and panel without edit buttons
- [ ] An agreed price from 01/07 is used on bills from 01/07; a scheduled price starts on its date; back to standard works; old bills keep their price
- [ ] Registering a customer with an agreed price bills the first purchase at that price

**Notes & comments**

| Date | By | Comment |
|---|---|---|
| 08/10/2026 | Amritha | Client feedback: customer status on the edit pop-up / list, and agreed customer prices (D10, D11) |

---

### M06 – Sales core

| Item | Detail |
|---|---|
| Branches | `feature/M06-sales-server` → then `feature/M06-sales-client` |
| Depends on | M02, M04, M05 |
| BRD | FR-08 (bottles at registration), FR-29 (customer part), FR-32, FR-33, FR-35, FR-36, FR-37, FR-38, FR-39, FR-40, FR-41, FR-59, BR-01–BR-07, BR-09, BR-13, BR-14 |
| Prototype | `sales/sales-bills.html`, `master-data/customers.html` (registration), `assets/js/store.js` (Ops.*), `assets/js/ui.js` (`printBill`, `custCard`) |
| Design | 4.3 (Sales), **5.1, 5.2, 5.5, 5.6 (charge & credit check)**, 6 (Sales & Bills), 7.2 (Sales), 10 (Bill) |

**Goal:** the sales transaction engine and every sale type except the daily grid: first purchase (incl. at registration), exchange & left-at-door posting API (the grid UI is M07), extra order, product sale, collect empties, customer-damaged bottle, settle owed, reversal, bill search and bill printing.

**Key logic & rules (common to all sales)**
- **Posting flow (one DB transaction):** validate → calculate with the rule class → credit check → Ledger post (stock + customer bottle effects) → save `sales_txn` + lines (with prices snapshot) → update `customer_account` (`+ amount − paid`) → audit → after-commit events (schedule advance, WhatsApp outbox in M13).
- **Bill number:** every sales txn (incl. reversals) gets `B000001…`. **Paper bill number** optional except on daily bills (M07); when given it must be unique among non-reversed, non-reversal txns ("Paper bill 41275 was already entered on 02/10/2026 (Exchange, Dilshan Fernando). A bill number can be entered only once." – code `DUPLICATE_PAPER_BILL`).
- **Customer must be active** ("<name> is inactive. Reactivate the customer before entering sales."). **Date ≤ today** ("Date cannot be after today (05/10/2026).").
- **Prices** from the Pricing Service on the txn date – the customer's **agreed price** if one is in force, otherwise the standard price (BR-08); unit price and deposit stored on the line (past bills keep their price). Previews show "(agreed price)" next to an agreed water price.
- **Credit check (BR-09):** if payment type ≠ Cash and limit > 0 and `balance + amount − paid > limit` → `CREDIT_LIMIT` "<name> would exceed the credit limit (Rs. 12,000.00). Balance after this bill: Rs. 13,840.00. Admin approval is required." Admin may resend with `approveOverLimit=true` → `credit_override_by` stored + audit "Over-limit sale approved by <name>". Non-Admin cannot approve.
- **Paid on bill:** ≥ 0; for Cash customers the UI pre-fills the full amount; anything unpaid goes to the balance.

**Key logic & rules (per sale type)** – F = filled, E = empties, O = old bottles, Q = qty
- **Exchange (FR-34 API, BR-03, BR-07):** per bottle type: `diff = F − E`; diff > 0 → owed += diff and **note required** ("Fewer empties than filled bottles: 1 bottle(s) will be recorded as owed. Add a note explaining the shortage."); diff < 0 → clear to_collect first, then owed; leftover → "<name> returned 2 more 20L empties than they hold, owe or have waiting for collection. Check the bill."; filled of a type not held → "<name> holds no 10L bottles. Use Extra order (deposit needed) for new bottles."; customer with no bottles at all → "… has no bottles yet. Record a First purchase instead."; at least one line ("Enter the filled bottles given and/or empties collected."). Amount = F × price. Stock: FILLED −F, EMPTY +E, CUSTOMERS +(F−E). Then **advance next delivery** (design 5.4).
- **Left at door (FR-35, BR-04):** empties forced 0; **phone agreement required** ("Confirm that the customer agreed by phone.") and **note required**; to_collect += F; FILLED −F, CUSTOMERS +F; amount = F × price; advance next delivery.
- **First purchase (FR-32, FR-33, BR-01, BR-02):** customer must hold **0 of that bottle type** ("<name> already holds 20L bottles. Use Extra order for additional bottles."); Q ≥ 1; O ≤ Q ("Old bottles handed in cannot be more than the bottles given."); if O > 0 the brand must be active and accepted for that bottle type ("American Water bottles are accepted only for 20L Bottle."); price and deposit must exist; amount = Q × price + (Q − O) × deposit; FILLED −Q, CUSTOMERS +Q, EMPTY +O; held +Q; advance next delivery.
- **Registration with bottles (FR-08 + FR-32):** `POST /customers/register` = create customer (M05 rules) **and** one first purchase per bottle type with qty > 0 in **one transaction** (all or nothing); old bottles only on 20L (min(old, 20L qty)); stock checked **before** saving; cash paid is allocated across the bills in bottle order; the user-chosen next delivery date is kept; response returns the customer and the bills (for "Print bill").
- **Extra order (FR-39, FR-33):** F ≥ 1; E ≤ F ("Empties returned are more than filled bottles. Use Bill entry or Collect empties for returns."); E ≤ held; new bottles = max(0, F − E); O ≤ new bottles; deposits = new − O; amount = F × price + deposits × deposit; FILLED −F, EMPTY +(E+O), CUSTOMERS +(F−E); held += new bottles. Does not move the schedule.
- **Collect empties (FR-36):** Q ≥ 1 and ≤ to_collect ("Only 2 20L empties are waiting to be collected from <name>."); EMPTY +Q, CUSTOMERS −Q; to_collect −Q; amount 0.
- **Customer-damaged bottle (FR-37, BR-06):** Q ≥ 1 and ≤ held; reason required; **REPLACE** (default): charge deposit × Q, plus water × Q if "give the replacement filled" (default on); stock CUSTOMERS −Q +Q (filled replacement from FILLED −Q), WRITTEN_OFF +Q; holding unchanged. **REDUCE:** no charge; CUSTOMERS −Q, WRITTEN_OFF +Q; held −Q. Creates `damage_record` (CUSTOMER, "At customer"). The Inventory "Record damage" pop-up's Customer option calls this.
- **Settle bottles owed (FR-38, BR-07):** Q ≥ 1 and ≤ owed; **RETURN** → owed −Q, EMPTY +Q, CUSTOMERS −Q; **DEPOSIT** → owed −Q, held +Q, charge Q × deposit.
- **Product sale (FR-40):** registered customer **or** walk-in name ("Select a customer or enter a walk-in name."); at least one product with qty > 0; price defaults to selling price (lower price allowed – shown as a discount warning); product stock −Q (BR-12 applies); cost recorded (for profit); **walk-in must pay in full** ("Walk-in sales must be paid in full."); payment method Cash / Bank transfer / Cheque.
- **Reversal (BR-14):** not allowed for REVERSAL or OPENING_BALANCE ("This entry cannot be reversed."), not twice ("Already reversed by B000123."), **reason required**; negated effects through the Ledger (may fail with BR-12 if stock has moved on); new REVERSAL txn with its own bill number, negative amount and paid; link both ways; account updated; damage record marked reversed; **paper bill number becomes free**; next delivery date is **not** moved back; audit "Reversal entry … – <reason>".
- **Bill search (FR-41):** by system bill number or paper bill number (partial), customer, type, date range; "Bill X is already entered – …" / "has not been entered yet" banner when searching a number.
- **Bill print (FR-59):** company header, BILL, bill no, date, paper bill no, customer (code, address, phone, payment type), type + note, lines (water, deposits "not refundable", new bottle for damage, old bottles "no deposit", empties returned), total, paid, account balance now, bottles held, next delivery, signatures.

#### SERVER tasks
- [ ] **S1 – Migration `V007__sales.sql`:** `sales_txn`, `sales_txn_line`, **partial unique index `ux_paper_bill`**, indexes (customer+date, date, kind+date); sequence `bill`.
- [ ] **S2 – Posting framework:** `SalesPostingService` (flow above), `AccountService` (balance update + `balance(customerId)`), `CreditCheck`, error codes.
- [ ] **S3 – Rule classes (pure, unit-tested):** `ExchangeRules` (incl. left at door, preview result: amount, owed added, to-collect added/cleared, owed cleared, excess, balances after), `FirstPurchaseRules`, `ExtraOrderRules`, `CustomerDamageRules`, `OwedSettlementRules`, `ProductSaleRules`.
- [ ] **S4 – Exchange / left-at-door API:** `POST /sales/bills` + `/preview` (paper bill required here), schedule advance after commit.
- [ ] **S5 – First purchase API** + `/preview`; **registration API** `POST /customers/register` + `/preview` (one transaction, returns bills).
- [ ] **S6 – Extra order API** + `/preview`.
- [ ] **S7 – Collect empties, Settle owed, Customer damage APIs** (+ previews); enable `responsibility=CUSTOMER` in `POST /stock/damage` by delegating to Customer damage.
- [ ] **S8 – Product sale API** + `/preview`.
- [ ] **S9 – Reversal API** `POST /sales/txns/{id}/reverse`.
- [ ] **S10 – Bill list/detail/paper check:** `GET /sales/txns` (filters, paging), `GET /sales/txns/{id}` (lines, effects, user, links), `GET /sales/paper-bill/{no}`.
- [ ] **S11 – Customer history:** complete `GET /customers/{id}/history` with sales txns + running balance; old-bottle "taken in so far" count for M02 brands tab.
- [ ] **S12 – Printing foundation + bill PDF:** `printing/` package (`PdfRenderer` interface + OpenPDF implementation, shared header from company settings and logo, money/date formatting), `GET /print/bill/{txnId}`.
- [ ] **S13 – Seed (local/UAT):** opening customer bottles (held → CUSTOMERS stock via IMPORT postings) and OPENING_BALANCE txns from the prototype data.
- [ ] **S14 – Tests:** every rule and message above; credit limit + Admin override (and non-Admin rejected); paper bill duplicate and re-entry after reversal; registration rollback when stock is short; reversal of each txn type; **invariants after every scenario** (CUSTOMERS bucket = Σ held + to_collect + owed; account balance = Σ(amount − paid) − payments); reference scenario "3 × 20L with 1 American Water bottle, Household = Rs. 3,050".

#### CLIENT tasks
- [ ] **C1 – Sales & Bills page:** actions **Enter daily bills** (link to M07) and **+ New ▾**; filters (bill no / paper bill no with the "already entered / not entered" banner, customer picker, type, from/to – default last 14 days); table (bill no + "paper …", date, type badge + reversed, customer, bottles "filled/empties", amount, paid); reversed rows muted; row click → details; `?bill=` and `?new=<type>&cust=<id>` URL support.
- [ ] **C2 – Bill details pop-up:** summary (bill no + paper, type, date, customer link, details per line, amount, paid, note, entered by + time), banners (over-limit approved, reversed with reason, "this entry cancels an earlier bill"), buttons **Reverse this bill** (ReasonDialog), **Print bill**, Close.
- [ ] **C3 – Sale dialog framework:** two columns – form (bill no "Generated on save", date) and live calculation + `CustomerCard` ("after this bill" with +/− deltas) using the preview endpoints (debounced); paid auto-fill for Cash customers until the user types; credit-limit approval dialog (`CreditApprovalDialog`: limit, balance, after; Admin "Approve and save", others told only Admin can approve).
- [ ] **C4 – First purchase & Extra order dialogs:** bottle type, qty / filled + empties, old bottles + brand (brands for the chosen bottle size), cash paid; calculation lines (water, deposits, new bottles beyond holding, old bottles "no deposit", total).
- [ ] **C5 – Product sale dialog:** customer **or** walk-in name, product (with price and stock), qty, unit price (defaults), paid, paid by; stock warning; discount warning.
- [ ] **C6 – Collect empties, Customer-damaged bottle, Settle owed dialogs** (qty pre-filled from the customer's balance; damage modes REPLACE/REDUCE + "give filled and charge water"; settle methods RETURN/DEPOSIT).
- [ ] **C7 – Registration with bottles:** add to the M05 New customer pop-up the section **"Bottles given on the registration date"** (filled per bottle type, old American Water bottles, cash paid; live summary "2 × 20L: water Rs. 700 + deposit 1 × Rs. 1,000 (1 old bottle – no deposit) · Total Rs. 1,700 · paid …"); save calls `/customers/register`; result pop-up "Customer registered" with bills and **Print bill**.
- [ ] **C8 – Customer panel & inventory links:** panel actions open the dialogs (First purchase / Extra order / Collect / Settle owed); History tab shows txns with running balance; Inventory "Record damage" gets the **Customer** responsibility option (customer picker, paid, charge preview).

**Acceptance checklist**
- [ ] Registering a customer with 3 × 20L and 1 American Water bottle (Credit) creates the customer and bill B… for Rs. 3,050 in one step; print shows the correct lines
- [ ] Registration fails cleanly (nothing saved) when filled stock is too low
- [ ] Extra order charges deposits only for bottles beyond the holding
- [ ] Customer damage REPLACE charges deposit + water and writes off the bottle; REDUCE lowers the holding without charge
- [ ] Settle owed by RETURN and by DEPOSIT update balances correctly
- [ ] Walk-in product sale must be fully paid; product stock reduces
- [ ] Reversing any bill restores stock, bottle balances and money; the paper bill number can be re-entered
- [ ] Credit limit blocks non-Admin; Admin approval is audited
- [ ] Invariants hold after the full test suite
- [ ] Accountant has no access to sales screens or APIs

**Notes & comments**

| Date | By | Comment |
|---|---|---|
| | | |

---

### M07 – Enter Bills (daily paper-bill grid)

| Item | Detail |
|---|---|
| Branches | `feature/M07-enter-bills-server` → then `feature/M07-enter-bills-client` |
| Depends on | M05, M06 |
| BRD | FR-34, FR-35, FR-36, FR-38, FR-41, FR-46, FR-47, FR-48, BR-03, BR-04, BR-07, BR-09, BR-13, NFR-04 |
| Prototype | `sales/enter-bills.html` |
| Design | 5.2, 5.4 (due list, advance), 6 (Sales – Enter Bills), 11 (NFR-04) |

**Goal:** the fast keyboard grid where the Admin types the day's paper bills. Customers due on the selected date appear automatically.

**Key logic & rules**
- **Bill date** (default today, calendar picker); **future dates blocked** ("Bills cannot be entered for a future date") and reset to today.
- **Auto list (FR-34, FR-42):** on page open and on every date change → rows for customers **due that day, not yet entered, holding bottles**, with usual quantities pre-filled: filled = usual; empties = usual + empties left at the door last trip. Rows added automatically are replaced when the date changes; **rows the user typed in are kept**. If nothing is pending, one empty row.
- **Day info line:** "Friday 02/10/2026: 8 customer(s) due – 6 listed below with their usual quantities · 2 already entered (names) · 1 new customer(s) without bottles – use First purchase: (names)" / "nobody is due. You can still add rows by hand."
- **Columns:** # · Paper bill no. · Date · Customer · Type (Exchange / Left at door) · per active bottle type: Filled, Empties · Cash paid · Phone ✓ · Note · Result · remove.
- **Paper bill no.:** required, unique – checked live against the database and against other rows in the grid ("Paper bill 41275 already entered (02/10/2026, Dilshan Fernando)" / "Same bill no. twice"). Default = previous row's number + 1 (or highest numeric paper number + 1).
- **Customer cell:** code (`17` / `C0017`), phone or name; on change fills usual quantities and empties (+ to collect).
- **Type:** keys **E** = Exchange, **L** = Left at door. Left at door: empties disabled and cleared, phone ✓ enabled, note placeholder "Who agreed, where left".
- **Live result per row** (from a TypeScript mirror of `ExchangeRules`; server re-validates on save): amount (and "→ Credit/Monthly bill"), "+1 20L owed", "2 20L to collect", "collects 2 left last trip", "settles 1 owed", errors (extra empties, no holding, customer not found, note needed for shortage, tick phone agreement, note needed), "Over credit limit" warning, "next: 09/10/2026".
- **Cash paid:** pre-filled with the amount for Cash customers on Exchange rows until the user types; empty for Credit/Monthly bill and Left at door.
- **Keyboard (NFR-04):** Enter → next field; Enter on the last field → next row (new row if last); **Ctrl+S** saves all; **Ctrl+Del** removes an unsaved row; saved rows cannot be removed ("Saved bills cannot be deleted. Use Sales & Bills → Reverse.").
- **Save all:** each pending row posted separately (`POST /sales/bills`); success → row locked and green "✓ Saved as B000278 · Rs. 700, paid Rs. 700 · next delivery 09/10/2026"; failure → row red with the server message; CREDIT_LIMIT → Admin sees **Approve & save** in the row. Toast "6 bill(s) saved, 1 need attention (see Result)". When all saved, a new empty row is added.
- **Customer card** below the grid for the active row ("Balances after this bill" or "Saved – balances updated") + rules box.
- **Footer totals:** bills, saved count, filled/empties per bottle type, cash, total charged.
- `?cust=C0017` opens the grid with one row for that customer (from customer panel / delivery list).

#### SERVER tasks
- [ ] **S1 – Due list endpoint:** `GET /delivery/due?date=&area=` → due customers with usual qty, balances (held, to collect, owed), account balance, flags (entered + bill numbers, missed/overdue, no bottles), counts for the info line. Uses ScheduleService (design 5.4). Reused by M08.
- [ ] **S2 – Grid support:** `GET /sales/paper-bill/next` (suggested next paper number); confirm `POST /sales/bills` returns bill no, amount, paid, schedule before/after; future-date rule.
- [ ] **S3 – Performance:** due list for 500 customers < 500 ms; posting a bill < 300 ms (indexes, no N+1).
- [ ] **S4 – Tests:** due list (weekly/2-weekly/4-weekly alignment, missed customer appears today, entered customers listed separately, new customers without bottles listed separately), future date rejected, end-to-end posting of 6 rows incl. shortage, left at door and duplicate paper bill.

#### CLIENT tasks
- [ ] **C1 – Page layout:** toolbar (Bill date DateField, Refresh list, + Add row, **Save bills (Ctrl+S)**), day-info banner, keyboard help line, grid card, customer card + rules box, footer totals.
- [ ] **C2 – Grid & row model:** dynamic bottle-type columns; row state (auto / typed / saved / error); auto-load on open and date change (keep typed rows); numbering of rows.
- [ ] **C3 – Cells:** paper bill no (suggested), date, customer lookup (prefill usual + empties), type select (E/L keys), filled/empties inputs, cash paid (auto-fill rules), phone ✓ (only left at door), note.
- [ ] **C4 – Live validation & result column:** TS port of `ExchangeRules` (shared test cases with the server) + duplicate checks (DB via debounced paper-bill check, grid locally).
- [ ] **C5 – Keyboard handling:** Enter flow, last-field new row, Ctrl+S, Ctrl+Del, E/L.
- [ ] **C6 – Save all:** sequential posting, per-row results, lock saved rows, Approve & save for Admin, toasts, new empty row when all saved; customer card refresh after save.
- [ ] **C7 – Entry points:** `?cust=` single-row mode with its info line; links from customer panel and delivery lists.

**Acceptance checklist**
- [ ] Opening the page lists today's due customers with usual quantities; changing the date updates the list; future date blocked
- [ ] Typing 6 paper numbers with the keyboard only and Ctrl+S saves 6 bills, each with its own B-number
- [ ] Shortage without a note is rejected; with a note → bottles owed
- [ ] Left at door needs phone ✓ and note → empties to collect; next trip's extra empties clear it
- [ ] Duplicate paper bill (in DB or twice in the grid) is shown before saving and rejected on save
- [ ] Over-limit row: Admin can approve and save; the approval is in the audit log
- [ ] Next delivery dates move forward by the cycle after saving
- [ ] Stock and customer balances stay consistent (invariants)

**Notes & comments**

| Date | By | Comment |
|---|---|---|
| | | |

---

### M08 – Delivery schedule (Delivery Planning & Daily Delivery List)

| Item | Detail |
|---|---|
| Branch | `feature/M08-delivery` |
| Depends on | M05, M06, M07 (due list) |
| BRD | FR-42, FR-43, FR-44, FR-45, FR-46, FR-58 |
| Prototype | `delivery/delivery-planning.html`, `delivery/delivery-schedule.html` |
| Design | 5.4, 6 (Delivery), 7.2 (Delivery), 10 (delivery & load sheets) |

**Goal:** the drivers' daily list with print, the Admin's next-day planning page with load sheet, and the upcoming bottle requirement.

**Key logic & rules**
- **Daily Delivery List (FR-42, FR-43):** date (prev ‹ / next › / Today, calendar), area filter. KPIs: customers (done · overdue), filled to load per bottle type (usual quantities), empties to collect, money to collect. Table: customer + address + area + notes, phone, payment badge, bottles held ("New – deposit" when none), usual, empties to collect (amber), owed (red), balance, status (Entered + bill numbers + next date / Missed dd/mm / Due); Admin sees **Enter bill** per row (not for future dates). Delivery Staff: view + print only.
- **Delivery sheet PDF (FR-44):** A4 landscape; header with date, driver (logged-in Delivery Staff or blank line), customer count; grouped by area; columns # · customer/address (+ "missed dd/mm", notes) · phone · pays · held ("NEW") · usual · collect · owed · balance · blank Bill no. / Filled / Empties / Cash-note; footer instructions; signatures (Driver, Checked by).
- **Upcoming bottle requirement (FR-45)** (Admin tab): next 14 days, 20L (bottle selector optional): per day customers due (not entered) and bottles needed, running total, filled left (red when < 0), plus bottles at factory; advice banner "Filled bottles run out on <day date>. Send more empties to the factory before then." or "Enough filled bottles for the next 14 days."; bar chart.
- **Delivery Planning (FR-58, Admin):** date (default **tomorrow**; Today / Tomorrow / Next week buttons), route (area), show (due on this date / all active customers). KPIs: customers due (entered · missed earlier), bottles needed per type, empties to collect, bottles owed, money to collect. **Ready check per bottle type:** needed on the date vs filled in store now, minus bottles still needed **before** that date (today … date−1, not entered), plus at factory → green "✔ Ready" or red "✖ Short by N" (with "Bottles at the factory will cover it if they come back in time" when factory ≥ short) and link to Send empties. **Table grouped by area** with a route header "Kottawa – 4 due · load 10×20L": customer (+ address, day/cycle), phone, pays, **last delivery** (date + bottles, "left at door"), **this date** qty, **next**, **week after**, collect, owed, balance, status (Entered / Missed / Due / First delivery / Not due; "Over limit" badge). Not-due rows muted in "all" mode.
- **Load sheet PDF:** route totals (customers, bottles per type, empties to collect) + customer list by route (only due & not entered) with blank columns; signatures (Loaded by, Driver, Checked by).

#### SERVER tasks
- [ ] **S1 – Daily list endpoint** (reuse M07 due list + KPIs).
- [ ] **S2 – Planning endpoint** `GET /delivery/planning?date&area&show` (rows with last / next / week after, KPIs, ready check per bottle type incl. "needed before").
- [ ] **S3 – Requirement endpoint** `GET /delivery/requirement?days=14&bottle=B20`.
- [ ] **S4 – PDFs:** `GET /print/delivery-sheet?date&area`, `GET /print/load-sheet?date&area`.
- [ ] **S5 – Tests:** last/next/week-after for each cycle; "needed before" sum; ready vs short; missed customers only on today; Delivery Staff can only read the daily list and sheet.

#### CLIENT tasks
- [ ] **C1 – Daily Delivery List page** (`/delivery/daily-list`): toolbar (‹ date › Today, area), KPIs, table, **Print delivery sheet**; tab **Upcoming bottle requirement** (Admin only) with advice, chart and table.
- [ ] **C2 – Delivery Planning page** (`/delivery/planning`): toolbar (date, Today/Tomorrow/Next week, route, show), KPIs, ready banners, grouped table, **Print load sheet**.
- [ ] **C3 – Role behaviour:** Delivery Staff lands on the Daily list, sees no Enter bill links and no requirement tab; Planning is Admin only.

**Acceptance checklist**
- [ ] Daily list for today shows due, entered and missed customers with correct balances
- [ ] Delivery sheet prints grouped by area with blank columns
- [ ] Planning for tomorrow shows the right customers, bottles needed and Ready/Short
- [ ] Last / next / week-after dates are correct for weekly and 2-weekly customers
- [ ] Load sheet totals per route match the screen
- [ ] Delivery Staff sees only the daily list (UI and API)

**Notes & comments**

| Date | By | Comment |
|---|---|---|
| | | |

---

### M09 – Purchasing

| Item | Detail |
|---|---|
| Branch | `feature/M09-purchasing` |
| Depends on | M03, M04 |
| BRD | FR-15, FR-16, FR-17, FR-18, FR-19, FR-20, FR-21, FR-28, BR-10 |
| Prototype | `purchasing/purchasing.html` |
| Design | 4.3 (Purchasing), 5.1 (goods receipt effects), 6 (Purchasing), 7.2 (Purchasing), 10 (PO) |

**Goal:** quotation requests, supplier quotations and side-by-side comparison, purchase orders with fixed steps, goods receipt into stock, supplier balances and payments.

**Key logic & rules**
- **Quotation request (FR-15):** number `QR-2026-001` (yearly reset); date ≤ today, needed-by (optional), note; ≥ 1 item with qty > 0 ("Add at least one item with a quantity."), items = active bottle types (empty bottles) + active products; ≥ 1 supplier ("Select the suppliers asked to quote."). **Suppliers who supply any chosen item are ticked automatically** (user can change). Status OPEN.
- **Record quotation (FR-16):** request must be OPEN / QUOTES_RECEIVED ("A quotation has already been chosen for QR-…."); supplier from those asked and not yet quoted ("… already has a quotation recorded for QR-…."); supplier's reference (optional), date, **valid until** (required), delivery days, unit price per item (blank = not quoted; ≥ 1 price required), notes, scanned file (PDF/JPG/PNG ≤ 5 MB via FileStorage). Number `QT-0001`. Request → QUOTES_RECEIVED.
- **Comparison (FR-17):** columns = quotations; rows = items (price each, lowest highlighted **only when ≥ 2 prices** – "information only, the system does not choose"), total, "Does not cover all items" warning, delivery, valid until (expired badge), notes + attachment. **Choose** = owner's manual choice with **reason required**; expired quotation cannot be chosen ("This quotation expired on dd/mm/yyyy. Ask the supplier to re-quote."); only one choice per request. Request → SELECTED.
- **Create PO (FR-18, BR-10):** only from the chosen quotation ("A purchase order can be created only from the chosen quotation."); one active PO per quotation; lines = requested qty × quoted price (items without a price are skipped); expected = today + delivery days; number `PO-2026-0001`; status DRAFT; request → CLOSED.
- **PO steps (FR-19):** DRAFT → (edit lines qty/price, expected date, notes; ≥ 1 line with qty) → **Approve** → APPROVED → **Mark as sent** → SENT → **Receive goods** → PARTLY_RECEIVED / RECEIVED. **Cancel** (reason required) only from DRAFT or APPROVED. Invalid transitions → "Cannot change APPROVED to RECEIVED." Every change in `po_status_history` + audit. **The UI shows the step bar and only the next action.**
- **Goods receipt (FR-20, BR-10):** only for SENT / PARTLY_RECEIVED ("Goods can be received only against an approved and sent PO. PO-… is Draft."); date, delivery note no., note; per line received ≤ still due, damaged ≤ received ("Damaged cannot be more than received for …"), ≥ 1 received; good = received − damaged → **bottles to EMPTY, products to product stock** (Ledger); damaged not added and not paid; value = good × price; number `GRN-0001`; PO status updated (RECEIVED when every line is fully received).
- **Supplier balances (FR-21):** goods received value − payments = owed; oldest unpaid GRN age (badge when older than terms). **Payment:** supplier, date, amount > 0, method (Cash / Bank transfer / Cheque), reference required unless Cash ("Enter the cheque number or bank reference."), optional PO, note; number `SP-0001`.
- **Accountant:** view everything in Purchasing; no buttons.

#### SERVER tasks
- [ ] **S1 – Migration `V008__purchasing.sql`:** all purchasing tables (design 4.3); sequences `qr` (yearly), `quote`, `po` (yearly), `grn`, `supplier_payment`.
- [ ] **S2 – Quotation request service + endpoints** (create, list with status and quote counts, detail with quotations).
- [ ] **S3 – Quotation service:** record (+ attachment upload/download), comparison data (lowest flags, totals, complete flag, expired), choose.
- [ ] **S4 – Purchase order service:** create from quotation, update draft, approve, send, cancel, history, list (status filter, "next step").
- [ ] **S5 – Goods receipt service** (ledger postings, PO status) + endpoint.
- [ ] **S6 – Supplier balances & payments** (`GET /supplier-balances`, `GET/POST /supplier-payments`); fill the "Owed" column of M03 suppliers.
- [ ] **S7 – PO PDF** (`GET /print/purchase-order/{id}`, DRAFT/CANCELLED watermark).
- [ ] **S8 – Seed (local/UAT):** QR-2026-010…014 history from the prototype (cancelled PO, received PO, partly received PO, open requests with quotations).
- [ ] **S9 – Tests:** every rule and transition; GRN stock effects; owed calculation; Accountant 403 on writes.

#### CLIENT tasks
- [ ] **C1 – Purchasing page** with tabs **Quotations · Purchase orders · Supplier payments**.
- [ ] **C2 – Quotations tab:** table (request + date, items, "n of m suppliers", chosen supplier, status, Open); **New request** pop-up (request no "Generated on save", date, needed by, note, item lines with + Add item / ×, supplier list with their items, auto-tick).
- [ ] **C3 – Request detail pop-up (comparison):** side-by-side table, Choose (ReasonDialog), **+ Add quotation**, **Create purchase order** (when chosen and no PO).
- [ ] **C4 – Add quotation pop-up:** supplier (pending only), supplier reference, date, valid until, delivery days, price per item with line totals, notes, file.
- [ ] **C5 – Purchase orders tab:** status filter, table (PO + date, supplier, items, total, received/ordered, status, **next step**, Open); **PO pop-up:** step bar (1. Draft 2. Approved 3. Sent 4. Received/Partly received), summary, editable lines in Draft, history, buttons **only for the next step** (Next: Approve / Next: Mark as sent to supplier / Next: Receive goods), Cancel PO (Draft/Approved), Save changes (Draft), Print / PDF.
- [ ] **C6 – Receive goods pop-up:** date, delivery note, note, lines (still due, received now, damaged, added to stock live).
- [ ] **C7 – Supplier payments tab:** owed table (goods received, paid, owed red, terms, Pay), **+ Record payment** pop-up, payments list.

**Acceptance checklist**
- [ ] New request for 20L bottles auto-ticks the bottle suppliers
- [ ] Three quotations compare side by side; lowest highlighted; expired one cannot be chosen
- [ ] PO created only from the chosen quotation; Draft editable; steps offered one at a time
- [ ] Receiving 300 with 3 damaged adds 297 to Empty in store and moves the PO to Received / Partly received correctly
- [ ] Supplier owed = received value − payments; payment requires a reference unless cash
- [ ] PO prints with the correct status watermark
- [ ] Accountant can view but not change anything

**Notes & comments**

| Date | By | Comment |
|---|---|---|
| | | |

---

### M10 – Production (Filling Factory)

| Item | Detail |
|---|---|
| Branch | `feature/M10-production` |
| Depends on | M03, M04 |
| BRD | FR-23, FR-24, FR-25, FR-26, FR-28, FR-29 (rejects), BR-11, BR-12 |
| Prototype | `production/production.html` |
| Design | 4.3 (Production), 5.1 (dispatch/return effects), 6 (Production), 7.2 (Production) |

**Goal:** send empties to a chosen factory, receive filled bottles and rejects, track bottles at each factory, filling cost and factory payments.

**Key logic & rules**
- **Send empties (FR-23) – one entry for all bottle types (client feedback 08/10/2026):** factory (active only – "Select an active filling factory."), date ≤ today, vehicle no., note, and **one quantity row per active bottle type** (default = all empties in store; 0 = not sent; at least one > 0). All rows are validated together and saved in one transaction under one **dispatch number `FD-0001`**; one batch per bottle type is created (receiving stays per batch). Printable **dispatch note** listing every bottle type. Factory must have a charge for that bottle type ("Pure Lanka Fillers has no filling charge for 10L Bottle. Set it under Suppliers & Factories."). Qty ≤ empty stock (BR-12 message). Effects EMPTY −Q, FACTORY +Q. **Charge copied to the batch.** Number `FB-0001`. Preview: "Empty in store 24 → 14 · At factory 66 → 76 · Cost when filled: 10 × Rs. 58".
- **Receive filled (FR-24, FR-26):** batch with bottles still at the factory; date ≥ dispatch date ("Return date cannot be before the dispatch date."); filled + rejected ≥ 1 and ≤ still at factory ("Only 6 bottles of this batch are still at the factory."); rejected > 0 needs a reason. Effects FACTORY −(filled + rejected), FILLED +filled, WRITTEN_OFF +rejected; **rejects create a company damage record "At factory"**; **cost = filled × batch charge** (BR-11 – rejects not charged); partial returns allowed; number `FR-0001`. Default filled = all still at factory.
- **Bottles at factory (FR-25):** per batch: sent, filled back, rejected, still at factory (amber), cost, status (At factory / Partly returned / Returned); per factory totals.
- **Factory payments (FR-26, BR-11):** cost by **factory and month** (bottles filled, rejected not charged, cost, paid, status Paid / Part paid / Unpaid); record payment: factory + month (from unpaid list), date, amount (default = due), method, reference unless Cash; number `FP-0001`.
- **KPIs:** empty and at-factory per bottle type, owed per factory. Accountant: view only (factory payments visibility per BRD).

#### SERVER tasks
- [ ] **S1 – Migration `V009__production.sql`:** `factory_dispatch`, `factory_batch` (FK to dispatch), `factory_return`, `factory_payment`; sequences.
- [ ] **S2 – Dispatch service + endpoint** `POST /factory-dispatches` with `lines[{bottleTypeId, qty}]` → one `factory_dispatch` + one `factory_batch` per line, all-or-nothing (+ list with returns, factory, status).
- [ ] **S3 – Return service + endpoint** `POST /factory-batches/{id}/returns` (company damage for rejects).
- [ ] **S4 – Factory balances & payments:** `GET /factory-balances` (factory × month), `GET/POST /factory-payments`; fill "At factory now" and "Owed" in M03 factories.
- [ ] **S5 – Seed (local/UAT):** weekly batches history from the prototype (B20 to F01, B10 to F02, one short-shipped batch, one batch still at the factory, August paid).
- [ ] **S6 – Tests:** charge copied at dispatch (later charge change does not affect it), return limits, reject → written off + damage record, cost, per-factory balances, Accountant 403 on writes.

#### CLIENT tasks
- [ ] **C1 – Filling Factory page:** actions **Send empties** and **Receive filled bottles** (Admin); KPIs; tabs **Batches · Factory payments**.
- [ ] **C2 – Batches tab:** table (batch, sent, factory, bottle, qty sent, filled back, rejected, still at factory, filling cost, status, Receive) .
- [ ] **C3 – Send empties pop-up:** factory, date, vehicle, note; table with a row per bottle type (empty in store, sent now, empty after, at factory after, charge, cost) and totals; BR-12 / missing-charge warnings; after saving, "Dispatch FD-… sent" with **Print dispatch note**. Batches table shows the dispatch number and a Print button.
- [ ] **C4 – Receive filled pop-up:** batch select ("FB-0017 – AquaSeal – sent 26/09 – 6 × 20L at factory"), date, filled (default), rejected, reason; calculation (still at factory after, written off, cost).
- [ ] **C5 – Factory payments tab:** month × factory table, **+ Record payment** pop-up (factory and month list with amount due), payments list.

**Acceptance checklist**
- [ ] Sending more empties than in store is blocked
- [ ] A batch can be returned in two parts; rejects go to Written off and the damage list
- [ ] Filling cost = filled × the charge at dispatch (not the current charge)
- [ ] Factory payments show per factory and month; paying a month marks it Paid
- [ ] Suppliers & Factories page shows bottles at each factory and amount owed
- [ ] Accountant can view, not change

**Notes & comments**

| Date | By | Comment |
|---|---|---|
| | | |

---

### M11 – Billing & payments

| Item | Detail |
|---|---|
| Branches | `feature/M11-billing-server` → then `feature/M11-billing-client` |
| Depends on | M05, M06 |
| BRD | FR-47, FR-48, FR-49, FR-50, FR-51, FR-52, BR-09 |
| Prototype | `billing/billing.html`, customer panel Payments/Invoices tabs |
| Design | 4.3 (Billing), **5.6**, 6 (Billing), 7.2 (Billing), 10 (receipt, invoice, statement) |

**Goal:** customer accounts with aging, payments with receipts, statements, monthly invoices, and cash collected.

**Key logic & rules**
- **Customer accounts (FR-48, FR-51):** one row per customer with a non-zero balance: customer, pays, limit, **0–30 / 31–60 / 61–90 (amber) / 90+ (red)**, balance ("over limit" in red for Credit/Monthly when balance > limit), actions Receive / Statement. KPIs: total outstanding and each bucket (90+ highlighted). Filters: search (name, code, phone), payment type, show (all with a balance / over credit limit / older than 30 days). Totals row.
- **Aging (design 5.6):** charges = txns with net (amount − paid) > 0 by date; credits = payments + txns with net < 0; credits settle the **oldest charges first**; remaining by days since the bill date; over-payment = negative balance (advance) shown as such.
- **Receive payment (FR-50, FR-47 later cash):** customer, date ≤ today, amount > 0 (default = balance), method Cash / Bank transfer / Cheque; reference required for Bank transfer ("Enter the bank reference.") and Cheque ("Enter the cheque number."); cheque bank and date optional; link to an invoice (Monthly bill customers, latest default); note. Shows balance now, paying, balance after; "Part payment" when amount < balance; "advance (credit balance)" when more. Number `RC-00001`; `balance_before` stored; account updated; audit "… – part payment". After saving: "Payment saved – Rs. 2,000.00 received from …. Balance now …" with **Print receipt**.
- **Recent payments and cash collected (FR-47):** latest 15: payments and cash paid on bills ("Cash on delivery – bill B000278").
- **Statement (FR-52):** customer, from (default start of last month) / to (today) → PDF: brought forward, each charge/payment with running balance, balance due, aging line, "Bottle deposits are not refundable."
- **Monthly invoices (FR-49):** month selector (current and 3 previous; default last month). Table for Monthly bill customers: deliveries in the month, this month (Σ amount − paid on bills), previous balance, payments in the month, total due, invoice status (Generated + number / Not generated), View/Print. **Generate** creates invoices for customers without one (skips "Not a Monthly bill customer", "Invoice already exists", "No deliveries and nothing due") – confirm dialog; number `INV-YYMM-0001`; invoice date = today; due = date + terms. **One invoice per customer per month.** Invoice PDF: lines per delivery (date, bill, description "Water 20L Bottle (left at door)", qty, unit price, amount), deposits, damage replacement, "Paid on delivery" negatives, previous balance, payments, this month, total due by due date, "VAT not applied (to be confirmed)". After generation → WhatsApp invoice message queued (M13).
- **Accountant** has full access here.

#### SERVER tasks
- [ ] **S1 – Migration `V010__billing.sql`:** `customer_payment`, `monthly_invoice` (unique customer+period), `invoice_txn`; sequences `receipt`, `invoice` (monthly).
- [ ] **S2 – PaymentService + endpoints** (`POST /payments`, `GET /payments`); customer history includes payments.
- [ ] **S3 – AgingService** (FIFO) + `GET /accounts` (filters, paging, totals) + `GET /accounts/summary`.
- [ ] **S4 – Recent payments & cash collected** endpoint.
- [ ] **S5 – InvoiceService:** `GET /invoices/preview?period`, `POST /invoices/generate`, `GET /invoices?period`, invoice detail; event for WhatsApp (used in M13).
- [ ] **S6 – PDFs:** receipt, invoice, statement (`/print/receipt/{id}`, `/print/invoice/{id}`, `/print/statement/{customerId}?from&to`).
- [ ] **S7 – Seed (local/UAT):** August invoices and payments history from the prototype.
- [ ] **S8 – Tests:** aging FIFO cases (partial, advance, reversal credit, 90+), part payment, reference rules, invoice totals = statement closing, duplicate invoice blocked, Accountant allowed, Delivery Staff 403.

#### CLIENT tasks
- [ ] **C1 – Billing & Payments page:** action **+ Receive payment**; tabs **Customer accounts · Monthly invoices**; URL params `?pay=<id|new>`, `?statement=<id>`, `?invoice=<id>`.
- [ ] **C2 – Customer accounts tab:** KPIs, filters, table with aging columns and actions, totals row; customer name opens the shared `CustomerPanel` (read-only actions by role).
- [ ] **C3 – Receive payment pop-up:** customer picker, date, amount (default balance), method with conditional reference/bank/cheque date, invoice link, note; side summary (balance now / paying / after, part payment banner); success dialog with **Print receipt**.
- [ ] **C4 – Statement pop-up:** from/to → opens the statement PDF.
- [ ] **C5 – Monthly invoices tab:** month select, table, **Generate invoices** (confirm, result toast), View / Print.
- [ ] **C6 – Recent payments & cash collected** list.
- [ ] **C7 – Customer panel:** Payments and Invoices tabs filled; Receive payment and Statement actions live; dashboard "Receive payment" quick button.

**Acceptance checklist**
- [ ] Aging buckets match the hand-calculated reference customer (incl. a 90+ amount)
- [ ] Part payment by cheque requires the cheque number; balance and aging update; receipt prints
- [ ] Statement closing balance equals the account balance
- [ ] Generating September invoices creates one per Monthly bill customer; running again skips them
- [ ] Invoice PDF lists every delivery in the month with correct totals
- [ ] Accountant can do everything here; Delivery Staff cannot open it

**Notes & comments**

| Date | By | Comment |
|---|---|---|
| | | |

---

### M12 – Dashboard & reports

| Item | Detail |
|---|---|
| Branch | `feature/M12-dashboard-reports` |
| Depends on | M04–M11 |
| BRD | Section 8 (dashboard + 10 reports), FR-31, FR-05 (agreed-price comparison), FR-63 (expenses in Profit) |
| Prototype | `dashboard/dashboard.html`, `reports/reports.html` |
| Design | 6 (Dashboard, Reports), 7.2 (Reports), 10 (exports), 11 |

**Goal:** the dashboard and the ten reports (incl. Expenses – built with M15) with date range, PDF and Excel export; plus a demo-data generator for realistic history on local/UAT.

**Key logic & rules**
- **Dashboard (Admin, Accountant):** quick buttons by permission (Enter bills, Receive payment); low-stock banner; KPIs (each links to its page only if the role may open it): Sales today (bills count), Collected today (cash on bills + payments), Filled 20L in store (alert when < minimum), Bottles at factory, Deliveries today "entered of due", Empties to collect, Bottles owed, Outstanding. **Today's deliveries** table (customer, area, usual, status Entered/Missed/Due). **Needs attention:** quotation requests waiting for a choice; draft POs waiting for approval; POs with goods to receive; monthly invoices for last month not generated (count); customers over the credit limit; customers owing for more than 90 days; factory batches with bottles short. **Sales – last 14 days** bar chart.
- **Reports page:** report selector (filtered by role: Accountant sees Sales, Purchasing, Outstanding & aging, Profit), date range (from/to + quick: Today, Last 7 days, This month, Last month, Aug–today), Apply, **Export PDF**, **Export Excel**. Each report shows KPIs + tables (+ chart for Sales).

| Report | KPIs | Tables |
|---|---|---|
| Sales | Total, water, deposits, products, bills | By day (chart); by sale type (First purchase, Exchange incl. left at door, Extra order, Product, Bottle replacement, Bottles owed deposit); by customer type; by month; top 15 customers – each with bills, water, deposits, products, total |
| Purchasing | POs, PO value (excl. cancelled), goods received, owed to suppliers | PO lines ordered vs received vs damaged, value, status; quotations by request & supplier (total, delivery, chosen + reason) |
| Production | Batches, sent, filled back, rejected, filling cost | Batches (factory, sent, bottle, qty, filled, rejected, at factory, cost) |
| Stock | – | Bottle stock by status **at the end date** (current minus later movements); product stock |
| Bottle movement | 20L in circulation, with customers, to collect, owed | 20L flows in range (delivered, collected, new bottles to customers, old American Water taken in, left at door, new owed); customers with to collect / owed |
| Damage & write-off | Written off, company, customer | Damaged (not reversed) + lost adjustments: date, bottle, qty, responsibility, where, reason |
| Outstanding & aging | Total outstanding at end date, over 90 days | By payment type (buckets); by customer (buckets) |
| Profit | Revenue, filling cost, bottle cost, product cost, gross profit | By bottle type + other products: revenue (water + deposits), filling (returns in range), bottles bought (GRN value), product cost, profit; note "Deposits count as revenue; staff, transport and overheads are outside the system." |
| Delivery | Due, entered, left at door | By day (Mon–Sat): due, entered, left at door, not entered (names) |

- **Exports:** PDF (company header, report name, range, same tables) and Excel (one sheet per table, formatted numbers and dates).

#### SERVER tasks
- [ ] **S1 – Dashboard endpoint** `GET /dashboard` (single round trip, aggregate queries).
- [ ] **S2 – Report services** for the 10 reports (Sales adds "customers on agreed prices – billed vs standard"; Profit adds running expenses and net profit) (`GET /reports/{name}?from&to`) with role checks.
- [ ] **S3 – Export** `GET /reports/{name}/export?format=pdf|xlsx` (PDF via `PdfRenderer`, Excel via Apache POI).
- [ ] **S4 – Demo-data generator (local/UAT only):** Java port of the prototype's `simulate()` (two months of deliveries, left at door, shortages, extra orders, product sales, factory batches, purchasing history, payments, invoices) using the real services, so all numbers are consistent.
- [ ] **S5 – Tests:** dashboard numbers and report totals against a known scenario; Accountant cannot run Admin-only reports; export files open and match.

#### CLIENT tasks
- [ ] **C1 – Dashboard page** (KPIs with permission-aware links, low-stock banner, today's deliveries, needs attention list, 14-day chart).
- [ ] **C2 – Reports page** (selector by role, range bar with quick ranges, KPIs, tables, sales chart, Export PDF / Excel).

**Acceptance checklist**
- [ ] Dashboard numbers match the underlying pages for today
- [ ] Needs-attention items link to the right pages (only if the role may open them)
- [ ] Each report's totals match a manual check on the demo data
- [ ] PDF and Excel exports contain the same data as the screen
- [ ] Accountant sees exactly 4 reports

**Notes & comments**

| Date | By | Comment |
|---|---|---|
| | | |

---

### M13 – WhatsApp Business (Phase 1 – Option A)

| Item | Detail |
|---|---|
| Branch | `feature/M13-whatsapp` |
| Depends on | M06, M07, M11 (hooks), M01 (settings tab) |
| BRD | FR-61, section 11 |
| Prototype | – (new; follow Administration tab style) |
| Design | 4.3 (whatsapp_message), **9.1, 9.2**, 6 (WhatsApp) |

**Goal:** send the three essential WhatsApp messages – "Left at door" notice, payment reminder, monthly invoice – reliably and with cost control.

**Client prerequisites (start early – needed before go-live)**
- [ ] Meta Business Manager account verified for Thuhina Water
- [ ] WhatsApp Business Account + dedicated phone number registered (not used in the normal WhatsApp app)
- [ ] Permanent access token (system user) and app secret
- [ ] Templates submitted and approved: `left_at_door_v1`, `payment_reminder_v1`, `monthly_invoice_v1` (document header)
- [ ] Confirm Meta's current rate for Sri Lanka (update the tech stack cost table)

**Key logic & rules**
- **Settings (Admin → WhatsApp tab):** enabled (master switch), phone number ID, business account ID, access token, app secret, webhook verify token (secrets write-only, stored AES-GCM encrypted with a key from the environment, never returned), per-message toggles (Left at door, Payment reminder, Monthly invoice), reminder schedule (default Monday 09:00), **Test send** to a number, **Verify connection**.
- **When a message is queued (outbox, same DB transaction as the business action):** WhatsApp enabled **and** that message type on **and** customer opted in **and** has a valid WhatsApp number; otherwise nothing (or SKIPPED with reason in the log).
  - **Left at door:** when a LEFT_AT_DOOR bill is saved (M06/M07) → params: customer name, bottles left ("2 × 20L"), date, next delivery date.
  - **Monthly invoice:** when an invoice is generated (M11) → params: name, month, total due, due date + invoice PDF uploaded as media.
  - **Payment reminder:** weekly job → Credit / Monthly bill customers, opted in, whose **oldest unpaid charge is older than their terms**; max one reminder per customer per 7 days → params: name, balance, due date.
- **Sender job (every minute):** takes up to 20 QUEUED messages; calls the Cloud API; success → SENT + Meta message id; error → attempts + 1, back off; after 3 failures → FAILED with error text. A Meta outage never blocks billing.
- **Webhook:** `GET` verify (hub.challenge with the verify token); `POST` checks `X-Hub-Signature-256` (HMAC SHA-256 with the app secret) → updates status DELIVERED / READ / FAILED. Public endpoint (no login), signature required.
- **Message log:** filters (type, status, date, customer), monthly count (cost control), error details.
- **Local/UAT:** a `FakeWhatsAppClient` logs messages instead of sending (default unless real credentials are configured).

#### SERVER tasks
- [ ] **S1 – Migration `V011__whatsapp.sql`:** `whatsapp_message` (+ index on status); settings group `whatsapp`.
- [ ] **S2 – Encryption util** (AES-GCM, key from env) + settings endpoints (write-only secrets).
- [ ] **S3 – Outbox service** (`enqueue(type, customer, params, source)` with the conditions above) + hooks in Left-at-door posting and invoice generation.
- [ ] **S4 – `WhatsAppClient`** interface + `MetaWhatsAppClient` (send template message, upload media document) + `FakeWhatsAppClient`.
- [ ] **S5 – Sender job** (retry/back-off, statuses).
- [ ] **S6 – Payment reminder job** (weekly, rule above).
- [ ] **S7 – Webhook endpoint** (verify + signature + status update).
- [ ] **S8 – Log & test endpoints:** `GET /whatsapp/messages`, `POST /whatsapp/test`, verify connection.
- [ ] **S9 – Tests:** enqueue conditions (each switch), sender retries/failure, webhook signature valid/invalid, reminder selection and once-per-week rule, secrets never returned.

#### CLIENT tasks
- [ ] **C1 – Administration → WhatsApp tab:** master switch, credentials (write-only fields with "saved" indicators), message toggles, reminder schedule, Test send, Verify connection.
- [ ] **C2 – Message log** (in the same tab): filters, table (time, customer, type, number, status badge, error), monthly count.
- [ ] **C3 – Customer pop-up/panel:** show WhatsApp opt-in and number (fields from M05); badge in the panel.

**Acceptance checklist**
- [ ] With the fake client: saving a Left-at-door bill for an opted-in customer logs one message; for a non-opted-in customer none
- [ ] Generating invoices queues one invoice message per opted-in Monthly bill customer with the PDF
- [ ] Reminder job selects only overdue Credit/Monthly customers and never twice in a week
- [ ] With real UAT credentials: test message received on a phone; status updates via webhook
- [ ] Switching a message type off stops new messages of that type
- [ ] Secrets are never visible in the UI or API responses

**Notes & comments**

| Date | By | Comment |
|---|---|---|
| | | |

---

### M14 – Data migration & go-live

| Item | Detail |
|---|---|
| Branch | `feature/M14-migration-golive` |
| Depends on | All modules |
| BRD | FR-53 (incl. optional agreed prices), FR-54, FR-55, NFR-01, NFR-02, NFR-03, NFR-05, NFR-06 |
| Prototype | `migration/data-migration.html` (hidden menu) |
| Design | 6 (Data migration), 7.2 (Migration), 11, 12 |

**Goal:** import the client's existing customers and opening stock, set up production, back-ups and monitoring, run UAT, and go live.

**Key logic & rules – import**
- **Customer CSV columns:** Name, Address, Area, Phone, Customer type, Delivery day, Cycle (weeks), Next delivery, Usual 20L, Usual 10L, Payment type, Credit limit, Terms (days), Bottles held 20L, Bottles held 10L, Empties to collect 20L, Bottles owed 20L, Outstanding (Rs.). Template download.
- **Row checks (FR-55):** missing columns → whole file rejected ("Missing column(s): … Please use the template."); per row: name and address required; area must exist; phone 10 digits and not already used (by an existing customer or an earlier row); customer type must exist; delivery day a weekday name; cycle 1–8; next delivery DD/MM/YYYY and on the delivery day; payment type Cash / Credit / Monthly bill; credit limit required for Credit / Monthly bill; numbers ≥ 0. Rejected rows listed with all reasons; **nothing saved until Confirm**.
- **Optional columns:** Agreed price 20L, Agreed price 10L (blank = standard; a value equal to the standard price is ignored) → `customer_price` rows from the import date, reason "Agreed price imported from <file>".
- **Import (one transaction):** customers (codes generated), bottle balances, CUSTOMERS stock (IMPORT postings), opening balance txns (OPENING_BALANCE) for outstanding amounts; `import_batch` record; audit "N customers imported (C0041 – C0090)".
- **Opening stock CSV:** Bottle type (name or code), Status (Empty in store / At factory / Filled in store / Written off), Quantity (whole number ≥ 0). "With customers" rejected ("comes from the customer import"); duplicates rejected; preview shows "Now" vs "From file"; import sets each bucket to the file value via IMPORT postings (difference).
- **Menu:** visible only when `features.dataMigration = true` (Admin). Turned off again after go-live.

**Key steps – go-live**
- **Production server:** AWS Mumbai, EC2 t4g.small, Ubuntu 24.04, 30 GB gp3, Elastic IP, security group (80/443 open; 22 from office IP only); Docker + Compose; domain `app.thuhinawater.lk` DNS; Let's Encrypt certificate with auto-renew; HSTS.
- **Configuration:** production `.env` (strong DB password, JWT secret, encryption key, S3 bucket, WhatsApp credentials); JVM heap ≈ 768 MB; Swagger off; seed scripts off; real company details and logo.
- **Back-ups (NFR-06):** cron 23:00 `pg_dump -Fc` (7 days local) → S3 (30-day lifecycle) with an IAM role; weekly EBS snapshot (Data Lifecycle Manager, 4 weeks); documented restore procedure; first restore test on UAT.
- **Monitoring:** CloudWatch alarms (CPU > 80 %, disk > 80 %), Docker health checks, log rotation (14 days).
- **Performance & browsers (NFR-01–03):** generate 10,000 customers and 3 months of bills on UAT; common screens < 3 s (target < 1 s); test Chrome, Edge, Firefox.
- **Security check (NFR-05):** HTTPS only, cookies Secure, role access tested for every page and API, default passwords changed.
- **UAT:** client tests against the BRD v4.0 checklist (incl. the list of agreed prices); issues logged in this module's comments; fixes on `fix/` branches.
- **Data:** client confirms real prices, deposits, filling charges, credit limits, areas, products, users; trial import on UAT and fix rejected rows with the client; final import on production.
- **Release:** release PR `dev → main`, tag `v1.0.0`, deploy production, smoke test (login each role, register a customer, enter bills, print), turn off the migration menu, 2 weeks of hypercare (daily check of logs, back-ups, WhatsApp log).

#### SERVER tasks
- [ ] **S1 – Migration `V012__migration.sql`:** `import_batch`.
- [ ] **S2 – Customer import:** `POST /migration/customers/validate` (preview), `POST /migration/customers/import`, template download.
- [ ] **S3 – Opening stock import:** validate + import + template.
- [ ] **S4 – Feature flag** in `/auth/me` menu; `GET /migration/batches`.
- [ ] **S5 – Tests:** every row rule, whole-file column check, transaction rollback on error, stock and balances after import, invariants.

#### CLIENT tasks
- [ ] **C1 – Data Migration page:** tabs Customers / Opening stock; file input (CSV; Excel → "save as CSV" message), Use sample file, Download template; preview (ready rows table, problem rows with reasons); Import button with confirm; result banner with link.

#### GO-LIVE tasks
- [ ] **G1 – Production AWS setup** (EC2, IP, security group, DNS, Docker, SSL)
- [ ] **G2 – Production configuration & deploy pipeline** (env, secrets, compose, tag deploy)
- [ ] **G3 – Back-ups & restore test** (cron, S3, snapshots, restore document)
- [ ] **G4 – Monitoring & logging** (CloudWatch alarms, health checks, rotation)
- [ ] **G5 – Performance, browser & security checks** (10,000 customers, 3 browsers, access matrix)
- [ ] **G6 – UAT with the client** (BRD checklist, fix list, sign-off)
- [ ] **G7 – Real master data & trial import on UAT**, then final import on production; real users created
- [ ] **G8 – Release v1.0.0** (dev → main, tag, deploy, smoke test, migration menu off, hypercare)

**Acceptance checklist**
- [ ] Sample customer file: valid rows imported, rejected rows listed with reasons, nothing saved before Confirm
- [ ] Opening stock import sets the buckets; "With customers" rejected
- [ ] Production reachable over HTTPS only; certificate auto-renews
- [ ] Nightly back-up appears in S3; restore test passed
- [ ] 10,000-customer performance test within targets
- [ ] Client UAT signed off; `v1.0.0` tagged and deployed

**Notes & comments**

| Date | By | Comment |
|---|---|---|
| | | |

---

### M15 – Expenses (client feedback 08/10/2026)

| Item | Detail |
|---|---|
| Branch | `feature/M15-expenses` |
| Depends on | M01 |
| BRD | FR-62, FR-63, BR-19, section 8 (Profit, Expenses report) – added in BRD v4.0 (decision D7) |
| Prototype | `expenses/expenses.html`; Reports → Expenses and Profit |

**Goal:** a separate page to keep the business's running expenses month by month (electricity, water bill, salaries, rent, vehicle fuel, vehicle spare parts & repairs, telephone, stationery, other) and show them in the Profit report.

**Key logic & rules**
- **Expense:** number `EX-0001`, date paid (≤ today), **for month** (the month the expense belongs to – e.g. September electricity paid on 02/10 counts for September), expense type, description, paid to, bill/account no., amount > 0, method Cash / Bank transfer / Cheque, reference required unless Cash.
- **Expense types:** managed in a pop-up; add new, activate/deactivate (inactive types hidden when adding, history kept). Seed: Electricity, Water bill, Salaries, Rent, Vehicle fuel, Vehicle spare parts & repairs, Telephone & internet, Stationery & printing, Other.
- **Edit** any field; **delete** needs a reason and is written to the audit log.
- **Page:** month selector, type filter, search; KPIs (month total, previous month and difference, entries, biggest type); tabs **Monthly expenses** (list with total + by-type bars) and **Month by month** (type × last 6 months, click a month to open it, Excel export); **Print month**; "Save & add another" for fast entry.
- **Reports:** new **Expenses** report (by type and month, all entries); **Profit** report shows Gross profit − running expenses = **Net profit**, counted for whole months from the From month to the To month.
- **Dashboard:** to-do "No expenses entered for <last month>" when the previous month is empty.
- **Roles:** Admin and Accountant full access.

#### SERVER tasks
- [ ] **S1 – Migration:** `expense_type`, `expense` (for_month as `yyyy-mm` / first day of month); sequence `expense`.
- [ ] **S2 – Service + endpoints:** CRUD with validations, delete with reason (audit), types CRUD, `GET /expenses/summary?from=&to=` (type × month).
- [ ] **S3 – Profit and Expenses reports** use the summary; seed (local/UAT) Aug–Oct 2026 from the prototype.
- [ ] **S4 – Tests:** validation messages, for-month vs paid date, delete audit, report totals.

#### CLIENT tasks
- [ ] **C1 – Expenses page** (month selector, filters, KPIs, two tabs, print, Excel).
- [ ] **C2 – Add / edit pop-up** and **Expense types pop-up**.
- [ ] **C3 – Reports:** Expenses report; Net profit section in Profit.

**Acceptance checklist**
- [ ] A bill paid in October can be entered for September and shows in September's total
- [ ] Cheque / bank transfer without a reference is rejected
- [ ] Deleting asks for a reason and appears in the audit log
- [ ] Profit report shows running expenses and net profit for the chosen months

**Notes & comments**

| Date | By | Comment |
|---|---|---|
| 08/10/2026 | Amritha | Added to the prototype from client feedback item 2 |

---

### M16 – Customer quotations (client feedback 08/10/2026)

| Item | Detail |
|---|---|
| Branch | `feature/M16-customer-quotations` |
| Depends on | M02, M05 (incl. agreed prices) |
| BRD | FR-64, FR-65, FR-66, BR-20 – added in BRD v4.0 (decision D9) |
| Prototype | `sales/quotations.html` (menu Sales & Deliveries → Customer Quotations) |

**Goal:** send price quotations to customers and to new organisations (government offices, medium-size factories, hotels, hospitals), the same way supplier quotations are handled but outgoing.

**Key logic & rules**
- **To:** an existing customer (picker) or a new organisation (name, contact person, designation, address, area, phone, email). **Customer type** chooses the standard prices.
- **Number** `QT-2026-001`; date ≤ today; valid until ≥ date (default +30 days); subject (e.g. tender reference); delivery text; payment terms (Cash on delivery, 7, 14, 30, 45, 60 days); terms & conditions (default text, editable, one per line).
- **Lines:** water per bottle type, bottle deposit per bottle type, products, or free text; description, qty ≥ 1, unit, unit price. Price **starts at the customer's current price** (agreed or standard – Pricing Service) or, for a new organisation, the standard price for the chosen type, and follows it until changed by hand; the current price is shown beside a changed price and lines below it are flagged.
- **Status:** Draft → Sent (by Email / By hand / Post / WhatsApp, date) → Accepted or Not accepted (reason required). A Sent quotation past its valid-until date shows **Expired** (can still be answered). Only Draft can be edited; **Make a copy** creates a new draft (history notes "Copy of …").
- **Print / PDF** (company header, To, subject, lines, total, delivery, terms, conditions, signatures) and **Email** (opens the email program with subject and summary; PDF attached by the user – sending from the server is a later option).
- **Accepted + new organisation → Register as customer:** opens New customer pre-filled (name, phone, address, area, type, notes **and the quoted water prices as agreed prices**) and links the customer to the quotation. **Accepted + existing customer → Apply prices:** the quoted water prices become the customer's agreed prices from a date (M05 service); `prices_applied_on` recorded.
- **Dashboard to-dos:** drafts not sent, sent quotations within 7 days of expiry, accepted but not registered. Customer side panel has a **Quotation** button.
- **Roles:** Admin full; Accountant view only.

#### SERVER tasks
- [ ] **S1 – Migration:** `customer_quotation`, `customer_quotation_line`, `customer_quotation_history`; sequence.
- [ ] **S2 – Service + endpoints:** create/edit draft, copy, status changes (next step only), link customer, **apply prices** (calls the M05 agreed-price service); PDF generation.
- [ ] **S3 – Seed + tests:** the five sample quotations; status rules, validations, expiry, link on registration.

#### CLIENT tasks
- [ ] **C1 – Customer Quotations page:** KPIs, search, status filter, list with next step.
- [ ] **C2 – New / edit / copy pop-up** with line table and live total.
- [ ] **C3 – View pop-up:** steps bar, details, history, buttons by status; mark-as-sent dialog; print; email.
- [ ] **C4 – Register as customer** pre-fill on the Customers page; Quotation button in the customer panel; dashboard to-dos.

**Acceptance checklist**
- [ ] A quotation to a new government office can be printed and marked as sent by email
- [ ] Changing customer type re-prices lines not changed by hand
- [ ] A sent quotation past its date shows Expired
- [ ] Accepting and registering creates the customer, links it to the quotation and sets the quoted water price as the agreed price
- [ ] "Apply prices" on an accepted quotation for an existing customer changes their price from the chosen date only
- [ ] Accountant can view and print, not change

**Resolved question**
- ~~Q1: how is an accepted lower price billed?~~ → it becomes the customer's **agreed price** (decision D11).

**Notes & comments**

| Date | By | Comment |
|---|---|---|
| 08/10/2026 | Amritha | Added to the prototype from client feedback item 4 |

---

## 7. Milestones & release plan

| Milestone | Modules | Merge dev → main | Tag | Client demo |
|---|---|---|---|---|
| R1 – Foundation & master data | M00–M05 | After demo sign-off | `v0.1.0` | Login, roles, admin, master data, suppliers/factories, stock, customers |
| R2 – Sales & deliveries | M06–M08 | After demo sign-off | `v0.2.0` | Registration with bottles, sales, Enter Bills, delivery planning & lists |
| R3 – Purchasing, production & billing | M09–M11, M15, M16 | After demo sign-off | `v0.3.0` | Quotations → PO → receipt, factory batches, payments, invoices, aging |
| R4 – Reports, WhatsApp, go-live | M12–M14 | After UAT sign-off | `v1.0.0` | Dashboard, reports, WhatsApp, migration, production go-live |

Before go-live there is no production server yet: `main` holds the last client-approved milestone. From `v1.0.0`, every tag on `main` is deployed to production.

---

## 8. Decision log

| # | Date | Decision | Source |
|---|---|---|---|
| D1 | 04/10/2026 | Paper bill number kept on daily bill entry for the duplicate check (BR-13) | Client |
| D2 | 04/10/2026 | WhatsApp in Phase 1 – Option A only (left at door, payment reminder, monthly invoice) | Client |
| D3 | 04/10/2026 | ~~No special customer prices (FR-05 removed)~~ – replaced by D11; first delivery = very next delivery day | Client |
| D4 | 05/10/2026 | Backend module-first structure; no `ServiceImpl` by default | Client |
| D5 | 05/10/2026 | Break down by module, each split into Server and Client parts; one branch per module (split branches for M00, M06, M07, M11) | Client |
| D6 | 05/10/2026 | Branching: feature → dev (UAT) → main (production, tagged); no separate prod branch; hotfix from main | Client / Amritha |
| D7 | 08/10/2026 | New Expenses page (month-wise running expenses) – new module M15; Profit report shows net profit after expenses | Client |
| D8 | 08/10/2026 | Send empties: one entry sends every bottle type with its quantity (dispatch FD-… with one batch per bottle type) | Client |
| D9 | 08/10/2026 | Customer quotations for government offices, factories and other organisations – new module M16 | Client |
| D10 | 08/10/2026 | Customer status (Active / Inactive with date and reason) on the customer edit pop-up and each list row | Client |
| D11 | 08/10/2026 | **Agreed customer prices**: standard price list by bottle type × customer type stays the default; Admin sets a dated agreed price per customer and bottle type (from, optional until, reason); bills store the price used. Accepted quotations set agreed prices. Automatic volume price bands not in Phase 1. One deposit for all customers. BRD v4.0 (FR-05, BR-08, BR-18) | Client / Amritha |
| D12 | 08/10/2026 | Supplier quotation number renamed `SQ-0001` so customer quotations can use `QT-2026-001` | Amritha |
| D13 | 08/10/2026 | Documents updated to BRD v4.0, Architecture v1.1, Tech stack v1.1; old versions removed from `doc/` (only the latest kept) | Client |
| D14 | 09/10/2026 | One GitHub repo `thuhina-water` (monorepo: server, client, deploy, doc, prototype). Integration branch named `dev` (not `develop`). `main` and `dev` created and pushed | Amritha |
| | | | |

---

## 9. Change log of this document

| Version | Date | Change | By |
|---|---|---|---|
| 1.0 | 05/10/2026 | First version: branching strategy, workflow, tracker, global rules, modules M00–M14, milestones, decision log | Amritha |
| 1.1 | 08/10/2026 | Client feedback: customer status (M05), multi-bottle dispatch (M10), new M15 Expenses and M16 Customer quotations; decisions D7–D10 | Amritha |
| 1.2 | 08/10/2026 | Agreed customer prices (M02 Pricing Service, M05 S10–S11 / C7–C8, M06, M12, M14, M16); Q1 resolved; references moved to BRD v4.0 / Architecture v1.1; decisions D11–D13 | Amritha |
| | | | |
