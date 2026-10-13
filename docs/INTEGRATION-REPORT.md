# SmartResume Analyzer — Frontend ⇄ Backend Integration Report

> **HISTORICAL DOCUMENT — SUPERSEDED.** This report describes the integration as it stood on 2026-10-08, when the
> project deliberately had **no** authentication: §13 and §18 correctly state that no token was issued, no ownership
> was enforced and every data endpoint was public.
>
> That is no longer true. JWT authentication, per-user ownership, a protected endpoint set, persisted
> recommendations and an analysis delete endpoint have since been implemented and tested. Read
> [../README.md](../README.md), [ARCHITECTURE.md](ARCHITECTURE.md) and [TESTING.md](TESTING.md) for the current
> state; the limitations listed here are kept only as a record of what was true at the time.

**Date:** 2026-10-08
**Scope:** Integrate the Stitch UI with the existing Spring Boot backend. Integration only — no rebuild, no redesign,
no replacement of the backend architecture.

**Result: integration complete and verified end-to-end against real API responses.** Two real defects were found by
verification and fixed. Three environment-level limitations are documented in §18 — none of them were worked around
by faking functionality.

---

## 1. Frontend location

`C:\Users\DEEPIKA\OneDrive\Desktop\resume-analyzer\frontend` — **new project** (created during this task).

