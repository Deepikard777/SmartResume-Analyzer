package com.resumeanalyzer.dto;

import com.resumeanalyzer.entity.UploadStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResumeDetailResponse {

    private Long id;
    private String originalFileName;
    private String fileType;
    private Long fileSize;
    private UploadStatus uploadStatus;
    private Integer extractedCharacterCount;
    private String extractedText;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
