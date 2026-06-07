package com.contractaudit.profile;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Генеративный профиль документа для адаптивного экрана аудита. Tenant-scoped: текст берётся из
 * {@code DocumentChunkRepository}, где зашит {@code WHERE tenant_id}.
 */
@RestController
@RequestMapping("/api/documents/{documentId}/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @PostMapping
    public AuditProfile profile(@PathVariable UUID documentId) {
        return profileService.profile(documentId);
    }
}
