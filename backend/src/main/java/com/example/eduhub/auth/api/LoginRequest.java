package com.example.eduhub.auth.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @Schema(description = "사번", example = "E1001") @NotBlank String employeeNo,
    @Schema(description = "비밀번호") @NotBlank String password) {}
