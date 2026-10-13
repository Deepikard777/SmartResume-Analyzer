# ---------------------------------------------------------------------------
# End-to-end check of the real workflow against a running backend.
#
#   register -> login -> upload resume -> create job description -> analyse
#   -> history -> reload stored analysis -> persistence
#   -> invalid file / empty input / unauthenticated / cross-user isolation
#
# Every value printed comes from a real HTTP response; nothing is stubbed.
# Each check prints PASS or FAIL and the script exits non-zero on any FAIL.
#
# Usage:  powershell -ExecutionPolicy Bypass -File scripts/e2e-api-check.ps1
# ---------------------------------------------------------------------------

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$Base = if ($env:E2E_BASE_URL) { $env:E2E_BASE_URL } else { 'http://localhost:8080' }
$PdfPath = Join-Path $PSScriptRoot 'test-resume.pdf'
$DocxPath = Join-Path $PSScriptRoot 'test-resume.docx'
if (-not (Test-Path $PdfPath)) { throw "Test PDF not found at $PdfPath" }
if (-not (Test-Path $DocxPath)) {
  throw "Test DOCX not found at $DocxPath — run scripts/make-test-resume-docx.ps1 first"
}

$Tmp = Join-Path $env:TEMP ("smartresume-e2e-" + [System.Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $Tmp | Out-Null

$script:Checks = New-Object System.Collections.Generic.List[object]

function Write-Section($text) { Write-Output ''; Write-Output "== $text ==" }

function Check {
  param([string]$Name, $Actual, $Expected)
  $ok = "$Actual" -eq "$Expected"
  $script:Checks.Add([pscustomobject]@{ Name = $Name; Expected = $Expected; Actual = $Actual; Result = $(if ($ok) { 'PASS' } else { 'FAIL' }) })
  Write-Output ("{0,-56} expected={1,-6} actual={2,-6} {3}" -f $Name, $Expected, $Actual, $(if ($ok) { 'PASS' } else { 'FAIL' }))
}

function New-JsonFile {
  param([string]$Name, $Object)
  $path = Join-Path $Tmp $Name
  ($Object | ConvertTo-Json -Compress) | Set-Content -Encoding UTF8 -NoNewline $path
  return $path
}

# Returns an object with Status (int) and Body (raw string).
function Invoke-Api {
  param(
    [string]$Method,
    [string]$Path,
    [string]$BodyFile,
    [string]$Token
  )
  $a = @('-s', '-X', $Method, "$Base$Path", '-w', "`n%{http_code}")
  if ($BodyFile) { $a += @('-H', 'Content-Type: application/json', '--data-binary', "@$BodyFile") }
  if ($Token) { $a += @('-H', "Authorization: Bearer $Token") }
  $raw = & curl.exe @a
  if ($raw -is [array]) {
    $status = [int]$raw[-1]
    $body = ($raw[0..($raw.Count - 2)] -join "`n")
  } else {
    $parts = "$raw" -split "`n"
    $status = [int]$parts[-1]
    $body = ($parts[0..($parts.Count - 2)] -join "`n")
  }
  return [pscustomobject]@{ Status = $status; Body = $body }
}

function Invoke-Upload {
  param([string]$FilePath, [string]$ContentType, [string]$Token, [string]$FileName)
  $a = @('-s', '-X', 'POST', "$Base/api/resumes/upload", '-w', "`n%{http_code}")
  if ($Token) { $a += @('-H', "Authorization: Bearer $Token") }
  if ($FileName) {
    $a += @('-F', "file=@$FilePath;type=$ContentType;filename=$FileName")
  } else {
    $a += @('-F', "file=@$FilePath;type=$ContentType")
  }
  $raw = & curl.exe @a
  if ($raw -is [array]) {
    $status = [int]$raw[-1]
    $body = ($raw[0..($raw.Count - 2)] -join "`n")
  } else {
    $parts = "$raw" -split "`n"
    $status = [int]$parts[-1]
    $body = ($parts[0..($parts.Count - 2)] -join "`n")
  }
  return [pscustomobject]@{ Status = $status; Body = $body }
}

$stamp = [DateTime]::UtcNow.Ticks
$emailA = "e2e.owner.a.$stamp@example.com"
$emailB = "e2e.owner.b.$stamp@example.com"
$password = 'StrongPass123'
$jdText = 'We are looking for a software engineer with Java, Spring Boot, React, SQL, ' +
          'PostgreSQL, Docker and AWS experience. Experience with microservices is required.'

# ---------------------------------------------------------------------------
Write-Section '1. Register user A'
$regA = Invoke-Api -Method POST -Path '/api/auth/register' -BodyFile (New-JsonFile 'regA.json' @{
    name = 'E2E Owner A'; email = $emailA; password = $password
  })
Write-Output "HTTP $($regA.Status)"
$objA = $regA.Body | ConvertFrom-Json
$regIssuedToken = [bool]$objA.token
$regLeaksPassword = [bool]($regA.Body -match '"password"')
Check 'register returns 201' $regA.Status 201
Check 'register issues a token' $regIssuedToken $true
Check 'register echoes the account' $objA.user.email $emailA
Check 'register does not leak a password field' $regLeaksPassword $false
$tokenA = $objA.token

# ---------------------------------------------------------------------------
Write-Section '2. Log in as user A'
$loginA = Invoke-Api -Method POST -Path '/api/auth/login' -BodyFile (New-JsonFile 'loginA.json' @{
    email = $emailA; password = $password
  })
Write-Output "HTTP $($loginA.Status)"
$loginObj = $loginA.Body | ConvertFrom-Json
$loginIssuedToken = [bool]$loginObj.token
$loginLeaksPassword = [bool]($loginA.Body -match '"password"')
Check 'login returns 200' $loginA.Status 200
Check 'login issues a token' $loginIssuedToken $true
Check 'login never returns the password' $loginLeaksPassword $false
$tokenA = $loginObj.token

Write-Section '2b. GET /api/auth/me confirms the token'
$me = Invoke-Api -Method GET -Path '/api/auth/me' -Token $tokenA
$meObj = $me.Body | ConvertFrom-Json
Check 'me returns 200' $me.Status 200
Check 'me returns the token owner' $meObj.email $emailA

# ---------------------------------------------------------------------------
Write-Section '3. Upload a valid PDF resume (authenticated)'
$up = Invoke-Upload -FilePath $PdfPath -ContentType 'application/pdf' -Token $tokenA
Write-Output "HTTP $($up.Status)"
$resume = $up.Body | ConvertFrom-Json
$textExtracted = [bool]($resume.extractedCharacterCount -gt 0)
Check 'upload returns 201' $up.Status 201
Check 'uploadStatus is PARSED' $resume.uploadStatus 'PARSED'
Check 'text was extracted (chars > 0)' $textExtracted $true
Write-Output "resumeId=$($resume.id) file=$($resume.originalFileName) chars=$($resume.extractedCharacterCount)"

# ---------------------------------------------------------------------------
Write-Section '3b. Upload a valid DOCX resume (authenticated)'
$docxUp = Invoke-Upload -FilePath $DocxPath -ContentType 'application/vnd.openxmlformats-officedocument.wordprocessingml.document' -Token $tokenA
Write-Output "HTTP $($docxUp.Status)"
$docxResume = $docxUp.Body | ConvertFrom-Json
$docxChars = [bool]($docxResume.extractedCharacterCount -gt 0)
Check 'DOCX upload returns 201' $docxUp.Status 201
Check 'DOCX uploadStatus is PARSED' $docxResume.uploadStatus 'PARSED'
Check 'DOCX text was extracted (chars > 0)' $docxChars $true
Write-Output "docxResumeId=$($docxResume.id) chars=$($docxResume.extractedCharacterCount)"

# ---------------------------------------------------------------------------
Write-Section '4. Create a job description'
$jd = Invoke-Api -Method POST -Path '/api/job-descriptions' -Token $tokenA -BodyFile (New-JsonFile 'jd.json' @{
    jobTitle = 'Full Stack Software Engineer'; companyName = 'Test Company'; descriptionText = $jdText
  })
$jdObj = $jd.Body | ConvertFrom-Json
Check 'job description returns 201' $jd.Status 201
Write-Output "jobDescriptionId=$($jdObj.id)"

# ---------------------------------------------------------------------------
Write-Section '5. Analyse the resume against the job description'
$an = Invoke-Api -Method POST -Path '/api/analyses' -Token $tokenA -BodyFile (New-JsonFile 'an.json' @{
    resumeId = $resume.id; jobDescriptionId = $jdObj.id
  })
Write-Output "HTTP $($an.Status)"
$analysis = $an.Body | ConvertFrom-Json
$atsInRange = [bool]($analysis.atsScore -ge 0 -and $analysis.atsScore -le 100)
$hasRecommendations = [bool]($analysis.recommendations.Count -gt 0)
$exposesResumeText = [bool]($an.Body -match '"extractedText"')
Check 'analysis returns 201' $an.Status 201
Check 'atsScore is in 0..100' $atsInRange $true
Check 'analysis has 5 sub-scores' (($analysis.scoreBreakdown.PSObject.Properties | Measure-Object).Count) 5
Check 'analysis has 8 structure checks' ($analysis.structure.Count) 8
Check 'analysis returns recommendations' $hasRecommendations $true
Check 'analysis exposes no raw resume text' $exposesResumeText $false

Write-Output "atsScore      : $($analysis.atsScore)"
Write-Output "breakdown     : skills=$($analysis.scoreBreakdown.skillMatch) keywords=$($analysis.scoreBreakdown.keywordMatch) relevance=$($analysis.scoreBreakdown.relevance) structure=$($analysis.scoreBreakdown.structure) formatting=$($analysis.scoreBreakdown.formatting)"
Write-Output "matchedSkills : $($analysis.matchedSkills -join ', ')"
Write-Output "missingSkills : $($analysis.missingSkills -join ', ')"
Write-Output "matchedKw     : $($analysis.matchedKeywords -join ', ')"
Write-Output "missingKw     : $($analysis.missingKeywords -join ', ')"
Write-Output "stuffingRisk  : $($analysis.keywordStuffing.risk)"
Write-Output 'recommendations:'
foreach ($r in $analysis.recommendations) {
  Write-Output "  [$($r.priority)] $($r.category) - $($r.title)"
  Write-Output "      => $($r.detail)"
}

# ---------------------------------------------------------------------------
Write-Section '6. Analysis history'
$hist = Invoke-Api -Method GET -Path '/api/analyses' -Token $tokenA
$history = $hist.Body | ConvertFrom-Json
Check 'history returns 200' $hist.Status 200
Check 'history has exactly 1 record for user A' ($history.Count) 1
Check 'history row is for the same analysis' $history[0].id $analysis.id
Write-Output "history: #$($history[0].id) $($history[0].jobTitle) ats=$($history[0].atsScore) status=$($history[0].status)"

# ---------------------------------------------------------------------------
Write-Section '7. Persistence: reload the stored analysis'
$reload = Invoke-Api -Method GET -Path "/api/analyses/$($analysis.id)" -Token $tokenA
$again = $reload.Body | ConvertFrom-Json
Check 'reload returns 200' $reload.Status 200
Check 'stored atsScore matches the created one' $again.atsScore $analysis.atsScore
Check 'stored matchedSkills identical' (($again.matchedSkills -join ',')) (($analysis.matchedSkills -join ','))
Check 'stored recommendations survive the round trip' ($again.recommendations.Count) ($analysis.recommendations.Count)

# ---------------------------------------------------------------------------
Write-Section '8. Negative cases: bad files, empty input, unauthenticated'

$txtPath = Join-Path $Tmp 'not-a-resume.txt'
'This is a plain text file, not a resume.' | Set-Content -Encoding UTF8 -NoNewline $txtPath
$badType = Invoke-Upload -FilePath $txtPath -ContentType 'text/plain' -Token $tokenA -FileName 'not-a-resume.txt'
Check 'unsupported file type rejected (400)' $badType.Status 400

$emptyPath = Join-Path $Tmp 'empty.pdf'
[System.IO.File]::WriteAllBytes($emptyPath, [byte[]]@())
$empty = Invoke-Upload -FilePath $emptyPath -ContentType 'application/pdf' -Token $tokenA -FileName 'empty.pdf'
Check 'empty file rejected (400)' $empty.Status 400

$emptyJd = Invoke-Api -Method POST -Path '/api/job-descriptions' -Token $tokenA -BodyFile (New-JsonFile 'emptyJd.json' @{
    jobTitle = ''; companyName = ''; descriptionText = ''
  })
Check 'empty job description rejected (400)' $emptyJd.Status 400

$noAuthList = Invoke-Api -Method GET -Path '/api/resumes'
Check 'GET /api/resumes without a token rejected (401)' $noAuthList.Status 401
$noAuthUpload = Invoke-Upload -FilePath $PdfPath -ContentType 'application/pdf'
Check 'upload without a token rejected (401)' $noAuthUpload.Status 401
$noAuthAn = Invoke-Api -Method POST -Path '/api/analyses' -BodyFile (New-JsonFile 'noauth.json' @{ resumeId = 1; jobDescriptionId = 1 })
Check 'POST /api/analyses without a token rejected (401)' $noAuthAn.Status 401

# Tamper with the signature, changing a character whose bits are ALL significant.
# Flipping the LAST character is unreliable: a 32-byte HS256 signature encodes to 43
# base64url characters, and the 43rd carries only 4 significant bits plus 2 padding bits
# that a lenient decoder ignores - so a signature ending in "A" flipped to "B" decodes to
# BYTE-IDENTICAL data. The token then genuinely still verifies and is correctly accepted,
# which failed this check roughly 1 run in 16.
$tokenParts = $tokenA -split '\.'
$origHead = $tokenParts[2][0]
$newHead = if ($origHead -eq 'A') { 'B' } else { 'A' }
$tokenParts[2] = $newHead + $tokenParts[2].Substring(1)
$tamperedToken = $tokenParts -join '.'
$tampered = Invoke-Api -Method GET -Path '/api/resumes' -Token $tamperedToken
Check 'tampered token rejected (401)' $tampered.Status 401

# ---------------------------------------------------------------------------
Write-Section '9. A second user cannot reach the first user''s data'
$regB = Invoke-Api -Method POST -Path '/api/auth/register' -BodyFile (New-JsonFile 'regB.json' @{
    name = 'E2E Owner B'; email = $emailB; password = $password
  })
$objB = $regB.Body | ConvertFrom-Json
$tokenB = $objB.token
Check 'second user registered' $regB.Status 201

$bResumes = Invoke-Api -Method GET -Path '/api/resumes' -Token $tokenB
Check "B's resume list is empty" (($bResumes.Body | ConvertFrom-Json).Count) 0
$bAnalyses = Invoke-Api -Method GET -Path '/api/analyses' -Token $tokenB
Check "B's analysis history is empty" (($bAnalyses.Body | ConvertFrom-Json).Count) 0

$bReadResume = Invoke-Api -Method GET -Path "/api/resumes/$($resume.id)" -Token $tokenB
Check "B cannot read A's resume (404)" $bReadResume.Status 404
$bReadAnalysis = Invoke-Api -Method GET -Path "/api/analyses/$($analysis.id)" -Token $tokenB
Check "B cannot read A's analysis (404)" $bReadAnalysis.Status 404
$bReadJd = Invoke-Api -Method GET -Path "/api/job-descriptions/$($jdObj.id)" -Token $tokenB
Check "B cannot read A's job description (404)" $bReadJd.Status 404
$bDeleteRaw = & curl.exe -s -o NUL -w '%{http_code}' -X DELETE "$Base/api/resumes/$($resume.id)" -H "Authorization: Bearer $tokenB"
$bDeleteStatus = [int]("$bDeleteRaw".Trim())
Check "B cannot delete A's resume (404)" $bDeleteStatus 404

# A must still see its own data after B's attempts (the PDF and the DOCX uploaded above)
$aResumes = Invoke-Api -Method GET -Path '/api/resumes' -Token $tokenA
Check "A's resume list still has both records" (($aResumes.Body | ConvertFrom-Json).Count) 2

# ---------------------------------------------------------------------------
Write-Section '10. Token lifetime is stateless (a discarded token stays valid until it expires)'
$stillValid = Invoke-Api -Method GET -Path '/api/resumes' -Token $tokenA
Check 'token still accepted until its expiry' $stillValid.Status 200

# ---------------------------------------------------------------------------
# NOTE: the delete check runs LAST (section 13) so that the analysis still exists for
# the report-download and AI sections below.
Write-Section '11. PDF report export'
$reportPath = Join-Path $Tmp 'report.pdf'
$reportHeaders = Join-Path $Tmp 'report.headers'
$reportStatus = & curl.exe -s -o $reportPath -D $reportHeaders -w '%{http_code}' `
  "$Base/api/analyses/$($analysis.id)/report" -H "Authorization: Bearer $tokenA"
$reportStatus = [int]("$reportStatus".Trim())
Check 'report download returns 200' $reportStatus 200

$headerText = (Get-Content $reportHeaders -Raw)
Check 'report Content-Type is application/pdf' ([bool]($headerText -match 'Content-Type:\s*application/pdf')) $true
Check 'report is sent as an attachment with a .pdf filename' ([bool]($headerText -match 'attachment;\s*filename="?[^";]+\.pdf')) $true
Check 'report is marked no-store' ([bool]($headerText -match 'Cache-Control:\s*no-store')) $true

$reportBytes = [System.IO.File]::ReadAllBytes($reportPath)
$magic = [System.Text.Encoding]::ASCII.GetString($reportBytes, 0, 5)
Check 'report body starts with the %PDF magic number' $magic '%PDF-'
Check 'report is a substantial document (> 1 KB)' ([bool]($reportBytes.Length -gt 1024)) $true
Write-Output "report: $($reportBytes.Length) bytes"

# Cross-user: B must not be able to fetch A's report.
$bReportStatus = & curl.exe -s -o NUL -w '%{http_code}' `
  "$Base/api/analyses/$($analysis.id)/report" -H "Authorization: Bearer $tokenB"
Check "B cannot download A's report (404)" ([int]("$bReportStatus".Trim())) 404

$anonReportStatus = & curl.exe -s -o NUL -w '%{http_code}' "$Base/api/analyses/$($analysis.id)/report"
Check 'report download without a token is rejected (401)' ([int]("$anonReportStatus".Trim())) 401

# ---------------------------------------------------------------------------
Write-Section '12. Optional AI recommendation feature'
$aiStatus = Invoke-Api -Method GET -Path '/api/ai/status' -Token $tokenA
$ai = $aiStatus.Body | ConvertFrom-Json
Check 'AI status returns 200' $aiStatus.Status 200
Check 'AI feature reports its enabled state' ([bool]($null -ne $ai.enabled)) $true
Write-Output "aiEnabled=$($ai.enabled) provider=$($ai.provider) model=$($ai.model)"
if (-not [bool]$ai.enabled) {
  Write-Output "reason: $($ai.reason)"
  $aiGenerated = Invoke-Api -Method POST -Path "/api/analyses/$($analysis.id)/ai-recommendations" -Token $tokenA
  Check 'AI generation without a provider returns 503' $aiGenerated.Status 503
  # The deterministic result must be untouched by the failed AI attempt.
  $afterAi = Invoke-Api -Method GET -Path "/api/analyses/$($analysis.id)" -Token $tokenA
  $afterAiObj = $afterAi.Body | ConvertFrom-Json
  Check 'deterministic score is unchanged after the AI attempt' $afterAiObj.atsScore $analysis.atsScore
  Check 'deterministic recommendations are still labelled RULE' (($afterAiObj.recommendations | Select-Object -First 1).source) 'RULE'
} else {
  Write-Output 'provider configured; exercising live generation'
  $aiGenerated = Invoke-Api -Method POST -Path "/api/analyses/$($analysis.id)/ai-recommendations" -Token $tokenA
  Write-Output "HTTP $($aiGenerated.Status)"
  $aiObj = $aiGenerated.Body | ConvertFrom-Json
  Check 'AI generation returns 200' $aiGenerated.Status 200
  Check 'AI suggestions are labelled source=AI' (($aiObj.aiRecommendations | Select-Object -First 1).source) 'AI'
  Check 'the score was not changed by the AI call' $aiObj.atsScore $analysis.atsScore
}

# ---------------------------------------------------------------------------
Write-Section '13. The owner can delete their own analysis'
$delRaw = & curl.exe -s -o NUL -w '%{http_code}' -X DELETE "$Base/api/analyses/$($analysis.id)" -H "Authorization: Bearer $tokenA"
Check 'owner deletes own analysis (204)' ([int]("$delRaw".Trim())) 204
$gone = Invoke-Api -Method GET -Path "/api/analyses/$($analysis.id)" -Token $tokenA
Check 'deleted analysis is gone (404)' $gone.Status 404
$histAfter = Invoke-Api -Method GET -Path '/api/analyses' -Token $tokenA
Check 'history is empty after the delete' (($histAfter.Body | ConvertFrom-Json).Count) 0
$resumeKept = Invoke-Api -Method GET -Path "/api/resumes/$($resume.id)" -Token $tokenA
Check 'the resume itself is untouched (200)' $resumeKept.Status 200
$reportGone = & curl.exe -s -o NUL -w '%{http_code}' "$Base/api/analyses/$($analysis.id)/report" -H "Authorization: Bearer $tokenA"
Check 'the report of a deleted analysis is gone too (404)' ([int]("$reportGone".Trim())) 404

# ---------------------------------------------------------------------------
Remove-Item -Recurse -Force $Tmp -ErrorAction SilentlyContinue

Write-Section 'SUMMARY'
$failed = @($script:Checks | Where-Object { $_.Result -eq 'FAIL' })
Write-Output "checks: $($script:Checks.Count)   passed: $(($script:Checks | Where-Object { $_.Result -eq 'PASS' }).Count)   failed: $($failed.Count)"
if ($failed.Count -gt 0) {
  Write-Output 'FAILED CHECKS:'
  $failed | ForEach-Object { Write-Output "  - $($_.Name): expected=$($_.Expected) actual=$($_.Actual)" }
  exit 1
}
Write-Output 'RESULT: ALL CHECKS PASSED'
exit 0
