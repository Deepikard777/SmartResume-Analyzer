package com.resumeanalyzer.config;

import com.resumeanalyzer.security.JwtAuthenticationFilter;
import com.resumeanalyzer.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Stateless, token-based security.
 *
 * Only the two credential endpoints are public. Every data endpoint
 * (`/api/resumes/**`, `/api/job-descriptions/**`, `/api/analyses/**`, `/api/auth/me`)
 * requires a valid JWT, and each service additionally scopes its queries to the
 * authenticated user id, so one user can never read another user's data.
 *
 * There is no session: the token is presented on every request, which is why
 * SessionCreationPolicy.STATELESS is kept.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint restAuthenticationEntryPoint;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          RestAuthenticationEntryPoint restAuthenticationEntryPoint) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.restAuthenticationEntryPoint = restAuthenticationEntryPoint;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Kept deliberately: without a UserDetailsService bean, Spring Boot would
     * auto-configure a default in-memory user with a generated password. This
     * application authenticates only through POST /api/auth/login + JWT, so
     * username/password lookup is intentionally unsupported.
     *
     * NOTE: the local authentication manager depends on this bean, so it is a
     * *lambda variable* (not a bean parameter) in securityFilterChain to avoid a
     * circular reference.
     */
    @Bean
    public org.springframework.security.core.userdetails.UserDetailsService userDetailsService() {
        return username -> {
            throw new org.springframework.security.core.userdetails.UsernameNotFoundException(
                    "Password authentication is not used. Obtain a token from POST /api/auth/login instead.");
        };
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public: obtaining credentials only.
                .requestMatchers("/api/auth/register", "/api/auth/login").permitAll()
                // Public: the container's error dispatch, which must be able to render
                // the 401/403 bodies produced above without recursing.
                .requestMatchers("/error").permitAll()
                // Everything else, including /api/auth/me and all data endpoints,
                // requires a valid bearer token.
                .anyRequest().authenticated()
            )
            .exceptionHandling(handling ->
                handling.authenticationEntryPoint(restAuthenticationEntryPoint))
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // Allow the local frontend development origins: Vite (5173) and React CRA (3000),
        // on both loopback hostnames. "localhost" and "127.0.0.1" are distinct origins to a
        // browser, so both must be listed - otherwise the preflight from whichever one the
        // developer did not list is rejected with 403 and the SPA cannot reach the API at all.
        configuration.setAllowedOrigins(List.of(
                "http://localhost:5173", "http://127.0.0.1:5173",
                "http://localhost:3000", "http://127.0.0.1:3000"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With", "Origin"));
        // The PDF report endpoint returns the download name in Content-Disposition.
        // Cross-origin, the browser hides that header unless it is explicitly exposed,
        // and the UI would then have to guess the file name.
        configuration.setExposedHeaders(List.of("Content-Disposition", "Content-Type", "Content-Length"));
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
