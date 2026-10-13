package com.resumeanalyzer.controller;

import com.resumeanalyzer.dto.CreateJobDescriptionRequest;
import com.resumeanalyzer.dto.JobDescriptionResponse;
import com.resumeanalyzer.security.AuthenticatedUser;
import com.resumeanalyzer.service.JobDescriptionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * All endpoints require a valid bearer token (see SecurityConfig) and operate
 * only on the authenticated user's own job descriptions.
 */
@RestController
@RequestMapping("/api/job-descriptions")
public class JobDescriptionController {

    private final JobDescriptionService jobDescriptionService;

    public JobDescriptionController(JobDescriptionService jobDescriptionService) {
        this.jobDescriptionService = jobDescriptionService;
    }

    /**
     * Create a job description that can later be compared against a resume.
     */
    @PostMapping
    public ResponseEntity<JobDescriptionResponse> create(
            @Valid @RequestBody CreateJobDescriptionRequest request,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        JobDescriptionResponse response = jobDescriptionService.create(request, caller.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * List the caller's job descriptions, newest first.
     */
    @GetMapping
    public ResponseEntity<List<JobDescriptionResponse>> listAll(
            @AuthenticationPrincipal AuthenticatedUser caller) {
        return ResponseEntity.ok(jobDescriptionService.getAll(caller.id()));
    }

    /**
     * Get one of the caller's job descriptions by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<JobDescriptionResponse> getById(@PathVariable Long id,
                                                          @AuthenticationPrincipal AuthenticatedUser caller) {
        return ResponseEntity.ok(jobDescriptionService.getById(id, caller.id()));
    }

    /**
     * Delete one of the caller's job descriptions by ID.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @AuthenticationPrincipal AuthenticatedUser caller) {
        jobDescriptionService.delete(id, caller.id());
        return ResponseEntity.noContent().build();
    }
}
