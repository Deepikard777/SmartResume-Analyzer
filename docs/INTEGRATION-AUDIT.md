# SmartResume Analyzer — Integration Audit & Compatibility Report

> **HISTORICAL DOCUMENT — SUPERSEDED.** This is the read-only audit of the project *as it was received*, written
> **before** any modification. Its findings about authentication (F3, F4), ownership and public endpoints described
> the state at that time and have since been implemented.
>
> For the current state of the project read [../README.md](../README.md) and
> [ARCHITECTURE.md](ARCHITECTURE.md). The pre-authentication audit and the ordered plan that followed it are in
> [REVIEW-AUDIT.md](REVIEW-AUDIT.md).

**Date:** 2026-10-08
**Scope:** Read-only audit of (a) the Stitch UI deliverable and (b) the existing Spring Boot backend, performed **before** any modification.
**Rule applied:** No project was recreated, no UI redesigned, no backend architecture replaced.

---

## 0. Headline findings (read this first)

| # | Finding | Impact |
|---|---------|--------|
| F1 | **The "Stitch frontend" is not a frontend application.** It is 3 static HTML prototypes (+1 SVG logo +1 design-token doc). There is **no** React, Vite, TypeScript, JS, `package.json`, router, state management, or service layer. | A real app must be scaffolded and the Stitch HTML ported into components. This is *porting*, not *redesigning*. |
| F2 | **The backend has no analysis or job-description REST API.** Entities, repositories, DTOs and the complete deterministic analysis engine exist, but `AnalysisService`/`JobDescriptionService` and `AnalysisController`/`JobDescriptionController` do not. | Analysis/JD endpoints must be added — minimally, in the existing style. |
| F3 | **The backend has no token-based authentication.** `POST /api/auth/login` returns `{message, user}` — no JWT, no session, no cookie. Security is `STATELESS`. | The frontend cannot obtain an auth token. Auth must be integrated as it actually is; this is a documented limitation, not something to fake. |
| F4 | **`anyRequest().authenticated()` blocks any new endpoint** because there is no working authentication mechanism (`UserDetailsService` throws by design). | `/api/job-descriptions/**` and `/api/analyses/**` must be added to `permitAll`, otherwise they 401/403. |
| F5 | **No AI recommendation capability exists anywhere** (no LLM client, no AI endpoint). | The Results page's "AI Recommendations" tab must show an honest placeholder, not fabricated AI output. |
| F6 | **MySQL is the default datastore but nothing is listening on `localhost:3306`.** | The backend cannot start with the default profile on this machine. See §7. |
| F7 | **Stitch provides no Login/Register, Analysis History, or Settings screens**, although its top nav links to "Analysis History" and "Settings". | These 4 screens must be built in the existing design language. |
| F8 | The project is **not a git repository** and has **no README**. | No version-control safety net; back up before editing. |

---

## 1. Frontend inventory

**Source archive:** `C:\Users\DEEPIKA\Downloads\stitch_smartresume_ai_analyzer_platform.zip`
**Extracted to:** `_stitch_source/stitch_smartresume_ai_analyzer_platform/` (kept as the design reference; nothing was overwritten)

### 1.1 Contents

| File | Lines | What it is |
|------|-------|-----------|
| `dashboard_smartresume_analyzer/code.html` | 396 | Dashboard prototype |
| `new_analysis_input_smartresume_analyzer/code.html` | 352 | New Analysis prototype (upload + JD form) |
| `analysis_results_smartresume_analyzer/code.html` | 705 | Results prototype (5 metrics, tabs, recommendations) |
| `smartresume_analyzer_logo/code.html` | 7 | Inline SVG logo (indigo rounded square, document + emerald check) |
| `smartresume_analyzer_design_system/DESIGN.md` | 219 | Design tokens + brand/style/component rules |
| `*/screen.png` | — | Rendered screenshots of each page |

### 1.2 Framework / tooling

