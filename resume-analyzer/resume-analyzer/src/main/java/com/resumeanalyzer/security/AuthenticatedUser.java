package com.resumeanalyzer.security;

/**
 * The authenticated caller, derived from a validated JWT.
 *
 * This is the ONLY identity object controllers and services use to scope data.
 * It deliberately carries no password and no token.
 */
public record AuthenticatedUser(Long id, String name, String email) {
}
