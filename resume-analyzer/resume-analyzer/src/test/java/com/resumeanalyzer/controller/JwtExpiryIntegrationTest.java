package com.resumeanalyzer.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeanalyzer.repository.UserRepository;
import com.resumeanalyzer.support.TestAuth;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves that token expiry is actually enforced.
 *
 * The token lifetime is deliberately shortened to 3 seconds for this context, which is
 * the only way to observe expiry without waiting a real day. The token must work while
 * it is valid and then be rejected with 401 — never a 500 — once it has expired.
 */
@SpringBootTest(properties = {
        "app.jwt.expiration-ms=3000"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JwtExpiryIntegrationTest {

    private static final long PAST_EXPIRY_WAIT_MS = 3_500L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("A token works while valid and is rejected with 401 once expired")
    void expiredTokenIsRejected() throws Exception {
        String token = TestAuth.registerAndGetToken(mockMvc, objectMapper, "Expiry Tester",
                TestAuth.uniqueEmail("jwt.expiry"));
        String email = userRepository.findAll().stream()
                .filter(user -> user.getName().equals("Expiry Tester"))
                .findFirst()
                .orElseThrow()
                .getEmail();

        // 1. Valid immediately after issue.
        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email", is(email)));

        // 2. Same token against a data endpoint is also accepted.
        mockMvc.perform(get("/api/resumes")
                        .header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(token)))
                .andExpect(status().isOk());

        // 3. Wait past the configured lifetime.
        Thread.sleep(PAST_EXPIRY_WAIT_MS);

        // 4. The token is now rejected, in the standard error envelope.
        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status", is(401)))
                .andExpect(jsonPath("$.error", is("Unauthorized")))
                .andExpect(jsonPath("$.message", containsString("Authentication is required")));

        mockMvc.perform(get("/api/resumes")
                        .header(HttpHeaders.AUTHORIZATION, TestAuth.bearer(token)))
                .andExpect(status().isUnauthorized());

        // 5. The account itself still exists and can sign in again.
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/auth/login")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"email\":" + objectMapper.writeValueAsString(email)
                                + ",\"password\":" + objectMapper.writeValueAsString(TestAuth.PASSWORD) + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", org.hamcrest.Matchers.not(org.hamcrest.Matchers.emptyString())));
    }
}
