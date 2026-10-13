# SmartResume Analyzer

**AI-Powered Resume Analyzer for Students** — upload a resume (PDF or DOCX), paste a job description, and get a
deterministic ATS-style diagnostic: an overall score, five sub-scores, matched and missing skills and keywords, a
per-keyword frequency and coverage view, keyword-repetition indicators, a structure audit, actionable
recommendations, and a downloadable PDF report.

- **Frontend:** React 18 + TypeScript 5.6 (strict) + Vite 5 + Tailwind 3 — `frontend/`
- **Backend:** Java 17 + Spring Boot 3.5.5 + Spring Data JPA — `resume-analyzer/resume-analyzer/`
- **Database:** MySQL by default; H2 for tests and for the opt-in `dev` profile
- **Auth:** stateless JWT (HS256) bearer tokens, BCrypt password hashing, per-user ownership on every record
- **Optional LLM:** provider-agnostic, **off by default**, never affects the score

> **The ATS score is a deterministic, documented heuristic — not an AI model and not a reproduction of any
> commercial ATS product.** An optional LLM layer can add advisory suggestions, but it is disabled unless an
> operator configures a provider, and it can never change a score. See
> [How the score is calculated](#how-the-score-is-calculated) and [AI recommendations](#ai-recommendations).

---

## 1. Project overview and objectives

University students apply to dozens of roles with the same resume, usually without knowing whether an applicant
tracking system will even find the keywords the advert asks for. This project makes that gap visible and
actionable:

1. **Parse** a real resume file (PDF/DOCX) into text, with honest failure reporting for unreadable documents.
2. **Compare** that text against a specific job description — skills, keywords, structure, formatting.
3. **Explain** the result: what matched, what is missing, how often terms repeat, and what to change.
4. **Keep it honest:** never invent a skill, qualification or achievement the student does not have.
5. **Package** the result as a printable report the student can keep.

Objectives met: a secure multi-user web application with real authentication, strict per-user data isolation,
deterministic scoring, a keyword-frequency view, PDF export, and an offline-tolerant UI.

---

## 2. Technology stack

| Layer | Choice | Notes |
|---|---|---|
| Frontend | React 18.3, TypeScript 5.6 (strict), Vite 5.4, React Router 6.26 | `noUnusedLocals`/`noUnusedParameters` enabled |
| Styling | Tailwind CSS 3.4 | Design tokens ported 1:1 from the original Stitch deliverable |
| Backend | Java 17 target, Spring Boot 3.5.5 | Built and tested with JDK 24.0.1 |
| Security | Spring Security 6, JJWT 0.13.0 (HS256), BCrypt | Stateless, no cookies |
| Persistence | Spring Data JPA / Hibernate | MySQL default, H2 for dev + tests |
| PDF | PDFBox 3.0.4 | Text extraction **and** report generation |
| DOCX | Apache POI 5.4.0 | Text extraction |
| Tests (backend) | JUnit 5, Spring Boot Test, MockMvc, AssertJ, spring-security-test | 98 tests |
| Tests (frontend) | Node's built-in test runner (`node:test`) + `assert/strict` | 55 tests, **zero extra dependencies** |

No AI SDK is required for the core application. Adding one is optional (see §7).

---

## 3. Feature status

### Completed and verified

**Accounts and security**
- Registration and login with server-side field validation (name 2–100 chars, valid email, password ≥ 6 chars).
- Passwords stored only as BCrypt hashes; no password or hash appears in any response or log.
- Registration and login both issue a signed **HS256 JWT**; the client sends it as `Authorization: Bearer <token>`.
- `GET /api/auth/me` re-validates a stored token on page load, so the session is never trusted from localStorage.
- **Expiry is enforced** (verified by a dedicated test) and an expired token yields `401`, never a `500`.
- Every resume, job description and analysis belongs to a user; all reads, writes and deletes are scoped by owner.
- Another user's record returns **404, not 403**, so the API never reveals which ids exist.
- Unknown email and wrong password return an identical message, so accounts cannot be enumerated.
- A 401 on any token-carrying request clears the client session and returns the user to the sign-in screen.
- No secrets in the repository: the JWT key and DB credentials come from environment variables only.

**Resume upload and parsing**
- PDF (PDFBox) and DOCX (POI) upload with extension + MIME validation and a 10 MB cap.
- Empty, oversized, wrong-type, corrupt, password-protected and scan-only documents are rejected with a
  human-readable `400`/`422`; a `FAILED` row is recorded and the stored file is cleaned up.
- Files are stored under a UUID name; the internal path is never returned to a client.

**Analysis**
- Skill matching against a curated dictionary (aliases, whole-word/phrase matching, optional-signal detection).
- Keyword matching against the job description (stopword-filtered, token-boundary aware).
- **Per-keyword frequency and coverage**: job-description count vs resume count, matched/missing status, and
  repetition flags — surfaced in the UI and included in the PDF report.
- Keyword-repetition ("stuffing") detection with documented density thresholds; reporting a count is **not** a
  penalty, and the UI says so explicitly.
- 8-section structure audit and an extracted-text formatting check.
- A prioritised list of actionable recommendations (HIGH/MEDIUM/LOW) derived from the measured findings.
- Results are persisted; reads never recompute, so a stored score is stable.

**PDF report export**
- `GET /api/analyses/{id}/report` renders the stored analysis as a multi-page A4 PDF (overall score, weighted
  sub-scores, skill match, keyword-frequency table, structure audit, recommendations, generation date).
- Long content paginates automatically; untrusted text (typographic quotes, dashes, accents, emoji) is sanitised so
  rendering can never fail.
- Served as an attachment with `Cache-Control: no-store`, and the download name is a slug that cannot break the
  `Content-Disposition` header. Ownership is enforced, so one user cannot fetch another user's report.

**Application**
- Dashboard (own totals, averages, best score, recent runs), New Analysis, Results (6 tabs), History, Settings, 404.
- Explicit loading / empty / error states throughout, with a single normalised error mapper.
- Responsive layout including a mobile navigation toggle.
- **Offline tolerant:** the design's webfonts are progressive enhancement only. System font fallbacks are declared
  for every family, and the icon rules are defined locally, so with no network the text stays readable and icons
  degrade to a neutral placeholder *without changing any layout measurement*.

### Deliberately not implemented

| Not present | Why |
|---|---|
| Auto-patching / rewriting the student's resume | Writing bullets as fact would fabricate their history. Suggestions are advice; the button stays disabled and says so. |
| Refresh tokens, revocation list, password reset, email verification, roles | Out of scope for this build; listed under known limitations. |
| A dashboard aggregate endpoint | Dashboard figures are derived client-side from the two list endpoints. |
| A DRF/Java-side "keyword density percentage" chart | The frequency table already conveys density with exact counts; a second rendering of the same numbers adds no information. |

---

## 4. Installation prerequisites

| Tool | Version used here | Notes |
|------|-------------------|-------|
| JDK | 17+ | Project targets Java 17. Verified with JDK 24.0.1. |
| Node.js | 18+ (22.23.2 used) | npm 10.9.8. Node 22+ is needed for the frontend test runner's TypeScript type-stripping. |
| MySQL | Optional | Only for the default profile. The `dev` profile uses in-memory H2. |

No global Maven install is needed — the Maven Wrapper (`mvnw.cmd` / `mvnw`) is committed.

---

## 5. Configuration

Everything below comes from environment variables. **No credential is hardcoded anywhere in the repository.**

### Database

```properties
spring.datasource.url=${DB_URL:jdbc:mysql://localhost:3306/resume_analyzer?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC}
spring.datasource.username=${DB_USERNAME:root}
spring.datasource.password=${DB_PASSWORD:}
spring.jpa.hibernate.ddl-auto=update
```

| Variable | Default | Purpose |
|---|---|---|
| `DB_URL` | MySQL `localhost:3306/resume_analyzer` | JDBC URL; `createDatabaseIfNotExist=true` creates the schema on first start |
| `DB_USERNAME` | `root` | MySQL user |
| `DB_PASSWORD` | *(empty)* | MySQL password |

### JWT signing key

| Variable | Default | Purpose |
|---|---|---|
| `JWT_SECRET` | *(empty)* | HS256 key. **Must be ≥ 32 bytes.** Never hardcoded. |
| `JWT_EXPIRATION_MS` | `86400000` (24 h) | Token lifetime |

If `JWT_SECRET` is empty the backend generates a random 256-bit key at startup and logs a warning. That is secure,
but every issued token is rejected after a restart (users simply sign in again). Set it for sessions that survive
restarts — the restart test does exactly this and proves it works.

```powershell
$env:JWT_SECRET = 'replace-with-at-least-32-random-characters'
```

If `JWT_SECRET` is set but shorter than 32 bytes, startup **fails** with an explanatory message rather than
silently weakening the keys.

### Other settings

| Variable | Default | Purpose |
|---|---|---|
| `RESUME_STORAGE_DIRECTORY` | `./uploads/resumes` | Where uploaded files are written |
| `RESUME_MAX_FILE_SIZE` | `10MB` | Upload cap |
| `VITE_API_BASE_URL` | `http://localhost:8080` | Frontend → backend base URL (`frontend/.env`) |

Ports: backend **8080**, frontend **5173** (`strictPort: true`). The CORS allow-list in `SecurityConfig` contains
`http://localhost:5173`, `http://127.0.0.1:5173`, `http://localhost:3000` and `http://127.0.0.1:3000`. Both loopback
spellings are listed because a browser treats `localhost` and `127.0.0.1` as **different origins** — listing only one
makes the app unusable from the other, with the preflight rejected as `403`. Changing the Vite port requires
updating that list. The backend also binds IPv4 loopback in addition to IPv6; `SecurityConfig` performs the
CORS check before authorization, so `OPTIONS` preflights are answered without a token while the real request
still requires one.

---

## 6. Database setup

**Option A — no MySQL needed (recommended for a review demo).** The opt-in `dev` profile uses in-memory H2:

```properties
spring.datasource.url=jdbc:h2:mem:resume_analyzer_dev;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE;MODE=MySQL
spring.jpa.hibernate.ddl-auto=update
```

Data lives only for the lifetime of the process — which is why the restart-persistence test overrides
`SPRING_DATASOURCE_URL` with a *file* URL.

**Option B — MySQL (the default profile).** Create nothing by hand: `createDatabaseIfNotExist=true` plus
`ddl-auto=update` build the schema on first start. Tables, generated from the entities:

`users`, `resumes`, `job_descriptions`, `resume_analyses`.

Relationships: `users 1─* resumes`, `users 1─* job_descriptions`, `users 1─* resume_analyses`,
`resumes 1─* resume_analyses`, `job_descriptions 1─* resume_analyses`.

**Tests** use `src/test/resources/application-test.properties` (in-memory H2, `create-drop`) and never touch MySQL.

---

## 7. AI recommendations

An optional LLM layer adds advisory suggestions. **It is disabled by default and there is no default API key.**

| Variable | Default | Purpose |
|---|---|---|
| `LLM_ENABLED` | `false` | Master switch |
| `LLM_BASE_URL` | *(empty)* | Provider base URL, **without** `/chat/completions` |
| `LLM_API_KEY` | *(empty)* | Secret; server-side only |
| `LLM_MODEL` | `gpt-4o-mini` | Model name |
| `LLM_TIMEOUT_MS` | `8000` | Connect/read timeout |
| `LLM_MAX_RESUME_CHARS` / `LLM_MAX_JD_CHARS` | `6000` / `4000` | Prompt size caps |
| `LLM_MAX_RECOMMENDATIONS` | `6` | Hard cap on accepted suggestions |

The transport is the OpenAI-compatible `POST {base-url}/chat/completions` shape, which Azure OpenAI, Groq,
Together, OpenRouter, vLLM, Ollama and LM Studio also serve — changing provider is a configuration change only.

```powershell
$env:LLM_ENABLED = 'true'
$env:LLM_BASE_URL = 'https://api.openai.com/v1'
$env:LLM_API_KEY  = '<your key>'
$env:LLM_MODEL    = 'gpt-4o-mini'
```

**Safety properties (all enforced in code):**

1. **The score is never affected.** The LLM output lands in a separate `aiRecommendations` field, labelled
   `source: "AI"`. The deterministic `recommendations` keep `source: "RULE"`. Rule-based output is never described
   as AI-generated, and vice versa.
2. **No fabrication.** The model may only reference items from an allow-list built from the analysis itself;
   `relatedItems` outside it are dropped. The prompt forbids asserting skills, employers, qualifications or
   metrics, and a block-list discards items that claim them anyway ("you have 5 years…", "you are certified in…").
3. **Untrusted input.** Resume and job-description text is wrapped in delimiters that are neutralised inside the
   document, length-capped, and explicitly described to the model as data that must never be treated as
   instructions.
4. **Validated output.** Responses must parse as JSON with the expected shape; markdown/HTML is stripped, fields are
   length-capped, categories and priorities are normalised, and unusable output raises `502` instead of being shown.
5. **Graceful failure.** No provider → `503` with an actionable reason, and the deterministic recommendations remain
   fully usable (that is the fallback). Provider unreachable/timeout/rate-limited → `502` within the configured
   timeout, verified by a test that points at a closed local port.
6. **Keys stay on the server.** `GET /api/ai/status` returns only `{enabled, reason, provider, model}` — never the
   key or the full URL. The API key is never logged and never reaches React.

**If you have no provider credentials:** leave `LLM_ENABLED=false`. Everything else in the application is complete;
the AI section simply reports that the feature is switched off and why. This is the state the project was verified
in.

---

## 8. Running the application

Two terminals, from the repository root:

```powershell
# Terminal 1 — backend (H2 dev profile; no MySQL required)
$env:JWT_SECRET = '<at least 32 random characters>'   # optional but recommended
.\scripts\run-backend.cmd
# equivalent to:
#   cd resume-analyzer\resume-analyzer
#   .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

```powershell
# Terminal 2 — frontend
.\scripts\run-frontend.cmd
# equivalent to:
#   cd frontend
#   npm install
#   npm run dev
```

Then open <http://localhost:5173>.

For MySQL instead of H2, drop the profile flag: `cd resume-analyzer\resume-analyzer; .\mvnw.cmd spring-boot:run`.

**Frontend production build:** `cd frontend; npm run build` (`tsc -b && vite build`).

> **Packaging note.** `mvnw package` / `java -jar target/*.jar` requires `maven-jar-plugin` and the Spring Boot
> repackage goal to be present in the local Maven cache. On a machine with no network access and a cache that was
> populated only by `compile`/`test`/`run`, `package` fails with a plugin-resolution error. `spring-boot:run` is
> unaffected and is what the scripts and tests use. With network access, `mvnw -DskipTests package` works normally.

---

## 9. API endpoint summary

All endpoints are under `/api`. Everything except `register` and `login` requires
`Authorization: Bearer <token>`; a missing or invalid token yields `401`.

| Method | Path | Auth | Body / notes | Success |
|---|---|---|---|---|
| POST | `/api/auth/register` | — | `{name, email, password}` | `201` `{message, user, token, tokenType, expiresInMs}` |
| POST | `/api/auth/login` | — | `{email, password}` | `200` same envelope |
| GET | `/api/auth/me` | required | Validates the token, returns the account | `200` `UserResponse` |
| POST | `/api/resumes/upload` | required | `multipart/form-data`, part `file`; PDF/DOCX ≤ 10 MB | `201` `ResumeResponse` |
| GET | `/api/resumes` | required | Metadata only (no extracted text) | `200` `[ResumeResponse]` |
| GET | `/api/resumes/{id}` | required | Includes `extractedText` | `200` `ResumeDetailResponse` |
| DELETE | `/api/resumes/{id}` | required | Removes the row **and** the stored file | `204` |
| POST | `/api/job-descriptions` | required | `{jobTitle, companyName, descriptionText}` | `201` |
| GET | `/api/job-descriptions` | required | Newest first | `200` `[JobDescriptionResponse]` |
| GET | `/api/job-descriptions/{id}` | required | — | `200` |
| DELETE | `/api/job-descriptions/{id}` | required | — | `204` |
| POST | `/api/analyses` | required | `{resumeId, jobDescriptionId}` — both must be yours | `201` `AnalysisResponse` |
| GET | `/api/analyses` | required | History summaries, newest first | `200` `[AnalysisSummaryResponse]` |
| GET | `/api/analyses/{id}` | required | Full stored diagnostic payload | `200` `AnalysisResponse` |
| DELETE | `/api/analyses/{id}` | required | Removes the stored result only | `204` |
| **GET** | **`/api/analyses/{id}/report`** | required | **PDF report** (`application/pdf`, attachment, `no-store`) | `200` |
| **POST** | **`/api/analyses/{id}/ai-recommendations`** | required | **Generates LLM suggestions** | `200` / `503` disabled / `502` provider error |
| **GET** | **`/api/ai/status`** | required | **`{enabled, reason, provider, model, disclaimer}`** | `200` |

### Analysis payload (abridged, real values from `scripts/test-resume.pdf`)

```json
{
  "id": 2, "resumeId": 3, "resumeFileName": "test-resume.pdf",
  "jobDescriptionId": 2, "jobTitle": "Full Stack Software Engineer", "companyName": "Test Company",
  "atsScore": 71,
  "scoreBreakdown": { "skillMatch": 67, "keywordMatch": 62, "relevance": 62, "structure": 95, "formatting": 100 },
  "matchedSkills": ["Java", "Spring Boot", "Spring", "React", "PostgreSQL", "SQL"],
  "missingSkills": ["Microservices", "Docker", "AWS"],
  "matchedKeywords": ["boot", "engineer", "java", "postgresql", "react", "software", "spring", "sql"],
  "missingKeywords": ["aws", "docker", "looking", "microservices", "required"],
  "keywordStuffing": { "risk": "MEDIUM", "flaggedKeywords": [{ "keyword": "java", "frequency": 5, "reason": "…" }] },
  "keywordCoverage": [
    { "keyword": "java", "jobDescriptionCount": 1, "resumeCount": 5, "matched": true,  "flagged": true,  "inJobDescription": true },
    { "keyword": "docker", "jobDescriptionCount": 1, "resumeCount": 0, "matched": false, "flagged": false, "inJobDescription": true }
  ],
  "structure": [{ "section": "Contact Information", "status": "PASSED", "message": "…" }],
  "recommendations": [{ "category": "Skills", "priority": "HIGH", "title": "…", "detail": "…", "relatedItems": ["Microservices","Docker","AWS"], "source": "RULE" }],
  "createdAt": "2026-10-11T15:40:12"
}
```

`aiRecommendations`, `aiGeneratedAt`, `aiProvider` and `aiModel` appear only after AI suggestions have been
generated.

### Error envelope

Every error — including `401` from the security layer — uses one shape:

```json
{
  "timestamp": "2026-10-11T15:40:12",
  "status": 400,
  "error": "Validation Failed",
  "message": "Input validation error",
  "validationErrors": { "jobTitle": "Job title is required." }
}
```

`validationErrors` is present only for field-validation failures.

---

## 10. How the score is calculated

`AnalysisService` runs the deterministic engine and combines five sub-scores with fixed weights:

| Component | Weight | Source |
|-----------|--------|--------|
| Skill match | 40% | `SkillMatcher` — curated dictionary, whole-word matching |
| Keyword match | 25% | `KeywordMatcher` — stopword-filtered JD keywords |
| Relevance | 15% | JD-emphasis-weighted keyword coverage |
| Structure | 12% | `ResumeStructureAnalyzer` — 8 section checks |
| Formatting | 8% | `FormattingAnalyzer` — extracted-text quality |

`ATS = 0.40·skill + 0.25·keyword + 0.15·relevance + 0.12·structure + 0.08·formatting`, rounded and clamped to 0–100.

*Keyword match* is the unweighted share of JD keywords present in the resume. *Relevance* is a different signal:
each JD keyword is weighted by how often the description itself uses it, so a resume that misses the employer's
most-repeated terms scores lower on relevance than on keyword match.

**Weights, thresholds and dictionary contents are documented in the source rather than left as magic numbers.**
The engine performs no inference and calls nothing external.

---

## 11. Testing

```powershell
# Backend — 98 tests (in-memory H2; does not touch MySQL)
cd resume-analyzer\resume-analyzer
.\mvnw.cmd test "-Dspring.profiles.active=test"

# Frontend — 55 tests (Node's built-in runner; no extra dependencies)
cd frontend
npm test

# Frontend type-check + production build
npm run build

# Live end-to-end API check (backend must be running) — 63 checks
powershell -ExecutionPolicy Bypass -File scripts\e2e-api-check.ps1

# Persistence across a real backend restart — 16 checks
powershell -ExecutionPolicy Bypass -File scripts\e2e-restart-persistence.ps1
```

**Actual results on this machine**

| Suite | Result |
|---|---|
| Backend (`mvnw test`) | **98 tests, 0 failures, 0 errors, 0 skipped — BUILD SUCCESS** |
| Frontend (`npm test`) | **55 tests across 17 suites, 55 passed, 0 failed** |
| Frontend (`npm run build`) | **PASS** — `tsc -b` strict + `vite build`, 57 modules, 289.5 kB JS / 26.9 kB CSS |
| `e2e-api-check.ps1` | **63 checks, 63 passed, 0 failed** |
| `e2e-restart-persistence.ps1` | **16 checks, 16 passed, 0 failed** |

Full test-case descriptions and observed values: [docs/TESTING.md](docs/TESTING.md).

---

## 12. Helper scripts

All scripts resolve paths relative to their own location, so they work from any clone.

| Script | Purpose |
|---|---|
| `scripts/run-backend.cmd` | Start the backend on the H2 `dev` profile |
| `scripts/run-frontend.cmd` | Start the Vite dev server |
| `scripts/e2e-api-check.ps1` | 63-check end-to-end API verification (register → login → PDF+ **DOCX** upload → JD → analyse → history → persistence → negatives → cross-user isolation → **PDF report** → **AI status/fallback** → delete). Exits non-zero on failure. |
| `scripts/e2e-restart-persistence.ps1` | Starts the backend twice against a file-based H2 and proves users, resumes, JDs, analyses, the score and even the pre-restart token survive a real restart (16 checks). |
| `scripts/seed-demo-analysis.ps1` | Registers a demo user and creates a complete analysis so `/results/<id>` can be inspected immediately; prints the credentials. |
| `scripts/check-servers.ps1` | Health check for both servers, including the CORS preflight |
| `scripts/make-test-resume-docx.ps1` | Regenerates `scripts/test-resume.docx` (no network, no external tooling) |
| `scripts/make-test-resume-pdf.mjs` | Regenerates `scripts/test-resume.pdf` |

---

## 13. Known limitations

1. **No refresh tokens and no revocation list.** A JWT is valid until it expires and cannot be revoked
   server-side, so signing out discards it on that device only. Mitigated by a bounded lifetime
   (`JWT_EXPIRATION_MS`, default 24 h).
2. **The signing key must be supplied** (`JWT_SECRET`) for sessions to survive a restart; otherwise a random
   per-boot key is used and users are signed out.
3. **No password reset, email verification or role model.** One role; registration accepts any unused,
   well-formed address.
4. **Scoring is a project-defined heuristic**, not a benchmark against any commercial ATS.
5. **Keyword matching is lexical.** A two-character token such as `c#` is below the 3-character minimum and is not
   treated as a keyword; inflected forms are not stemmed, so a word outside the stopword list (e.g. *uses*) can
   still surface as a keyword. Trailing sentence periods are stripped so `experience.` cannot become a phantom
   keyword.
6. **Structure detection is pattern-based.** Unconventionally named headings may not be recognised; a missing
   optional section is a warning, not a failure.
7. **The recommendation generator is rule-based.** It reports measured gaps and explains what to do; it does not
   know whether a specific technology is relevant to a specific student's coursework.
8. **AI suggestions are advisory** and require human review — the block-list is defence in depth, not a proof. They
   never modify the score, and without provider credentials the feature simply reports itself as unavailable.
9. **No dashboard aggregate endpoint**; dashboard figures are computed client-side from the list endpoints.
10. **Browser-level UI verification was not completed in this environment** (the built-in browser is disabled and
    the Chrome relay was not connected). The UI is type-checked and its pure logic is unit-tested; the interactive
    walk-through is documented as a manual checklist in [docs/TESTING.md](docs/TESTING.md) §6 and
    [docs/REVIEW-GUIDE.md](docs/REVIEW-GUIDE.md). The UI screenshots in `verify-ui/` predate the authentication work
    and are **not** current.
11. **Frontend tests cover pure logic, not DOM rendering** — no jsdom/Testing Library was installable offline.
    Component behaviour is therefore covered by the type-checker, the contract-level E2E checks, and the manual
    checklist, not by a rendering test.
12. **Offline icons are placeholders, not glyphs.** Without network access the icon font cannot be fetched, so the
    UI draws a neutral placeholder of the same size. Achieving full offline icon *fidelity* needs either the font
    file vendored into the repo or an inline SVG sprite (see future enhancements).

## 14. Future enhancements

1. Refresh tokens plus a revocation/deny list, with server-side sign-out.
2. Password reset and email verification.
3. Vendor the icon font or replace it with a local inline-SVG sprite for full offline icon fidelity.
4. Add a DOM test layer (Vitest + Testing Library + jsdom) once the registry is reachable, to cover component
   rendering, form validation and route guards.
5. Stemming/synonym expansion in keyword matching, and a user-configurable stopword list.
6. A dashboard aggregate endpoint and pagination on the history endpoint.
7. Persist and display AI suggestions per analysis version, so a historical result shows the advice given at the time.
8. Structured logging/metrics and rate limiting on the credential endpoints.

---

## 15. Documentation map

| Document | Contents |
|---|---|
| [docs/REVIEW-GUIDE.md](docs/REVIEW-GUIDE.md) | **Five-minute demonstration script and review checklist** |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Architecture, components, request/data flow, data model, security model |
| [docs/TESTING.md](docs/TESTING.md) | Test cases, exact commands, actual observed results, manual checklist |
| [docs/CHANGES.md](docs/CHANGES.md) | Every file added/changed, stage by stage, with the reason |
| [docs/REVIEW-AUDIT.md](docs/REVIEW-AUDIT.md) | The pre-change audit and the ordered implementation plan |
| [docs/INTEGRATION-AUDIT.md](docs/INTEGRATION-AUDIT.md) | **Historical** — audit of the project as originally received |
| [docs/INTEGRATION-REPORT.md](docs/INTEGRATION-REPORT.md) | **Historical** — original integration report, written before authentication existed |
| [verify-ui/](verify-ui/) | **Historical** — UI screenshots and report from before authentication was added |
