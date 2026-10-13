# UI Verification Report — SmartResume Analyzer

> ## ⚠ STATUS OF THIS FILE — HISTORICAL, SCREENSHOTS ARE STALE
>
> This is the UI verification report produced **before authentication was added**. The workflow steps and the
> screenshots under `verify-ui/` describe the old build, in which:
>
> * no token was issued (`Auth token → None issued`),
> * the Recommendations tab read *"AI Actionable Recommendations — Not implemented"*, and
> * every data endpoint was public.
>
> **All three of those statements are now false.** The current build issues a JWT, enforces per-user ownership, and
> shows deterministic recommendations returned by the backend.
>
> The screenshots in this folder are therefore **not representative of the current application** and must not be
> presented as such. They are retained only as a record of the earlier verification. See
> [../docs/TESTING.md](../docs/TESTING.md) §6 for what is and is not verified in the current build.
>
> The content below is unchanged from the original run.

**Status: PASS** — full workflow completed against live frontend (http://localhost:5173) and backend (http://localhost:8080).
Run: user `UI Check User` / `ui.check.1791479838@example.com`, analysis id `1`.

> Note: the built-in browser is disabled in Accio Work Settings, so the user's Chrome was used (per the caller's instruction). No services were started/stopped.

## (a) Steps — PASS/FAIL

| # | Step | Result | Notes |
|---|------|--------|-------|
| 1 | Open `/` → redirect to `/login` | **PASS** | Landed on `/login` ("Sign in to SmartResume Analyzer") |
| 2 | Register unique user | **PASS** | `ui.check.1791479838@example.com` / StrongPass123 / UI Check User |
| 3 | Dashboard shows signed-in user | **PASS** | Header shows "UI Check User" + email; "Welcome back, UI Check User" |
| 4 | Open "New Analysis" from top nav | **PASS** | Navigated to `/analysis/new` |
| 5 | Upload test-resume.pdf, wait "Parsed successfully" | **PASS** | `test-resume.pdf` — PDF • 1.8 KB • 1013 characters extracted, "Parsed successfully" |
| 6 | Enter Job Title / Company / JD | **PASS** | Full Stack Software Engineer / Test Company / JD as specified |
| 7 | Click "Analyze Resume & Match Job" | **PASS** | Redirected to `/results/1` |
| 8 | Record results values | **PASS** | See table below |
| 9 | Cross-check API | **PASS** | UI == JSON (all fields). See (c) |
| 10 | Analysis History shows new row | **PASS** | 1 record: Full Stack Software Engineer / Test Company / test-resume.pdf / 76 Strong |
| 11 | Reopen via "View analysis" | **PASS** | `/results/1` re-shows same stored numbers (76, Skills 75%) |

## (b) Observed numbers

| Metric | UI value |
|---|---|
| Overall ATS score (dial) | **76 / 100** (label STRONG) |
| Skills | **75%** (6 of 8 recognised skills match) |
| Keywords | **67%** (8 of 12 JD keywords found) |
| Relevance | **67%** |
| Structure | **95%** (7 of 8 sections detected) |
| Formatting | **100%** |
| Matched skills | Java, Spring Boot, Spring, React, PostgreSQL, SQL (**6**) |
| Missing skills | Docker, AWS (**2**) |
| Keyword risk label | **MEDIUM RISK** (java — 5×, 3.5% of resume) |
| Matched keywords | boot, engineer, java, postgresql, react, software, spring, sql (**8**) |
| Missing keywords | aws, docker, experience., looking (**4**) |
| Structure audit | **7 PASSED / 1 WARNING** (Warning: Internships — optional section not found) |

Resume parse: `test-resume.pdf`, type **PDF**, size **1.8 KB**, **1013** characters extracted.

## (c) UI vs API cross-check — MATCH

Source: `GET http://localhost:8080/api/analyses` → `[{id:1, atsScore:76, jobTitle:"Full Stack Software Engineer", companyName:"Test Company", status:"COMPLETED"}]`
and `GET http://localhost:8080/api/analyses/1`.

