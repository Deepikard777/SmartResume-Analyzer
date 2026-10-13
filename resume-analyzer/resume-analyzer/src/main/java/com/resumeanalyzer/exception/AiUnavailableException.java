package com.resumeanalyzer.exception;

/**
 * Raised when the LLM feature is requested but not configured. Reported as HTTP 503,
 * which tells the client the capability exists but is not available right now — as
 * opposed to 400/500, which would wrongly imply a bad request or a bug.
 */
public class AiUnavailableException extends RuntimeException {
    public AiUnavailableException(String message) {
        super(message);
    }
}
