package com.example.eduhub.auth.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @Schema(description = "사번", example = "E1001") @NotBlank @Size(max = 20) String employeeNo,
    @Schema(description = "비밀번호") @NotBlank @Size(max = 72) String password) {}
