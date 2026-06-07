package com.contractaudit.user;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Управление пользователями арендатора. Приглашать может только ADMIN (см. {@code @PreAuthorize},
 * роль берётся из claim {@code role} токена). Новый пользователь создаётся в арендаторе
 * вызывающего — другого он указать не может.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public InvitedUser invite(@Valid @RequestBody InviteRequest request) {
        UUID id = userService.invite(request.email(), request.password(), request.role());
        return new InvitedUser(id);
    }

    public record InviteRequest(
            @Email @NotBlank String email,
            @NotBlank @Size(min = 8) String password,
            @NotBlank @Pattern(regexp = "ADMIN|ANALYST", message = "role должна быть ADMIN или ANALYST")
            String role) {
    }

    public record InvitedUser(UUID id) {
    }
}
