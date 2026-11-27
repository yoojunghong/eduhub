package com.example.eduhub.enrollment.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** 내 신청 내역 한 건 (FR-07). waitingNumber는 대기일 때만, cancelable은 화면 표시용(D-07). */
public record MyEnrollmentResponse(
    Long enrollmentId,
    Long sessionId,
    Long courseId,
    String courseTitle,
    LocalDate startDate,
    String place,
    @Schema(allowableValues = {"CONFIRMED", "WAITING", "CANCELED", "COMPLETED", "INCOMPLETE"})
        String status,
    Integer waitingNumber,
    OffsetDateTime enrolledAt,
    boolean cancelable) {}
