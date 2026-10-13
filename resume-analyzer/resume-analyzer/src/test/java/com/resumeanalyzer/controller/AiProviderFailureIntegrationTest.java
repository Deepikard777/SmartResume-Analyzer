package com.resumeanalyzer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeanalyzer.entity.Resume;
import com.resumeanalyzer.entity.UploadStatus;
import com.resumeanalyzer.entity.User;
import com.resumeanalyzer.repository.AnalysisRepository;
import com.resumeanalyzer.repository.JobDescriptionRepository;
import com.resumeanalyzer.repository.ResumeRepository;
import com.resumeanalyzer.repository.UserRepository;
import com.resumeanalyzer.support.TestAuth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the failure path of the LLM integration without contacting any real
 * provider: the feature is switched ON with a deliberately unreachable base URL
 * (a closed local port), so the call fails at the transport layer.
 *
 * This is the case that matters operationally — a configured-but-down provider must
 * produce a clean 502, must not hang the request, and must leave the deterministic
 * analysis fully usable.
 */
@SpringBootTest(properties = {
        "app.llm.enabled=true",
        "app.llm.api-key=integration-test-key",
        "app.llm.base-url=http://127.0.0.1:9/v1",
        "app.llm.model=test-model",
        "app.llm.timeout-ms=1500"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AiProviderFailureIntegrationTest {

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

    private String token;
    private User owner;

    private static final String RESUME_TEXT = """
            Jane Doe
            Skills: Java, Spring Boot, SQL, PostgreSQL, React
            Experience: Software Engineer, Acme Corp, 2022 - 2025
            Education: B.Tech Computer Science, 2022
            """;

    private static final String JD_TEXT = "Software engineer with Java, Spring Boot, React, SQL, "
            + "PostgreSQL, Docker and AWS experience.";

    @BeforeEach
    void setUp() throws Exception {
        analysisRepository.deleteAll();
        jobDescriptionRepository.deleteAll();
        resumeRepository.deleteAll();

        String email = TestAuth.uniqueEmail("ai.failure");
        token = TestAuth.registerAndGetToken(mockMvc, objectMapper, "AI Failure User", email);
        owner = userRepository.findByEmail(email);
    }

    private ResultActions perform(MockHttpServletRequestBuilder builder) throws Exception {
        return mockMvc.perform(
                builder.header(TestAuth.authorizationHeader(token), TestAuth.bearer(token)));
    }

    @Test
    @DisplayName("Status reports the feature as enabled without leaking the API key")
    void statusHidesTheKey() throws Exception {
        perform(get("/api/ai/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled", is(true)))
                .andExpect(jsonPath("$.provider", is("127.0.0.1")))
                .andExpect(jsonPath("$.model", is("test-model")))
                .andExpect(jsonPath("$.apiKey").doesNotExist())
                .andExpect(jsonPath("$.reason").doesNotExist());
    }

    @Test
    @DisplayName("An unreachable provider yields 502 and leaves the deterministic analysis intact")
    void unreachableProviderYieldsBadGateway() throws Exception {
        Resume resume = resumeRepository.save(Resume.builder()
                .originalFileName("failure_resume.pdf")
                .storedFileName("stored-" + java.util.UUID.randomUUID() + ".pdf")
                .fileType("PDF")
                .fileSize(120L)
                .storagePath("target/test-uploads")
                .extractedText(RESUME_TEXT)
                .uploadStatus(UploadStatus.PARSED)
                .user(owner)
                .build());

        String jdBody = perform(post("/api/job-descriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobTitle\":\"Software Engineer\",\"companyName\":\"Test\","
                                + "\"descriptionText\":" + objectMapper.writeValueAsString(JD_TEXT) + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long jdId = objectMapper.readTree(jdBody).get("id").asLong();

        String analysisBody = perform(post("/api/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resumeId\":" + resume.getId() + ",\"jobDescriptionId\":" + jdId + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long analysisId = objectMapper.readTree(analysisBody).get("id").asLong();

        long startedAt = System.currentTimeMillis();
        perform(post("/api/analyses/{id}/ai-recommendations", analysisId))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status", is(502)))
                .andExpect(jsonPath("$.error", is("AI Provider Error")))
                .andExpect(jsonPath("$.message", containsString("could not be reached")));
        long elapsed = System.currentTimeMillis() - startedAt;

        // The configured timeout must bound the request rather than hanging it.
        org.assertj.core.api.Assertions.assertThat(elapsed)
                .as("AI failure should return promptly, not hang")
                .isLessThan(15_000L);

        // Nothing was persisted and the deterministic result still serves.
        var stored = analysisRepository.findById(analysisId).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(stored.getAiRecommendationsJson()).isNull();

        perform(get("/api/analyses/{id}", analysisId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations", org.hamcrest.Matchers.not(org.hamcrest.Matchers.empty())))
                .andExpect(jsonPath("$.recommendations[*].source",
                        org.hamcrest.Matchers.everyItem(is("RULE"))));
    }

    @Test
    @DisplayName("With the feature enabled, another user still cannot reach the analysis (404, not 502)")
    void crossUserIs404WhileFeatureIsEnabled() throws Exception {
        Resume resume = resumeRepository.save(Resume.builder()
                .originalFileName("owner_resume.pdf")
                .storedFileName("stored-" + java.util.UUID.randomUUID() + ".pdf")
                .fileType("PDF")
                .fileSize(120L)
                .storagePath("target/test-uploads")
                .extractedText(RESUME_TEXT)
                .uploadStatus(UploadStatus.PARSED)
                .user(owner)
                .build());

        String jdBody = perform(post("/api/job-descriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"jobTitle\":\"Software Engineer\",\"companyName\":\"Test\","
                                + "\"descriptionText\":" + objectMapper.writeValueAsString(JD_TEXT) + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long jdId = objectMapper.readTree(jdBody).get("id").asLong();

        String analysisBody = perform(post("/api/analyses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resumeId\":" + resume.getId() + ",\"jobDescriptionId\":" + jdId + "}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long analysisId = objectMapper.readTree(analysisBody).get("id").asLong();

        String otherToken = TestAuth.registerAndGetToken(mockMvc, objectMapper, "Other User",
                TestAuth.uniqueEmail("ai.failure.other"));

        mockMvc.perform(post("/api/analyses/{id}/ai-recommendations", analysisId)
                        .header(TestAuth.authorizationHeader(otherToken), TestAuth.bearer(otherToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/analyses/{id}/report", analysisId)
                        .header(TestAuth.authorizationHeader(otherToken), TestAuth.bearer(otherToken)))
                .andExpect(status().isNotFound());
    }
}