| Aspect | Finding |
|--------|---------|
| Framework | **None.** Plain static HTML. |
| React / Vite / TypeScript | **Absent.** |
| `package.json` | **Absent.** |
| Routing | **Absent.** Nav links are `href="#"` with a `data-path` attribute (`dashboard`, `new-analysis`, `analysis-history`, `settings`). |
| Reusable components | **Absent** (inline Tailwind markup only). |
| API / service files | **Absent.** |
| State management | **Absent.** |
| Authentication UI | **Absent.** No login/register screen in the Stitch deliverable. |
| Upload UI | Present as markup only — dropzone + file input + "parsed" preview card with **hardcoded** file name/size/word count. |
| Analysis UI | Present as markup only; the Analyze button runs a fake 1600 ms `setTimeout` spinner. |
| Dashboard | Present. |
| Results page | Present. |
| History page | **Absent** (only a "Recent Job Analyses" list card inside the dashboard). |
| Settings / profile | **Absent** (nav link only). |
| Loading / error states | Two fake ones: the analyze spinner, and a toast helper `triggerToast()`. No real error states. |

### 1.3 Styling system (must be preserved)

* Tailwind loaded from **CDN** (`https://cdn.tailwindcss.com`) with an inline `tailwind.config` extending colors, radii, spacing and font families.
* Tokens come from `DESIGN.md`: primary `#4F46E5` / `#3525cd`, success-match emerald `#10B981`, warning amber `#F59E0B`, critical rose `#F43F5E`, canvas `#F8FAFC`, borders `#E2E8F0`.
* Fonts: **Plus Jakarta Sans** (display/headlines), **Inter** (body), **JetBrains Mono** (code/badges).
* Custom spacing tokens used throughout: `gutter`, `gutter-mobile`, `margin`, `margin-mobile`, `space-xs|sm|md|lg|xl`.
* Custom font-size tokens: `display`, `headline-lg`, `headline-lg-mobile`, `headline-md`, `headline-sm`, `body-lg`, `body-md`, `body-sm`, `label-code`, `label-badge`.
* Radius cap `0.5rem` on cards; hairline `1px` borders instead of shadows.
* Icons: Google **Material Symbols Outlined**.
* Shared chrome on every page: fixed 64px header (logo + 4 nav tabs + "Analyze Resume" CTA + notification bell + user chip) and a footer reading `SmartResume Core Engine v2.4`.

> **Preservation rule adopted:** the port keeps the exact token set, class structure, palette, typography and layout. CDN Tailwind is replaced with a real Tailwind build only because a production app needs it; the emitted design is the same.

### 1.4 Hardcoded / fake data inventory (to be replaced by API data)

