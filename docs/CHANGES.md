
---

# Second round — keyword frequency, PDF export, AI layer, tests, offline assets

Continues from the sections above. Stages A–B (authentication, ownership) were already complete; this round covers
stages C–I of the review brief. The baseline for this round was **43 backend tests passing, frontend build
passing**, both re-verified before any edit.

## Stage C — Keyword frequency and coverage

| File | Status | Change |
|---|---|---|
| `…/dto/AnalysisResponse.java` | modified | Added `keywordCoverage` (a list of `KeywordCoverage`) and the nested DTO `{keyword, jobDescriptionCount, resumeCount, matched, flagged, flagReason, inJobDescription}`. Also added `aiRecommendations`, `aiGeneratedAt`, `aiProvider`, `aiModel`, and a `source` field on `Recommendation` (stage E). |
| `…/entity/ResumeAnalysis.java` | modified | Added `keyword_coverage_json` and the AI columns. |
| `…/service/AnalysisService.java` | modified | New `buildKeywordCoverage(...)`: the row universe is the JD's keywords plus anything the repetition detector flagged (so repetition is visible even for terms the advert did not ask for), ordered by JD emphasis → resume frequency → alphabetically. Counts are whole-word occurrences; the only judgement applied is the detector's `flagged` flag. Persisted and read back with null-safe deserialisation. |
| `frontend/src/types/api.ts` | modified | Added `KeywordCoverage`, `AiStatus`, and the new `Analysis` fields. |
| `frontend/src/utils/keywords.ts` | **added** | Pure, React-free presentation logic: `buildKeywordRows`, `summariseKeywords`, `relativeBarPercent`, `statusTone`, `splitForDisplay`. Repetition is mapped to an advisory `warning` tone, never `danger`. |
| `frontend/src/pages/ResultsPage.tsx` | modified | New **Keyword Frequency** tab: summary tiles, a coverage bar, and a per-keyword table with two count bars, a status badge and the detector's reason. Horizontally scrollable on narrow screens. The old "counts are not part of the response contract" note was replaced by a link to the new tab. |

## Stage D — PDF report export

| File | Status | Change |
|---|---|---|
| `…/service/report/AnalysisReportPdfService.java` | **added** | Renders a stored analysis as a multi-page A4 PDF with PDFBox and Standard-14 fonts (no embedded assets). Auto-paginating layout, score bars, keyword table, structure audit, recommendations, a deterministic-analysis disclaimer, and a `sanitize()` pass that folds Unicode so untrusted text cannot break rendering. |
| `…/exception/ReportGenerationException.java` | **added** | Typed failure, mapped to `500`. |
| `…/exception/GlobalExceptionHandler.java` | modified | Added the report handler (logging the cause server-side) plus the two AI handlers. Added a logger to the class. |
| `…/controller/AnalysisController.java` | modified | Added `GET /api/analyses/{id}/report`: ownership-scoped, `application/pdf`, `attachment` with a `[a-z0-9-]`-only slug, `no-store`. |
| `…/config/SecurityConfig.java` | modified | Exposes `Content-Disposition` (and `Content-Type`/`Content-Length`) via CORS, without which the browser hides the suggested download name from the client. |
| `frontend/src/services/api.ts` | modified | Added `requestBlob()` (same auth/error handling as `request`) and `saveBlobAsFile()`. |
| `frontend/src/utils/http.ts` | **added** | `friendlyMessage`, `fileNameFromContentDisposition` (RFC 5987 first), `fallbackDownloadName`. Extracted into a Vite-free module so it can be unit-tested in plain Node. The 502/503/504 messages were reworded to be accurate for the AI endpoints as well as for a down backend. |
| `frontend/src/services/analysisService.ts` | rewritten | Added `aiStatus()`, `generateAiRecommendations()`, `downloadReport()`. |
| `frontend/src/pages/ResultsPage.tsx` | modified | The previously disabled "Export PDF Report" button now works, with a busy state and success/error feedback. |

## Stage E — Optional LLM recommendations

