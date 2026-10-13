package com.resumeanalyzer.security;

import com.resumeanalyzer.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Issues and validates HS256 JSON Web Tokens.
 *
 * The signing key comes from {@code app.jwt.secret} (environment variable
 * {@code JWT_SECRET}). It is never hardcoded:
 *
 *  - If a secret is supplied it must be at least 32 bytes (256 bits), which is the
 *    minimum HS256 key length jjwt enforces.
 *  - If no secret is supplied, a random 256-bit key is generated for the lifetime
 *    of the process and a warning is logged. The application still runs, but every
 *    previously issued token is rejected after a restart. Setting JWT_SECRET is the
 *    documented way to keep sessions stable across restarts.
 *
 * Only the user id is used as the token subject; the email and name are carried as
 * claims for convenience. Nothing sensitive (password, hash) is ever placed in a token.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    /** HS256 requires at least 256 bits of key material. */
    private static final int MIN_SECRET_BYTES = 32;

    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_NAME = "name";

    private final SecretKey signingKey;
    private final long expirationMs;
    private final String issuer;

    public JwtService(
            @Value("${app.jwt.secret:}") String secret,
            @Value("${app.jwt.expiration-ms:86400000}") long expirationMs,
            @Value("${app.jwt.issuer:smartresume-analyzer}") String issuer) {
        this.expirationMs = expirationMs;
        this.issuer = issuer;
        this.signingKey = buildSigningKey(secret);
    }

    private SecretKey buildSigningKey(String secret) {
        if (secret != null && !secret.isBlank()) {
            byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
            if (keyBytes.length < MIN_SECRET_BYTES) {
                throw new IllegalStateException(
                        "JWT secret is too short: " + keyBytes.length + " bytes. HS256 requires at least "
                                + MIN_SECRET_BYTES + " bytes (256 bits). Set the JWT_SECRET environment "
                                + "variable to a longer random value.");
            }
            return Keys.hmacShaKeyFor(keyBytes);
        }

        byte[] randomKey = new byte[MIN_SECRET_BYTES];
        new SecureRandom().nextBytes(randomKey);
        log.warn("app.jwt.secret (JWT_SECRET) is not set. A random 256-bit signing key was generated for "
                + "this run — tokens issued now will be rejected after the next restart. Set the JWT_SECRET "
                + "environment variable for a key that survives restarts.");
        return Keys.hmacShaKeyFor(randomKey);
    }

    /** Issue a token whose subject is the user id. */
    public String generateToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .issuer(issuer)
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_NAME, user.getName())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Validate the signature and expiry, then return the user id from the subject.
     * Any malformed, tampered, expired or unsigned token yields an empty result —
     * callers must treat that as "not authenticated".
     */
    public Optional<Long> extractUserId(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            String subject = claims.getSubject();
            if (subject == null || subject.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(Long.valueOf(subject));
        } catch (JwtException | IllegalArgumentException e) {
            // Never log the token itself.
            log.debug("Rejected bearer token: {}", e.getMessage());
            return Optional.empty();
        }
    }

    public long getExpirationMs() {
        return expirationMs;
    }
}
