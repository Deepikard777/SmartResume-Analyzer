# SmartResume Analyzer — Pre-Review Audit & Ordered Plan

**Date:** 2026-10-11
**Scope:** Read-only inspection of the extracted project (`resume-analyzer/`) before any change.
**Rule applied:** No rebuild. No architecture replacement. No deletion of working code.

---

## 1. What the archive actually contains

| Path | What it is |
|---|---|
| `frontend/` | Vite 5 + React 18 + TypeScript 5.6 + Tailwind 3.4 UI (routes, services, context, 8 pages) |
| `resume-analyzer/resume-analyzer/` | Spring Boot 3.5.5 backend (Maven wrapper present) |
| `_stitch_source/` | Original static HTML design deliverable, kept as reference |
| `docs/` | Prior integration audit + integration report |
| `verify-ui/` | 7 browser screenshots + a prior UI verification report |
| `scripts/` | `run-backend.cmd`, `run-frontend.cmd`, `e2e-api-check.ps1`, `check-servers.ps1`, `seed-demo-analysis.ps1`, `make-test-resume-pdf.mjs` |
| Root logs | `_backend-run.log`, `_baseline-test.log`, `_final-test.log`, `_post-api-test.log` (stale artefacts from the earlier session) |

The project is **already integrated and end-to-end verified** by a previous session. Prior documentation
claims 43 passing backend tests and a passing frontend build. **Both claims are being re-verified in this
session** before anything is changed.

---

## 2. Status by area

| Area | Status | Evidence |
|---|---|---|
| Resume upload (PDF/DOCX) | **Implemented** | `ResumeController`, `ResumeService`, `ResumeFileValidationService`, `ResumeTextExtractionService` |
| File type + size validation | **Implemented** | extension + MIME checks, 10 MB cap, empty-file check |
| Corrupt / scanned / unreadable handling | **Implemented** | `ResumeParsingException` → 422; `FAILED` upload status row + file cleanup |
| ATS scoring engine | **Implemented** | `AnalysisService` + 5 engine components, weights 40/25/15/12/8 |
| Skill match vs JD | **Implemented** | `SkillMatcher` + `SkillDictionary` (whole-word, aliases, optional-signal windows) |
| Keyword match vs JD | **Implemented** | `KeywordMatcher` (stopwords, token boundaries) |
| Keyword-stuffing detection | **Implemented** | `KeywordStuffingDetector` (density + absolute thresholds; LOW/MEDIUM/HIGH) |
| Structure / formatting analysis | **Implemented** | `ResumeStructureAnalyzer` (8 sections), `FormattingAnalyzer` (5 checks) |
| Recommendations | **Partial** | Client-side only (Results page derives 3 cards). No backend recommendations, no persisted field |
| Persistence / history | **Implemented** | JPA entities + repositories; `GET /api/analyses` ordered newest-first |
| Frontend pages + routes | **Implemented** | Login, Register, Dashboard, New Analysis, Results, History, Settings, 404 |
| Loading / error / empty states | **Implemented** | `useAsync`, `ApiError`, `describeError`, `ErrorPanel` |
| **Authentication** | **Not implemented** | `POST /api/auth/login` returns `{message, user}` only — **no JWT, no session, no cookie** |
| **Authorization / ownership** | **Not implemented** | `Resume.user` is set to `null` on upload; `JobDescription` and `ResumeAnalysis` have no owner relation |
| **Endpoint protection** | **Absent** | `SecurityConfig` marks `/api/resumes/**`, `/api/job-descriptions/**`, `/api/analyses/**` as `permitAll` |
| Password hashing | **Implemented** | BCrypt; `UserResponse` has no password field (asserted by tests) |
| Secret leakage | **None found** | No password/token in any response DTO; `ResumeResponse` omits `storagePath` and `extractedText` |
| Tests | **Present** | 3 test classes + context-load test; **no auth/ownership tests exist** |
| Docs | **Broad but now partly wrong** | README + 2 reports describe the *pre-auth* state; they must be updated |

---

## 3. Findings that block the review

