package com.resumeanalyzer.exception;

public class JobDescriptionNotFoundException extends RuntimeException {
    public JobDescriptionNotFoundException(String message) {
        super(message);
    }
}
