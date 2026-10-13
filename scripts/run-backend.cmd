@echo off
REM ---------------------------------------------------------------------------
REM Starts the Spring Boot backend with the H2 "dev" profile (no MySQL needed).
REM Runs in the foreground; press Ctrl+C to stop.
REM
REM AUTHENTICATION / JWT SIGNING KEY
REM   The signing key is read from the JWT_SECRET environment variable and is never
REM   hardcoded. If JWT_SECRET is not set, the backend generates a random 256-bit
REM   key at startup: that is secure, but every issued token becomes invalid when
REM   the process restarts (users are simply asked to sign in again).
REM
REM   For a key that survives restarts, set it first, for example:
REM       set "JWT_SECRET=replace-with-at-least-32-random-characters"
REM
REM Usage (from the repository root):
REM   scripts\run-backend.cmd
REM ---------------------------------------------------------------------------
setlocal
set "ROOT=%~dp0.."
pushd "%ROOT%\resume-analyzer\resume-analyzer"
call mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
popd
endlocal
