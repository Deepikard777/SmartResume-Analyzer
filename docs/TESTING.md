# Test Cases and Results

Every result in this document was produced by actually running the command shown, on this machine
(JDK 24.0.1 building the project's Java 17 target, Node 22.23.2, npm 10.9.8). Nothing here is estimated, and no
check is reported as passing that was not observed to pass.

---

## 1. How to run everything

```powershell
# 1. Backend unit + integration tests (in-memory H2; does not touch MySQL)
cd resume-analyzer\resume-analyzer
.\mvnw.cmd test "-Dspring.profiles.active=test"

# 2. Frontend unit tests (Node's built-in runner — no extra dependencies)
cd frontend
npm test

# 3. Frontend type-check (strict) + production build
npm run build

# 4. Live end-to-end API check — start the backend first, then:
powershell -ExecutionPolicy Bypass -File scripts\e2e-api-check.ps1

# 5. Persistence across a real backend restart (starts and stops the backend itself)
powershell -ExecutionPolicy Bypass -File scripts\e2e-restart-persistence.ps1
```

---

## 2. Summary of actual results

| Suite | Command | Result |
|---|---|---|
| Backend | `.\mvnw.cmd test "-Dspring.profiles.active=test"` | **Tests run: 98, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS** |
| Frontend unit | `npm test` | **tests 55, suites 17, pass 55, fail 0** |
| Frontend build | `npm run build` | **PASS** — `tsc -b` strict + `vite build`, `57 modules transformed`, 289.48 kB JS / 26.86 kB CSS |
| End-to-end API | `scripts\e2e-api-check.ps1` | **63 checks, 63 passed, 0 failed** (`RESULT: ALL CHECKS PASSED`, exit 0) |
| Restart persistence | `scripts\e2e-restart-persistence.ps1` | **16 checks, 16 passed, 0 failed** (`RESULT: ALL CHECKS PASSED`, exit 0) |
| Seed script | `scripts\seed-demo-analysis.ps1` | **PASS** — registered a user, uploaded, created a JD, analysed, printed history |

For reference, the backend suite contained 43 tests before this round of work; it now contains 98 — 92 from the
rounds described below plus `CorsConfigurationIntegrationTest` (6 tests, §3.13), added with the CORS origin fix. **No test was
removed, disabled, skipped or weakened at any point** — the count only grew, and one regression the new tests caught
(a `DELETE` endpoint accidentally replaced by a later edit) was fixed rather than accommodated.

---

## 3. Backend test inventory — 98 tests

### 3.1 `AuthControllerIntegrationTest` — 11 tests

| # | Case | Expected |
|---|---|---|
| 1 | Successful registration | `201`, echoes name/email/id, **no** `password` key anywhere in the body |
| 2 | Duplicate email registration | `409 Conflict` |
| 3 | Password stored hashed | Stored value starts with `$2a$` and BCrypt matches the raw password |
| 4 | Password never returned | No raw password and no `"password"` key |
| 5 | Successful login | `200`, `Login successful`, correct user, token issued |
| 6 | Login with wrong password | `401` |
| 7 | Login with unknown email | `401` — same message as case 6 |
| 8 | Register validation failure | `400` with field errors for name, email and password |
| 9 | Login validation failure | `400` with field errors for email and password |
| 10 | `GET /api/auth/me` | `200` with the token's account **with** a token; `401` **without** |
| 11 | Tampered / malformed token | `401` (never `500`) |

### 3.2 `JwtExpiryIntegrationTest` — 1 test *(new)*

Runs with `app.jwt.expiration-ms=3000`. Asserts the token works on both a public-ish and a data endpoint, then
**waits past the lifetime** and asserts `401` with the standard error envelope on both — and finally that the
account still exists and can sign in again. This is the only way to observe expiry without waiting 24 hours.

### 3.3 `ResumeControllerIntegrationTest` — 16 tests

Valid PDF upload; valid DOCX upload; **the committed `scripts/test-resume.docx` fixture parses** (guards the fixture
the E2E script depends on); unsupported type (`.txt`) → `400`; file > 10 MB → `400`; empty file → `400`; PDF text
extraction ≥ 50 chars; DOCX text extraction ≥ 50 chars; metadata persisted (UUID stored name, `PARSED` status,
timestamps); list resumes; resume detail includes `extractedText`; unknown id → `404`; delete removes the row **and**
the file from disk; delete unknown id → `404`; response leaks no internal path; two uploads both appear in the list.

### 3.4 `JobDescriptionControllerIntegrationTest` — 8 tests

Create (`201`); missing `jobTitle` → `400`; missing `descriptionText` → `400`; list; get by id; unknown id → `404`;
delete → `204` and row removed; delete unknown id → `404`.

### 3.5 `AnalysisControllerIntegrationTest` — 17 tests

| # | Case | Expected |
|---|---|---|
| T01 | Analyse returns the full payload | `201` with score, five sub-scores, matched/missing skills and keywords, stuffing risk, **keyword coverage rows with both counts**, 8 structure checks, recommendations tagged `source=RULE` |
| T02 | Result is persisted | Repository holds one row with the coverage JSON and `"source":"RULE"` |
| T03 | `GET /api/analyses/{id}` | Same stored ATS score and skills |
| T04 | History | One lightweight summary; no `matchedSkills`, no `structure` |
| T05 | Unknown resume id | `404` |
| T06 | Unknown job description id | `404` |
| T07 | Resume with no extracted text | `422` |
| T08 | Missing `resumeId` | `400` with a field error |
| T09 | Unknown analysis id | `404` |
| T10 | Full HTTP flow | Upload DOCX → create JD → analyse → reload returns identical score and file name |
| T11 | `DELETE /api/analyses/{id}` | `204`; row gone; resume and JD untouched |
| T12 | Delete unknown id | `404` |
| T13 | **`GET …/report`** | `200`, `application/pdf`, `attachment`, `no-store`, body starts `%PDF-`; text extracted back from the PDF contains every required section, the owner's name and `Docker`; and **no** storage path, stored file name or `storagePath` text |
| T14 | Report for unknown id | `404`, not a PDF |
| T15 | **`GET /api/ai/status`** | `enabled:false` + a reason naming `app.llm`; no `apiKey`, `provider` or `model` leaked |
| T16 | **AI request with no provider** | `503`; then the analysis still returns its score, `source=RULE` recommendations, and no `aiRecommendations` field |
| T17 | **AI output separation** | Stored AI output is returned in `aiRecommendations` with `source=AI` while the deterministic list keeps `source=RULE` and **the score is unchanged** |

### 3.6 `AiProviderFailureIntegrationTest` — 3 tests *(new)*

Context configured with `app.llm.enabled=true` and an intentionally unreachable base URL (a closed local port), so
the transport really fails — no provider is contacted.

| # | Case | Expected |
|---|---|---|
| 1 | Status with the feature enabled | `enabled:true`, provider host only, **no API key**, no reason |
| 2 | Unreachable provider | `502` with a "could not be reached" message, returned **within the configured timeout** (asserted `< 15 s`, so a hang fails the test); nothing persisted; the deterministic result still serves |
| 3 | Another user, feature enabled | `404` for both the AI endpoint and the report — the ownership check is genuinely reached |

### 3.7 `SecurityAndOwnershipIntegrationTest` — 4 tests

Two users registered through the real endpoint, acting only through the real HTTP API.

| # | Case | Assertions |
|---|---|---|
| T01 | Unauthenticated access refused | `401` for all 13 protected request shapes, including `GET /api/analyses/{id}/report`, `GET /api/ai/status` and `POST /api/analyses/{id}/ai-recommendations` |
| T02 | Cross-user access blocked | User B gets `404` reading/deleting/analysing A's resume, JD and analysis, and downloading A's report; B's three lists are empty; A still sees its own records |
| T03 | History is per user | With one run each, A sees only A's (correct file name) and B only B's |
| T04 | Recommendations exposed | A stored analysis returns a recommendations array including a `Skills` entry, each with priority and title |

### 3.8 `AnalysisReportPdfServiceTest` — 7 tests *(new)*

Unit tests that parse the generated bytes back with PDFBox and assert the extracted text:

1. a complete report contains every required section (score, sub-scores, skills, keyword table, structure,
   recommendations, owner, date, file name);
2. repetition is reported as a flag while the raw counts stay visible, and the report says repetition is not
   penalised;
3. **30 long recommendations paginate** — page count > 1 and both the first and last item are present (nothing is
   clipped);
4. Unicode sanitisation: typographic quotes/dashes/ellipsis, accented characters, `×` and `·`, and emoji all fold
   to ASCII instead of throwing;
5. a document full of unmappable text still renders;
6. a null analysis raises `ReportGenerationException` (not an NPE);
7. a minimal analysis with no optional data still produces a valid document.

### 3.9 `AiRecommendationServiceTest` — 13 tests *(new)*

No provider and no Spring context; covers prompt construction and output validation.

| Group | Cases |
|---|---|
| Gating | `isUsable()` is false when disabled or partially configured; the unavailable reason names the missing setting and never contains a key |
| Prompt | Findings and the allow-list are present; delimiters inside a document are neutralised (the closing marker appears exactly once in the prompt); the system prompt forbids fabricating skills, achievements, employers and metrics |
| Allow-list | Built only from measured findings; contains the real skills/keywords/sections and nothing invented |
| Validation | Well-formed output accepted and tagged `source=AI`; **`relatedItems` outside the allow-list dropped**; **fabrication assertions discarded**; markdown/HTML stripped; unknown category normalised; over-long output truncated to the cap; malformed/non-JSON raises `AiGenerationException`; the `{recommendations:[…]}` wrapper **and** a bare array are both accepted |

### 3.10 `KeywordMatcherTest` — 4 tests

Sentence-final periods do not create phantom keywords (`experience.`, `required.`); dotted and symbol-suffixed names
survive (`node.js`, `asp.net`, `c++`, `ci/cd`); stopwords and tokens shorter than 3 characters are excluded; whole-word
matching never matches `java` inside `javascript`.

### 3.11 `RecommendationGeneratorTest` — 7 tests

Missing required skills → one HIGH `Skills` item naming them, whose wording tells the user to add evidence only if
they genuinely have it; a clean analysis → no recommendations; missing keywords ordered by JD emphasis; stuffing
flags → `Repetition` at the matching priority; structure warnings → MEDIUM `Structure`; low relevance and low
formatting → their own items; ordering is HIGH → MEDIUM → LOW.

### 3.12 `ResumeAnalyzerApplicationTests` — 1 test

Spring context loads.

### 3.13 `CorsConfigurationIntegrationTest` — 6 tests *(new)*

Added after the SPA could not reach the API from `http://127.0.0.1:5173`: the allow-list held only the
`localhost` spellings, so the browser's preflight was answered `403` with no `Access-Control-Allow-Origin`.
MockMvc issues real `OPTIONS` preflights and asserts that — the preflight for `POST /api/auth/register` from
`http://127.0.0.1:5173` is `200` with the matching allow-origin, allow-methods (including `POST`) and
allow-headers; `POST /api/auth/login` from `http://localhost:5173` is still allowed; a preflight for a protected
endpoint (`/api/analyses`) is answered without a token; the real call to that endpoint without a token is still
`401` **and** still carries the allow-origin header, so the browser can read the error; and an origin that is not
on the list (`http://evil.example.com`, `http://localhost:5174`) is refused with no allow-origin header — so the
fix cannot silently degrade into a blanket allow.

---

## 4. Frontend test inventory — 55 tests

Run with Node's built-in test runner and TypeScript type-stripping, so **no test dependency was added**
(`npm test`). The tests target the frontend's pure logic; see §6 for what that deliberately excludes.

| File | Tests | Covers |
|---|---|---|
| `tests/keywords.test.ts` | 17 | `buildKeywordRows` (empty input, matched, missing, repetition-as-advisory with the raw count preserved, repetition precedence over matched, resume-only, de-duplication, clamping of negative/NaN counts), `summariseKeywords` (coverage over JD keywords only, zero rather than NaN, max count for bar scaling, flagged-not-in-JD), `relativeBarPercent` (scaling, zero denominator, clamping), `statusTone`, `splitForDisplay` |
| `tests/http.test.ts` | 15 | `friendlyMessage` for every status the API returns, the generic fallback, and that **no message leaks a Java exception or class name**; `fileNameFromContentDisposition` (quoted, unquoted, RFC 5987 preferred and decoded, missing/unparseable → null, malformed percent-encoding, no false match); `fallbackDownloadName` |
| `tests/format.test.ts` | 12 | `scoreBand` and `levelLabel` at each documented threshold, `formatBytes` units and null handling, `uploadStatusLabel` mapping and pass-through, `parseBackendDate` with valid/invalid input returning null rather than an Invalid Date |
| `tests/icons.test.ts` | 8 | `resolveIconFontState` (available, unavailable, real family name queried, **fail-open** when the API is absent or throws, explicit family), `applyIconFontState` writes and returns the state |
| `tests/session.test.ts` | 8 + 1 | Token and cached-user round trips, **no password persisted**, `clearSession` removes both keys, the 401 handler is notified once per call and is replaceable, no handler → no throw, and a storage that throws does not break the app |

---

## 5. End-to-end API check — 63 checks

`scripts/e2e-api-check.ps1` drives the real workflow against a running backend with real files. Observed run:

| Section | Checks | Result |
|---|---|---|
| 1. Register | `201`; token issued; account echoed; no password field | 4/4 PASS |
| 2. Login | `200`; token issued; no password field | 3/3 PASS |
| 2b. `GET /api/auth/me` | `200`; returns the token owner | 2/2 PASS |
| 3. Upload PDF | `201`; `PARSED`; 1013 characters extracted | 3/3 PASS |
| **3b. Upload DOCX** | `201`; `PARSED`; 865 characters extracted | 3/3 PASS |
| 4. Create job description | `201` | 1/1 PASS |
| 5. Analyse | `201`; score in range; 5 sub-scores; 8 structure checks; recommendations present; **no `extractedText` in the response** | 6/6 PASS |
| 6. History | `200`; exactly 1 record; same analysis id | 3/3 PASS |
| 7. Persistence | Reload `200`; identical score, matched skills and recommendation count | 4/4 PASS |
| 8. Negative cases | `.txt` → `400`; empty file → `400`; empty JD → `400`; unauthenticated list/upload/analyse → `401`; tampered token → `401` | 7/7 PASS |
| 9. Cross-user isolation | B's lists empty; B cannot read A's resume/analysis/JD (`404`); B cannot delete A's resume (`404`); A's two resumes intact | 8/8 PASS |
| 10. Token lifetime | Token still accepted until expiry | 1/1 PASS |
| **11. PDF report** | `200`; `application/pdf`; `attachment` with a `.pdf` name; `no-store`; body starts `%PDF-`; **4471 bytes**; B → `404`; anonymous → `401` | 8/8 PASS |
| **12. AI feature** | Status `200` reporting `enabled=false` with a reason; generation → `503`; score unchanged afterwards; deterministic advice still `source=RULE` | 5/5 PASS |
| **13. Delete** | Owner deletes own analysis → `204`; gone → `404`; history empty; resume untouched; the report of a deleted analysis → `404` | 5/5 PASS |

### Observed values for the analysis run

`scripts/test-resume.pdf` (1013 characters extracted) against the job description in
[REVIEW-GUIDE.md](REVIEW-GUIDE.md) §6:

| Metric | Value |
|---|---|
| ATS score | **71 / 100** |
| Skills / Keywords / Relevance / Structure / Formatting | 67 / 62 / 62 / 95 / 100 |
| Matched skills | Java, Spring Boot, Spring, React, PostgreSQL, SQL |
| Missing skills | Microservices, Docker, AWS |
| Matched keywords | boot, engineer, java, postgresql, react, software, spring, sql |
| Missing keywords | aws, docker, looking, microservices, required |
| Repetition risk | MEDIUM (`java` ×5) |
| Structure audit | 7 passed / 1 warning (Internships — an optional section) |
| Recommendations | 4 — two HIGH (`Keywords`, `Skills`), two MEDIUM (`Repetition`, `Structure`) |
| PDF report | 4471 bytes, `Content-Type: application/pdf`, starts `%PDF-`, sent as an `attachment` with a `.pdf` name slugged from the job title |

Reloading the analysis afterwards returned the same score, the same matched-skill list, the same four
recommendations and the same keyword-coverage rows, confirming the values come from the stored row.

---

## 6. Restart-persistence check — 16 checks

`scripts/e2e-restart-persistence.ps1` starts the backend, creates data, **stops it, starts it again** against the
same file-based H2 database with a fixed `JWT_SECRET`, and then verifies:

| Check | Result |
|---|---|
| User registered before the restart | PASS |
| PDF resume parsed before the restart | PASS |
| DOCX resume parsed before the restart | PASS |
| Job description created before the restart | PASS |
| Analysis created before the restart | PASS |
| The pre-restart token is still accepted (`200`) | PASS |
| The account still exists and can sign in | PASS |
| Both resumes survived | PASS |
| The job description survived | PASS |
| The analysis history survived | PASS |
| **Stored ATS score is identical after the restart** | PASS |
| Matched skills identical after the restart | PASS |
| Recommendation count identical after the restart | PASS |
| Keyword-coverage row count identical after the restart | PASS |
| Resume file names still correct | PASS |
| A different user still cannot read the analysis (`404`) | PASS |

Observed: analysis `#1`, `atsScore=66`, 4 recommendations, 10 keyword-coverage rows — all identical before and
after the restart.

---

## 7. Manual workflow checklist (the review sequence)

Steps 1–18 of the review brief map onto the automated checks as follows.

| # | Step | Status |
|---|---|---|
| 1 | Register a new student | API-verified (`201` + token) |
| 2 | Log in and obtain a token | API-verified (`200` + token) |
| 3 | Upload a valid PDF resume | API-verified (`201`, `PARSED`, 1013 chars) |
| 4 | Upload a valid DOCX resume | API-verified (`201`, `PARSED`, 865 chars) |
| 5 | Enter a job description | API-verified (`201`) |
| 6 | Run the ATS analysis | API-verified (`201`) |
| 7 | View score, matched and missing skills | API-verified in the payload |
| 8 | Review keyword frequency and repetition indicators | API-verified (`keywordCoverage` rows incl. counts and flags) |
| 9 | View actionable recommendations | API-verified (4 items, `source=RULE`) |
| 10 | Download the PDF report | API-verified (real `%PDF-` document, 4471 bytes, correct headers) |
| 11 | Open analysis history | API-verified (`200`, 1 record) |
| 12 | Refresh and confirm persisted results | API-verified (reload returns the identical stored result) **and** across a full restart (§6). The browser-side refresh path is covered by `GET /api/auth/me` plus the route guard; see §8. |
| 13 | Log out and test unauthenticated access | API-verified (`401` for all 13 protected request shapes) |
| 14 | Test invalid files and empty inputs | API-verified (`400` ×3, `422` cases covered by backend tests) |
| 15 | Test expired, invalid and tampered tokens | Expired: `JwtExpiryIntegrationTest`. Tampered and malformed: backend tests + E2E. |
| 16 | Create a second user and verify data isolation | API-verified (B sees empty lists, `404` for A's ids) |
| 17 | Restart the backend and verify persistence | API-verified (§6, 16 checks) |
| 18 | Verify the frontend and backend builds | `mvnw test` + `npm run build` both PASS |

---

## 8. What could NOT be verified, and why

**Browser-level UI verification was not completed in this environment.** The built-in browser is disabled in Accio
Work Settings (Browser), and the Chrome extension relay reported "not connected" when a browser session was
attempted. The attempt reached only the first step (opening the app redirected to `/login`) before the session
ended. This is reported as **NOT VERIFIED**, not as a pass.

Consequently the following rest on type-level, contract-level and unit-level evidence rather than a scripted
browser run, and should be confirmed by hand once using [REVIEW-GUIDE.md](REVIEW-GUIDE.md):

1. That the running app attaches the token to every request. Structurally guaranteed — `services/api.ts` is the
   only `fetch` in the codebase and attaches it centrally.
2. That the Results page's six tabs, the keyword-frequency table and the AI panel render as intended at 1440 px and
   375 px. The components type-check strictly and their pure logic is unit-tested; the rendering itself is not.
3. That the PDF download button triggers a browser download. The endpoint, headers, filename parsing and blob
   handling are all verified, but the final click → save interaction is not.
4. **The UI screenshots in `verify-ui/` are historical** — captured before authentication existed. They show the old
   "no token issued" and "AI Recommendations — not implemented" states and must not be presented as current.

Also not covered by any automated check: performance under load, concurrent analysis requests, MySQL-specific
behaviour (all automated runs used H2), browser-specific quirks, and offline *icon fidelity* (the fallback keeps the
layout correct but draws a placeholder — see README §13 item 12).

---

## 9. Reproducing the exact numbers

```powershell
# Terminal 1 — backend with a stable signing key
$env:JWT_SECRET = 'local-verification-secret-0123456789abcdef'
cd resume-analyzer\resume-analyzer
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"

# Terminal 2
cd resume-analyzer
powershell -ExecutionPolicy Bypass -File scripts\e2e-api-check.ps1
```

Each `dev`-profile run starts from an empty in-memory database and the check script registers fresh users with
unique emails, so the run is repeatable. The ATS score depends on the resume and job description used; the values in
§5 correspond exactly to `scripts/test-resume.pdf` and the job description quoted in
[REVIEW-GUIDE.md](REVIEW-GUIDE.md) §6.

The restart script is fully self-contained — it starts and stops the backend itself — and needs port 8080 to be
free.

---

## 10. A note on the two encoding issues found while building this

Recorded because they are easy to hit again on Windows:

1. **PowerShell 5.1 reads `.ps1` files as ANSI unless a BOM is present.** Scripts containing non-ASCII characters
   (an em dash, for example) fail to parse with a confusing "Unexpected token" error. All scripts in `scripts/` are
   therefore saved as **UTF-8 with BOM**.
2. **`Compress-Archive` writes backslash entry names**, which strict OOXML readers reject. `make-test-resume-docx.ps1`
   therefore builds the DOCX with `System.IO.Compression.ZipArchive` directly, using forward-slash entry names, and
   reads it back to prove the archive is well formed.
