package com.resumeanalyzer.repository;

import com.resumeanalyzer.entity.JobDescription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobDescriptionRepository extends JpaRepository<JobDescription, Long> {

    List<JobDescription> findAllByOrderByCreatedAtDesc();

    /** Job descriptions owned by one user, newest first. */
    List<JobDescription> findAllByUserIdOrderByCreatedAtDesc(Long userId);

    /** Ownership-checked lookup: a row belonging to another user is simply absent. */
    Optional<JobDescription> findByIdAndUserId(Long id, Long userId);
}
