@echo off
REM ---------------------------------------------------------------------------
REM Starts the Vite dev server on http://localhost:5173.
REM
REM That port is deliberate: the backend CORS bean allows exactly
REM http://localhost:5173 and http://localhost:3000 (see SecurityConfig).
REM
REM Usage (from the repository root):
REM   scripts\run-frontend.cmd
REM ---------------------------------------------------------------------------
setlocal
set "ROOT=%~dp0.."
pushd "%ROOT%\frontend"
call npm run dev
popd
endlocal
