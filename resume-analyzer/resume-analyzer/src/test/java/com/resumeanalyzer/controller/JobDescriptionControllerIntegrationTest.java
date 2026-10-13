package com.resumeanalyzer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeanalyzer.dto.CreateJobDescriptionRequest;
import com.resumeanalyzer.repository.JobDescriptionRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class JobDescriptionControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JobDescriptionRepository jobDescriptionRepository;

    /** Bearer token of the user registered for this test. */
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        jobDescriptionRepository.deleteAll();
        token = TestAuth.registerAndGetToken(mockMvc, objectMapper, "JD Test User",
                TestAuth.uniqueEmail("jd.test"));
    }

    /**
     * Job-description endpoints require authentication. Rather than repeating the
     * header on every request, all requests in this class go through this helper.
     */
    private ResultActions perform(MockHttpServletRequestBuilder builder) throws Exception {
        return mockMvc.perform(
                builder.header(TestAuth.authorizationHeader(token), TestAuth.bearer(token)));
    }

    private CreateJobDescriptionRequest validRequest() {
        return CreateJobDescriptionRequest.builder()
                .jobTitle("Full Stack Software Engineer")
                .companyName("Test Company")
                .descriptionText("We are looking for a software engineer with Java, Spring Boot, "
                        + "React, SQL, PostgreSQL, Docker and AWS experience.")
                .build();
    }

    // -----------------------------------------------------------------------
    // T01: create
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("T01: POST /api/job-descriptions returns 201 with the created record")
    void testCreateJobDescription() throws Exception {
        perform(post("/api/job-descriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.jobTitle", is("Full Stack Software Engineer")))
                .andExpect(jsonPath("$.companyName", is("Test Company")))
                .andExpect(jsonPath("$.descriptionText", containsString("Spring Boot")))
                .andExpect(jsonPath("$.createdAt", notNullValue()))
                .andExpect(jsonPath("$.updatedAt", notNullValue()));
    }

    // -----------------------------------------------------------------------
    // T02/T03: validation
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("T02: POST /api/job-descriptions without jobTitle returns 400 with a field error")
    void testCreateWithoutJobTitle() throws Exception {
        CreateJobDescriptionRequest invalid = CreateJobDescriptionRequest.builder()
                .companyName("Test Company")
                .descriptionText("Some description")
                .build();

        perform(post("/api/job-descriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Validation Failed")))
                .andExpect(jsonPath("$.validationErrors.jobTitle", notNullValue()));
    }

    @Test
    @DisplayName("T03: POST /api/job-descriptions without descriptionText returns 400 with a field error")
    void testCreateWithoutDescriptionText() throws Exception {
        CreateJobDescriptionRequest invalid = CreateJobDescriptionRequest.builder()
                .jobTitle("Backend Engineer")
                .companyName("Test Company")
                .descriptionText("")
                .build();

        perform(post("/api/job-descriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Validation Failed")))
                .andExpect(jsonPath("$.validationErrors.descriptionText", notNullValue()));
    }

    // -----------------------------------------------------------------------
    // T04: list
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("T04: GET /api/job-descriptions returns created records")
    void testListJobDescriptions() throws Exception {
        perform(post("/api/job-descriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated());

        perform(get("/api/job-descriptions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].jobTitle", is("Full Stack Software Engineer")));
    }

    // -----------------------------------------------------------------------
    // T05/T06: get by id
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("T05: GET /api/job-descriptions/{id} returns the stored record")
    void testGetJobDescriptionById() throws Exception {
        String body = perform(post("/api/job-descriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long id = objectMapper.readTree(body).get("id").asLong();

        perform(get("/api/job-descriptions/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is((int) id)))
                .andExpect(jsonPath("$.jobTitle", is("Full Stack Software Engineer")));
    }

    @Test
    @DisplayName("T06: GET /api/job-descriptions/{id} with unknown id returns 404")
    void testGetJobDescriptionNotFound() throws Exception {
        perform(get("/api/job-descriptions/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("Not Found")))
                .andExpect(jsonPath("$.message", containsString("999999")));
    }

    // -----------------------------------------------------------------------
    // T07/T08: delete
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("T07: DELETE /api/job-descriptions/{id} returns 204 and removes the record")
    void testDeleteJobDescription() throws Exception {
        String body = perform(post("/api/job-descriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        long id = objectMapper.readTree(body).get("id").asLong();

        perform(delete("/api/job-descriptions/{id}", id))
                .andExpect(status().isNoContent());

        assertThat(jobDescriptionRepository.findById(id)).isEmpty();
    }

    @Test
    @DisplayName("T08: DELETE /api/job-descriptions/{id} with unknown id returns 404")
    void testDeleteJobDescriptionNotFound() throws Exception {
        perform(delete("/api/job-descriptions/{id}", 999999L))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("Not Found")));
    }
}
