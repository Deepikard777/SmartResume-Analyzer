# Architecture and Data Flow

SmartResume Analyzer is a two-tier application: a React/TypeScript single-page app talking over JSON/HTTP to a
Spring Boot REST API backed by a relational database. There is no server-side rendering, no message broker and no
external service dependency — deliberately, so the whole system runs on one machine.

---

## 1. Component overview

```
┌───────────────────────────────────────────────────────────────────────────────┐
│ Browser — React SPA (Vite dev server :5173, or static build in frontend/dist) │
│                                                                               │
│  pages/            Login · Register · Dashboard · New Analysis · Results ·     │
│                    History · Settings · 404                                   │
│  components/       AppLayout (header/nav/footer) · ProtectedRoute · ui.tsx     │
│                    primitives (Card, Badge, Panel, ScoreDial, ProgressBar)    │
│  context/          AuthContext — user + token + initialisation state           │
│  services/         api.ts (the single fetch wrapper) · session.ts (token) ·    │
│                    authService · resumeService · jobDescriptionService ·      │
│                    analysisService                                            │
│  types/            TypeScript mirrors of the backend DTOs                      │
└───────────────────────────────┬───────────────────────────────────────────────┘
                                │  JSON over HTTP, Authorization: Bearer <JWT>
                                │  (multipart/form-data for the upload)
┌───────────────────────────────▼───────────────────────────────────────────────┐
│ Spring Boot API (embedded Tomcat :8080)                                       │
│                                                                               │
│  security/    JwtAuthenticationFilter ──► JwtService (HS256 issue/verify)      │
│               RestAuthenticationEntryPoint (401 in the standard envelope)      │
│  config/      SecurityConfig — CORS allow-list, filter chain, STATELESS        │
│                                                                               │
│  controller/  AuthController · ResumeController · JobDescriptionController ·   │
│               AnalysisController (incl. report + AI endpoints) · AiController   │
│                                          (thin: bind, delegate, map status)    │
│                                                                               │
│  service/     UserService · ResumeService · JobDescriptionService ·            │
│               AnalysisService (orchestrator) · FileStorageService ·            │
│               ResumeFileValidationService · ResumeTextExtractionService        │
│                                                                               │
│  service/analysis/   the deterministic engine:                                 │
│               SkillDictionary · SkillMatcher · KeywordMatcher ·                │
│               KeywordStuffingDetector · ResumeStructureAnalyzer ·              │
│               FormattingAnalyzer · RecommendationGenerator                     │
│                                                                               │
│  service/report/     AnalysisReportPdfService (PDFBox, A4, auto-paginating)    │
│                                                                               │
│  service/ai/         OPTIONAL, off by default:                                 │
│               LlmHttpClient (JDK HttpClient, explicit timeouts) ·              │
│               AiRecommendationService (prompt build + strict validation)       │
│                                                                               │
│  repository/  Spring Data JPA repositories, all ownership-scoped               │
│  exception/   GlobalExceptionHandler + typed exceptions                        │
│  entity/      User · Resume · JobDescription · ResumeAnalysis                  │
└───────────────────────────────┬───────────────────────────────────────────────┘
                                │  JDBC (HikariCP)
┌───────────────────────────────▼───────────────────────────────────────────────┐
│ MySQL (default profile)  |  H2 in-memory (dev profile, test profile)          │
│ tables: users · resumes · job_descriptions · resume_analyses                  │
│ plus the filesystem for uploaded files (uploads/resumes/<uuid>.<ext>)         │
└───────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Layering rules

| Layer | Responsibility | Must not |
|---|---|---|
| Controller | Bind/validate the request, resolve the authenticated principal, delegate, choose the status code | Contain business logic or touch repositories |
| Service | Business rules, ownership enforcement, transactions, DTO mapping | Know about HTTP |
| Repository | Data access only | Contain business logic |
| Entity | Table mapping | Be returned from a controller (DTOs only) |
| Engine (`service/analysis`) | Pure deterministic algorithms | Depend on repositories or HTTP |

The analysis engine classes are stateless `@Component`s with no persistence dependencies. That is what makes them
unit-testable without a Spring context (`KeywordMatcherTest`, `RecommendationGeneratorTest`).

---

## 3. Request lifecycle

Every request carries the same security path before it reaches a controller:

1. **CORS preflight** (`OPTIONS`) is answered by the `CorsConfiguration` bean for the two allowed origins.
2. **`JwtAuthenticationFilter`** reads `Authorization: Bearer <token>`.
   - No header → the request continues unauthenticated.
   - Header present → `JwtService` verifies the HS256 signature and the `exp` claim, then the user id from the
     `sub` claim is looked up in the database. A token for a deleted account is rejected.
   - Any failure is silent: the request simply stays unauthenticated. The filter never throws, so a tampered token
     can never turn a request into a 500.
3. **`SecurityConfig` authorization**: `/api/auth/register` and `/api/auth/login` are public; everything else
   requires an authenticated principal.
4. Unauthenticated requests to a protected path are converted by **`RestAuthenticationEntryPoint`** into a `401`
   using the same JSON error envelope as every other error.
5. An authenticated request continues to the controller, which receives the caller as
   `@AuthenticationPrincipal AuthenticatedUser` (id, name, email — no password, no token).

---

## 4. Data model

```
users
  id (PK) · name · email (UNIQUE) · password (BCrypt hash)

