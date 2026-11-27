package com.example.eduhub.enrollment.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 사원별 신청 내역 행 (FR-07). waitingNumber는 대기 상태일 때만 값이 있다. */
public record MyEnrollmentRow(
    Long enrollmentId,
    Long sessionId,
    Long courseId,
    String courseTitle,
    LocalDate startDate,
    String place,
    EnrollmentStatus status,
    Long waitingNumber,
    OffsetDateTime enrolledAt) {}