| Location | Fake value |
|----------|-----------|
| Dashboard KPI 1 | `Resumes Analyzed: 14`, `+3 this week`, "Active in parsing pipeline" |
| Dashboard KPI 2 | `Average ATS Score: 79.4 / 100`, `+6.2 pts`, "Elevated from 73.2 baseline" |
| Dashboard KPI 3 | `Highest Match Score: 92`, `Frontend Dev @ Stripe` |
| Dashboard KPI 4 | `Critical Gaps Resolved: 8 Skills` |
| Dashboard highlight card | Job `Full Stack Software Engineer`, tag `FinTech Core`, `Matched v3.1` |
| Dashboard dial | `ATS 82`, ring offset hardcoded, label `Strong Compatibility` |
| Dashboard bars | Skills `84%`, Keywords `87%`, Structure `91%` |
| Dashboard callout | "Missing Spring Boot & Docker keyword instances…" |
| Dashboard recent list | 4 rows: `Full Stack Engineer/FinTech Core/Today/82/Strong`, `Software Engineer I/CloudScale AI/2 days ago/88/Exceptional`, `Junior Backend Developer/PayLogic/Oct 12/68/Needs Work`, `Web Developer/NextGen Apps/Oct 08/74/Fair`; "Showing 4 of 14" |
| Dashboard right rail | Missing skills `Docker (9 Target JDs)`, `AWS (ECS/S3) (8)`, `Spring Boot (6, Partial)`, `Kubernetes (4)` |
| Dashboard tips | Static prose tips + a "Recommended Phrasing Diff" example |
| Header (all pages) | User `Alex Chen`, role `CS Senior` |
| New Analysis | "Manage Vault (3)"; dropdown options `Spring 2025 General`, `Alex_Chen_Backend_Focused_v2.pdf (198 KB)`, `Alex_Chen_Quantitative_Dev_2024.pdf (310 KB)` |
| New Analysis | File preview `Alex_Chen_Software_Resume_2025.pdf`, `245.8 KB • 1 Page • 488 Words`, "Parsed successfully" |
| New Analysis | Parsed entity tags `Java (Spring)`, `TypeScript`, `PostgreSQL`, `Docker`, `+14 more tags` |
| New Analysis | Baseline card `Alex Chen Portfolio`, `Last checked 3 days ago: 88.4 Match`, hardcoded sparkline bars |
| New Analysis | Prefilled `Job Title: Full Stack Software Engineer`, `Company: FinTech Core Inc.`, prefilled JD text, `1,420 / 10,000 characters`, "8 Key Competencies Detected (Java, TypeScript, React, SQL, Spring Boot, Docker, AWS, Testing)" |
| New Analysis | `Model 4.2 • High Sensitivity`, "Estimated analysis time: ~5 seconds" |
| Results | File `Alex_Chen_Software_Resume_2025.pdf (184 KB)`; job `Full Stack Software Engineer (New Grad)` at `FinTech Core` |
| Results | `ATS Engine 4.2.1`, `Target Match Threshold: 75%`, `Qualified for Interview Screening` |
| Results | Dial `82`, inner ring `84%`, legend `Overall ATS Alignment (82%)` / `Direct JD Resonance (84%)`, `Top 15% Applicants` |
| Results | 5 metric cards: Skills `84%` "16 of 19 tech skills match", Keywords `87%`, Relevance `76%`, Structure `91%`, Parsing `88%` |
| Results | Tab badges: Skill Match `84%`, Keyword Density `1 Alert` |
| Results | Matched chips: `Java, SQL, React, Git, REST APIs, TypeScript, PostgreSQL, JUnit`; "16 / 19 Matched"; "Weight: +45 pts" |
| Results | Missing chips: `Spring Boot (Critical)`, `Docker (Desired)`, `AWS (Cloud)` with tooltips naming JD mention counts |
| Results | Bonus chips: `GraphQL`, `TailwindCSS`, `CI/CD Pipelines` |
| Results | Stuffing: `Medium Risk`, `"Java" appears 11 times` |
| Results | Keyword parity: `REST APIs Resume 3 • JD 4`, `Microservices 0 • 3`, `Distributed Systems 1 • 2`, `Spring Boot 0 • 5` |
| Results | Structure audit: `5 Passed / 2 Warnings` with per-section prose |
| Results | 3 recommendation cards (`+6`, `+4`, `+3 ATS Pts`) with fabricated rewrite snippets and an "Auto-Patch" button |

---

## 2. Backend inventory

**Location:** `resume-analyzer/resume-analyzer/resume-analyzer/` (three nested levels below the workspace root; left in place — see §8)

| Aspect | Finding |
|--------|---------|
| Build | Maven, wrapper present (`mvnw`, `mvnw.cmd`) |
| Framework | Spring Boot **3.5.5** (`spring-boot-starter-parent`) |
| Java | `java.version = 17` (local JDK is 24.0.1 — compiles to release 17) |
| Packages | `com.resumeanalyzer`: `config`, `controller`, `dto`, `entity`, `exception`, `repository`, `service`, `service.analysis` |
| Dependencies | `web`, `data-jpa`, `validation`, `security`, `pdfbox 3.0.4`, `poi-ooxml 5.4.0`, `mysql-connector-j` (runtime), `lombok`, `spring-boot-starter-test`, `spring-security-test`, `h2` (test) |

### 2.1 Present

**Controllers**
* `AuthController` → `POST /api/auth/register` (201), `POST /api/auth/login` (200)
* `ResumeController` → `POST /api/resumes/upload` (201, multipart), `GET /api/resumes` (200), `GET /api/resumes/{id}` (200), `DELETE /api/resumes/{id}` (204)

