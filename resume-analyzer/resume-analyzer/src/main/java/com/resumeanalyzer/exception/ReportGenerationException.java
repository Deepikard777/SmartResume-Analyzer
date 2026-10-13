package com.resumeanalyzer.exception;

/**
 * Raised when a PDF report cannot be produced. Reported as HTTP 500: the request was
 * valid and the caller was authorised, so the failure is on the server side.
 */
public class ReportGenerationException extends RuntimeException {
    public ReportGenerationException(String message) {
        super(message);
    }

    public ReportGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
