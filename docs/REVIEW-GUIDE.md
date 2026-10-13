# College Review Guide

Everything needed to demonstrate SmartResume Analyzer in about five minutes, plus the questions a reviewer is
likely to ask and the answers this codebase actually supports.

---

## 1. Before the review — 5-minute setup

```powershell
# 1. Backend (Terminal 1) — H2, no MySQL needed
cd resume-analyzer\resume-analyzer
$env:JWT_SECRET = 'review-demo-secret-at-least-32-characters-long'
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"

# 2. Frontend (Terminal 2)
cd frontend
npm install
npm run dev
```

Open <http://localhost:5173>.

Optional warm-up so you do not demo against an empty dashboard:

```powershell
# Terminal 3, from the repository root
powershell -ExecutionPolicy Bypass -File scripts\seed-demo-analysis.ps1
```

It prints a demo email/password and an analysis id — sign in with those instead of registering live if you are
short on time.

---

## 2. The five-minute demonstration

| Time | Do this | Say this |
|---|---|---|
| 0:00 | Open <http://localhost:5173> → you land on **/login** | "Every data endpoint is protected. An unauthenticated visitor is redirected to sign-in." |
| 0:20 | **Register** a new student (note the password rule: ≥ 6 characters) | "Registration validates on the server, hashes the password with BCrypt, and immediately issues a signed JWT — there is no server-side session." |
| 0:45 | Open **DevTools → Application → Local Storage** | "Only the token and the public user object are stored — never the password. Every request carries `Authorization: Bearer …`, and on reload the token is re-validated against `GET /api/auth/me` rather than trusted." |
| 1:05 | Go to **New Analysis**, upload `scripts/test-resume.pdf` | "PDF and DOCX are both supported. Validation is extension + MIME + a 10 MB cap, and parsing failures are reported as readable errors, not crashes." |
| 1:30 | Paste the job description from §6 → **Run analysis** | "The job description must belong to the same user as the resume, so an analysis can never mix two people's data." |
| 1:50 | On **Results**, point at the score dial and the five metric cards | "The score is a fixed weighted blend: skills 40%, keywords 25%, relevance 15%, structure 12%, formatting 8%. One line in `AnalysisService` — reproducible by hand, no AI involved." |
| 2:20 | Switch to the **Keyword Frequency** tab | "This is the per-keyword table: how often the job description uses a term, how often the resume does, whether it matched, and whether repetition crossed the detector's thresholds. Counts are shown as numbers — repeating 'java' is not penalised by itself." |
| 2:50 | Switch to **Recommendations** | "These are generated deterministically from the measured findings. Note the wording: it tells the student to add evidence *only if they genuinely have it*. The tool never invents a skill." |
| 3:10 | Point at the **AI Suggestions** block (Not configured) | "There is an optional, provider-agnostic LLM layer. It is off by default, it never touches the score, and its output is labelled separately. With no credentials configured the endpoint returns 503 and the rule-based advice remains — that is the fallback." |
| 3:30 | Click **Export PDF Report** → open the download | "The report is rendered server-side from the *stored* analysis. It is ownership-checked, paginates, and carries a statement that the analysis is deterministic." |
| 3:55 | Open **Analysis History**, then press **F5** | "Results are persisted and never recomputed on read. Reload returns the identical stored score." |
| 4:10 | **Log out**, then try to open `/analysis/history` directly | "The route guard sends you back to sign-in." |
| 4:20 | Sign in, then open the same URL in a second browser profile as a **different user** | "User B sees an empty history and gets 404 — not 403 — for A's ids, so the API does not even reveal which records exist." |
| 4:40 | Show the terminal: run the two check scripts | "63 end-to-end API checks and 16 restart-persistence checks, all passing." |

---

## 3. Commands to have ready

```powershell
# Backend tests — 92 tests
cd resume-analyzer\resume-analyzer
.\mvnw.cmd test "-Dspring.profiles.active=test"

# Frontend tests — 55 tests
cd frontend
npm test

# Frontend type-check + production build
npm run build

# End-to-end API workflow — 63 checks (backend must be running)
powershell -ExecutionPolicy Bypass -File scripts\e2e-api-check.ps1

# Persistence across a real backend restart — 16 checks
powershell -ExecutionPolicy Bypass -File scripts\e2e-restart-persistence.ps1
```

---

## 4. Quick reference: what to point at in the code

