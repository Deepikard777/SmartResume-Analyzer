package com.resumeanalyzer.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnalyzeResumeRequest {

    @NotNull(message = "Resume ID is required.")
    private Long resumeId;

    @NotNull(message = "Job description ID is required.")
    private Long jobDescriptionId;
}