| # | Finding | Impact |
|---|---|---|
| **F1** | **JWT is absent.** Login issues no token; `SecurityConfig` is `STATELESS`. (Verified in source, not assumed.) | Cannot identify a caller → every data endpoint is public. |
| **F2** | **No ownership.** `ResumeService.uploadResume` persists `user(null)`. `JobDescription` / `ResumeAnalysis` have no user FK. | Any user can read/delete any other user's resumes, JDs and analyses. |
| **F3** | **All data endpoints are `permitAll`.** | Unauthenticated access works by design of the current code. |
| **F4** | `ResumeRepository` already declares `findByIdAndUserId` / `findByUserIdOrderByCreatedAtDesc`, but nothing calls them. | Ownership was scaffolded but never wired. |
| **F5** | Recommendations are computed in the frontend only. | Not persisted, not part of the API contract, not testable server-side. |
| **F6** | Docs describe the app as having no authentication. | Would contradict the finished product at review time. |
| **F7** | Stale log/artefact files at repo root (`_backend-run.log`, `_baseline-test.log`, …). | Cosmetic; not deleted without permission. |

---

## 4. Ordered implementation plan

Each step is independently testable. Order is chosen so the app is never left in a broken state.

**Stage A — Authentication (highest priority)**
1. Add `jjwt` (HS256) — already present in the local Maven cache (0.13.0), so no network dependency risk.
2. `JwtService` — issue/parse/validate tokens; secret from `app.jwt.secret` (env-overridable), expiry from config.
3. `JwtAuthenticationFilter` — `OncePerRequestFilter`, reads `Authorization: Bearer`, populates `SecurityContext`.
4. `SecurityConfig` — `/api/auth/register`, `/api/auth/login` public; everything else `authenticated()`; JSON 401 entry point.
5. `AuthResponse` gains `token`; add `GET /api/auth/me` so the client can validate a stored token.
6. Frontend: persist the token, attach the header centrally in `api.ts`, clear session + redirect on 401.

**Stage B — Ownership**
7. Add `user` FK to `JobDescription` and `ResumeAnalysis` (nullable column, so existing MySQL rows are not broken).
8. Scope every service query by the authenticated user; return **404** for another user's record so existence is not leaked.
9. Set the owner on upload / JD create / analysis create; verify **both** resume and JD belong to the caller before analysing.
10. Replace the `permitAll` rules with `authenticated()`.

**Stage C — Recommendations**
11. `RecommendationGenerator` (deterministic, no AI) — actionable items derived from missing required skills, missing
    keywords, structure warnings, stuffing flags and low sub-scores. Free of invented resume content.
12. Persist as `recommendationsJson`; expose `recommendations` on `AnalysisResponse`; render on the Results page.

**Stage D — Tests**
13. Update the 3 existing integration test classes to authenticate (they currently call endpoints anonymously).
14. Add new tests: 401 without token, cross-user 404 isolation, token issuance/validation, recommendation generation.

**Stage E — Verification & docs**
15. Run the full backend test suite and the frontend build; fix anything the changes broke.
16. Update README + add an API/test/architecture document reflecting the **actual** implementation.

### Deliberately NOT doing
- No AI/LLM integration (there is no provider; recommendations stay deterministic).
- No rewrite of the scoring engine, the extraction pipeline, or the visual design.
- No dependency upgrades beyond adding the JWT libraries.

---

## 5. Decisions taken

| Decision | Reason |
|---|---|
| **Use `io.jsonwebtoken` (HS256) rather than hand-rolled crypto** | The library is already in the local Maven cache, is the standard choice for Spring Boot stateless APIs, and avoids custom cryptography. |
| **JWT, not server-side sessions** | Keeps `SessionCreationPolicy.STATELESS`, matches the existing `Authorization` header already allowed by the CORS bean, and needs no new storage. |
| **404, not 403, for another user's record** | Prevents leaking which IDs exist. |
| **Nullable `user_id` on new relations** | `ddl-auto=update` cannot add a `NOT NULL` column to a table that already has rows. |
| **Fix the existing tests rather than disable them** | They test real behaviour; they must keep passing under the new security rules. |