| Field (API) | API value | UI value | Match |
|---|---|---|---|
| atsScore | 76 | 76 | ✅ |
| scoreBreakdown.skillMatch | 75 | Skills 75% | ✅ |
| scoreBreakdown.keywordMatch | 67 | Keywords 67% | ✅ |
| scoreBreakdown.relevance | 67 | Relevance 67% | ✅ |
| scoreBreakdown.structure | 95 | Structure 95% | ✅ |
| scoreBreakdown.formatting | 100 | Formatting 100% | ✅ |
| matchedSkills | Java, Spring Boot, Spring, React, PostgreSQL, SQL | same 6 | ✅ |
| missingSkills | Docker, AWS | same 2 | ✅ |
| matchedKeywords | boot, engineer, java, postgresql, react, software, spring, sql | same 8 | ✅ |
| missingKeywords | aws, docker, experience., looking | same 4 | ✅ |
| keywordStuffing.risk / flagged | MEDIUM / java×5 (3.5%) | MEDIUM RISK / java×5 (3.5%) | ✅ |
| structure | 7 PASSED + 1 WARNING (Internships) | 7 passed / 1 warning | ✅ |

**No mismatches found.**

## (d) Screenshots (all in `resume-analyzer/verify-ui/`)

| File | Viewport | CDN |
|---|---|---|
| dashboard-desktop.png | 1440x900 | https://sc02.alicdn.com/kf/Aaf3365b3b84144d889042fcdb089d0c0k.png |
| new-analysis-desktop.png | 1440x900 | https://sc02.alicdn.com/kf/A08346c96ebb2496e9505e7c5b4ce9431L.png |
| results-desktop.png | 1440x900 | https://sc02.alicdn.com/kf/Ac725dfadd0424b8fb16655d61551dc22O.png |
| history-desktop.png | 1440x900 | https://sc02.alicdn.com/kf/A0b406183494146139c6e0bbfade91584V.png |
| dashboard-mobile.png | 375x812 | https://sc02.alicdn.com/kf/A2b799d680a7a4f98afd4c0963715fc49V.png |
| results-mobile.png | 375x812 | https://sc02.alicdn.com/kf/A7957e197b4ca478f867ae1991eba4ab0l.png |
| dashboard-mobile-nav-open.png (extra) | 375x812 | https://sc02.alicdn.com/kf/Adfc5fefc93774f9ab08a4514f633a736D.png |

## (e) Responsive findings (375x812)

- **Horizontal overflow: NONE.** `document.scrollWidth == clientWidth == 375` on both Dashboard and Results (and with the nav open). Page is not wider than the viewport.
  - Note: on Results, the metric **tab strip** ("Full Diagnostic View / Skill Match Breakdown / Keyword Density / Structure Audit / AI Recommendations") is `overflow-x: auto` with scrollWidth 1128 vs clientWidth 343 — it scrolls **within its own container**, so all tabs are reachable and the page itself does not overflow. This is intentional, not clipping.
- **Top navigation reachable at 375px: YES.** Header collapses to a hamburger ("Toggle navigation"). Clicking it opens the nav — Dashboard, New Analysis, Analysis History, Settings and the "Analyze Resume" button all become visible/reachable (see `dashboard-mobile-nav-open.png`).
- **Clipping at top/bottom: NONE observed.** Header renders at top; page is vertically scrollable (scrollHeight 4977 vs viewport 812) so all content is reachable by scrolling. No content cut off at top or bottom.

## (f) Console / network

- **JS exceptions: none** (`errors` = `[]`; console level=error = `[]`).
- **Failed network requests: none.** All requests returned 200 / 201 / 304 (static assets, fonts, and every `/api/*` call). Key calls: `POST /api/auth/register` 201, `POST /api/resumes/upload` 201, `POST /api/job-descriptions` 201, `POST /api/analyses` 201, `GET /api/analyses/1` 200.
- **Observation (not a console/network failure):** `POST /api/resumes/upload` fired **twice** (both 201), and the dashboard/history reflected **2 stored resumes** ("2 parsed") though only one file was selected once. The analysis itself correctly used `resumeId: 2`. This looks like a duplicate upload signal in the dev build (possibly React StrictMode double-invoke); reported as observed.

