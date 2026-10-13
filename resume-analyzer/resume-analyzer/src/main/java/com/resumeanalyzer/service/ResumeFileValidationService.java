package com.resumeanalyzer.service;

import com.resumeanalyzer.exception.ResumeFileValidationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;

@Service
public class ResumeFileValidationService {

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of("pdf", "docx");
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    private final long maxFileSizeBytes;

    public ResumeFileValidationService(
            @Value("${resume.max-file-size:10MB}") String maxFileSize) {
        this.maxFileSizeBytes = parseSizeToBytes(maxFileSize);
    }

    public void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResumeFileValidationException("Uploaded file is empty.");
        }

        if (file.getSize() > maxFileSizeBytes) {
            throw new ResumeFileValidationException("Resume file must not exceed 10 MB.");
        }

        String extension = extractExtension(file.getOriginalFilename());
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new ResumeFileValidationException(
                    "Only PDF and DOCX resumes are supported. Received: '" + extension + "'.");
        }

        String contentType = file.getContentType();
        if (contentType != null && !contentType.isBlank() && !ALLOWED_MIME_TYPES.contains(contentType)) {
            // Allow if extension is valid but content-type reporting differs (e.g., some browsers)
            // Only hard-reject clearly wrong MIME types
            if (contentType.startsWith("image/") || contentType.startsWith("video/")
                    || contentType.startsWith("audio/") || contentType.equals("application/x-msdownload")
                    || contentType.equals("text/plain")) {
                throw new ResumeFileValidationException(
                        "Only PDF and DOCX resumes are supported. Detected content type: '" + contentType + "'.");
            }
        }
    }

    public String extractExtension(String filename) {
        if (filename == null || filename.isBlank()) {
            throw new ResumeFileValidationException("File name is missing. Please provide a valid PDF or DOCX file.");
        }
        int dotIndex = filename.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == filename.length() - 1) {
            throw new ResumeFileValidationException(
                    "Only PDF and DOCX resumes are supported. No file extension detected.");
        }
        return filename.substring(dotIndex + 1).toLowerCase();
    }

    private long parseSizeToBytes(String sizeStr) {
        if (sizeStr == null || sizeStr.isBlank()) {
            return 10L * 1024 * 1024; // 10 MB default
        }
        String trimmed = sizeStr.trim().toUpperCase();
        try {
            if (trimmed.endsWith("MB")) {
                return Long.parseLong(trimmed.replace("MB", "").trim()) * 1024L * 1024;
            } else if (trimmed.endsWith("KB")) {
                return Long.parseLong(trimmed.replace("KB", "").trim()) * 1024L;
            } else if (trimmed.endsWith("GB")) {
                return Long.parseLong(trimmed.replace("GB", "").trim()) * 1024L * 1024 * 1024;
            } else {
                return Long.parseLong(trimmed);
            }
        } catch (NumberFormatException e) {
            return 10L * 1024 * 1024; // fallback to 10 MB
        }
    }
}
