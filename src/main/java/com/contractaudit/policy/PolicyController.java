package com.contractaudit.policy;

import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

import java.util.List;
import java.util.UUID;

/** Библиотека политик компании. Tenant-scoped — политики не пересекаются между арендаторами. */
@RestController
@RequestMapping("/api/policies")
public class PolicyController {

    private final PolicyService policyService;

    public PolicyController(PolicyService policyService) {
        this.policyService = policyService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedPolicy create(@Valid @RequestBody CreatePolicyRequest request) {
        UUID id = policyService.create(request.title(), request.text(), request.mandatory());
        return new CreatedPolicy(id);
    }

    @GetMapping
    public List<PolicyRepository.PolicySummary> list() {
        return policyService.list();
    }

    public record CreatePolicyRequest(@NotBlank String title, @NotBlank String text, boolean mandatory) {
    }

    public record CreatedPolicy(UUID id) {
    }
}
