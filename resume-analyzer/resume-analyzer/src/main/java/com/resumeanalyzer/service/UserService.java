package com.resumeanalyzer.service;

import com.resumeanalyzer.dto.AuthResponse;
import com.resumeanalyzer.dto.LoginRequest;
import com.resumeanalyzer.dto.RegisterRequest;
import com.resumeanalyzer.dto.UserResponse;
import com.resumeanalyzer.entity.User;
import com.resumeanalyzer.exception.InvalidCredentialsException;
import com.resumeanalyzer.exception.UserAlreadyExistsException;
import com.resumeanalyzer.repository.UserRepository;
import com.resumeanalyzer.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration and login.
 *
 * Passwords are stored only as BCrypt hashes. A successful login issues a signed
 * JWT which the client presents on every subsequent request. Neither the password
 * nor its hash is ever returned or logged.
 */
@Service
public class UserService {

    private static final String TOKEN_TYPE = "Bearer";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public UserService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();

        if (userRepository.findByEmail(normalizedEmail) != null) {
            throw new UserAlreadyExistsException("Email is already registered: " + request.getEmail());
        }

        User user = User.builder()
                .name(request.getName().trim())
                .email(normalizedEmail)
                .password(passwordEncoder.encode(request.getPassword()))
                .build();

        User savedUser = userRepository.save(user);

        return buildAuthResponse("User registered successfully", savedUser);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(normalizedEmail);

        // Same message for "unknown email" and "wrong password" so the response
        // cannot be used to enumerate registered accounts.
        if (user == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        return buildAuthResponse("Login successful", user);
    }

    /** Resolve the user referenced by a validated token. Used by GET /api/auth/me. */
    @Transactional(readOnly = true)
    public UserResponse getById(Long userId) {
        return userRepository.findById(userId)
                .map(this::mapToUserResponse)
                .orElseThrow(() -> new InvalidCredentialsException("The account for this token no longer exists."));
    }

    private AuthResponse buildAuthResponse(String message, User user) {
        return AuthResponse.builder()
                .message(message)
                .user(mapToUserResponse(user))
                .token(jwtService.generateToken(user))
                .tokenType(TOKEN_TYPE)
                .expiresInMs(jwtService.getExpirationMs())
                .build();
    }

    public UserResponse mapToUserResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .build();
    }
}