The original Stitch deliverable was extracted, unmodified, to
`C:\Users\DEEPIKA\OneDrive\Desktop\resume-analyzer\_stitch_source\stitch_smartresume_ai_analyzer_platform\`
and kept as the design reference.

## 2. Backend location

`C:\Users\DEEPIKA\OneDrive\Desktop\resume-analyzer\resume-analyzer\resume-analyzer` — **unchanged location**.

It was left at its existing nested path rather than moved, per the instruction not to move working folders.

## 3. Frontend framework

| Aspect | Value |
|---|---|
| Build tool | Vite 5.4.21 |
| UI | React 18.3.1 + react-dom 18.3.1 |
| Language | TypeScript 5.6.3 (strict, `noUnusedLocals`, `noUnusedParameters`) |
| Routing | react-router-dom 6.26.2 |
| Styling | Tailwind CSS 3.4.13 — `tailwind.config.js` is a **1:1 port of the Stitch inline `tailwind.config`** (same colour, spacing, radius and font-size token names), so the original class names resolve to identical values |
| Fonts / icons | Google Fonts (Plus Jakarta Sans, Inter, JetBrains Mono) + Material Symbols Outlined — same as Stitch |

> The Stitch files are static HTML with Tailwind loaded from a CDN. A production app needs a real build, so the CDN
> was replaced with a local Tailwind build **using the identical token set**. The visual output is unchanged.

## 4. Backend framework

| Aspect | Value |
|---|---|
| Framework | Spring Boot 3.5.5 (Maven, wrapper present) |
| Java | targets Java 17 (built locally with JDK 24) |
| Persistence | Spring Data JPA + Hibernate |
| Security | Spring Security, `STATELESS`, BCrypt — **no JWT** |
| Extraction | PDFBox 3.0.4 (PDF), Apache POI 5.4.0 (DOCX) |
| Database | MySQL by default; H2 for tests and for the new opt-in `dev` profile |

## 5. API compatibility findings

### 5.1 Already compatible — no change needed (6)

| Frontend operation | Endpoint | Method | Compatible |
|---|---|---|---|
| Register | `/api/auth/register` | POST | YES |
| Login | `/api/auth/login` | POST | YES — but **returns no token** |
| Upload resume | `/api/resumes/upload` | POST (multipart, part `file`) | YES |
| List resumes | `/api/resumes` | GET | YES |
| Resume detail | `/api/resumes/{id}` | GET | YES |
| Delete resume | `/api/resumes/{id}` | DELETE | YES |

### 5.2 Missing — added (7)

| Frontend operation | Endpoint | Method | What was added |
|---|---|---|---|
| Create JD | `/api/job-descriptions` | POST | `JobDescriptionService` + `JobDescriptionController` |
| List JDs | `/api/job-descriptions` | GET | same |
| View JD | `/api/job-descriptions/{id}` | GET | same |
| Delete JD | `/api/job-descriptions/{id}` | DELETE | same |
| Analyse resume vs JD | `/api/analyses` | POST | `AnalysisService` (orchestrator) + `AnalysisController` |
| View analysis | `/api/analyses/{id}` | GET | same |
| Analysis history | `/api/analyses` | GET | same |

**No new matching logic was written.** All six engine components already existed
(`SkillMatcher`, `KeywordMatcher`, `KeywordStuffingDetector`, `ResumeStructureAnalyzer`, `FormattingAnalyzer`,
`SkillDictionary`), as did every entity, repository and DTO — including `AnalysisResponse`, whose own javadoc said it was
"designed to provide all data needed by the future Stitch Results page". Only the orchestrator, the two controllers and
the persistence mapping were missing.

### 5.3 Deliberately NOT built

| Frontend expectation | Decision |
|---|---|
| Dashboard aggregate endpoint (`/api/dashboard`) | **Not built.** Totals, averages, highest score and the missing-skill matrix are derived in the frontend from `GET /api/resumes` + `GET /api/analyses` (plus up to 5 `GET /api/analyses/{id}` calls for missing-skill frequency). This was the smaller, non-invasive option. |
| AI recommendations (`/api/analyses/{id}/recommendations`) | **Not built, not faked.** No AI/LLM capability exists anywhere in the project. |
| PDF export, save-draft | **Not built.** Controls are disabled and labelled. |

### 5.4 Contract mismatches handled in the UI (no backend contract changed)

| Backend reality | UI handling |
|---|---|
| `fileSize` is bytes | Formatting helper renders KB/MB |
| No page count / word count | Dropped "1 Page • 488 Words"; shows type, size and `extractedCharacterCount` instead |
| `keywordFrequencies` is stored on the entity but **not exposed** by `AnalysisResponse` | Per-keyword "Resume n • JD m" parity chips were **removed** and the omission is stated on screen |
| No percentile, no target threshold, no relevance narrative, no formatting narrative | Those fabricated elements were removed |
| `ScoreBreakdown` values can be null | Rendered as `0` with a neutral caption; never invented |

## 6. Files created

**Frontend (33 files)** — `frontend/`
`package.json`, `vite.config.ts`, `tsconfig.json`, `tailwind.config.js`, `postcss.config.js`, `index.html`, `.env`,
`.gitignore`, `public/logo.svg`,
`src/main.tsx`, `src/App.tsx`, `src/index.css`, `src/vite-env.d.ts`,
`src/types/api.ts`,
`src/services/api.ts`, `src/services/authService.ts`, `src/services/resumeService.ts`,
`src/services/jobDescriptionService.ts`, `src/services/analysisService.ts`,
`src/context/AuthContext.tsx`, `src/hooks/useAsync.ts`, `src/utils/format.ts`,
`src/components/ui.tsx`, `src/components/AppLayout.tsx`, `src/components/ProtectedRoute.tsx`,
`src/pages/LoginPage.tsx`, `src/pages/RegisterPage.tsx`, `src/pages/DashboardPage.tsx`,
`src/pages/NewAnalysisPage.tsx`, `src/pages/ResultsPage.tsx`, `src/pages/HistoryPage.tsx`,
`src/pages/SettingsPage.tsx`, `src/pages/NotFoundPage.tsx`

**Backend (7 files)**
`service/JobDescriptionService.java`, `controller/JobDescriptionController.java`,
`service/AnalysisService.java`, `controller/AnalysisController.java`,
`resources/application-dev.properties`,
`test/.../JobDescriptionControllerIntegrationTest.java`, `test/.../AnalysisControllerIntegrationTest.java`

**Docs & tooling**
`README.md`, `docs/INTEGRATION-AUDIT.md`, `docs/INTEGRATION-REPORT.md`,
`scripts/make-test-resume-pdf.mjs` (+ generated `scripts/test-resume.pdf`),
`scripts/e2e-api-check.ps1`, `scripts/check-servers.ps1`, `scripts/seed-demo-analysis.ps1`,
`scripts/run-backend.cmd`, `scripts/run-frontend.cmd`,
`verify-ui/` (7 screenshots + `verify-report.md`)

## 7. Files modified (existing files)

| File | Change | Why |
|---|---|---|
| `config/SecurityConfig.java` | added `.requestMatchers("/api/job-descriptions/**", "/api/analyses/**").permitAll()` | `anyRequest().authenticated()` plus a `UserDetailsService` that throws by design would have made the new endpoints permanently unreachable — no request can ever authenticate. Same rule the existing resume endpoints already use. |
| `exception/GlobalExceptionHandler.java` | added 3 handlers: `JobDescriptionNotFoundException` → 404, `AnalysisNotFoundException` → 404, `AnalysisException` → 422 | The first two exception classes already existed but were never wired; without this, analysis errors surfaced as generic 500s. |
| `pom.xml` | H2 scope `test` → `runtime` | Required so the new opt-in `dev` profile can start without MySQL. One attribute; no dependency added, removed or upgraded. |

Nothing else in the backend was touched — no existing class, entity, DTO, repository, service or test was rewritten.

## 8. API integrations completed

13 endpoints are wired through the frontend service layer (2 auth, 4 resumes, 4 job descriptions, 3 analyses).
No component contains a raw `fetch`/`axios` call — everything goes through `src/services/api.ts`.

## 9. Frontend pages connected

| Page | Route | Real integration |
|---|---|---|
| Login | `/login` | `POST /api/auth/login` |
| Register | `/register` | `POST /api/auth/register` |
| Dashboard | `/` | `GET /api/resumes` + `GET /api/analyses` (+ `GET /api/analyses/{id}` ×N) |
| New Analysis | `/analysis/new` | `GET /api/resumes`, `GET /api/job-descriptions`, `POST /api/resumes/upload`, `POST /api/job-descriptions`, `POST /api/analyses` |
| Results | `/results/:id` | `GET /api/analyses/{id}` |
| Analysis History | `/analysis/history` | `GET /api/analyses` |
| Settings | `/settings` | real user object + live `GET /api/resumes` connectivity probe |
| Not found | `*` | — |

Stitch ships only Dashboard, New Analysis and Results. **Login, Register, Analysis History and Settings had to be
authored** (its top nav links to History and Settings but provides no design). They were built from the same tokens,
card markup and typography, not from a new visual language.

## 10. Mock data removed

Every data-bearing mock value from the Stitch prototypes was replaced with API data:

- Dashboard: `Resumes Analyzed 14 / +3 this week`, `Average ATS 79.4 / +6.2 pts / 73.2 baseline`, `Highest Match 92 /
  Frontend Dev @ Stripe`, `Critical Gaps Resolved 8 skills`, the `82` dial, `Skills 84% / Keywords 87% / Structure 91%`
  bars, the 4 fake recent rows (FinTech Core, CloudScale AI, PayLogic, NextGen Apps), `Showing 4 of 14`, and the
  `Docker (9 Target JDs) / AWS (8) / Spring Boot (6) / Kubernetes (4)` deficit list.
- Header: `Alex Chen / CS Senior` → the real signed-in user; `AI Beta` badge → `Engine v2.4`.
- New Analysis: `Alex_Chen_*.pdf` vault entries, `Spring 2025 General`, the `245.8 KB • 1 Page • 488 Words` preview,
  the `Java (Spring) / TypeScript / PostgreSQL / Docker / +14 more tags` chip row, the `88.4 Match` historical baseline
  and its fake sparkline.
- Results: `184 KB`, `ATS Engine 4.2.1`, `Target Match Threshold 75%`, `Qualified for Interview Screening`,
  `Top 15% Applicants`, the second benchmark ring, `82`, `16 of 19`, `Weight: +45 pts`, `Impact: -12 pts`,
  `"Java" appears 11 times`, the `4-6 natural occurrences` claim, all four keyword-parity chips, and the three
  recommendation cards with their `+6 / +4 / +3 ATS Pts` badges and fabricated rewrite snippets.
- New Analysis also dropped Stitch's "Experience Tier" / "Scoring Calibration" selectors and its heuristic-weight
  toggles, because the backend has no such inputs and the weights are fixed in code. The fixed weights are now displayed
  read-only (40/25/15/12/8) so the block still communicates the same idea honestly.

## 11. Mock data still remaining

**No mock *data* remains.** What is left is static design copy, which carries no data claim:

| Element | Location | Status |
|---|---|---|
| `SmartResume Core Engine v2.4` / `ATS Diagnostic Ready` | footer | Static brand copy from the Stitch design, kept for visual fidelity |
| `Engine v2.4` chip | header | Same |
| `© 2025 SmartResume Analyzer Inc. Institutional Grade Telemetry.` | footer | Stitch's own copyright line, kept verbatim as design copy |

The "AI Recommendations" tab is an explicit, labelled placeholder — it is intentionally empty, not mock data.

## 12. Backend changes made

Additive, architecture-preserving:

1. **`AnalysisService`** — orchestrates the existing engine, computes the aggregate score, persists a `ResumeAnalysis`
   row (JSON columns via the injected `ObjectMapper`, exactly as the entity javadoc prescribed) and maps to the existing
   DTOs. It introduces no matching logic of its own.
2. **`JobDescriptionService`** — CRUD over the existing repository/entity using the existing DTOs.
3. **`AnalysisController`** (`/api/analyses`), **`JobDescriptionController`** (`/api/job-descriptions`) — written in the
   same style as the existing `ResumeController`.
4. **Scoring formula** (documented in the class javadoc, not a magic number):
   `ATS = 0.40·skillMatch + 0.25·keywordMatch + 0.15·relevance + 0.12·structure + 0.08·formatting`, rounded, clamped
   to 0–100. *Relevance* is deliberately a distinct signal from *keyword match*: keywordMatch is the unweighted share
   of JD keywords present, relevance is the share of the JD's **keyword emphasis** covered (each JD keyword weighted by
   how often the JD itself uses it).
5. `application-dev.properties` — opt-in H2 profile so the app can run without MySQL. `application.properties`
   (MySQL) was **not** modified.

**No AI integration was added, and no existing endpoint contract changed.**

## 13. Authentication status

**Honest summary: there is no real authentication in this project, and none was invented.**

| Aspect | Status |
|---|---|
| Register / Login endpoints | Work (201 / 200) |
| Password hashing | **Yes** — BCrypt |
| Password leakage | **None** — verified by the pre-existing tests and re-confirmed live (no `password` key in the response) |
| Token / JWT / session / cookie | **None.** `POST /api/auth/login` returns `{ message, user }` only, and `SecurityConfig` is `STATELESS` |
| Server-side identity verification | **None** — every data endpoint is `permitAll` |
| What the frontend stores | Only the public user object (`{id, name, email}`) in `localStorage`, so it can greet the user and gate routes. **No password, ever.** |
| Route protection | Client-side only (`ProtectedRoute`) — a UI convenience, explicitly documented as *not* security, both in code comments and on the Settings page |

Per the instruction "do not invent JWT if the backend does not currently use JWT", no token layer was added. The
limitation is surfaced in the UI (Settings → "What This Build Does Not Do") rather than hidden.

## 14. CORS status

**Already correct — no change required.** The existing `SecurityConfig` CORS bean is already explicit and
non-wildcard: origins `http://localhost:5173` and `http://localhost:3000`, methods
`GET/POST/PUT/DELETE/OPTIONS/PATCH`, headers `Authorization, Content-Type, Accept, X-Requested-With, Origin`,
`allowCredentials=true`, `maxAge=3600`.

