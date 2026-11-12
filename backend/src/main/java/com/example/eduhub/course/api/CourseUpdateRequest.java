package com.example.eduhub.course.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 과정 수정 요청 (FR-03). 종료된 과정은 수정할 수 없다. */
public record CourseUpdateRequest(
    @NotBlank @Size(max = 200) String title,
    @NotBlank @Size(max = 50) String category,
    @Size(max = 2000) String description) {}
