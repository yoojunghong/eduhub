package com.example.eduhub.course.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 과정 등록 요청 (FR-03). */
public record CourseCreateRequest(
    @NotBlank @Size(max = 200) String title,
    @NotBlank @Size(max = 50) String category,
    @Size(max = 2000) String description) {}
