package com.resumeanalyzer.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Guards the browser's cross-origin access to the API.
 *
 * The SPA is served from a different origin than the API, so every JSON POST is preceded by a
 * CORS preflight. "localhost" and "127.0.0.1" are <em>distinct</em> origins to a browser: an
 * allow-list containing only one of them makes the app unusable from the other, and the failure
 * surfaces in the browser as
 *
 * <pre>blocked by CORS policy: Response to preflight request doesn't pass access control check</pre>
 *
 * with the backend answering 403 to the OPTIONS request. These tests therefore assert that both
 * loopback spellings of the Vite origin are accepted, that a preflight needs no token while the
 * real request still does, and that an origin which is not on the list is still refused — so the
 * fix can never silently degrade into a blanket allow.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CorsConfigurationIntegrationTest {

    /** The origin a browser sends when the SPA is opened at http://127.0.0.1:5173. */
    private static final String VITE_IP = "http://127.0.0.1:5173";

    /** The origin a browser sends when the SPA is opened at http://localhost:5173. */
    private static final String VITE_LOCALHOST = "http://localhost:5173";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Preflight for POST /api/auth/register from http://127.0.0.1:5173 is allowed")
    void preflightForRegisterFromIpv4LoopbackIsAllowed() throws Exception {
        mockMvc.perform(options("/api/auth/register")
                        .header(HttpHeaders.ORIGIN, VITE_IP)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, VITE_IP))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        containsStringIgnoringCase("POST")))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
                        containsStringIgnoringCase("content-type")));
    }

    @Test
    @DisplayName("Preflight for POST /api/auth/login from http://localhost:5173 is still allowed")
    void preflightForLoginFromLocalhostIsAllowed() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, VITE_LOCALHOST)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, VITE_LOCALHOST))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS,
                        containsStringIgnoringCase("POST")));
    }

    @Test
    @DisplayName("A CORS preflight is answered without a token, for a protected endpoint too")
    void preflightForProtectedEndpointNeedsNoToken() throws Exception {
        mockMvc.perform(options("/api/analyses")
                        .header(HttpHeaders.ORIGIN, VITE_IP)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, VITE_IP));
    }

    @Test
    @DisplayName("CORS support does not weaken authentication: the real call still needs a token")
    void realRequestStillRequiresAuthentication() throws Exception {
        // No Authorization header -> 401, and the browser must still be able to read that
        // error, so the allow-origin header has to be present on the failure as well.
        mockMvc.perform(get("/api/analyses")
                        .header(HttpHeaders.ORIGIN, VITE_IP))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, VITE_IP));
    }

    @Test
    @DisplayName("An origin that is not on the allow-list is still refused")
    void unknownOriginIsNotAllowed() throws Exception {
        mockMvc.perform(options("/api/auth/register")
                        .header(HttpHeaders.ORIGIN, "http://evil.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    @DisplayName("A loopback origin on an unlisted port is still refused")
    void unlistedLoopbackPortIsNotAllowed() throws Exception {
        mockMvc.perform(options("/api/auth/register")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5174")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }
}
