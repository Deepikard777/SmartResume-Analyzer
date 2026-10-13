package com.resumeanalyzer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Lightweight summary for the Analysis History list.
 * Does NOT include full diagnostic payload (skills, keywords, structure).
 * The frontend uses this for the history page cards.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalysisSummaryResponse {

    private Long id;
    private Long resumeId;
    private String resumeFileName;
    private Long jobDescriptionId;
    private String jobTitle;
    private String companyName;
    private Integer atsScore;
    private String status;
    private LocalDateTime createdAt;
}