**Services** — `UserService`, `ResumeService`, `FileStorageService`, `ResumeFileValidationService`, `ResumeTextExtractionService`
**Analysis engine** (`service.analysis`, all present and complete) — `SkillDictionary`, `SkillMatcher`, `KeywordMatcher`, `KeywordStuffingDetector`, `ResumeStructureAnalyzer`, `FormattingAnalyzer`

**Entities** — `User`, `Resume` (FK `user_id` **nullable**, `extractedText` LONGTEXT, `UploadStatus`), `JobDescription` (`jobTitle`, `companyName`, `descriptionText`), `ResumeAnalysis` (ATS + 5 sub-scores, matched/missing/optional skills JSON, matched/missing keywords JSON, keyword frequencies JSON, stuffing risk + flagged JSON, structure checks JSON, `AnalysisStatus`)
**Repositories** — `UserRepository`, `ResumeRepository`, `JobDescriptionRepository`, `AnalysisRepository` (incl. `findAllByOrderByCreatedAtDesc`)
**DTOs** — `AuthResponse`, `UserResponse`, `LoginRequest`, `RegisterRequest`, `ResumeResponse`, `ResumeDetailResponse`, `CreateJobDescriptionRequest`, `JobDescriptionResponse`, `AnalyzeResumeRequest`, `AnalysisResponse` (+ nested `ScoreBreakdown`, `KeywordStuffingInfo`, `FlaggedKeyword`, `StructureCheck`), `AnalysisSummaryResponse`, `ErrorResponse`
**Exceptions** — `GlobalExceptionHandler` + `InvalidCredentials`, `UserAlreadyExists`, `ResumeNotFound`, `ResumeFileValidation`, `ResumeParsing`, `FileStorage`, `JobDescriptionNotFound`, `AnalysisNotFound`, `AnalysisException`
**Config** — `SecurityConfig`, `application.properties`, `application-test.properties`

> Note: `AnalysisResponse` is explicitly documented as "Designed to provide all data needed by the future Stitch Results page", and `ResumeAnalysis`'s javadoc references "a Jackson ObjectMapper in AnalysisService" — confirming the analysis layer was scaffolded but the orchestrator + controllers were never written.

### 2.2 Missing

| Missing | Consequence |
|---------|-------------|
| `JobDescriptionService` | No way to create/list/get/delete JDs |
| `JobDescriptionController` | `/api/job-descriptions/**` does not exist |
| `AnalysisService` (orchestrator that runs the 6 engine components and persists `ResumeAnalysis`) | No analysis possible |
| `AnalysisController` | `/api/analyses/**` does not exist |
| `permitAll` for the two new path prefixes | New endpoints would be rejected by `anyRequest().authenticated()` (F4) |
| Token/session issuance | No real authentication possible (F3) |
| Any AI/LLM integration | No AI recommendations possible (F5) |

### 2.3 Security configuration (as-is)

```
SecurityFilterChain:
  .cors(withDefaults())
  .csrf(disabled)
  .sessionManagement(STATELESS)
  .authorizeHttpRequests:
      /api/auth/**    -> permitAll
      /api/resumes/** -> permitAll   ("User ownership will be enforced once JWT authentication is implemented")
      anyRequest()    -> authenticated
UserDetailsService: throws UsernameNotFoundException("Use custom auth endpoints")
PasswordEncoder: BCryptPasswordEncoder
```

* **JWT: absent.** Login contract is `{ "message": "...", "user": { id, name, email } }`.
* **Password hashing: present** (BCrypt). **Password leakage: none** — `UserResponse` has no password field, enforced by tests.
* **Ownership: not enforced.** `ResumeService.uploadResume` explicitly persists `user(null)`.
* **CORS: already explicit** — origins `http://localhost:5173` and `http://localhost:3000`, methods `GET/POST/PUT/DELETE/OPTIONS/PATCH`, headers `Authorization, Content-Type, Accept, X-Requested-With, Origin`, `allowCredentials=true`, `maxAge=3600`. No wildcard. **No change required**, provided the Vite dev port is 5173.

### 2.4 Database configuration

