package com.resumeanalyzer.entity;

/**
 * Status of a ResumeAnalysis record.
 *
 * COMPLETED — analysis ran and produced a result.
 * FAILED    — analysis could not be completed (logged internally).
 */
public enum AnalysisStatus {
    COMPLETED,
    FAILED
}
