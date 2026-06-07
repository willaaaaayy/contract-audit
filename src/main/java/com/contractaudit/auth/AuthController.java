package com.contractaudit.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Регистрация компании и вход. Эндпоинты открыты (см. {@code SecurityConfig}); в ответе —
 * access-токен (Bearer) для остальных API.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;

    public AuthController(AuthService authService, TokenService tokenService) {
        this.authService = authService;
        this.tokenService = tokenService;
    }

    @PostMapping("/register")
    public TokenResponse register(@Valid @RequestBody RegisterRequest request) {
        String token = authService.register(
                request.companyName(), request.slug(), request.email(), request.password());
        return token(token);
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        String token = authService.login(request.slug(), request.email(), request.password());
        return token(token);
    }

    private TokenResponse token(String accessToken) {
        return new TokenResponse(accessToken, "Bearer", tokenService.accessTokenTtlSeconds());
    }

    public record RegisterRequest(
            @NotBlank String companyName,
            @NotBlank @Size(min = 2, max = 40) String slug,
            @Email @NotBlank String email,
            @NotBlank @Size(min = 8) String password) {
    }

    public record LoginRequest(
            @NotBlank String slug,
            @Email @NotBlank String email,
            @NotBlank String password) {
    }

    public record TokenResponse(String accessToken, String tokenType, long expiresInSeconds) {
    }
}