| Profile | URL | Schema |
|---------|-----|--------|
| default (main) | `jdbc:mysql://localhost:3306/resume_analyzer?...` (`DB_URL`/`DB_USERNAME`/`DB_PASSWORD` overridable) | `ddl-auto=update` |
| `test` | `jdbc:h2:mem:resume_analyzer;MODE=MySQL` | `create-drop` |

Upload limits: `resume.max-file-size=10MB`, `resume.storage.directory=./uploads/resumes` (tests: `target/test-uploads`).

### 2.5 Test configuration and current status

| Test class | Methods | Covers |
|-----------|---------|--------|
| `AuthControllerIntegrationTest` | 9 | register (success/duplicate/validation), BCrypt hashing, no password leak, login (success/wrong password/unknown email/validation) |
| `ResumeControllerIntegrationTest` | 15 | PDF/DOCX upload, wrong type, oversize, empty, extraction, persistence, list, get, delete, path-leak protection, multi-upload |
| `ResumeAnalyzerApplicationTests` | 1 | context loads |

**Total: 25 tests.** No analysis or job-description tests exist.

---

## 3. API compatibility table

Legend — **Exp.** = endpoint the Stitch UI implies; **Act.** = endpoint that actually exists today.

| # | Frontend operation | Exp. endpoint | Act. endpoint | Method | Request | Response | Compatible | Required change |
|---|---|---|---|---|---|---|---|---|
| 1 | Register user | `/api/auth/register` | `/api/auth/register` | POST | `{name,email,password}` JSON | 201 `{message,user{id,name,email}}` | **YES** | None |
| 2 | Login | `/api/auth/login` | `/api/auth/login` | POST | `{email,password}` JSON | 200 `{message,user{...}}` | **YES** | None — but **no token**; frontend keeps the returned user in memory/localStorage (never the password) |
| 3 | Upload resume | `/api/resumes/upload` | `/api/resumes/upload` | POST | `multipart/form-data`, part `file` | 201 `ResumeResponse` | **YES** | None |
| 4 | List resumes (vault/dropdown/dashboard) | `/api/resumes` | `/api/resumes` | GET | — | 200 `[ResumeResponse]` | **YES** | None |
| 5 | View resume detail | `/api/resumes/{id}` | `/api/resumes/{id}` | GET | — | 200 `ResumeDetailResponse` (+`extractedText`) | **YES** | None |
| 6 | Delete resume | `/api/resumes/{id}` | `/api/resumes/{id}` | DELETE | — | 204 | **YES** | None |
| 7 | Create Job Description | `/api/job-descriptions` | **missing** | POST | `{jobTitle,companyName,descriptionText}` | `JobDescriptionResponse` | **NO** | **Add** controller + service (DTOs already exist) |
| 8 | List Job Descriptions | `/api/job-descriptions` | **missing** | GET | — | `[JobDescriptionResponse]` | **NO** | Same addition |
| 9 | View Job Description | `/api/job-descriptions/{id}` | **missing** | GET | — | `JobDescriptionResponse` | **NO** | Same addition |
| 10 | Delete Job Description | `/api/job-descriptions/{id}` | **missing** | DELETE | — | 204 | **NO** | Same addition |
| 11 | Analyze resume vs JD | `/api/analyses` | **missing** | POST | `{resumeId,jobDescriptionId}` | `AnalysisResponse` | **NO** | **Add** `AnalysisService` orchestrating the 6 existing engine components + controller + persistence |
| 12 | View analysis | `/api/analyses/{id}` | **missing** | GET | — | `AnalysisResponse` | **NO** | Same addition |
| 13 | Analysis history | `/api/analyses` | **missing** | GET | — | `[AnalysisSummaryResponse]` | **NO** | Same addition |
| 14 | Dashboard aggregates (count, avg, best, recent) | `/api/dashboard` | **missing** | GET | — | aggregate | **NO** | **Do not build a backend endpoint** — derive in the frontend from `GET /api/resumes` + `GET /api/analyses` (reasonable: both lists are already ordered by date) |
| 15 | AI recommendations | `/api/analyses/{id}/recommendations` | **missing** | GET | — | — | **NO** | **Do not build.** Show an honest placeholder: "AI recommendations will appear here." |
| 16 | Missing-skill frequency across JDs | aggregate | **missing** | — | — | — | **NO** | Derive client-side by intersecting `missingSkills` across `GET /api/analyses` |

