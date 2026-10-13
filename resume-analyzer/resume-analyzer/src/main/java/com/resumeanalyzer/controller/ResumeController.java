package com.resumeanalyzer.controller;

import com.resumeanalyzer.dto.ResumeDetailResponse;
import com.resumeanalyzer.dto.ResumeResponse;
import com.resumeanalyzer.security.AuthenticatedUser;
import com.resumeanalyzer.service.ResumeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * All endpoints require a valid bearer token (see SecurityConfig) and operate
 * only on the authenticated user's own resumes.
 */
@RestController
@RequestMapping("/api/resumes")
public class ResumeController {

    private final ResumeService resumeService;

    public ResumeController(ResumeService resumeService) {
        this.resumeService = resumeService;
    }

    /**
     * Upload a resume file (PDF or DOCX).
     * Validates, stores, extracts text, and persists metadata owned by the caller.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResumeResponse> upload(@RequestParam("file") MultipartFile file,
                                                 @AuthenticationPrincipal AuthenticatedUser caller) {
        ResumeResponse response = resumeService.uploadResume(file, caller.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * List the caller's uploaded resumes (metadata only, no extracted text).
     */
    @GetMapping
    public ResponseEntity<List<ResumeResponse>> listAll(@AuthenticationPrincipal AuthenticatedUser caller) {
        return ResponseEntity.ok(resumeService.getAllResumes(caller.id()));
    }

    /**
     * Get full details of one of the caller's resumes, including extracted text.
     */
    @GetMapping("/{id}")
    public ResponseEntity<ResumeDetailResponse> getById(@PathVariable Long id,
                                                        @AuthenticationPrincipal AuthenticatedUser caller) {
        return ResponseEntity.ok(resumeService.getResumeById(id, caller.id()));
    }

    /**
     * Delete one of the caller's resumes, removing both the DB record and the stored file.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @AuthenticationPrincipal AuthenticatedUser caller) {
        resumeService.deleteResume(id, caller.id());
        return ResponseEntity.noContent().build();
    }
}
