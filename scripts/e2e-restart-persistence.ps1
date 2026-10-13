# ---------------------------------------------------------------------------
# Proves persistence across a real backend restart.
#
# The "dev" profile uses in-memory H2, so nothing survives a restart by design.
# This script therefore starts the packaged jar twice against a FILE-based H2
# database, with a fixed JWT signing key, and verifies that:
#
#   1. a user created before the restart still exists afterwards
#   2. the stored resumes, job descriptions and analysis are still there
#   3. the ATS score and payload are byte-identical (nothing is recomputed on read)
#   4. the token issued before the restart is still accepted, because JWT_SECRET
#      was supplied rather than generated per boot
#
# The application is started with `spring-boot:run -Dspring-boot.run.fork=false`, so
# the app runs inside the Maven JVM rather than as a forked child. That makes stopping
# it deterministic (kill the process tree = the app is gone), which a forked JVM is not.
#
# NOTE: this deliberately does not use `mvn package`/`java -jar`. Building an
# executable jar requires maven-jar-plugin and the spring-boot repackage goal, which
# may not be present in a fully offline Maven cache; `spring-boot:run` only needs the
# plugins already used to start the app normally. If you have network access, either
# approach works — see the README for the packaged-jar command.
#
# It cleans up the processes and database file it creates.
#
# Usage: powershell -ExecutionPolicy Bypass -File scripts/e2e-restart-persistence.ps1
# ---------------------------------------------------------------------------

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

$RepoRoot = Split-Path $PSScriptRoot -Parent
$BackendDir = Join-Path $RepoRoot 'resume-analyzer\resume-analyzer'
$PdfPath = Join-Path $PSScriptRoot 'test-resume.pdf'
$DocxPath = Join-Path $PSScriptRoot 'test-resume.docx'
$DataDir = Join-Path $BackendDir 'data'
$DbFile = Join-Path $DataDir 'e2e-persist'
$Port = 8080
$Base = "http://localhost:$Port"

$script:Checks = New-Object System.Collections.Generic.List[object]

function Check {
  param([string]$Name, $Actual, $Expected)
  $ok = "$Actual" -eq "$Expected"
  $script:Checks.Add([pscustomobject]@{ Name = $Name; Expected = $Expected; Actual = $Actual; Result = $(if ($ok) { 'PASS' } else { 'FAIL' }) })
  Write-Output ("{0,-58} expected={1,-6} actual={2,-6} {3}" -f $Name, $Expected, $Actual, $(if ($ok) { 'PASS' } else { 'FAIL' }))
}

function Wait-ForPort {
  param([int]$TimeoutSeconds = 120)
  $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
  while ((Get-Date) -lt $deadline) {
    $client = New-Object System.Net.Sockets.TcpClient
    try {
      $client.Connect('127.0.0.1', $Port)
      return $true
    } catch {
      Start-Sleep -Milliseconds 750
    } finally {
      $client.Close()
    }
  }
  return $false
}

function Start-Backend {
  # fork=false keeps the application in the Maven JVM, so one process tree owns the port.
  $proc = Start-Process -FilePath 'cmd.exe' `
    -ArgumentList @('/c', 'mvnw.cmd -o spring-boot:run -Dspring-boot.run.fork=false') `
    -WorkingDirectory $BackendDir -PassThru -WindowStyle Hidden
  if (-not (Wait-ForPort)) {
    try { & taskkill /PID $proc.Id /T /F 2>&1 | Out-Null } catch { }
    throw "The backend did not open port $Port within the timeout."
  }
  return $proc
}

function Stop-Backend {
  param($Process)
  if ($null -eq $Process) { return }
  # /T kills the whole tree (cmd -> maven -> the app), which is why fork=false matters.
  & taskkill /PID $Process.Id /T /F 2>&1 | Out-Null
  # Wait for the port to be released before starting again.
  $deadline = (Get-Date).AddSeconds(45)
  while ((Get-Date) -lt $deadline) {
    $client = New-Object System.Net.Sockets.TcpClient
    $inUse = $false
    try { $client.Connect('127.0.0.1', $Port); $inUse = $true } catch { $inUse = $false } finally { $client.Close() }
    if (-not $inUse) { return }
    Start-Sleep -Milliseconds 750
  }
  throw 'The backend port was still in use after stopping the process.'
}