### 3.1 Contract mismatches to handle in the UI (not backend changes)

| Backend reality | UI implication |
|---|---|
| `ResumeResponse.fileSize` is a **number of bytes** | Format to KB/MB in the frontend |
| `ResumeResponse` has **no page count or word count** | Drop "1 Page • 488 Words"; show size, type and `extractedCharacterCount` instead |
| `ResumeResponse.uploadStatus` ∈ `UPLOADED, PROCESSING, PARSED, FAILED` | Map to badges ("Parsed successfully", etc.) |
| `AnalysisResponse.keywordStuffing.risk` ∈ `LOW, MEDIUM, HIGH`; `flaggedKeywords[].reason` is generated prose | Render risk badge + the backend `reason` string; do not invent counts |
| `AnalysisResponse.structure[].status` ∈ `PASSED, WARNING` | Render pass/warning rows; **no** per-section prose beyond `message` |
| `AnalysisResponse` has **no keyword-frequency map** (entity stores one, the response DTO does not expose it) | Keyword "Resume n • JD m" parity chips cannot be populated → drop them, or add the map to the response DTO only if required |
| `AnalysisResponse` has **no relevance/formatting narrative, no percentile, no threshold, no "Top 15%"** | Remove those fabricated elements |
| No login token | Guard routes by the presence of a stored user object; acknowledge this is not real security |

---

## 4. Recommended change set (minimum necessary)

**Backend (additive only, no existing file rewritten except one security rule):**
1. `AnalysisService` — orchestrates `ResumeTextExtractionService` output + JD text through `SkillMatcher`, `KeywordMatcher`, `KeywordStuffingDetector`, `ResumeStructureAnalyzer`, `FormattingAnalyzer`; computes the weighted ATS score; persists `ResumeAnalysis` with a Jackson `ObjectMapper` (as the entity javadoc prescribes); maps to `AnalysisResponse`/`AnalysisSummaryResponse`.
2. `JobDescriptionService` — CRUD over the existing repository/entity using the existing DTOs.
3. `JobDescriptionController` (`/api/job-descriptions`) and `AnalysisController` (`/api/analyses`) — mirroring `ResumeController`'s style (`ResponseEntity`, constructor injection, javadoc).
4. `SecurityConfig`: add `.requestMatchers("/api/job-descriptions/**", "/api/analyses/**").permitAll()` — consistent with the existing resume rule and required because no auth mechanism exists.
5. Wire the two unused exceptions (`JobDescriptionNotFoundException`, `AnalysisNotFoundException`) into `GlobalExceptionHandler` → 404.
6. New tests for the added endpoints (no existing test modified or removed).

**Frontend (new project, porting only):**
1. Vite + React + TypeScript + React Router + Tailwind (local build replicating the Stitch config) at `frontend/`.
2. Shared chrome components (Header/Footer/Layout) + the ported Dashboard, New Analysis, Results pages.
3. New Login/Register, Analysis History, Settings pages in the same design language (Stitch supplies no design for these).
4. `src/services/*` + `src/types/*` central API layer and error/loading states.
5. All §1.4 mock values replaced by API responses; AI tab keeps an honest placeholder.

**Explicitly NOT doing:** any AI/LLM integration, any JWT implementation, any user-ownership enforcement, any backend dashboard endpoint (until proven necessary), any dependency upgrade, any file moving.

---

## 5. Consolidated mock-data → real-data mapping