| Claim | File to open |
|---|---|
| Passwords are BCrypt-hashed | `service/UserService.java` |
| A JWT is issued on register/login | `security/JwtService.java` |
| Tokens are validated on every request | `security/JwtAuthenticationFilter.java` |
| Only credentials are public; everything else needs a token | `config/SecurityConfig.java` |
| The score formula and its weights | `service/AnalysisService.java` |
| Ownership is enforced in queries, not the UI | `service/ResumeService.java`, `service/JobDescriptionService.java`, `service/AnalysisService.java` |
| Another user's record returns 404 | the `findByIdAndUserId` repository methods + `*NotFoundException` handlers |
| Per-keyword frequency data | `service/analysis/KeywordMatcher.java`, `buildKeywordCoverage` in `AnalysisService.java` |
| PDF report rendering | `service/report/AnalysisReportPdfService.java` |
| Recommendations never invent skills | `service/analysis/RecommendationGenerator.java` |
| The LLM layer is off by default and separate from scoring | `config/LlmProperties.java`, `service/ai/AiRecommendationService.java` |
| Offline-tolerant fonts and icons | `tailwind.config.js`, `src/index.css`, `src/utils/icons.ts` |

---

## 5. Likely questions and honest answers

**"Is the ATS score real / does it match a real ATS?"**
No — and the code says so. It is a documented, deterministic heuristic with fixed weights and thresholds. It is
intended to surface concrete gaps, not to predict a hiring outcome. No commercial ATS is reproduced or reverse
engineered.

**"Where is the AI?"**
Optional and off by default. A provider-agnostic LLM layer exists (`service/ai/`), is separated from scoring by
design, and validates/limits everything it receives. Without credentials the endpoint answers 503 and the
rule-based recommendations continue to work. I can wire it up in a minute with an API key if you want to see it,
but I would not put a key on a shared machine.

**"How do you stop one student seeing another's data?"**
Three layers: the endpoint requires a token; the controller receives the caller's id and never an id from the
request body; and every repository query filters by `user_id` (`findByIdAndUserId`, `findAllByUserId…`). A
mismatch surfaces as 404 so existence is not disclosed. This is covered by `SecurityAndOwnershipIntegrationTest`
and by section 9 of the E2E script.

**"What happens if someone tampers with the token?"**
The HS256 signature check fails, the request stays unauthenticated, and the entry point returns 401 in the standard
error envelope — never a 500. There is a test for the tampered case and another for expiry.

**"What if the resume is a scanned image?"**
Text extraction yields too little text and the upload is rejected with 422 and a message telling the student the
document may be image-based. A `FAILED` row is recorded and the uploaded file is deleted rather than kept unusable.

**"Is anything hardcoded?"**
No credentials. The JWT key and DB password come from environment variables only; there is no default key. The
demo fixtures under `scripts/` are test documents, not application data.

**"What is not finished?"**
Stated plainly in [../README.md](../README.md) §13. The headline items: no refresh/revocation tokens, no password
reset, no DOM-level frontend tests (the test runner had no registry access to install jsdom), and offline icons
degrade to a placeholder rather than a glyph.

---

## 6. Job description to paste during the demo

Paste this so the numbers you see match the ones quoted in the documentation:

```
Full Stack Software Engineer — Test Company

We are looking for a software engineer with Java, Spring Boot, React, SQL, PostgreSQL, Docker
and AWS experience. Experience with microservices is required.
```

Job title: `Full Stack Software Engineer` · Company: `Test Company`

Expected result with `scripts/test-resume.pdf`: **ATS 71** — skills 67, keywords 62, relevance 62, structure 95,
formatting 100; matched skills Java, Spring Boot, Spring, React, PostgreSQL, SQL; missing Microservices, Docker,
AWS; repetition risk MEDIUM (`java` ×5); 4 recommendations. Other resumes will legitimately produce different
numbers.

---

## 7. If something does not work during the review

| Symptom | Cause | Fix |
|---|---|---|
| "Cannot reach the backend" on every screen | Backend not running | Start Terminal 1; check <http://localhost:8080/api/ai/status> returns 401 (that means it is up and protected) |
| You are signed out after restarting the backend | `JWT_SECRET` was not set, so a random key was used | Set `JWT_SECRET` before starting, as in §1 |
| Upload says the document could not be read | The file is image-based / corrupt | Use `scripts/test-resume.pdf` or `scripts/test-resume.docx` |
| Icons look like small empty boxes | No network access to the icon font | Expected and handled — layout is unchanged. See README §13 item 12 |
| `mvnw package` fails with a plugin-resolution error | Offline Maven cache lacks `maven-jar-plugin` | Use `spring-boot:run` (what the scripts use). See README §8 |
| Dashboard is empty | Fresh database | Register and run one analysis, or run `scripts/seed-demo-analysis.ps1` |
