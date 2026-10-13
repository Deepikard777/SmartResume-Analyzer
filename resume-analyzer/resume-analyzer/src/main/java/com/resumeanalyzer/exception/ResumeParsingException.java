package com.resumeanalyzer.exception;

public class ResumeParsingException extends RuntimeException {

    public ResumeParsingException(String message) {
        super(message);
    }

    public ResumeParsingException(String message, Throwable cause) {
        super(message, cause);
    }
}