The actual Vite port was **verified, not assumed**: `vite.config.ts` pins `port: 5173, strictPort: true` and the live
preflight check returned `Access-Control-Allow-Origin: http://localhost:5173` for `OPTIONS /api/analyses` from that
origin.

## 15. End-to-end test result

**PASS — verified twice: once at the API layer, and once through the real UI in a browser.**

### 15.1 Through the UI (browser, real clicks)

| # | Step | Result |
|---|---|---|
| 1 | Open app → redirect to `/login` | PASS |
| 2 | Register a test user | PASS |
| 3 | Login → Dashboard shows the real user | PASS |
| 4 | Open New Analysis | PASS |
| 5 | Upload `test-resume.pdf` | PASS — PDF, 1.8 KB, **1013 characters extracted**, status `PARSED` |
| 6 | Enter Job Title / Company / JD as specified | PASS |
| 7 | **Analyze** → redirected to `/results/1` | PASS |
| 8–11 | Results → History → reopen the stored analysis | PASS |

**Observed values (all from the backend):** ATS **76**; Skills **75%** (6 of 8), Keywords **67%** (8 of 12),
Relevance **67%**, Structure **95%** (7 of 8), Formatting **100%**; matched skills *Java, Spring Boot, Spring, React,
PostgreSQL, SQL*; missing skills *Docker, AWS*; keyword risk **MEDIUM** (java ×5); structure **7 passed / 1 warning**
(Internships, an optional section).