| File | Status | Change |
|---|---|---|
| `…/config/LlmProperties.java` | **added** | All configuration from environment variables; no default key. `isUsable()` requires the feature to be switched on **and** fully configured, so a half-configured deployment behaves exactly like a disabled one. `unavailableReason()` is safe to display (never contains the key). |
| `…/service/ai/LlmHttpClient.java` | **added** | The only place that talks to a provider. JDK `HttpClient` with explicit connect/read timeouts; OpenAI-compatible `/chat/completions` so the provider is a configuration change. Prompt/response bodies are never logged — only status and error category. Maps 401/403, 429, 5xx and transport failures to distinct messages. |
| `…/service/ai/AiRecommendationService.java` | **added** | Builds the prompt from measured findings, computes the allow-list, neutralises delimiters inside the documents, then parses and validates the response: fences/HTML stripped, lengths capped, category/priority normalised, `relatedItems` restricted to the allow-list, fabrication assertions discarded, item count capped. `generateForAnalysis` enforces ownership, persists the result and returns the refreshed DTO. |
| `…/exception/AiUnavailableException.java`, `AiGenerationException.java` | **added** | `503` (not configured) vs `502` (provider error), which the UI can distinguish. |
| `…/controller/AiController.java` | **added** | `GET /api/ai/status` — capability probe returning `{enabled, reason, provider, model, maxRecommendations, disclaimer}` and never the key or the full URL. |
| `…/controller/AnalysisController.java` | modified | Added `POST /api/analyses/{id}/ai-recommendations`. |
| `…/service/AnalysisService.java` | modified | Exposed `findOwnedEntity` / `toResponse`, and added `storeAiRecommendations`, which writes **only** the AI columns. Dependency direction stays one-way: `service/ai → service`. |
| `…/resources/application.properties` | modified | Added the documented `app.llm.*` block, disabled by default. |
| `frontend/src/pages/ResultsPage.tsx` | modified | New AI Suggestions block inside Recommendations: shows the reason when disabled, a generate/regenerate button when enabled, AI-labelled cards with a disclaimer, and error handling for 502. The auto-patch block's copy was corrected (suggestions exist now; rewriting a student's history as fact still deliberately does not). |

## Stage F — Frontend automated tests

| File | Status | Change |
|---|---|---|
| `frontend/tests/keywords.test.ts` | **added** | 17 tests for the keyword-frequency logic. |
| `frontend/tests/http.test.ts` | **added** | 15 tests for status messaging and `Content-Disposition` parsing. |
| `frontend/tests/format.test.ts` | **added** | 12 tests for the shared formatting helpers. |
| `frontend/tests/icons.test.ts` | **added** | 8 tests for icon-font detection, including the fail-open behaviour. |
| `frontend/tests/session.test.ts` | **added** | 9 tests for the token store, using an in-memory `localStorage` shim, including a storage that throws. |
| `frontend/package.json` | modified | Added `npm test` / `npm run test:all` using Node's built-in runner with type-stripping. **No test framework was added** — the npm registry is unreachable in this environment, so Vitest/Testing Library could not be installed. `tsconfig.json` already restricted `include` to `src`, so the tests do not affect the production build. |

## Stage G — Offline font and icon support

| File | Status | Change |
|---|---|---|
| `frontend/src/index.css` | modified | **The important fix.** `.material-symbols-outlined` had no rule in this project at all — Google's stylesheet normally supplies it, so with no network the ligature names would be painted as literal words and every icon box would break. The rules are now defined locally (webfont first, system font after), and `html[data-icons='unavailable']` hides the text with `-webkit-text-fill-color` (keeping `currentColor` intact for the placeholder) while painting a neutral `::after` placeholder inside the same box. |
| `frontend/src/utils/icons.ts` | **added** | `resolveIconFontState`, `applyIconFontState`, `initialiseIconFontDetection`. Awaits `document.fonts.load` + `fonts.ready` with a 3 s ceiling and **fails open**, so an unknown state never hides working icons. |
| `frontend/src/main.tsx` | modified | Calls the detection after render; deliberately not awaited so first paint is never delayed. |
| `frontend/index.html` | modified | Added `&display=block` to the icon-font URL (no flash of literal icon names while loading) and documented that the webfonts are progressive enhancement only. |
| `frontend/tailwind.config.js` | modified | Cross-platform fallback stacks for every family (system-ui / Segoe UI / Roboto / Helvetica Neue / Arial for text; ui-monospace / Consolas / Liberation Mono for the code faces), plus `sans` and `mono` defaults. **Existing class names and the primary faces are unchanged.** |

## Stage H — Verification