| UI element | Source of truth after integration |
|---|---|
| Welcome name / user chip | `POST /api/auth/login` → `user.name` |
| Resumes Analyzed | `GET /api/resumes` → `.length` |
| Average ATS Score | mean of `GET /api/analyses` → `atsScore` |
| Highest Match Score (+ job) | max `atsScore` + its `jobTitle`/`companyName` |
| Recent Analyses list | first N of `GET /api/analyses` |
| Quick Select Vault | `GET /api/resumes` (real names, real sizes, delete wired) |
| Uploaded file preview | `POST /api/resumes/upload` → `ResumeResponse` |
| ATS dial + legend | `AnalysisResponse.atsScore` |
| 5 metric cards | `AnalysisResponse.scoreBreakdown` |
| Matched / missing / optional skills | `AnalysisResponse.matchedSkills` / `missingSkills` / `optionalSkills` |
| Keyword section | `matchedKeywords`, `missingKeywords`, `keywordStuffing.risk`, `keywordStuffing.flaggedKeywords[].reason` |
| Structure audit | `AnalysisResponse.structure[]` (`section`, `status`, `message`) + counts |
| History page | `GET /api/analyses` → `AnalysisSummaryResponse[]` |
| Reopened analysis | `GET /api/analyses/{id}` |
| AI Recommendations tab | **No backend capability** → honest placeholder |
| Keyword parity chips | **Not exposed by the response DTO** → removed |

---

## 6. Stitch screens that do not exist and must be authored

| Screen | Why | Approach |
|---|---|---|
| Login | Needed to obtain `user` from the real auth API | Split-card layout using the same tokens, logo SVG, primary button, focus-ring inputs |
| Register | Needed to create a test user | Same shell, 3 fields matching `RegisterRequest` validation (name 2–100, valid email, password ≥ 6) |
| Analysis History | Nav links to it; only a 4-row card exists inside the dashboard | Reuse the dashboard "Recent Job Analyses" row markup, full-width, paginated by `GET /api/analyses` |
| Settings | Nav links to it | Account card (real user), API base URL display, "application limitations" panel stating no JWT / no AI |
| Results — empty/loading/error | Backend calls can fail | Indeterminate spinner, empty state, error state with the backend `message` |

---

## 7. Runtime blockers found during the audit

| Blocker | Evidence | Minimal resolution |
|---|---|---|
| No MySQL on `localhost:3306` | `Test-NetConnection localhost 3306` → `False` | Add a **dev-only** H2 profile so the app starts locally; leave the MySQL default untouched. Document that production still expects MySQL. |
| `mvnw clean` failed once with "Failed to delete …\target\classes\com\resumeanalyzer\config" | Maven log | Transient lock (likely OneDrive sync); manual delete succeeded, re-run passed the clean phase. Not a code issue. |
| Not a git repository | `git status` → "not a git repository" | Flagged; no commits will be made unless requested. |

---

## 8. Structural decision

The requested ideal (§24 of the brief) is `SmartResume-Analyzer/{frontend,backend,README.md}`. The brief also says **not to move folders if the current structure works**. Therefore:

* Backend stays at its current nested path — untouched.
* Frontend is created as a **new sibling** at the workspace root: `frontend/`.
* `README.md` is added at the workspace root.
* `_stitch_source/` retains the original Stitch deliverable as the design reference.

Final layout:

```
resume-analyzer/                     <- workspace root
├── frontend/                        <- NEW (Vite + React + TS, ported Stitch UI)
├── resume-analyzer/
│   └── resume-analyzer/             <- existing Spring Boot backend (unchanged location)
├── _stitch_source/                  <- extracted Stitch deliverable (read-only reference)
├── docs/INTEGRATION-AUDIT.md        <- this file
└── README.md                        <- NEW
```

---

## 9. Honest limitations to carry into the final report

1. **No real authentication.** No token is issued; the frontend identifies the user only by the object returned from login. No endpoint verifies identity. Nothing is user-scoped.
2. **No AI anywhere.** The "AI Recommendations" tab and "Auto-Patch" are not backed by any capability. They will be presented as unavailable, not simulated.
3. **Resume/JD/analysis data is global**, not per-user (`Resume.user` is set to `null` on upload; `JobDescription` and `ResumeAnalysis` have no user relation at all).
4. **`/api/resumes/**` and the new `/api/analyses/**`, `/api/job-descriptions/**` are `permitAll`** — unauthenticated access is possible by design of the current code.
5. **The ATS scoring is deterministic and project-defined.** It is not a reproduction of any commercial ATS, and the code comments say so.
6. **Stitch's visual claims** (percentile rankings, target thresholds, model versions, "Top 15% Applicant") have no data source and are being removed rather than faked.