**UI ⇄ API cross-check: no mismatches.** Every value above was compared field-by-field against
`GET /api/analyses/1` and matched (`atsScore`, all five `scoreBreakdown` values, matched/missing skills, matched/missing
keywords, `keywordStuffing` incl. the flagged keyword and its reason, and all 8 structure checks).

**Persistence:** reopening from History returns the identical stored result (same ATS score and same payload) — the
analysis is a real database row, not recomputed state.

**Console/network:** no JS exceptions, no console errors, no failed requests (every call 200/201/304).

Full evidence, measurements and screenshots: [verify-ui/verify-report.md](../verify-ui/verify-report.md).

### 15.2 Defects found by verification and fixed

| # | Defect | Root cause | Fix | Re-verified |
|---|---|---|---|---|
| 1 | One file selection fired **two** `POST /api/resumes/upload` calls (2 stored resumes) | The hidden `<input type="file">` sits inside the dropzone whose `onClick` calls `input.click()`; the input's own click bubbled back up and opened the picker twice | `onClick={(e) => e.stopPropagation()}` on the input + a re-entrancy guard in the change handler | PASS — exactly 1 upload, 1 record, `resumeId:1`, no orphan rows |
| 2 | Results-page ATS dial was compressed to ~87px so `76`, `/100` and `STRONG` overflowed the ring | The dial had no `shrink-0`, so the long paragraph beside it in the same flex row squeezed the ring | `shrink-0` on the dial container; `min-w-0` on the text block | PASS — measured **144×144**, all inner elements inside with positive margin, paragraph clear of the ring |

