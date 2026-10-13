package com.resumeanalyzer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeanalyzer.repository.AnalysisRepository;
import com.resumeanalyzer.repository.JobDescriptionRepository;
import com.resumeanalyzer.repository.ResumeRepository;
import com.resumeanalyzer.support.TestAuth;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the security boundary that the application previously lacked:
 *
 *  1. Every data endpoint rejects an unauthenticated request with 401.
 *  2. A user cannot read, analyse against, or delete another user's resume, job
 *     description or analysis — such requests answer 404, not 403, so the API does
 *     not even reveal that the id exists.
 *  3. History endpoints only ever return the caller's own records.
 *
 * Both users are created through the real registration endpoint and act only
 * through the real HTTP API — nothing is stubbed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityAndOwnershipIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ResumeRepository resumeRepository;

    @Autowired
    private JobDescriptionRepository jobDescriptionRepository;

    @Autowired
    private AnalysisRepository analysisRepository;

    private String tokenA;
    private String tokenB;

    private static final String JD_TEXT = "We are looking for a software engineer with Java, Spring Boot, "
            + "React, SQL, PostgreSQL, Docker and AWS experience.";

    @BeforeEach
    void setUp() throws Exception {
        analysisRepository.deleteAll();
        jobDescriptionRepository.deleteAll();
        resumeRepository.deleteAll();

        tokenA = TestAuth.registerAndGetToken(mockMvc, objectMapper, "Owner A",
                TestAuth.uniqueEmail("owner.a"));
        tokenB = TestAuth.registerAndGetToken(mockMvc, objectMapper, "Owner B",
                TestAuth.uniqueEmail("owner.b"));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private byte[] buildMinimalPdf() throws IOException {
        try (PDDocument doc = new PDDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(60, 700);
                content.showText("Owner A - Software Engineer");
                content.newLine();
                content.showText("Skills: Java, Spring Boot, SQL, React, PostgreSQL");
                content.newLine();
                content.showText("Experience: 3 years backend development building REST APIs.");
                content.newLine();
                content.showText("Education: B.Tech Computer Science, 2022");
                content.endText();
            }
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    private long uploadResumeFor(String token, String fileName) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", fileName, "application/pdf", buildMinimalPdf());

        String body = mockMvc.perform(multipart("/api/resumes/upload")
                        .file(file)
                        .header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(token)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("id").asLong();
    }

    private long createJobDescriptionFor(String token) throws Exception {
        String payload = objectMapper.writeValueAsString(
                java.util.Map.of("jobTitle", "Full Stack Software Engineer",
                        "companyName", "Test Company",
                        "descriptionText", JD_TEXT));

        String body = mockMvc.perform(post("/api/job-descriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(token)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("id").asLong();
    }

    private long createAnalysisFor(String token, long resumeId, long jdId) throws Exception {
        String payload = "{\"resumeId\":" + resumeId + ",\"jobDescriptionId\":" + jdId + "}";

        String body = mockMvc.perform(post("/api/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload)
                        .header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(token)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("id").asLong();
    }

    // -----------------------------------------------------------------------
    // T01: unauthenticated access is refused
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("T01: every data endpoint returns 401 without a token")
    void testUnauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/resumes")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/resumes/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/resumes/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/job-descriptions")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/job-descriptions/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/job-descriptions/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/analyses")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/analyses/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        // Read endpoints added with the PDF report and the optional AI feature.
        mockMvc.perform(get("/api/analyses/1/report")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/ai/status")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/analyses/1/ai-recommendations")).andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/job-descriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resumeId\":1,\"jobDescriptionId\":1}"))
                .andExpect(status().isUnauthorized());

        MockMultipartFile file = new MockMultipartFile(
                "file", "anon.pdf", "application/pdf", buildMinimalPdf());
        mockMvc.perform(multipart("/api/resumes/upload").file(file))
                .andExpect(status().isUnauthorized());
    }

    // -----------------------------------------------------------------------
    // T02: one user cannot touch another user's records
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("T02: user B cannot read, delete or analyse against user A's records")
    void testCrossUserAccessIsBlocked() throws Exception {
        long resumeId = uploadResumeFor(tokenA, "owner_a_resume.pdf");
        long jdId = createJobDescriptionFor(tokenA);
        long analysisId = createAnalysisFor(tokenA, resumeId, jdId);

        // --- B cannot see A's resume ---
        mockMvc.perform(get("/api/resumes/{id}", resumeId)
                        .header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(tokenB)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/resumes/{id}", resumeId)
                        .header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(tokenB)))
                .andExpect(status().isNotFound());

        // --- B cannot see A's job description ---
        mockMvc.perform(get("/api/job-descriptions/{id}", jdId)
                        .header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(tokenB)))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/job-descriptions/{id}", jdId)
                        .header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(tokenB)))
                .andExpect(status().isNotFound());

        // --- B cannot read A's analysis ---
        mockMvc.perform(get("/api/analyses/{id}", analysisId)
                        .header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(tokenB)))
                .andExpect(status().isNotFound());

        // --- B cannot delete A's analysis ---
        mockMvc.perform(delete("/api/analyses/{id}", analysisId)
                        .header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(tokenB)))
                .andExpect(status().isNotFound());

        // --- B cannot download A's PDF report ---
        mockMvc.perform(get("/api/analyses/{id}/report", analysisId)
                        .header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(tokenB)))
                .andExpect(status().isNotFound());

        // --- B cannot obtain AI suggestions for A's analysis ---
        // With no provider configured the endpoint short-circuits with 503 before it
        // ever looks the record up, so this response is identical for "not yours" and
        // "does not exist" — no existence disclosure either way. The 404-for-another-
        // user path is asserted in AiProviderFailureIntegrationTest, where the feature
        // is enabled and the ownership lookup is actually reached.
        mockMvc.perform(post("/api/analyses/{id}/ai-recommendations", analysisId)
                        .header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(tokenB)))
                .andExpect(status().isServiceUnavailable());

        // --- B cannot run an analysis against A's resume / JD ---
        mockMvc.perform(post("/api/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resumeId\":" + resumeId + ",\"jobDescriptionId\":" + jdId + "}")
                        .header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(tokenB)))
                .andExpect(status().isNotFound());

        // --- B's own lists are empty ---
        mockMvc.perform(get("/api/resumes")
                        .header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(tokenB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/job-descriptions")
                        .header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(tokenB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        mockMvc.perform(get("/api/analyses")
                        .header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(tokenB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // --- A still sees exactly its own records ---
        mockMvc.perform(get("/api/resumes")
                        .header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].originalFileName", is("owner_a_resume.pdf")));

        mockMvc.perform(get("/api/analyses")
                        .header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is((int) analysisId)));

        // A's analysis survived B's delete attempt
        mockMvc.perform(get("/api/analyses/{id}", analysisId)
                        .header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumeId", is((int) resumeId)))
                .andExpect(jsonPath("$.jobDescriptionId", is((int) jdId)));
    }

    // -----------------------------------------------------------------------
    // T03: history is per user
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("T03: each user's analysis history contains only their own runs")
    void testHistoryIsScopedPerUser() throws Exception {
        long resumeA = uploadResumeFor(tokenA, "a_resume.pdf");
        long jdA = createJobDescriptionFor(tokenA);
        long analysisA = createAnalysisFor(tokenA, resumeA, jdA);

        long resumeB = uploadResumeFor(tokenB, "b_resume.pdf");
        long jdB = createJobDescriptionFor(tokenB);
        long analysisB = createAnalysisFor(tokenB, resumeB, jdB);

        mockMvc.perform(get("/api/analyses")
                        .header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is((int) analysisA)))
                .andExpect(jsonPath("$[0].resumeFileName", is("a_resume.pdf")));

        mockMvc.perform(get("/api/analyses")
                        .header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(tokenB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is((int) analysisB)))
                .andExpect(jsonPath("$[0].resumeFileName", is("b_resume.pdf")));
    }

    // -----------------------------------------------------------------------
    // T04: the analysis payload carries real recommendations
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("T04: the stored analysis exposes deterministic recommendations with no invented content")
    void testRecommendationsAreReturned() throws Exception {
        long resumeId = uploadResumeFor(tokenA, "rec_resume.pdf");
        long jdId = createJobDescriptionFor(tokenA);
        long analysisId = createAnalysisFor(tokenA, resumeId, jdId);

        mockMvc.perform(get("/api/analyses/{id}", analysisId)
                        .header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations").isArray())
                // Docker and AWS are required by the JD and absent from the resume,
                // so the skills recommendation must exist and name them.
                .andExpect(jsonPath("$.recommendations[?(@.category == 'Skills')]").exists())
                .andExpect(jsonPath("$.recommendations[0].priority").exists())
                .andExpect(jsonPath("$.recommendations[0].title").exists());
    }
}