| File | Status | Change |
|---|---|---|
| `scripts/e2e-api-check.ps1` | extended | Grew from 46 to **63 checks**: DOCX upload, PDF report download (headers, `%PDF-` magic, size, cross-user `404`, anonymous `401`), AI status/fallback behaviour, and report-gone-after-delete. Section order was corrected so the delete check runs last. |
| `scripts/e2e-restart-persistence.ps1` | **added** | Starts the backend twice against a **file-based** H2 database with a fixed `JWT_SECRET` and verifies 16 properties survive a real restart. Uses `spring-boot:run -Dspring-boot.run.fork=false` so the app runs in the Maven JVM and one process tree owns the port; `mvn package` was rejected because `maven-jar-plugin` is absent from the offline Maven cache. |
| `scripts/make-test-resume-docx.ps1` | **added** | Generates `scripts/test-resume.docx` by writing the OPC zip with `System.IO.Compression.ZipArchive` and forward-slash entry names (`Compress-Archive` writes backslashes, which strict OOXML readers reject). Reads the archive back to prove it is well formed. |
| `scripts/test-resume.docx` | **added** | The DOCX end-to-end fixture (1483 bytes, 865 characters extracted). |
| All `scripts/*.ps1` | modified | Re-saved as **UTF-8 with BOM**. PowerShell 5.1 reads `.ps1` as ANSI without a BOM, which broke parsing on a non-ASCII character. |
| `…/test/…/controller/JwtExpiryIntegrationTest.java` | **added** | Proves token expiry is enforced using a 3-second lifetime. |
| `…/test/…/controller/AiProviderFailureIntegrationTest.java` | **added** | 3 tests against an intentionally unreachable provider: status hides the key, an unreachable provider yields `502` promptly and persists nothing, and another user still gets `404`. |
| `…/test/…/service/ai/AiRecommendationServiceTest.java` | **added** | 13 unit tests for prompt building and response validation. |
| `…/test/…/service/report/AnalysisReportPdfServiceTest.java` | **added** | 7 tests that parse the generated PDF back and assert its text, including pagination and Unicode sanitisation. |
| `…/test/…/controller/AnalysisControllerIntegrationTest.java` | modified | Extended to 17 tests (keyword coverage assertions, report download, AI status/503/separation). |
| `…/test/…/controller/ResumeControllerIntegrationTest.java` | modified | Extended to 16 tests, including one that uploads the committed DOCX fixture so the fixture itself is guarded. |
| `…/test/…/controller/SecurityAndOwnershipIntegrationTest.java` | modified | Extended to cover the three new endpoints in both the unauthenticated and cross-user cases. |

## Stage I — Documentation

| File | Status | Change |
|---|---|---|
| `README.md` | rewritten | Final state: objectives, stack, feature status, prerequisites, database/JWT/LLM configuration, run commands (including the `mvn package` caveat), API summary with the three new endpoints, scoring formula, testing, scripts, 12 known limitations and 8 future enhancements. |
| `docs/REVIEW-GUIDE.md` | **added** | Timed five-minute demonstration script, commands to have ready, a claim → file-to-open table, likely questions with honest answers, the exact demo job description, and a troubleshooting table. |
| `docs/ARCHITECTURE.md` | extended | New report, AI and offline-asset data-flow sections; updated component diagram and data model; extra design-decision rows including why AI output lives in separate columns. |
| `docs/TESTING.md` | rewritten | Full inventory of all 92 backend, 55 frontend, 63 E2E and 16 restart checks with the exact commands and observed values, the step-by-step mapping to the review brief, and an explicit "could not verify" section. |
| `docs/CHANGES.md` | extended | This section. |

## Deliberately unchanged in this round

* The five analysis engine components' algorithms (including the ATS weights 40/25/15/12/8) — except the earlier
  trailing-period fix, which is documented in the first-round table.
* Every existing API path, request body and response field. All additions are additive; the only behavioural change
  to an existing endpoint is that `recommendations[*]` now carries `source: "RULE"`.
* The visual design, Tailwind colour tokens, component structure and page layout. The only UI additions are the new
  Keyword Frequency tab, the AI block, the working export button, and the corrected copy.
* `application-dev.properties`, `application-test.properties`, and every database default.
* No dependency was upgraded and **no file was deleted**. Three stale log files at the repository root
  (`_baseline-test.log`, `_final-test.log`, `_post-api-test.log`, `_backend-run.log`) were left in place and are
  listed as optional clean-up rather than removed without permission.