### 15.3 Responsiveness

Verified at **1440×900** and **375×812** (Dashboard, Results, Analysis History, New Analysis):
no horizontal overflow (`scrollWidth == clientWidth == 375`), the hamburger toggle opens the full nav at 375px
(Stitch hides its nav below `lg` with no replacement — a mobile toggle was added), and no content is clipped at the top
or bottom. On the Results page the metric **tab strip** scrolls horizontally *within its own container* (1128px of tabs
in 343px) so all five tabs remain reachable without overflowing the page.

## 16. Frontend build result

**PASS.**

```
npm install          -> clean
npm run build        -> tsc -b && vite build
                        ✓ 53 modules transformed
                        dist/index.html                 0.98 kB │ gzip:  0.51 kB
                        dist/assets/index-*.css        25.17 kB │ gzip:  5.29 kB
                        dist/assets/index-*.js        272.34 kB │ gzip: 74.18 kB
                        ✓ built
```

TypeScript is compiled in **strict mode** with `noUnusedLocals`/`noUnusedParameters`. No `any`, no `@ts-ignore`,
no `eslint-disable` and no error suppression of any kind is used in the codebase. The existing `package-lock.json`
situation: the frontend project is new, so `npm install` generated its own lockfile; nothing existing was replaced.

## 17. Backend test result

