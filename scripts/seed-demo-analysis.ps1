# Seeds one complete analysis through the real API so the UI can be inspected
# on /results/<id> without replaying the whole upload flow in the browser.
#
#   0. register a throwaway user            -> POST /api/auth/register  (returns a JWT)
#   1. upload scripts/test-resume.pdf       -> POST /api/resumes/upload
#   2. create the job description           -> POST /api/job-descriptions
#   3. run the analysis                     -> POST /api/analyses
#   4. print the history for that user      -> GET  /api/analyses
#
# Every endpoint except register/login now requires
# `Authorization: Bearer <token>`, so the user is registered first and the token
# is sent on every subsequent call.
#
# Sign in to the UI with the printed email/password to see this analysis.
#
# Usage: powershell -ExecutionPolicy Bypass -File scripts/seed-demo-analysis.ps1

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$Base = if ($env:E2E_BASE_URL) { $env:E2E_BASE_URL } else { 'http://localhost:8080' }
$PdfPath = Join-Path $PSScriptRoot 'test-resume.pdf'
if (-not (Test-Path $PdfPath)) { throw "Test PDF not found at $PdfPath" }
$Tmp = Join-Path $env:TEMP ("seed-" + [System.Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Force -Path $Tmp | Out-Null

function Write-Step($text) { Write-Output ''; Write-Output "== $text ==" }

function New-JsonFile {
  param([string]$Name, $Object)
  $path = Join-Path $Tmp $Name
  ($Object | ConvertTo-Json -Compress) | Set-Content -Encoding UTF8 -NoNewline $path
  return $path
}

$email = "demo.user.$([DateTime]::UtcNow.Ticks)@example.com"
$password = 'DemoPass123'

# --- 0. Register a demo user and keep the token ------------------------------
Write-Step '0. Register demo user'
$reg = (curl.exe -s -X POST "$Base/api/auth/register" `
    -H 'Content-Type: application/json' `
    --data-binary "@$(New-JsonFile 'reg.json' @{ name = 'Demo User'; email = $email; password = $password })") | ConvertFrom-Json
$token = $reg.token
if (-not $token) { throw 'Registration did not return a token — is the backend running?' }
$headers = @('-H', "Authorization: Bearer $token")
Write-Output "userId=$($reg.user.id) email=$email password=$password"

# --- 1. Upload ---------------------------------------------------------------
Write-Step '1. Upload resume'
$resume = (& curl.exe -s -X POST "$Base/api/resumes/upload" @headers -F "file=@$PdfPath;type=application/pdf") | ConvertFrom-Json
Write-Output "resumeId=$($resume.id) status=$($resume.uploadStatus) chars=$($resume.extractedCharacterCount)"

# --- 2. Job description ------------------------------------------------------
Write-Step '2. Create job description'
$jd = (& curl.exe -s -X POST "$Base/api/job-descriptions" @headers `
    -H 'Content-Type: application/json' `
    --data-binary "@$(New-JsonFile 'jd.json' @{
      jobTitle        = 'Full Stack Software Engineer'
      companyName     = 'Test Company'
      descriptionText = 'We are looking for a software engineer with Java, Spring Boot, React, SQL, PostgreSQL, Docker and AWS experience. Experience with microservices is required.'
    })") | ConvertFrom-Json
Write-Output "jobDescriptionId=$($jd.id)"

# --- 3. Analyse --------------------------------------------------------------
Write-Step '3. Analyse'
$an = (& curl.exe -s -X POST "$Base/api/analyses" @headers `
    -H 'Content-Type: application/json' `
    --data-binary "@$(New-JsonFile 'an.json' @{ resumeId = $resume.id; jobDescriptionId = $jd.id })") | ConvertFrom-Json
Write-Output "analysisId=$($an.id) atsScore=$($an.atsScore) skillMatch=$($an.scoreBreakdown.skillMatch) structure=$($an.scoreBreakdown.structure)"
Write-Output "missingSkills=$($an.missingSkills -join ', ')"
Write-Output "recommendations=$($an.recommendations.Count)"

Remove-Item -Recurse -Force $Tmp -ErrorAction SilentlyContinue

# --- 4. History --------------------------------------------------------------
Write-Step '4. Analysis history for this user'
$hist = (& curl.exe -s "$Base/api/analyses" @headers) | ConvertFrom-Json
foreach ($row in $hist) {
  Write-Output "#$($row.id) $($row.jobTitle) @ $($row.companyName) | ats=$($row.atsScore) | $($row.status)"
}
Write-Output ''
Write-Output "Open http://localhost:5173/results/$($an.id) after signing in as $email / $password"
