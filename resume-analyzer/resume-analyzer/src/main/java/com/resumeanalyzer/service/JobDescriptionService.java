package com.resumeanalyzer.service;

import com.resumeanalyzer.dto.CreateJobDescriptionRequest;
import com.resumeanalyzer.dto.JobDescriptionResponse;
import com.resumeanalyzer.entity.JobDescription;
import com.resumeanalyzer.entity.User;
import com.resumeanalyzer.exception.InvalidCredentialsException;
import com.resumeanalyzer.exception.JobDescriptionNotFoundException;
import com.resumeanalyzer.repository.JobDescriptionRepository;
import com.resumeanalyzer.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * CRUD service for Job Description records.
 *
 * Mirrors the style of ResumeService: read-only at class level, write methods
 * override with @Transactional, and every query is scoped to the authenticated
 * user id. A job description owned by another user is reported as 404, never 403.
 */
@Service
@Transactional(readOnly = true)
public class JobDescriptionService {

    private static final Logger log = LoggerFactory.getLogger(JobDescriptionService.class);

    private final JobDescriptionRepository jobDescriptionRepository;
    private final UserRepository userRepository;

    public JobDescriptionService(JobDescriptionRepository jobDescriptionRepository,
                                 UserRepository userRepository) {
        this.jobDescriptionRepository = jobDescriptionRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public JobDescriptionResponse create(CreateJobDescriptionRequest request, Long ownerId) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new InvalidCredentialsException(
                        "The account for this token no longer exists."));

        JobDescription jobDescription = JobDescription.builder()
                .jobTitle(request.getJobTitle().trim())
                .companyName(request.getCompanyName() == null ? null : request.getCompanyName().trim())
                .descriptionText(request.getDescriptionText())
                .user(owner)
                .build();

        JobDescription saved = jobDescriptionRepository.save(jobDescription);
        log.info("Job description created: id={}, ownerId={}, title={}",
                saved.getId(), ownerId, saved.getJobTitle());

        return toResponse(saved);
    }

    public List<JobDescriptionResponse> getAll(Long ownerId) {
        return jobDescriptionRepository.findAllByUserIdOrderByCreatedAtDesc(ownerId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    public JobDescriptionResponse getById(Long id, Long ownerId) {
        return toResponse(findEntity(id, ownerId));
    }

    @Transactional
    public void delete(Long id, Long ownerId) {
        JobDescription jobDescription = findEntity(id, ownerId);
        jobDescriptionRepository.delete(jobDescription);
        log.info("Job description deleted: id={}, ownerId={}", id, ownerId);
    }

    /**
     * Resolve the JD being compared against, for the given owner.
     * Used by AnalysisService, which therefore cannot analyse against a JD it does
     * not own.
     */
    public JobDescription findEntity(Long id, Long ownerId) {
        return jobDescriptionRepository.findByIdAndUserId(id, ownerId)
                .orElseThrow(() -> new JobDescriptionNotFoundException(
                        "Job description not found with id: " + id));
    }

    private JobDescriptionResponse toResponse(JobDescription jobDescription) {
        return JobDescriptionResponse.builder()
                .id(jobDescription.getId())
                .jobTitle(jobDescription.getJobTitle())
                .companyName(jobDescription.getCompanyName())
                .descriptionText(jobDescription.getDescriptionText())
                .createdAt(jobDescription.getCreatedAt())
                .updatedAt(jobDescription.getUpdatedAt())
                .build();
    }
}