## (g) AI Recommendations tab — HONEST PLACEHOLDER (not fabricated)

The "AI Recommendations" panel shows an explicit placeholder, no invented content:
- Header: **"AI Actionable Recommendations — Not available in this project"**, badge **"NOT IMPLEMENTED"**.
- Body: *"AI recommendations will appear here. The backend in this project has no AI or LLM capability and no recommendation endpoint, so no rewritten bullets, phrasing suggestions or score projections are generated. Nothing on this panel is simulated."*
- It also surfaces real **deterministic** findings ("DETERMINISTIC FINDINGS YOU CAN ACT ON": missing skills Docker/AWS; java repetition risk; 1 structure warning) — these are derived from the analysis data, not AI-generated.
- Dashboard likewise states: *"Rewritten bullet suggestions are not generated anywhere in this project — there is no AI capability in the backend, so none are shown."*

## (h) Could not verify / caveats

- Nothing in the required workflow was left unverified.
- Extra observation above (duplicate resume upload → 2 stored resume records) is reported as-is; I did not modify data.
- Cleaned up a stray `verify-ui/verify-ui/` subfolder created by a first relative-path screenshot; final assets are directly in `resume-analyzer/verify-ui/`.


---

# Regression re-check — duplicate resume upload fix

**Status: PASS — the fix works. Exactly 1 upload and 1 stored record for a single file selection.**
Servers restarted, H2 empty at start. Session: user "UI Check User" (still signed in from prior run).

## Raw observations

| # | Check | Expected | Observed | Result |
|---|-------|----------|----------|--------|
| 1 | Sign in | signed in | Dashboard for "UI Check User" | PASS |
| 2 | Open New Analysis | `/analysis/new` | `/analysis/new` loaded | PASS |
| 3 | `GET /api/resumes` **before** touching input | 0 | **0** | PASS |
| 4 | Select `test-resume.pdf` via file input once | 1 selection | 1 selection (no extra dropzone clicks) | PASS |
| 5 | Count `POST /api/resumes/upload` for that selection | 1 | **exactly 1** (id `r228`, status **201**, 17:25:54Z) | PASS |
| 6 | `GET /api/resumes` after | 1 | **1** (`{id:1, originalFileName:"test-resume.pdf", fileType:"PDF", fileSize:1797}`); vault badge "Stored (1)" | PASS |
| 7 | Complete flow → Analyze | redirect | Job "Full Stack Software Engineer" / Test Company / JD as given → redirected to **`/results/1`** | PASS |
| 8 | ATS score + `GET /api/analyses` | consistent | Dial **76**; API list length **1**, `{id:1, atsScore:76, jobTitle:"Full Stack Software Engineer", companyName:"Test Company", resumeId:1, status:"COMPLETED"}` | PASS |
| 9 | Console errors / failed requests | none | **none** (`errors=[]`, console error `[]`) | PASS |

## Bug status
- **FIXED.** Previously a single selection produced **2** `POST /api/resumes/upload` and stored 2 resumes. Now one selection produces **exactly 1** upload and **1** resume record (`id:1`), and the analysis references `resumeId:1`. No duplicate.
- Method used was identical to the run that reproduced the bug (setting the file on `input[type=file]` once, no dropzone click), so this is a like-for-like confirmation.

## No regression
- Analysis completed, results page `/results/1` shows ATS **76**, and `/api/analyses` holds exactly **1** record whose `atsScore` (76) equals the UI.
- `GET /api/resumes` = 1 record; no orphan/duplicate resume rows.

## Console / network
- JS exceptions: **none**. Console errors: **none**.
- Failed requests: **none**. All requests returned 200/201/304, including a single `POST /api/resumes/upload` (201).