**PASS — 43 tests, 0 failures, 0 errors.** Command (the project's existing one):

```powershell
.\mvnw.cmd clean test "-Dspring.profiles.active=test"
```

| Test class | Before | After |
|---|---|---|
| `AuthControllerIntegrationTest` | 9 | 9 (unchanged) |
| `ResumeControllerIntegrationTest` | 15 | 15 (unchanged) |
| `ResumeAnalyzerApplicationTests` | 1 | 1 (unchanged) |
| `JobDescriptionControllerIntegrationTest` | — | 8 (new) |
| `AnalysisControllerIntegrationTest` | — | 10 (new) |
| **Total** | **25, all passing** | **43, all passing** |

Baseline was captured **before** any change: 25 tests, BUILD SUCCESS. **All 25 previously passing tests still pass**;
no test was removed, skipped, disabled or weakened to make the build green. The new tests cover the JD CRUD contract,
validation errors, 404s, the full analysis payload, persistence, history summaries (asserting they stay lightweight),
422 for an unparseable resume, and a full HTTP flow (upload DOCX → create JD → analyse → reload).

## 18. Known limitations

**Product / backend (not worked around, not faked):**

1. **No real authentication.** No token, cookie or session exists; identity is never verified server-side; all data
   endpoints are public.
2. **No user ownership anywhere.** Uploads are persisted with `user = null`; `JobDescription` and `ResumeAnalysis` have
   no owner relation at all. Every record is visible to every caller.
3. **No AI of any kind.** No LLM client, no recommendation endpoint, no auto-patch. The Results page states this
   explicitly and shows only deterministic findings.
4. **Scoring is a project-defined heuristic**, not a benchmark against any commercial ATS — stated in the code and in
   the UI.
5. **No dashboard aggregate endpoint** — dashboard figures are computed client-side from the list endpoints.
6. **Per-keyword frequencies are stored but not exposed** by the analysis response contract, so the UI shows matched /
   missing keyword lists only.
7. **No PDF export, no draft saving.**

**Environment (this machine — the user should know these):**

8. **MySQL is not installed** and nothing listens on `localhost:3306`. The default profile therefore cannot start here;
   the app was run with the new **`dev`** profile (in-memory H2). Production is unaffected — `application.properties`
   still points at MySQL.
9. **Dev servers launched as agent background jobs are killed after ~15 minutes.** Start them yourself in a terminal
   (`scripts\run-backend.cmd` and `scripts\run-frontend.cmd` are provided for exactly this). This repeatedly interrupted
   browser verification and is why the browser session had to be resumed several times.
10. **OneDrive interferes with the workspace.** `mvnw clean` intermittently fails with *"Failed to delete …
    \target\classes\…"* because a sync lock is held; deleting `target` manually clears it. Launching Vite **detached**
    via `Start-Process` from an agent shell fails with esbuild `spawn EPERM`; running it from a normal terminal works.
    If these persist, moving the project out of the OneDrive-synced folder is the real fix.
11. **The built-in browser is disabled** in Accio Work Settings, so verification used the user's Chrome instead.

## 19. Exact next steps

**To run it now** (two terminals):

```powershell
# terminal 1 — backend (H2 dev profile; use plain `.\mvnw.cmd spring-boot:run` if MySQL is available)
cd resume-analyzer\resume-analyzer
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"

# terminal 2 — frontend
cd frontend
npm install
npm run dev
```

Then open <http://localhost:5173>, register a user, and run Dashboard → New Analysis → upload → Analyze → Results →
History. Isolated files only work if they have a text layer (a scanned/image PDF will be rejected with a clear message).

**Highest-value follow-ups, in order:**

1. **Make authentication real.** Add JWT issuance on login plus a `OncePerRequestFilter`, then replace the four
   `permitAll` rules with `authenticated()` and enforce ownership by adding the user relation to `Resume`,
   `JobDescription` and `ResumeAnalysis`. *This is the single biggest gap* — until it is done the app has no security
   boundary, and the Settings page documents that.
2. **Expose `keywordFrequencies` on `AnalysisResponse`** (it is already computed and stored) so the Results page can
   show the per-keyword Resume-vs-JD parity that the Stitch design intended.
3. **Add a dashboard aggregate endpoint** once the data volume makes 5 extra detail calls per dashboard load wasteful.
4. **Decide on AI recommendations.** If wanted, it is a genuinely new capability requiring an LLM provider, a new
   endpoint and new UI states — and the placeholder should be replaced only when it actually works.
5. **Add PDF export** (server-side render) if the disabled "Export PDF Report" button should become functional.
6. **Add version control.** The project is still **not a git repository** and has no commit history; initialising one
   is strongly advisable before further work.
7. **Move the project out of OneDrive** if the file-locking problems in §18.10 continue.
