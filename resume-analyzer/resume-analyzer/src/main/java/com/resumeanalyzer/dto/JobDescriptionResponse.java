package com.resumeanalyzer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JobDescriptionResponse {

    private Long id;
    private String jobTitle;
    private String companyName;
    private String descriptionText;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