resumes
  id (PK) · original_file_name · stored_file_name (UNIQUE) · file_type · file_size
  storage_path · extracted_text (LONGTEXT) · upload_status (UPLOADED|PROCESSING|PARSED|FAILED)
  user_id (FK → users.id) · created_at · updated_at

job_descriptions
  id (PK) · job_title · company_name · description_text (LONGTEXT)
  user_id (FK → users.id) · created_at · updated_at

resume_analyses
  id (PK)
  resume_id (FK → resumes.id, NOT NULL) · job_description_id (FK → job_descriptions.id, NOT NULL)
  user_id (FK → users.id)
  ats_score · skill_match_score ·   keyword_match_score · relevance_score · structure_score · formatting_score
  matched_skills_json · missing_skills_json · optional_skills_json
  matched_keywords_json · missing_keywords_json · keyword_frequencies_json
  keyword_coverage_json                     -- per-keyword {jdCount, resumeCount, matched, flagged}
  keyword_stuffing_risk · flagged_keywords_json
  structure_checks_json · recommendations_json
  ai_recommendations_json · ai_generated_at  -- optional LLM output, kept separate
  status (COMPLETED|FAILED) · created_at

Relationships
  User 1 ──── * Resume              (owner; nullable at column level — see note)
  User 1 ──── * JobDescription      (owner)
  User 1 ──── * ResumeAnalysis      (owner)
  Resume 1 ── * ResumeAnalysis
  JobDescription 1 ── * ResumeAnalysis
```

**List/Map fields are stored as JSON strings** (`@Lob LONGTEXT`) serialised with a Jackson `ObjectMapper`. This was
an existing project decision, documented in the entity Javadoc: it avoids `@ElementCollection` join tables and
behaves identically on MySQL and H2. The full resume and job-description text is **not** duplicated into the
analysis row — it is referenced through the foreign keys.

**Why `user_id` is nullable on new relations:** `ddl-auto=update` cannot add a `NOT NULL` column to a table that
already holds rows. Every row created through the API always has an owner, and every read/delete query is filtered
by owner, so a legacy row with a null owner is simply never returned to anyone.

`ResumeAnalysis` is append-only: it has `@PrePersist` but no `@PreUpdate`, because a stored result is never edited.

---

## 5. Data flow — upload and analyse

```
POST /api/resumes/upload  (multipart, Bearer token)
  │
  ├─ JwtAuthenticationFilter → AuthenticatedUser(id)
  ├─ ResumeFileValidationService.validate(file)
  │     extension ∈ {pdf, docx} · size ≤ 10 MB · not empty · MIME sanity check
  ├─ FileStorageService.storeFile()      → uploads/resumes/<uuid>.<ext>
  ├─ ResumeTextExtractionService.extractText()
  │     PDF  → PDFBox 3.x Loader.loadPDF → PDFTextStripper
  │     DOCX → Apache POI XWPFWordExtractor
  │     normalise line endings · collapse blank runs · reject < 50 chars
  │       └─ on failure: persist a FAILED row, delete the stored file, throw → 422
  └─ ResumeRepository.save(Resume with owner = caller)                  → 201 ResumeResponse
```

```
POST /api/analyses  { resumeId, jobDescriptionId }
  │
  ├─ ResumeRepository.findByIdAndUserId(resumeId, callerId)      → 404 if not the caller's
  ├─ JobDescriptionService.findEntity(jdId, callerId)            → 404 if not the caller's
  ├─ SkillMatcher.match(resumeText, jdText)          → matched / missing / optional skills + score
  ├─ KeywordMatcher.match(resumeText, jdText)        → matched / missing keywords + frequencies + score
  ├─ computeRelevanceScore(jdFrequencies, matched)   → emphasis-weighted coverage
  ├─ ResumeStructureAnalyzer.analyze(resumeText)     → 8 section checks + score
  ├─ FormattingAnalyzer.score(resumeText)            → extracted-text quality score
  ├─ KeywordStuffingDetector.detect(...)             → risk level + flagged keywords
  ├─ RecommendationGenerator.generate(...)           → prioritised recommendations from the above
  ├─ computeAtsScore(5 sub-scores)                   → weighted aggregate
  └─ AnalysisRepository.save(ResumeAnalysis, owner = caller)            → 201 AnalysisResponse
