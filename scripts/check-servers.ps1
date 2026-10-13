# Quick health check of both running applications.
#   node scripts\..\  ->  frontend  http://localhost:5173
#   Spring Boot       ->  backend   http://localhost:8080
# Usage: powershell -ExecutionPolicy Bypass -File scripts/check-servers.ps1

$ErrorActionPreference = 'Continue'

Write-Output '--- frontend http://localhost:5173 ---'
curl.exe -s -o NUL -w "GET /                -> HTTP %{http_code}`n" http://localhost:5173/
curl.exe -s -o NUL -w "GET /src/main.tsx    -> HTTP %{http_code}`n" http://localhost:5173/src/main.tsx
curl.exe -s -o NUL -w "GET /logo.svg        -> HTTP %{http_code}`n" http://localhost:5173/logo.svg

Write-Output ''
Write-Output '--- backend http://localhost:8080 ---'
curl.exe -s -o NUL -w "GET /api/resumes          -> HTTP %{http_code}`n" http://localhost:8080/api/resumes
curl.exe -s -o NUL -w "GET /api/job-descriptions -> HTTP %{http_code}`n" http://localhost:8080/api/job-descriptions
curl.exe -s -o NUL -w "GET /api/analyses         -> HTTP %{http_code}`n" http://localhost:8080/api/analyses

Write-Output ''
Write-Output '--- CORS preflight from http://localhost:5173 ---'
curl.exe -s -i -X OPTIONS http://localhost:8080/api/analyses `
  -H "Origin: http://localhost:5173" `
  -H "Access-Control-Request-Method: POST" `
  -H "Access-Control-Request-Headers: content-type" |
  Select-String -Pattern 'HTTP/|access-control-allow-origin|access-control-allow-methods|access-control-allow-credentials'

Write-Output ''
Write-Output '--- index.html served by Vite ---'
curl.exe -s http://localhost:5173/ | Select-String -Pattern '<title>|main.tsx'
