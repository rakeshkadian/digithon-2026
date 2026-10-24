package com.copilot.scan;

import jakarta.validation.constraints.NotBlank;

public record ScanRequest(
        @NotBlank String repoUrl,
        String branch,
        String token) {
}