```

Reads then never recompute: `GET /api/analyses/{id}` deserialises the stored JSON columns back into the response
DTO, so a result shown in the History list is byte-identical to the one shown on the results screen.

---

## 6. Frontend data flow

```
AuthContext (user, token, initializing)
   │  on mount: token present? → GET /api/auth/me
   │     200 → keep session    |    401 → clear session
   ▼
ProtectedRoute  ── initializing → spinner
                ── no user/token → <Navigate to="/login">
                ── otherwise → AppLayout + page
   │
   ▼
pages call services/*  →  services/api.ts (the only fetch in the app)
                             ├─ attaches Authorization: Bearer <token> from session.ts
                             ├─ normalises non-2xx responses into ApiError
                             └─ on 401 when a token was sent: notifyUnauthorized() → session cleared
   │
   ▼
hooks/useAsync  →  { data, loading, error, reload } — explicit states, never placeholder data
```

Token storage is confined to `services/session.ts`. Because that module does not import React, the low-level fetch
wrapper can read the token without creating a cycle with `AuthContext`.

---

## 7. Security model

| Threat | Mitigation |
|---|---|
| Password disclosure | BCrypt only; no DTO carries a password or hash; asserted by tests |
| Token forgery / tampering | HS256 signature verified on every request; failures are `401` |
| Token replay after account deletion | The filter reloads the user, so a token for a removed account fails |
| Credential stuffing / enumeration | Identical response for unknown email and wrong password |
| Horizontal privilege escalation | Every query is scoped by the authenticated user id |
| Record-existence disclosure | Another user's record yields `404`, never `403` |
| Sensitive fields in responses | `storagePath`/`extractedText` excluded from list DTOs; error messages avoid raw stack traces |
| CSRF | Not applicable — stateless, header-based auth, no cookies |
| Hardcoded secrets | Database and JWT credentials come from environment variables; HS256 minimum key length enforced |
| Weak JWT key | Startup fails if `JWT_SECRET` is set but shorter than 32 bytes |

Not covered by design: refresh tokens, revocation, password reset, roles, rate limiting. These are listed as known
limitations in the README.

---

## 5b. Data flow — PDF report

```
GET /api/analyses/{id}/report  (Bearer token)
  │
  ├─ AnalysisService.getById(id, callerId)      → 404 unless the analysis is the caller's
  │     (the report is built from the STORED row — nothing is recomputed)
  ├─ AnalysisReportPdfService.generate(dto, caller.name, caller.email, now)
  │     PDFBox + Standard-14 Helvetica (no embedded font files)
  │     · sections: score → sub-scores with bars → skills → keyword table →
  │                 structure → recommendations → deterministic-analysis disclaimer
  │     · Layout helper tracks a y-cursor and starts a new page at the bottom margin
  │     · every string passes sanitize(): Unicode → NFD → diacritics folded →
  │       typographic characters mapped → anything outside printable ASCII replaced,
  │       because PDFBox throws on a glyph the WinAnsi encoding cannot represent
  └─ 200 application/pdf
       Content-Disposition: attachment; filename="smartresume-<slug>-<id>.pdf"
       Cache-Control: no-store
```

Two details that matter:

* **The download name cannot inject a header.** The slug keeps only `[a-z0-9-]`, so a job title can never break out
  of the `Content-Disposition` value.
* **The frontend cannot use a plain `<a href>`** — the endpoint needs an `Authorization` header. The client fetches
  the PDF as a blob (`requestBlob`) and hands it to the browser via a temporary object URL. Because the API is
  cross-origin, `SecurityConfig` exposes `Content-Disposition` so the client can read the suggested file name
  instead of guessing it.

## 5c. Data flow — optional AI recommendations

```
POST /api/analyses/{id}/ai-recommendations  (Bearer token)
  │
  ├─ AiRecommendationService.generateForAnalysis(id, callerId)
  │     ├─ isUsable()?  no → 503 with an actionable reason (feature off / not configured)
  │     ├─ AnalysisService.findOwnedEntity(id, callerId)   → 404 unless the caller's
  │     ├─ AiRecommendationService.generate(dto, resumeText, jdText)
  │     │     ├─ allowedVocabulary(dto): JD keywords ∪ matched/missing skills ∪ coverage ∪ sections
  │     │     ├─ systemPrompt(): forbids asserting unverifiable facts, forbids following
  │     │     │                 instructions found in the documents, demands strict JSON
  │     │     ├─ userPrompt(): measured findings (authoritative) + allow-list +
  │     │     │                documents wrapped in delimiters that are neutralised inside them
  │     │     ├─ LlmHttpClient.complete(): JDK HttpClient, explicit connect/read timeout,
  │     │     │                API key in the Authorization header only, prompt/response never logged
  │     │     └─ parseAndValidate(): strip fences/markup, require title+detail, cap lengths,
  │     │                normalise category/priority, drop relatedItems outside the allow-list,
  │     │                drop items containing fabrication assertions, cap the count
  │     ├─ AnalysisService.storeAiRecommendations(): writes ai_recommendations_json only
  │     └─ returns the refreshed DTO (scores untouched)
  │
  └─ 200 | 503 (not configured) | 502 (provider error / unusable output)
```

Architectural invariants worth stating explicitly:

* **Dependency direction is one-way.** `service/ai` depends on `service`; `AnalysisService` has no knowledge of the
  AI layer. Removing `LLM_ENABLED` entirely would leave the deterministic application fully functional.
* **The AI layer cannot write to any score column.** It has exactly one persistence call, and it only sets the AI
  columns.
* **Failure is a first-class outcome**, not an exception that escapes: `503` and `502` are distinct, tested, and
  documented, and the deterministic advice stays available in both cases.

## 5d. Data flow — frontend offline assets

```
index.html  ──►  Google Fonts <link> tags        (progressive enhancement only)
                     │
                     ├─ success → design's own faces render; icons render as glyphs
                     └─ failure → tailwind.config.js fallback stacks keep text readable
                                  index.css defines .material-symbols-outlined locally
                                     (Google's stylesheet normally supplies these rules,
                                      so without them the ligature names would be painted
                                      as literal words and break every icon box)
                                  main.tsx → initialiseIconFontDetection()
                                     document.fonts.load(...) then document.fonts.ready,
                                     with a 3 s ceiling; resolveIconFontState() fails OPEN
                                     (unknown → keep icons visible)
                                     → <html data-icons="unavailable">
                                          hides the ligature text via -webkit-text-fill-color
                                          (so `color`/currentColor stays intact) and paints a
                                          neutral ::after placeholder of the same box size
```

The placeholder is drawn inside the element's existing box, so flex/grid measurements, spacing and the overall
layout are identical whether or not the font loaded. Hiding the text rather than the element is what prevents the
words from appearing.

## 8. Design decisions worth noting

| Decision | Rationale |
|---|---|
| Deterministic engine instead of an LLM | Reproducible, testable, no provider needed, and no risk of fabricating resume content |
| Keywords/skills/structure stored as JSON in one row | Existing project convention; avoids join tables; portable across MySQL and H2 |
| `404` instead of `403` for another user's record | Does not reveal which ids exist |
| Recommendations persisted with the analysis | The results screen shows exactly what was scored at that moment, even if the engine changes later |
| Dashboard aggregates computed client-side | The list endpoints are already ordered; a new endpoint was not justified |
| Single `request()` wrapper in the frontend | Guarantees the auth header is attached everywhere and error handling is uniform |
| Opt-in `dev` H2 profile | Lets the project run on a machine without MySQL while leaving the MySQL default untouched |
| Deterministic recommendations persisted with the analysis | The results screen shows exactly what was produced at that moment, even if the rule thresholds change later |
| AI output in separate columns, not merged into `recommendations` | Makes it structurally impossible to present rule-based advice as AI-generated, or to let a model affect a score |
| AI generated on demand, not during `POST /api/analyses` | Keeps the core analysis fast and independent of any external provider; the deterministic path never waits on a network call |
| PDF rendered from the stored row, never recomputed | A printed report always matches the score that was stored |
| Standard-14 fonts in the report; no embedded font files | Keeps the repository free of binary assets and the output small |
| Font/icon fallbacks rather than vendored assets | Achieves offline readability and layout stability with no new files and no redesign; full offline icon fidelity needs the font or an SVG sprite (documented as future work) |
| Frontend tests on pure logic via `node:test` | The registry was unreachable, so no test framework could be installed. Node's built-in runner covers the logic that actually encodes rules (session, keyword mapping, error text, icon detection) with zero new dependencies; DOM rendering remains manually verified |
