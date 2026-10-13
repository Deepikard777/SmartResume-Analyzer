package com.resumeanalyzer.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Persisted result of one deterministic ATS analysis run.
 *
 * List/Map fields (skills, keywords, frequencies, structure checks) are stored as
 * JSON strings using a Jackson ObjectMapper in AnalysisService.
 * This avoids @ElementCollection complexity and works cleanly with both MySQL and H2.
 *
 * The full resume text and JD text are NOT duplicated here — they are referenced
 * via the FK relationships to Resume and JobDescription.
 */
@Entity
@Table(name = "resume_analyses")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResumeAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resume_id", nullable = false)
    private Resume resume;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_description_id", nullable = false)
    private JobDescription jobDescription;

    /**
     * Owner of this analysis — the user who requested it.
     *
     * Nullable at the column level so `ddl-auto=update` can add it to a table with
     * existing rows. Analysis history and detail reads are scoped by this field.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = true)
    private User user;

    // --- Scores (0–100) ---

    @Column(nullable = false)
    private Integer atsScore;

    @Column(nullable = false)
    private Integer skillMatchScore;

    @Column(nullable = false)
    private Integer keywordMatchScore;

    @Column(nullable = false)
    private Integer relevanceScore;

    @Column(nullable = false)
    private Integer structureScore;

    @Column(nullable = false)
    private Integer formattingScore;

    // --- Skill analysis (stored as JSON arrays) ---

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String matchedSkillsJson;   // List<String>

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String missingSkillsJson;   // List<String>

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String optionalSkillsJson;  // List<String>

    // --- Keyword analysis (stored as JSON) ---

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String matchedKeywordsJson;    // List<String>

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String missingKeywordsJson;    // List<String>

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String keywordFrequenciesJson; // Map<String, Integer> resume-side frequencies

    /**
     * Per-keyword coverage rows: {keyword, jobDescriptionCount, resumeCount, matched,
     * flagged, flagReason, inJobDescription}. Powers the keyword-frequency view.
     */
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String keywordCoverageJson;

    // --- Keyword stuffing detection ---

    @Column(length = 10)
    private String keywordStuffingRisk; // LOW / MEDIUM / HIGH

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String flaggedKeywordsJson; // List<Map> with keyword + frequency + reason

    // --- Structure analysis (stored as JSON array) ---

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String structureChecksJson; // List<Map> with section + status + message

    // --- Actionable recommendations derived from the findings above ---

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String recommendationsJson; // List<Map> with category + priority + title + detail + relatedItems

    // --- Optional LLM-authored suggestions (kept strictly separate from the above) ---

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String aiRecommendationsJson; // List<Map>, same shape, source = "AI"

    private LocalDateTime aiGeneratedAt;

    // --- Status ---

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AnalysisStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
