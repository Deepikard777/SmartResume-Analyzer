package com.resumeanalyzer.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeanalyzer.dto.AnalysisResponse;
import com.resumeanalyzer.dto.AnalysisSummaryResponse;
import com.resumeanalyzer.dto.AnalyzeResumeRequest;
import com.resumeanalyzer.entity.AnalysisStatus;
import com.resumeanalyzer.entity.JobDescription;
import com.resumeanalyzer.entity.Resume;
import com.resumeanalyzer.entity.ResumeAnalysis;
import com.resumeanalyzer.entity.User;
import com.resumeanalyzer.exception.AnalysisException;
import com.resumeanalyzer.exception.AnalysisNotFoundException;
import com.resumeanalyzer.exception.InvalidCredentialsException;
import com.resumeanalyzer.exception.ResumeNotFoundException;
import com.resumeanalyzer.repository.AnalysisRepository;
import com.resumeanalyzer.repository.ResumeRepository;
import com.resumeanalyzer.repository.UserRepository;
import com.resumeanalyzer.service.analysis.FormattingAnalyzer;
import com.resumeanalyzer.service.analysis.KeywordMatcher;
import com.resumeanalyzer.service.analysis.KeywordStuffingDetector;
import com.resumeanalyzer.service.analysis.RecommendationGenerator;
import com.resumeanalyzer.service.analysis.ResumeStructureAnalyzer;
import com.resumeanalyzer.service.analysis.SkillMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Orchestrates the deterministic ATS analysis.
 *
 * This class adds no new matching logic of its own — it wires together the
 * existing engine components (SkillMatcher, KeywordMatcher,
 * KeywordStuffingDetector, ResumeStructureAnalyzer, FormattingAnalyzer,
 * RecommendationGenerator), derives the aggregate ATS score, persists the result
 * as a ResumeAnalysis record (JSON columns, as the entity javadoc prescribes) and
 * maps it to the existing AnalysisResponse / AnalysisSummaryResponse DTOs.
 *
 * This is NOT a reproduction of any commercial ATS product and it performs no
 * AI / LLM inference of any kind.
 *
 * Ownership: every method takes the authenticated user id. A resume or job
 * description belonging to somebody else resolves to "not found" (404), so an
 * analysis can only ever be run on, and read by, its owner.
 *
 * ATS score weighting (project-defined, documented so it is not a magic number):
 *   skill match 40% + keyword match 25% + relevance 15% + structure 12% + formatting 8%
 *
 * "Relevance" is deliberately a different signal from "keyword match":
 *   - keyword match = unweighted share of JD keywords found in the resume
 *   - relevance     = share of the JD's keyword *emphasis* covered, i.e. each JD
 *                     keyword is weighted by how often the JD itself uses it
 */