## Note (unchanged, not part of this fix)
- At a 375px viewport the form fields were reported "occluded" for programmatic fill; after resizing to 1440x900 they filled normally. Not a functional defect observed in the mobile screenshots — noted for completeness only.


---

# Dial fix re-check — ATS score dial compression

**Status: PASS — the dial renders at its full 144×144 size and nothing overflows or overlaps.**
Pre-seeded data used (no upload/analysis created this run). Session: "UI Check User" (signed in). Page: `http://localhost:5173/results/1`, viewport 1440×900.

## Raw measurements (getBoundingClientRect, px)

| Element | left | top | right | bottom | width | height |
|---|---|---|---|---|---|---|
| score `<svg>` (the ring) | 48.0 | 348.0 | 192.0 | 492.0 | **144.0** | **144.0** |
| parent `<div>` (dial container) | 48.0 | 348.0 | 192.0 | 492.0 | **144.0** | **144.0** |
| overlay (absolute inset-0) | 48.0 | 348.0 | 192.0 | 492.0 | 144.0 | 144.0 |
| \"76\" number span | 85.1 | 391.7 | 129.5 | 431.7 | 44.4 | 40.0 |
| \"/100\" span | 129.5 | 414.3 | 154.9 | 432.3 | 25.4 | 18.0 |
| \"Strong\" label (rendered uppercase) | 99.2 | 434.3 | 140.8 | 448.3 | 41.6 | 14.0 |
| paragraph beside dial | 216.0 | 385.5 | 570.7 | 476.5 | 354.7 | 91.0 |

## Checks

| Check | Expected | Observed | Result |
|---|---|---|---|
| svg is 144×144 | 144×144 | 144×144 | **PASS** |
| parent div is 144×144 | 144×144 | 144×144 | **PASS** |
| \"76\" fully inside svg (left/right/top/bottom) | all true | left T, right T, top T, bottom T | **PASS** |
| \"/100\" fully inside svg | true | true (right 154.9 < 192, bottom 432.3 < 492) | **PASS** |
| \"STRONG\" fully inside svg | true | true (bottom 448.3 < 492) | **PASS** |
| number's right edge exceeds svg | false | **false** | **PASS** |
| STRONG's bottom edge exceeds svg | false | **false** | **PASS** |
| paragraph.left >= svg.right (no overlap) | true | 216.0 >= 192.0 → **true** | **PASS** |

## Mobile (375×812)

| Check | Observed | Result |
|---|---|---|
| `document.scrollWidth` vs `clientWidth` | **375 vs 375** → no horizontal overflow | PASS |
| Dial svg on mobile | 144×144 (left 115.7 – right 259.7) | PASS |

## API cross-check
- `GET http://localhost:8080/api/analyses/1` → `{id:1, atsScore:76}`.
- Dial number reads **76** → equals `atsScore` 76. **MATCH.**

## Console / network
- JS exceptions: **none** (`errors=[]`). Console errors: **none**.
- Failed requests: **none**. API calls: `GET /api/analyses/1` → **200** (×3).

## Screenshots (overwritten)
- `C:\Users\DEEPIKA\OneDrive\Desktop\resume-analyzer\verify-ui\results-desktop.png` (1440×900) — https://sc02.alicdn.com/kf/Aae48b653ccb9409bbed333d6757b896dD.png
- `C:\Users\DEEPIKA\OneDrive\Desktop\resume-analyzer\verify-ui\results-mobile.png` (375×812) — https://sc02.alicdn.com/kf/A4a5a52df6c514328ae063441c8af91dfQ.png

## Bug status
- **FIXED.** The dial container is now 144×144 (`shrink-0`), and the score number, \"/100\" and \"STRONG\" all sit inside the ring with positive margin on every side; the adjacent paragraph starts at x=216 (right of the ring's 192 edge), so there is no overlap. Previously the ring was ~87px and the number/label overflowed.
