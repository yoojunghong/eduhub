package com.example.eduhub.enrollment.api;

import com.example.eduhub.enrollment.domain.CompletionOutcome;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/** 수료 처리 요청 (FR-08). 사원 1~100명, 중복은 한 번으로 본다. */
public record CompletionRequest(
    @NotEmpty @Size(max = 100) List<@NotNull @Positive Long> employeeIds,
    @NotNull CompletionOutcome outcome) {}