@Service
@Transactional(readOnly = true)
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    private static final double WEIGHT_SKILL_MATCH   = 0.40;
    private static final double WEIGHT_KEYWORD_MATCH = 0.25;
    private static final double WEIGHT_RELEVANCE     = 0.15;
    private static final double WEIGHT_STRUCTURE     = 0.12;
    private static final double WEIGHT_FORMATTING    = 0.08;

    /** Neutral value used by the engine components when there is no data to score. */
    private static final int NEUTRAL_SCORE = 50;

    private final AnalysisRepository analysisRepository;
    private final ResumeRepository resumeRepository;
    private final UserRepository userRepository;
    private final JobDescriptionService jobDescriptionService;

    private final SkillMatcher skillMatcher;
    private final KeywordMatcher keywordMatcher;
    private final KeywordStuffingDetector keywordStuffingDetector;
    private final ResumeStructureAnalyzer resumeStructureAnalyzer;
    private final FormattingAnalyzer formattingAnalyzer;
    private final RecommendationGenerator recommendationGenerator;

    private final ObjectMapper objectMapper;

    public AnalysisService(
            AnalysisRepository analysisRepository,
            ResumeRepository resumeRepository,
            UserRepository userRepository,
            JobDescriptionService jobDescriptionService,
            SkillMatcher skillMatcher,
            KeywordMatcher keywordMatcher,
            KeywordStuffingDetector keywordStuffingDetector,
            ResumeStructureAnalyzer resumeStructureAnalyzer,
            FormattingAnalyzer formattingAnalyzer,
            RecommendationGenerator recommendationGenerator,
            ObjectMapper objectMapper) {
        this.analysisRepository = analysisRepository;
        this.resumeRepository = resumeRepository;
        this.userRepository = userRepository;
        this.jobDescriptionService = jobDescriptionService;
        this.skillMatcher = skillMatcher;
        this.keywordMatcher = keywordMatcher;
        this.keywordStuffingDetector = keywordStuffingDetector;
        this.resumeStructureAnalyzer = resumeStructureAnalyzer;
        this.formattingAnalyzer = formattingAnalyzer;
        this.recommendationGenerator = recommendationGenerator;
        this.objectMapper = objectMapper;
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Analyse a stored resume against a stored job description and persist the result.
     * Both records must belong to {@code ownerId}.
     */
    @Transactional
    public AnalysisResponse analyze(AnalyzeResumeRequest request, Long ownerId) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new InvalidCredentialsException(
                        "The account for this token no longer exists."));

        Resume resume = resumeRepository.findByIdAndUserId(request.getResumeId(), ownerId)
                .orElseThrow(() -> new ResumeNotFoundException(
                        "Resume not found with id: " + request.getResumeId()));

        JobDescription jobDescription = jobDescriptionService.findEntity(request.getJobDescriptionId(), ownerId);

        String resumeText = resume.getExtractedText();
        if (resumeText == null || resumeText.isBlank()) {
            throw new AnalysisException("Resume " + resume.getId()
                    + " has no extracted text, so it cannot be analysed. Upload status was "
                    + resume.getUploadStatus() + ".");
        }

        String jdText = jobDescription.getDescriptionText();
        if (jdText == null || jdText.isBlank()) {
            throw new AnalysisException("Job description " + jobDescription.getId()
                    + " has no description text, so it cannot be analysed.");
        }

        // --- 1. Skills -----------------------------------------------------
        SkillMatcher.SkillMatchResult skillResult = skillMatcher.match(resumeText, jdText);

        // --- 2. Keywords ---------------------------------------------------
        KeywordMatcher.KeywordMatchResult keywordResult = keywordMatcher.match(resumeText, jdText);

        // --- 3. Relevance (JD-emphasis weighted keyword coverage) -----------
        int relevanceScore = computeRelevanceScore(
                keywordResult.keywordFrequenciesInJd(), keywordResult.matchedKeywords());

        // --- 4. Structure --------------------------------------------------
        ResumeStructureAnalyzer.StructureResult structureResult =
                resumeStructureAnalyzer.analyze(resumeText);

        // --- 5. Formatting -------------------------------------------------
        int formattingScore = formattingAnalyzer.score(resumeText);

        // --- 6. Keyword stuffing -------------------------------------------
        List<String> jdKeywords = new ArrayList<>(keywordResult.keywordFrequenciesInJd().keySet());
        KeywordStuffingDetector.StuffingResult stuffingResult =
                keywordStuffingDetector.detect(resumeText, jdKeywords, keywordMatcher);

        // --- Aggregate ------------------------------------------------------
        int atsScore = computeAtsScore(
                skillResult.skillMatchScore(),
                keywordResult.keywordMatchScore(),
                relevanceScore,
                structureResult.structureScore(),
                formattingScore);

        // --- 7. Recommendations derived from the findings above --------------
        List<RecommendationGenerator.Recommendation> recommendations =
                recommendationGenerator.generate(new RecommendationGenerator.Input(
                        skillResult.missingSkills(),
                        skillResult.optionalSkills(),
                        keywordResult.missingKeywords(),
                        keywordResult.keywordFrequenciesInJd(),
                        structureResult.checks(),
                        stuffingResult.overallRisk().name(),
                        stuffingResult.flaggedKeywords(),
                        skillResult.skillMatchScore(),
                        keywordResult.keywordMatchScore(),
                        relevanceScore,
                        structureResult.structureScore(),
                        formattingScore));

        ResumeAnalysis entity = new ResumeAnalysis();
        entity.setUser(owner);
        entity.setResume(resume);
        entity.setJobDescription(jobDescription);
        entity.setAtsScore(atsScore);
        entity.setSkillMatchScore(skillResult.skillMatchScore());
        entity.setKeywordMatchScore(keywordResult.keywordMatchScore());
        entity.setRelevanceScore(relevanceScore);
        entity.setStructureScore(structureResult.structureScore());
        entity.setFormattingScore(formattingScore);

        entity.setMatchedSkillsJson(toJson(skillResult.matchedSkills()));
        entity.setMissingSkillsJson(toJson(skillResult.missingSkills()));
        entity.setOptionalSkillsJson(toJson(skillResult.optionalSkills()));

        entity.setMatchedKeywordsJson(toJson(keywordResult.matchedKeywords()));
        entity.setMissingKeywordsJson(toJson(keywordResult.missingKeywords()));
        entity.setKeywordFrequenciesJson(toJson(keywordResult.keywordFrequenciesInResume()));

        entity.setKeywordCoverageJson(toJson(buildKeywordCoverage(
                keywordResult.keywordFrequenciesInJd(),
                keywordResult.keywordFrequenciesInResume(),
                keywordResult.matchedKeywords(),
                stuffingResult.flaggedKeywords())));

        entity.setKeywordStuffingRisk(stuffingResult.overallRisk().name());
        entity.setFlaggedKeywordsJson(toJson(flaggedToMaps(stuffingResult.flaggedKeywords())));

        entity.setStructureChecksJson(toJson(sectionsToMaps(structureResult.checks())));
        entity.setRecommendationsJson(toJson(recommendationsToMaps(recommendations)));

        entity.setStatus(AnalysisStatus.COMPLETED);

        ResumeAnalysis saved = analysisRepository.save(entity);
        log.info("Analysis completed: id={}, ownerId={}, resumeId={}, jobDescriptionId={}, atsScore={}",
                saved.getId(), ownerId, resume.getId(), jobDescription.getId(), atsScore);

        return toAnalysisResponse(saved);
    }

    /** Full stored diagnostic payload for one analysis owned by the caller. */
    public AnalysisResponse getById(Long id, Long ownerId) {
        return toAnalysisResponse(findEntity(id, ownerId));
    }

    /** History list for the caller, newest first. */
    public List<AnalysisSummaryResponse> getAllSummaries(Long ownerId) {
        return analysisRepository.findAllByUserIdOrderByCreatedAtDesc(ownerId)
                .stream()
                .map(this::toSummaryResponse)
                .collect(Collectors.toList());
    }

    /**
     * Delete one of the caller's own analysis runs.
     * The resume and job description are left untouched — only the stored result
     * is removed.
     */
    @Transactional
    public void deleteById(Long id, Long ownerId) {
        ResumeAnalysis entity = findEntity(id, ownerId);
        analysisRepository.delete(entity);
        log.info("Analysis deleted: id={}, ownerId={}", id, ownerId);
    }

    public List<AnalysisSummaryResponse> getSummariesForResume(Long resumeId, Long ownerId) {
        return analysisRepository.findByUserIdAndResumeIdOrderByCreatedAtDesc(ownerId, resumeId)
                .stream()
                .map(this::toSummaryResponse)
                .collect(Collectors.toList());
    }

    // -----------------------------------------------------------------------
    // Scoring
    // -----------------------------------------------------------------------

    /**
     * JD-emphasis weighted keyword coverage.
     * Each JD keyword contributes its frequency in the JD as its weight; the
     * score is the share of total JD weight covered by keywords present in the resume.
     */
    int computeRelevanceScore(Map<String, Integer> jdKeywordFrequencies,
                              List<String> matchedKeywords) {
        if (jdKeywordFrequencies == null || jdKeywordFrequencies.isEmpty()) {
            return NEUTRAL_SCORE;
        }

        double totalWeight = 0.0;
        double matchedWeight = 0.0;
        for (Map.Entry<String, Integer> entry : jdKeywordFrequencies.entrySet()) {
            double weight = Math.max(1, entry.getValue() == null ? 0 : entry.getValue());
            totalWeight += weight;
            if (matchedKeywords != null && matchedKeywords.contains(entry.getKey())) {
                matchedWeight += weight;
            }
        }

        if (totalWeight == 0.0) {
            return NEUTRAL_SCORE;
        }
        return clampRound((matchedWeight / totalWeight) * 100.0);
    }

    int computeAtsScore(int skillMatch, int keywordMatch, int relevance,
                        int structure, int formatting) {
        double weighted = skillMatch * WEIGHT_SKILL_MATCH
                + keywordMatch * WEIGHT_KEYWORD_MATCH
                + relevance * WEIGHT_RELEVANCE
                + structure * WEIGHT_STRUCTURE
                + formatting * WEIGHT_FORMATTING;
        return clampRound(weighted);
    }

    private static int clampRound(double value) {
        return (int) Math.round(Math.min(100.0, Math.max(0.0, value)));
    }

    // -----------------------------------------------------------------------
    // Mapping helpers
    // -----------------------------------------------------------------------

    /** Ownership-scoped lookup: another user's analysis is simply absent here. */
    private ResumeAnalysis findEntity(Long id, Long ownerId) {
        return analysisRepository.findByIdAndUserId(id, ownerId)
                .orElseThrow(() -> new AnalysisNotFoundException("Analysis not found with id: " + id));
    }

    private AnalysisResponse toAnalysisResponse(ResumeAnalysis entity) {
        return AnalysisResponse.builder()
                .id(entity.getId())
                .resumeId(entity.getResume().getId())
                .resumeFileName(entity.getResume().getOriginalFileName())
                .jobDescriptionId(entity.getJobDescription().getId())
                .jobTitle(entity.getJobDescription().getJobTitle())
                .companyName(entity.getJobDescription().getCompanyName())
                .atsScore(entity.getAtsScore())
                .scoreBreakdown(AnalysisResponse.ScoreBreakdown.builder()
                        .skillMatch(entity.getSkillMatchScore())
                        .keywordMatch(entity.getKeywordMatchScore())
                        .relevance(entity.getRelevanceScore())
                        .structure(entity.getStructureScore())
                        .formatting(entity.getFormattingScore())
                        .build())
                .matchedSkills(readStringList(entity.getMatchedSkillsJson()))
                .missingSkills(readStringList(entity.getMissingSkillsJson()))
                .optionalSkills(readStringList(entity.getOptionalSkillsJson()))
                .matchedKeywords(readStringList(entity.getMatchedKeywordsJson()))
                .missingKeywords(readStringList(entity.getMissingKeywordsJson()))
                .keywordStuffing(AnalysisResponse.KeywordStuffingInfo.builder()
                        .risk(entity.getKeywordStuffingRisk())
                        .flaggedKeywords(readFlaggedKeywords(entity.getFlaggedKeywordsJson()))
                        .build())
                .keywordCoverage(readKeywordCoverage(entity.getKeywordCoverageJson()))
                .structure(readStructureChecks(entity.getStructureChecksJson()))
                .recommendations(readRecommendations(entity.getRecommendationsJson()))
                // Omitted entirely (rather than an empty array) until the user has
                // actually requested AI suggestions, so "not generated" is
                // distinguishable from "generated and produced nothing".
                .aiRecommendations(readOptionalRecommendations(entity.getAiRecommendationsJson()))
                .aiGeneratedAt(entity.getAiGeneratedAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    // -----------------------------------------------------------------------
    // Accessors used by the optional AI layer (which depends on this service,
    // never the other way round, so the deterministic path stays independent).
    // -----------------------------------------------------------------------

    /** Ownership-scoped entity lookup for collaborators in the same package tree. */
    public ResumeAnalysis findOwnedEntity(Long id, Long ownerId) {
        return findEntity(id, ownerId);
    }

    /** Map a persisted analysis to its response DTO. */
    public AnalysisResponse toResponse(ResumeAnalysis entity) {
        return toAnalysisResponse(entity);
    }

    /**
     * Persist validated LLM-authored recommendations against a stored analysis.
     * Deliberately does not touch any score: the model's output cannot alter the ATS
     * result, which is why this is a separate write from {@link #analyze}.
     */
    @Transactional
    public void storeAiRecommendations(Long analysisId,
                                       Long ownerId,
                                       List<AnalysisResponse.Recommendation> recommendations) {
        ResumeAnalysis entity = findEntity(analysisId, ownerId);
        entity.setAiRecommendationsJson(toJson(recommendationDtosToMaps(recommendations)));
        entity.setAiGeneratedAt(LocalDateTime.now());
        analysisRepository.save(entity);
        log.info("Stored {} AI recommendation(s) for analysis id={}, ownerId={}",
                recommendations.size(), analysisId, ownerId);
    }

    private AnalysisSummaryResponse toSummaryResponse(ResumeAnalysis entity) {
        return AnalysisSummaryResponse.builder()
                .id(entity.getId())
                .resumeId(entity.getResume().getId())
                .resumeFileName(entity.getResume().getOriginalFileName())
                .jobDescriptionId(entity.getJobDescription().getId())
                .jobTitle(entity.getJobDescription().getJobTitle())
                .companyName(entity.getJobDescription().getCompanyName())
                .atsScore(entity.getAtsScore())
                .status(entity.getStatus() == null ? null : entity.getStatus().name())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    /**
     * Build one row per keyword for the frequency view.
     *
     * The universe is the job description's keywords plus any keyword the repetition
     * detector flagged, so repetition in the resume is visible even when the term was
     * not requested by the employer. Counts are whole-word occurrences, reported as
     * raw numbers rather than penalties — a Java developer's resume is expected to
     * repeat "java", and only the detector's documented thresholds set `flagged`.
     *
     * Rows are ordered by how much the job description itself emphasises the term,
     * then by resume frequency, then alphabetically, so the ordering is deterministic.
     */
    private List<Map<String, Object>> buildKeywordCoverage(
            Map<String, Integer> jdFrequencies,
            Map<String, Integer> resumeFrequencies,
            List<String> matchedKeywords,
            List<KeywordStuffingDetector.FlaggedEntry> flaggedKeywords) {

        Map<String, Integer> jd = jdFrequencies == null ? Map.of() : jdFrequencies;
        Map<String, Integer> resume = resumeFrequencies == null ? Map.of() : resumeFrequencies;
        List<String> matched = matchedKeywords == null ? List.of() : matchedKeywords;

        Map<String, String> flagReasons = new LinkedHashMap<>();
        for (KeywordStuffingDetector.FlaggedEntry entry : flaggedKeywords) {
            flagReasons.put(entry.keyword(), entry.reason());
        }

        Set<String> universe = new LinkedHashSet<>();
        universe.addAll(jd.keySet());
        universe.addAll(matched);
        universe.addAll(flagReasons.keySet());

        List<Map<String, Object>> rows = new ArrayList<>(universe.size());
        for (String keyword : universe) {
            int jdCount = jd.getOrDefault(keyword, 0);
            int resumeCount = resume.getOrDefault(keyword, 0);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("keyword", keyword);
            row.put("jobDescriptionCount", jdCount);
            row.put("resumeCount", resumeCount);
            row.put("matched", matched.contains(keyword));
            row.put("flagged", flagReasons.containsKey(keyword));
            row.put("flagReason", flagReasons.get(keyword));
            row.put("inJobDescription", jdCount > 0);
            rows.add(row);
        }

        rows.sort(Comparator
                .comparingInt((Map<String, Object> row) -> -asInt(row.get("jobDescriptionCount")))
                .thenComparingInt(row -> -asInt(row.get("resumeCount")))
                .thenComparing(row -> String.valueOf(row.get("keyword"))));

        return rows;
    }

    private static int asInt(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private List<Map<String, Object>> flaggedToMaps(
            List<KeywordStuffingDetector.FlaggedEntry> flaggedKeywords) {
        List<Map<String, Object>> maps = new ArrayList<>();
        for (KeywordStuffingDetector.FlaggedEntry entry : flaggedKeywords) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("keyword", entry.keyword());
            map.put("frequency", entry.frequency());
            map.put("reason", entry.reason());
            maps.add(map);
        }
        return maps;
    }

    private List<Map<String, Object>> sectionsToMaps(
            List<ResumeStructureAnalyzer.SectionCheck> checks) {
        List<Map<String, Object>> maps = new ArrayList<>();
        for (ResumeStructureAnalyzer.SectionCheck check : checks) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("section", check.section());
            map.put("status", check.status());
            map.put("message", check.message());
            maps.add(map);
        }
        return maps;
    }

    private List<Map<String, Object>> recommendationsToMaps(
            List<RecommendationGenerator.Recommendation> recommendations) {
        List<Map<String, Object>> maps = new ArrayList<>();
        for (RecommendationGenerator.Recommendation recommendation : recommendations) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("category", recommendation.category());
            map.put("priority", recommendation.priority());
            map.put("title", recommendation.title());
            map.put("detail", recommendation.detail());
            map.put("relatedItems", recommendation.relatedItems());
            map.put("source", "RULE"); // deterministic generator output
            maps.add(map);
        }
        return maps;
    }

    /**
     * Serialise already-validated DTO recommendations (the LLM path, where each item
     * carries its own `source`).
     */
    private List<Map<String, Object>> recommendationDtosToMaps(
            List<AnalysisResponse.Recommendation> recommendations) {
        List<Map<String, Object>> maps = new ArrayList<>();
        for (AnalysisResponse.Recommendation recommendation : recommendations) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("category", recommendation.getCategory());
            map.put("priority", recommendation.getPriority());
            map.put("title", recommendation.getTitle());
            map.put("detail", recommendation.getDetail());
            map.put("relatedItems", recommendation.getRelatedItems());
            map.put("source", recommendation.getSource() == null ? "AI" : recommendation.getSource());
            maps.add(map);
        }
        return maps;
    }

    // -----------------------------------------------------------------------
    // JSON helpers (the entity stores list/map payloads as JSON strings)
    // -----------------------------------------------------------------------

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new AnalysisException("Failed to serialise analysis payload for persistence.", e);
        }
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            log.warn("Could not deserialise stored string list; returning empty list.", e);
            return List.of();
        }
    }

    private List<AnalysisResponse.FlaggedKeyword> readFlaggedKeywords(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> raw =
                    objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
            List<AnalysisResponse.FlaggedKeyword> result = new ArrayList<>();
            for (Map<String, Object> row : raw) {
                result.add(AnalysisResponse.FlaggedKeyword.builder()
                        .keyword(asString(row.get("keyword")))
                        .frequency(asInteger(row.get("frequency")))
                        .reason(asString(row.get("reason")))
                        .build());
            }
            return result;
        } catch (Exception e) {
            log.warn("Could not deserialise stored flagged keywords; returning empty list.", e);
            return List.of();
        }
    }

    /**
     * Like {@link #readRecommendations} but returns null when nothing was stored, so
     * the field is omitted from the JSON response.
     */
    private List<AnalysisResponse.Recommendation> readOptionalRecommendations(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        return readRecommendations(json);
    }

    private List<AnalysisResponse.KeywordCoverage> readKeywordCoverage(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> raw =
                    objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
            List<AnalysisResponse.KeywordCoverage> result = new ArrayList<>(raw.size());
            for (Map<String, Object> row : raw) {
                result.add(AnalysisResponse.KeywordCoverage.builder()
                        .keyword(asString(row.get("keyword")))
                        .jobDescriptionCount(asInteger(row.get("jobDescriptionCount")))
                        .resumeCount(asInteger(row.get("resumeCount")))
                        .matched(asBoolean(row.get("matched")))
                        .flagged(asBoolean(row.get("flagged")))
                        .flagReason(asString(row.get("flagReason")))
                        .inJobDescription(asBoolean(row.get("inJobDescription")))
                        .build());
            }
            return result;
        } catch (Exception e) {
            log.warn("Could not deserialise stored keyword coverage; returning empty list.", e);
            return List.of();
        }
    }

    private List<AnalysisResponse.StructureCheck> readStructureChecks(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> raw =
                    objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
            List<AnalysisResponse.StructureCheck> result = new ArrayList<>();
            for (Map<String, Object> row : raw) {
                result.add(AnalysisResponse.StructureCheck.builder()
                        .section(asString(row.get("section")))
                        .status(asString(row.get("status")))
                        .message(asString(row.get("message")))
                        .build());
            }
            return result;
        } catch (Exception e) {
            log.warn("Could not deserialise stored structure checks; returning empty list.", e);
            return List.of();
        }
    }

    private List<AnalysisResponse.Recommendation> readRecommendations(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            List<Map<String, Object>> raw =
                    objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
            List<AnalysisResponse.Recommendation> result = new ArrayList<>();
            for (Map<String, Object> row : raw) {
                result.add(AnalysisResponse.Recommendation.builder()
                        .category(asString(row.get("category")))
                        .priority(asString(row.get("priority")))
                        .title(asString(row.get("title")))
                        .detail(asString(row.get("detail")))
                        .relatedItems(asStringList(row.get("relatedItems")))
                        .source(asString(row.get("source")))
                        .build());
            }
            return result;
        } catch (Exception e) {
            log.warn("Could not deserialise stored recommendations; returning empty list.", e);
            return List.of();
        }
    }

    private static List<String> asStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>(list.size());
        for (Object item : list) {
            result.add(asString(item));
        }
        return result;
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static Boolean asBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null) {
            return null;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
