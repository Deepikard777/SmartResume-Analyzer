package com.resumeanalyzer.controller;

import com.resumeanalyzer.dto.AnalysisResponse;
import com.resumeanalyzer.dto.AnalysisSummaryResponse;
import com.resumeanalyzer.dto.AnalyzeResumeRequest;
import com.resumeanalyzer.security.AuthenticatedUser;
import com.resumeanalyzer.service.AnalysisService;
import com.resumeanalyzer.service.ai.AiRecommendationService;
import com.resumeanalyzer.service.report.AnalysisReportPdfService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * All endpoints require a valid bearer token (see SecurityConfig). The analysis
 * runs against, and the history returns, only the authenticated user's records.
 */
@RestController
@RequestMapping("/api/analyses")
public class AnalysisController {

    private final AnalysisService analysisService;
    private final AnalysisReportPdfService reportPdfService;
    private final AiRecommendationService aiRecommendationService;

    public AnalysisController(AnalysisService analysisService,
                              AnalysisReportPdfService reportPdfService,
                              AiRecommendationService aiRecommendationService) {
        this.analysisService = analysisService;
        this.reportPdfService = reportPdfService;
        this.aiRecommendationService = aiRecommendationService;
    }

    /**
     * Run a deterministic ATS analysis of one of the caller's resumes against one
     * of the caller's job descriptions and persist the result.
     */
    @PostMapping
    public ResponseEntity<AnalysisResponse> analyze(@Valid @RequestBody AnalyzeResumeRequest request,
                                                    @AuthenticationPrincipal AuthenticatedUser caller) {
        AnalysisResponse response = analysisService.analyze(request, caller.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * The caller's analysis history, newest first (lightweight summaries for list views).
     */
    @GetMapping
    public ResponseEntity<List<AnalysisSummaryResponse>> listAll(
            @AuthenticationPrincipal AuthenticatedUser caller) {
        return ResponseEntity.ok(analysisService.getAllSummaries(caller.id()));
    }

    /**
     * Full stored diagnostic payload for one of the caller's analyses.
     */
    @GetMapping("/{id}")
    public ResponseEntity<AnalysisResponse> getById(@PathVariable Long id,
                                                    @AuthenticationPrincipal AuthenticatedUser caller) {
        return ResponseEntity.ok(analysisService.getById(id, caller.id()));
    }

    /**
     * Delete one of the caller's analyses from the history.
     * Mirrors DELETE /api/resumes/{id} and DELETE /api/job-descriptions/{id}.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @AuthenticationPrincipal AuthenticatedUser caller) {
        analysisService.deleteById(id, caller.id());
        return ResponseEntity.noContent().build();
    }

    /**
     * Generate LLM-authored suggestions for one of the caller's analyses.
     *
     * Separate from GET /api/analyses/{id} on purpose: the analysis itself is and
     * remains a deterministic computation, and the AI step is an explicit, optional
     * extra. When no provider is configured the endpoint answers 503 and the existing
     * rule-based recommendations remain fully usable — that is the fallback.
     *
     * Ownership is enforced by the scoped lookup inside the service.
     */
    @PostMapping("/{id}/ai-recommendations")
    public ResponseEntity<AnalysisResponse> generateAiRecommendations(
            @PathVariable Long id,
            @AuthenticationPrincipal AuthenticatedUser caller) {
        return ResponseEntity.ok(aiRecommendationService.generateForAnalysis(id, caller.id()));
    }

    /**
     * Download the stored analysis as a PDF report.
     *
     * Ownership is enforced by the same scoped lookup as GET /api/analyses/{id}, so
     * one user can never obtain another user's report. `no-store` is set because the
     * document contains personal data.
     */
    @GetMapping("/{id}/report")
    public ResponseEntity<byte[]> downloadReport(@PathVariable Long id,
                                                 @AuthenticationPrincipal AuthenticatedUser caller) {
        AnalysisResponse analysis = analysisService.getById(id, caller.id());
        byte[] pdf = reportPdfService.generate(analysis, caller.name(), caller.email(), LocalDateTime.now());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PDF);
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(reportFileName(analysis))
                .build());
        headers.setContentLength(pdf.length);
        headers.setCacheControl(CacheControl.noStore());

        return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
    }

    /**
     * A safe download name: the slug keeps only [a-z0-9-], so nothing from the job
     * title can break out of the Content-Disposition header.
     */
    private static String reportFileName(AnalysisResponse analysis) {
        String source = analysis.getJobTitle() == null ? "" : analysis.getJobTitle();
        String slug = source.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (slug.isEmpty()) {
            slug = "analysis";
        }
        if (slug.length() > 40) {
            slug = slug.substring(0, 40).replaceAll("-+$", "");
        }
        return "smartresume-" + slug + "-" + analysis.getId() + ".pdf";
    }
}
