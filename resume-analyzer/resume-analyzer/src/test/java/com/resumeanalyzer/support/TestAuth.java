package com.resumeanalyzer.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeanalyzer.dto.RegisterRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Small helper for the integration tests.
 *
 * The API is now token-protected, so every test that touches a data endpoint has
 * to present a real token. Rather than fake authentication, these helpers register
 * a real user through the real endpoint and reuse the token it returns — the same
 * path the browser takes.
 */
public final class TestAuth {

    /** Shared test password. Not a production credential. */
    public static final String PASSWORD = "testPassword123";

    private TestAuth() {
    }

    /** A unique email so tests never collide on the users table. */
    public static String uniqueEmail(String localPart) {
        return localPart + "." + UUID.randomUUID() + "@example.com";
    }

    public static String bearer(String token) {
        return "Bearer " + token;
    }

    /** Register a user through POST /api/auth/register and return its bearer token. */
    public static String registerAndGetToken(MockMvc mockMvc,
                                             ObjectMapper objectMapper,
                                             String name,
                                             String email) throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .name(name)
                .email(email)
                .password(PASSWORD)
                .build();

        String body = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(body).get("token").asText();
    }

    /** The Authorization header value for a token. */
    public static String authorizationHeader(String token) {
        return HttpHeaders.AUTHORIZATION;
    }
}
