package com.resumeanalyzer.exception;

/**
 * Raised when the LLM provider was reachable but the call failed (timeout, rejected
 * credentials, rate limit, unusable or unparseable output). Reported as HTTP 502 —
 * the failure is in the upstream service, not in the client's request.
 */
public class AiGenerationException extends RuntimeException {
    public AiGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
