package com.resumeanalyzer.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeanalyzer.dto.CreateJobDescriptionRequest;
import com.resumeanalyzer.entity.Resume;
import com.resumeanalyzer.entity.UploadStatus;
import com.resumeanalyzer.entity.User;
import com.resumeanalyzer.repository.AnalysisRepository;
import com.resumeanalyzer.repository.JobDescriptionRepository;
import com.resumeanalyzer.repository.ResumeRepository;
import com.resumeanalyzer.repository.UserRepository;
import com.resumeanalyzer.support.TestAuth;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AnalysisControllerIntegrationTest {

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

    @Autowired
    private UserRepository userRepository;

    /** Bearer token of the user registered for this test. */
    private String token;

    /** The registered user, used as the owner of the persisted fixtures. */
    private User owner;

    private static final String JD_TEXT = "We are looking for a software engineer with Java, "
            + "Spring Boot, React, SQL, PostgreSQL, Docker and AWS experience.";

    private static final String RESUME_TEXT = """
            Jane Doe
            jane.doe@example.com | +1 555 0100 | linkedin.com/in/janedoe

            Summary
            Backend focused software engineer building REST APIs and relational data services.

            Skills
            Java, Spring Boot, REST APIs, SQL, PostgreSQL, React, TypeScript, Git, JUnit

            Experience
            Software Engineer, Acme Corp, 2022 - 2025
            Built REST APIs in Java and Spring Boot backed by PostgreSQL for payment processing.

            Education
            B.Tech Computer Science, 2022

            Projects
            Resume Analyzer - Java, Spring Boot, React

            Certifications
            Oracle Certified Associate
            """;

    @BeforeEach
    void setUp() throws Exception {
        analysisRepository.deleteAll();
        jobDescriptionRepository.deleteAll();
        resumeRepository.deleteAll();

        // Register through the real endpoint — analysis requires a valid token AND
        // the resume/JD fixtures below must be owned by that same user.
        String email = TestAuth.uniqueEmail("analysis.test");
        token = TestAuth.registerAndGetToken(mockMvc, objectMapper, "Analysis Test User", email);
        owner = userRepository.findByEmail(email);
    }

    /**
     * Analysis endpoints require authentication. Rather than repeating the header on
     * every request, all requests in this class go through this helper.
     */
    private ResultActions perform(MockHttpServletRequestBuilder builder) throws Exception {
        return mockMvc.perform(
                builder.header(TestAuth.authorizationHeader(token), TestAuth.bearer(token)));
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private Resume persistResume(String text, UploadStatus status) {
        return resumeRepository.save(Resume.builder()
                .originalFileName("jane_doe_resume.docx")
                .storedFileName("stored-" + java.util.UUID.randomUUID() + ".docx")
                .fileType("DOCX")
                .fileSize(text == null ? 0L : (long) text.length())
                .storagePath("target/test-uploads")
                .extractedText(text)
                .uploadStatus(status)
                .user(owner)
                .build());
    }

    private long createJobDescription() throws Exception {
        CreateJobDescriptionRequest request = CreateJobDescriptionRequest.builder()
                .jobTitle("Full Stack Software Engineer")
                .companyName("Test Company")
                .descriptionText(JD_TEXT)
                .build();

        String body = perform(post("/api/job-descriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(body).get("id").asLong();
    }

    private byte[] buildDocxResume() throws IOException {
        try (XWPFDocument doc = new XWPFDocument();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            for (String line : RESUME_TEXT.split("\n")) {
                XWPFParagraph paragraph = doc.createParagraph();
                XWPFRun run = paragraph.createRun();
                run.setText(line);
            }
            doc.write(baos);
            return baos.toByteArray();
        }
    }

    private static String analysisPayload(long resumeId, long jobDescriptionId) {
        return "{\"resumeId\":" + resumeId + ",\"jobDescriptionId\":" + jobDescriptionId + "}";
    }

    // -----------------------------------------------------------------------
    // T01: analyse produces a full deterministic payload
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("T01: POST /api/analyses returns 201 with score, skills, keywords, structure and stuffing")
    void testAnalyze() throws Exception {
        Resume resume = persistResume(RESUME_TEXT, UploadStatus.PARSED);
        long jdId = createJobDescription();

        perform(post("/api/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(analysisPayload(resume.getId(), jdId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.resumeId", is(resume.getId().intValue())))
                .andExpect(jsonPath("$.resumeFileName", is("jane_doe_resume.docx")))
                .andExpect(jsonPath("$.jobDescriptionId", is((int) jdId)))
                .andExpect(jsonPath("$.jobTitle", is("Full Stack Software Engineer")))
                .andExpect(jsonPath("$.companyName", is("Test Company")))
                .andExpect(jsonPath("$.atsScore", allOf(notNullValue(), greaterThan(0), lessThanOrEqualTo(100))))
                .andExpect(jsonPath("$.scoreBreakdown.skillMatch", notNullValue()))
                .andExpect(jsonPath("$.scoreBreakdown.keywordMatch", notNullValue()))
                .andExpect(jsonPath("$.scoreBreakdown.relevance", notNullValue()))
                .andExpect(jsonPath("$.scoreBreakdown.structure", notNullValue()))
                .andExpect(jsonPath("$.scoreBreakdown.formatting", notNullValue()))
                .andExpect(jsonPath("$.matchedSkills", hasItem("Java")))
                .andExpect(jsonPath("$.matchedSkills", hasItem("Spring Boot")))
                .andExpect(jsonPath("$.matchedSkills", hasItem("PostgreSQL")))
                .andExpect(jsonPath("$.missingSkills", hasItem("Docker")))
                .andExpect(jsonPath("$.missingSkills", hasItem("AWS")))
                .andExpect(jsonPath("$.matchedKeywords", not(empty())))
                .andExpect(jsonPath("$.missingKeywords", hasItem("docker")))
                .andExpect(jsonPath("$.keywordStuffing.risk", oneOf("LOW", "MEDIUM", "HIGH")))
                .andExpect(jsonPath("$.structure", hasSize(8)))
                .andExpect(jsonPath("$.structure[0].section", notNullValue()))
                 .andExpect(jsonPath("$.structure[*].status", everyItem(oneOf("PASSED", "WARNING"))))
                 // Keyword-frequency telemetry (stage C): one row per keyword with both
                 // counts, the matched flag and the repetition flag.
                 .andExpect(jsonPath("$.keywordCoverage", not(empty())))
                 .andExpect(jsonPath("$.keywordCoverage[*].keyword", everyItem(notNullValue())))
                 .andExpect(jsonPath("$.keywordCoverage[*].jobDescriptionCount", everyItem(notNullValue())))
                 .andExpect(jsonPath("$.keywordCoverage[*].resumeCount", everyItem(notNullValue())))
                 .andExpect(jsonPath("$.keywordCoverage[?(@.keyword=='docker')].matched",
                         contains(false)))
                 .andExpect(jsonPath("$.keywordCoverage[?(@.keyword=='java')].resumeCount",
                         contains(greaterThan(0))))
                 .andExpect(jsonPath("$.recommendations[*].source", everyItem(is("RULE"))))
                 .andExpect(jsonPath("$.createdAt", notNullValue()));
    }

    // -----------------------------------------------------------------------
    // T02: the analysis is persisted
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("T02: Analysis is persisted and retrievable from the repository")
    void testAnalysisIsPersisted() throws Exception {
        Resume resume = persistResume(RESUME_TEXT, UploadStatus.PARSED);
        long jdId = createJobDescription();

        perform(post("/api/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(analysisPayload(resume.getId(), jdId)))
                .andExpect(status().isCreated());

        assertThat(analysisRepository.findAllByOrderByCreatedAtDesc()).hasSize(1);
        var stored = analysisRepository.findAllByOrderByCreatedAtDesc().get(0);
        assertThat(stored.getAtsScore()).isNotNull();
        assertThat(stored.getMatchedSkillsJson()).contains("Java");
        assertThat(stored.getMissingSkillsJson()).contains("Docker");
        assertThat(stored.getStructureChecksJson()).contains("Contact Information");
        assertThat(stored.getKeywordStuffingRisk()).isNotNull();
        assertThat(stored.getKeywordCoverageJson()).contains("jobDescriptionCount");
        assertThat(stored.getRecommendationsJson()).contains("\"source\":\"RULE\"");
        assertThat(stored.getCreatedAt()).isNotNull();
    }

    // -----------------------------------------------------------------------
    // T03: GET /api/analyses/{id} returns the same stored result
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("T03: GET /api/analyses/{id} returns the same ATS score that was stored")
    void testGetAnalysisById() throws Exception {
        Resume resume = persistResume(RESUME_TEXT, UploadStatus.PARSED);
        long jdId = createJobDescription();

        String created = perform(post("/api/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(analysisPayload(resume.getId(), jdId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        JsonNode createdNode = objectMapper.readTree(created);
        long id = createdNode.get("id").asLong();
        int atsScore = createdNode.get("atsScore").asInt();

        perform(get("/api/analyses/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is((int) id)))
                .andExpect(jsonPath("$.atsScore", is(atsScore)))
                .andExpect(jsonPath("$.matchedSkills", hasItem("Java")))
                .andExpect(jsonPath("$.structure", hasSize(8)));
    }

    // -----------------------------------------------------------------------
    // T04: history list
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("T04: GET /api/analyses returns history summaries including job title and company")
    void testAnalysisHistory() throws Exception {
        Resume resume = persistResume(RESUME_TEXT, UploadStatus.PARSED);
        long jdId = createJobDescription();

        perform(post("/api/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(analysisPayload(resume.getId(), jdId)))
                .andExpect(status().isCreated());

        perform(get("/api/analyses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].jobTitle", is("Full Stack Software Engineer")))
                .andExpect(jsonPath("$[0].companyName", is("Test Company")))
                .andExpect(jsonPath("$[0].resumeFileName", is("jane_doe_resume.docx")))
                .andExpect(jsonPath("$[0].atsScore", notNullValue()))
                .andExpect(jsonPath("$[0].status", is("COMPLETED")))
                // summary must stay lightweight
                .andExpect(jsonPath("$[0].matchedSkills").doesNotExist())
                .andExpect(jsonPath("$[0].structure").doesNotExist());
    }

    // -----------------------------------------------------------------------
    // T05: error handling
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("T05: POST /api/analyses with unknown resume id returns 404")
    void testAnalyzeUnknownResume() throws Exception {
        long jdId = createJobDescription();

        perform(post("/api/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(analysisPayload(999999L, jdId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("999999")));
    }

    @Test
    @DisplayName("T06: POST /api/analyses with unknown job description id returns 404")
    void testAnalyzeUnknownJobDescription() throws Exception {
        Resume resume = persistResume(RESUME_TEXT, UploadStatus.PARSED);

        perform(post("/api/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(analysisPayload(resume.getId(), 999999L)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("999999")));
    }

    @Test
    @DisplayName("T07: POST /api/analyses for a resume with no extracted text returns 422")
    void testAnalyzeResumeWithoutText() throws Exception {
        Resume resume = persistResume(null, UploadStatus.FAILED);
        long jdId = createJobDescription();

        perform(post("/api/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(analysisPayload(resume.getId(), jdId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error", is("Unprocessable Analysis")));
    }

    @Test
    @DisplayName("T08: POST /api/analyses without resumeId returns 400 with a field error")
    void testAnalyzeMissingResumeId() throws Exception {
        long jdId = createJobDescription();

        perform(post("/api/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobDescriptionId\":" + jdId + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Validation Failed")))
                .andExpect(jsonPath("$.validationErrors.resumeId", notNullValue()));
    }

    @Test
    @DisplayName("T09: GET /api/analyses/{id} with unknown id returns 404")
    void testGetAnalysisNotFound() throws Exception {
        perform(get("/api/analyses/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("Not Found")));
    }

    // -----------------------------------------------------------------------
    // T10: full HTTP flow — upload a real DOCX, create a JD, analyse, read back
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("T10: end-to-end flow via HTTP — upload DOCX, create JD, analyse, reload analysis")
    void testFullHttpFlow() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "flow_resume.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                buildDocxResume());

        String uploadBody = perform(multipart("/api/resumes/upload").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uploadStatus", is("PARSED")))
                .andExpect(jsonPath("$.extractedCharacterCount", greaterThan(0)))
                .andReturn().getResponse().getContentAsString();

        long resumeId = objectMapper.readTree(uploadBody).get("id").asLong();
        long jdId = createJobDescription();

        String analysisBody = perform(post("/api/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(analysisPayload(resumeId, jdId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.atsScore", greaterThan(0)))
                .andExpect(jsonPath("$.matchedSkills", hasItem("Java")))
                .andReturn().getResponse().getContentAsString();

        long analysisId = objectMapper.readTree(analysisBody).get("id").asLong();

        // History contains it
        perform(get("/api/analyses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id", is((int) analysisId)));

        // Reloading returns the same stored result
        perform(get("/api/analyses/{id}", analysisId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.atsScore",
                        is(objectMapper.readTree(analysisBody).get("atsScore").asInt())))
                .andExpect(jsonPath("$.resumeFileName", is("flow_resume.docx")));
    }

    // -----------------------------------------------------------------------
    // T11/T12: delete
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("T11: DELETE /api/analyses/{id} returns 204 and removes the stored result")
    void testDeleteAnalysis() throws Exception {
        Resume resume = persistResume(RESUME_TEXT, UploadStatus.PARSED);
        long jdId = createJobDescription();

        String created = perform(post("/api/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(analysisPayload(resume.getId(), jdId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long id = objectMapper.readTree(created).get("id").asLong();
        assertThat(analysisRepository.findById(id)).isPresent();

        perform(delete("/api/analyses/{id}", id))
                .andExpect(status().isNoContent());

        assertThat(analysisRepository.findById(id)).isEmpty();
        // The resume and job description are not touched.
        assertThat(resumeRepository.findById(resume.getId())).isPresent();
        assertThat(jobDescriptionRepository.findById(jdId)).isPresent();
    }

    @Test
    @DisplayName("T12: DELETE /api/analyses/{id} with unknown id returns 404")
    void testDeleteAnalysisNotFound() throws Exception {
        perform(delete("/api/analyses/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("Not Found")));
    }

    // -----------------------------------------------------------------------
    // T13/T14: PDF report export
    // -----------------------------------------------------------------------

    private long createAnalysis() throws Exception {
        Resume resume = persistResume(RESUME_TEXT, UploadStatus.PARSED);
        long jdId = createJobDescription();

        String created = perform(post("/api/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(analysisPayload(resume.getId(), jdId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(created).get("id").asLong();
    }

    @Test
    @DisplayName("T13: GET /api/analyses/{id}/report returns a real, complete PDF")
    void testReportDownload() throws Exception {
        long analysisId = createAnalysis();

        byte[] pdf = perform(get("/api/analyses/{id}/report", analysisId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString(".pdf")))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(pdf.length).isGreaterThan(1000);
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");

        // Parse the document back and verify the content is genuinely there.
        try (org.apache.pdfbox.pdmodel.PDDocument document = org.apache.pdfbox.Loader.loadPDF(pdf)) {
            String text = new org.apache.pdfbox.text.PDFTextStripper().getText(document);

            assertThat(text).contains("SmartResume Analyzer");
            assertThat(text).contains("jane_doe_resume.docx");
            assertThat(text).contains("Full Stack Software Engineer");
            assertThat(text).contains("Overall ATS score");
            assertThat(text).contains("Skill matching");
            assertThat(text).contains("Keyword frequency and coverage");
            assertThat(text).contains("Resume structure and formatting");
            assertThat(text).contains("Recommended improvements");
            assertThat(text).contains("Analysis Test User");
            assertThat(text).contains("Docker");

            // No internal storage detail may leak into the report.
            assertThat(text).doesNotContain("target/test-uploads");
            assertThat(text).doesNotContain("stored-");
            assertThat(text).doesNotContain("storagePath");
        }
    }

    @Test
    @DisplayName("T14: report for an unknown analysis returns 404, not a PDF")
    void testReportForUnknownAnalysis() throws Exception {
        perform(get("/api/analyses/{id}/report", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("Not Found")));
    }

    // -----------------------------------------------------------------------
    // T15/T16/T17: optional AI recommendations
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("T15: GET /api/ai/status reports the feature as disabled and explains what to configure")
    void testAiStatusWhenDisabled() throws Exception {
        // The test profile does not configure a provider, so the feature must be off.
        perform(get("/api/ai/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled", is(false)))
                .andExpect(jsonPath("$.reason", not(emptyString())))
                .andExpect(jsonPath("$.reason", containsString("app.llm")))
                // No key, no base URL, no model may be exposed while disabled.
                .andExpect(jsonPath("$.apiKey").doesNotExist())
                .andExpect(jsonPath("$.provider").doesNotExist())
                .andExpect(jsonPath("$.model").doesNotExist());
    }

    @Test
    @DisplayName("T16: requesting AI recommendations without a provider returns 503 and keeps the analysis usable")
    void testAiRecommendationsUnavailable() throws Exception {
        long analysisId = createAnalysis();

        perform(post("/api/analyses/{id}/ai-recommendations", analysisId))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status", is(503)))
                .andExpect(jsonPath("$.message", not(emptyString())));

        // The deterministic result is untouched by the failed AI attempt.
        perform(get("/api/analyses/{id}", analysisId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.atsScore", notNullValue()))
                .andExpect(jsonPath("$.recommendations", not(empty())))
                .andExpect(jsonPath("$.recommendations[*].source", everyItem(is("RULE"))))
                .andExpect(jsonPath("$.aiRecommendations").doesNotExist());
    }

    @Test
    @DisplayName("T17: AI recommendations are stored separately and never alter the score")
    void testAiRecommendationsAreStoredSeparately() throws Exception {
        long analysisId = createAnalysis();

        var before = analysisRepository.findById(analysisId).orElseThrow();
        int scoreBefore = before.getAtsScore();
        assertThat(before.getAiRecommendationsJson()).isNull();

        // Simulate a successful provider round-trip by storing validated output.
        var stored = analysisRepository.findById(analysisId).orElseThrow();
        stored.setAiRecommendationsJson("[{\"category\":\"Skills\",\"priority\":\"HIGH\","
                + "\"title\":\"Add Docker evidence if you have it\",\"detail\":\"Docker is required.\","
                + "\"relatedItems\":[\"Docker\"],\"source\":\"AI\"}]");
        stored.setAiGeneratedAt(java.time.LocalDateTime.now());
        analysisRepository.save(stored);

        perform(get("/api/analyses/{id}", analysisId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aiRecommendations", hasSize(1)))
                .andExpect(jsonPath("$.aiRecommendations[0].source", is("AI")))
                .andExpect(jsonPath("$.aiGeneratedAt", notNullValue()))
                // Deterministic advice is still present and still labelled RULE.
                .andExpect(jsonPath("$.recommendations[*].source", everyItem(is("RULE"))))
                // The score is unchanged: the model output cannot influence it.
                .andExpect(jsonPath("$.atsScore", is(scoreBefore)));
    }
}