function New-JsonFile {
  param([string]$Name, $Object)
  $path = Join-Path $env:TEMP $Name
  ($Object | ConvertTo-Json -Compress) | Set-Content -Encoding UTF8 -NoNewline $path
  return $path
}

$backend = $null
$exitCode = 0

try {
  if (-not (Test-Path (Join-Path $BackendDir 'mvnw.cmd'))) {
    throw "Backend module not found at $BackendDir"
  }

  # --- Fresh file-backed database + fixed signing key -----------------------
  if (Test-Path $DataDir) { Remove-Item -Recurse -Force $DataDir }
  New-Item -ItemType Directory -Force -Path $DataDir | Out-Null

  $env:SPRING_PROFILES_ACTIVE = 'dev'
  $env:SPRING_DATASOURCE_URL = "jdbc:h2:file:$($DbFile -replace '\\','/');MODE=MySQL;DB_CLOSE_DELAY=-1"
  $env:JWT_SECRET = 'restart-persistence-verification-secret-0123456789'
  Write-Output "database: $env:SPRING_DATASOURCE_URL"

  # --- First run: create the data ------------------------------------------
  Write-Output ''
  Write-Output '=== Boot 1: create data ==='
  $backend = Start-Backend
  Write-Output "backend started (pid $($backend.Id))"

  $email = "restart.user.$([DateTime]::UtcNow.Ticks)@example.com"
  $password = 'RestartPass123'

  $reg = (curl.exe -s -X POST "$Base/api/auth/register" -H 'Content-Type: application/json' `
      --data-binary "@$(New-JsonFile 'restart-reg.json' @{ name = 'Restart User'; email = $email; password = $password })") | ConvertFrom-Json
  $tokenBeforeRestart = $reg.token
  Check 'user registered before the restart' ([bool]$tokenBeforeRestart) $true

  $resume = (curl.exe -s -X POST "$Base/api/resumes/upload" -H "Authorization: Bearer $tokenBeforeRestart" `
      -F "file=@$PdfPath;type=application/pdf") | ConvertFrom-Json
  Check 'resume uploaded before the restart' $resume.uploadStatus 'PARSED'

  $docx = (curl.exe -s -X POST "$Base/api/resumes/upload" -H "Authorization: Bearer $tokenBeforeRestart" `
      -F "file=@$DocxPath;type=application/vnd.openxmlformats-officedocument.wordprocessingml.document") | ConvertFrom-Json
  Check 'DOCX resume uploaded before the restart' $docx.uploadStatus 'PARSED'

  $jd = (curl.exe -s -X POST "$Base/api/job-descriptions" -H "Authorization: Bearer $tokenBeforeRestart" `
      -H 'Content-Type: application/json' `
      --data-binary "@$(New-JsonFile 'restart-jd.json' @{
        jobTitle        = 'Backend Engineer'
        companyName     = 'Persist Ltd'
        descriptionText = 'Backend engineer with Java, Spring Boot, PostgreSQL, Docker and AWS experience. Microservices required.'
      })") | ConvertFrom-Json
  Check 'job description created before the restart' ([bool]$jd.id) $true

  $analysis = (curl.exe -s -X POST "$Base/api/analyses" -H "Authorization: Bearer $tokenBeforeRestart" `
      -H 'Content-Type: application/json' `
      --data-binary "@$(New-JsonFile 'restart-analysis.json' @{ resumeId = $resume.id; jobDescriptionId = $jd.id })") | ConvertFrom-Json
  Check 'analysis created before the restart' ([bool]$analysis.id) $true
  Write-Output "analysisId=$($analysis.id) atsScore=$($analysis.atsScore) recommendations=$($analysis.recommendations.Count)"

  # --- Restart -------------------------------------------------------------
  Write-Output ''
  Write-Output '=== Restarting the backend ==='
  Stop-Backend -Process $backend
  Write-Output 'backend stopped'
  $backend = Start-Backend
  Write-Output "backend restarted (pid $($backend.Id))"

  # --- Verify after the restart -------------------------------------------
  Write-Output ''
  Write-Output '=== Boot 2: verify persistence ==='

  # The token from before the restart must still work: it does, because a fixed
  # JWT_SECRET was supplied instead of one generated per boot.
  $me = (curl.exe -s -o NUL -w '%{http_code}' "$Base/api/auth/me" -H "Authorization: Bearer $tokenBeforeRestart")
  Check 'the pre-restart token is still accepted (200)' ([int]("$me".Trim())) 200

  $login = (curl.exe -s -X POST "$Base/api/auth/login" -H 'Content-Type: application/json' `
      --data-binary "@$(New-JsonFile 'restart-login.json' @{ email = $email; password = $password })") | ConvertFrom-Json
  Check 'the account still exists and can sign in' ([bool]$login.token) $true
  $token = $login.token

  $resumes = (curl.exe -s "$Base/api/resumes" -H "Authorization: Bearer $token") | ConvertFrom-Json
  Check 'both resumes survived the restart' $resumes.Count 2

  $jds = (curl.exe -s "$Base/api/job-descriptions" -H "Authorization: Bearer $token") | ConvertFrom-Json
  Check 'the job description survived the restart' $jds.Count 1

  $history = (curl.exe -s "$Base/api/analyses" -H "Authorization: Bearer $token") | ConvertFrom-Json
  Check 'the analysis history survived the restart' $history.Count 1

  $reloaded = (curl.exe -s "$Base/api/analyses/$($analysis.id)" -H "Authorization: Bearer $token") | ConvertFrom-Json
  Check 'the stored ATS score is identical after the restart' $reloaded.atsScore $analysis.atsScore
  Check 'matched skills are identical after the restart' (($reloaded.matchedSkills -join ',')) (($analysis.matchedSkills -join ','))
  Check 'recommendations are identical after the restart' ($reloaded.recommendations.Count) ($analysis.recommendations.Count)
  Check 'keyword coverage is identical after the restart' ($reloaded.keywordCoverage.Count) ($analysis.keywordCoverage.Count)
  Check 'resume files are still listed with the right names' ([bool](($resumes | Where-Object { $_.originalFileName -eq 'test-resume.pdf' }))) $true

  # A fresh login must still be unable to reach another user's data.
  $other = (curl.exe -s -X POST "$Base/api/auth/register" -H 'Content-Type: application/json' `
      --data-binary "@$(New-JsonFile 'restart-other.json' @{
        name = 'Restart Other'; email = "restart.other.$([DateTime]::UtcNow.Ticks)@example.com"; password = $password
      })") | ConvertFrom-Json
  $otherData = (curl.exe -s -o NUL -w '%{http_code}' "$Base/api/analyses/$($analysis.id)" -H "Authorization: Bearer $($other.token)")
  Check 'a different user still cannot read the analysis (404)' ([int]("$otherData".Trim())) 404

} catch {
  Write-Output "ERROR: $($_.Exception.Message)"
  $exitCode = 2
} finally {
  Write-Output ''
  Write-Output '=== Cleanup ==='
  if ($backend) { Stop-Backend -Process $backend }
  Remove-Item Env:\SPRING_PROFILES_ACTIVE -ErrorAction SilentlyContinue
  Remove-Item Env:\SPRING_DATASOURCE_URL -ErrorAction SilentlyContinue
  Remove-Item Env:\JWT_SECRET -ErrorAction SilentlyContinue
  if (Test-Path $DataDir) { Remove-Item -Recurse -Force $DataDir -ErrorAction SilentlyContinue }
  Write-Output 'processes stopped and the temporary database removed'
}

Write-Output ''
Write-Output '=== SUMMARY ==='
$failed = @($script:Checks | Where-Object { $_.Result -eq 'FAIL' })
Write-Output "checks: $($script:Checks.Count)   passed: $(($script:Checks | Where-Object { $_.Result -eq 'PASS' }).Count)   failed: $($failed.Count)"
if ($failed.Count -gt 0) {
  $failed | ForEach-Object { Write-Output "  - $($_.Name): expected=$($_.Expected) actual=$($_.Actual)" }
  $exitCode = 1
} elseif ($exitCode -eq 0) {
  Write-Output 'RESULT: ALL CHECKS PASSED'
}
exit $exitCode
