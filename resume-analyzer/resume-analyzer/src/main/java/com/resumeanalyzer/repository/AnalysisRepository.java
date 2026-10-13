package com.resumeanalyzer.repository;

import com.resumeanalyzer.entity.ResumeAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AnalysisRepository extends JpaRepository<ResumeAnalysis, Long> {

    List<ResumeAnalysis> findAllByOrderByCreatedAtDesc();

    /** Analysis history for one user, newest first. */
    List<ResumeAnalysis> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    /** Ownership-checked lookup: a row belonging to another user is simply absent. */
    Optional<ResumeAnalysis> findByIdAndUserId(Long id, Long userId);

    List<ResumeAnalysis> findByResumeIdOrderByCreatedAtDesc(Long resumeId);

    List<ResumeAnalysis> findByUserIdAndResumeIdOrderByCreatedAtDesc(Long userId, Long resumeId);

    List<ResumeAnalysis> findByJobDescriptionIdOrderByCreatedAtDesc(Long jobDescriptionId);
}
