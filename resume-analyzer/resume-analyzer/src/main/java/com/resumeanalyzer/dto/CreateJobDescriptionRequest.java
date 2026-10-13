package com.resumeanalyzer.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateJobDescriptionRequest {

    @NotBlank(message = "Job title is required.")
    @Size(max = 255, message = "Job title must not exceed 255 characters.")
    private String jobTitle;

    @Size(max = 255, message = "Company name must not exceed 255 characters.")
    private String companyName;

    @NotBlank(message = "Job description text is required.")
    @Size(max = 50000, message = "Job description must not exceed 50,000 characters.")
    private String descriptionText;
}
