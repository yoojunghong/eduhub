package com.example.eduhub.enrollment.api;

import com.example.eduhub.enrollment.domain.Enrollment;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public record EnrollmentResponse(
    Long id,
    Long sessionId,
    Long courseId,
    Long employeeId,
    String status,
    Integer waitingNumber,
    OffsetDateTime enrolledAt) {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  public static EnrollmentResponse from(Enrollment enrollment) {
    return from(enrollment, null);
  }

  /** waitingNumber: WAITING이면 1부터의 대기 순번, 그 밖에는 null. */
  public static EnrollmentResponse from(Enrollment enrollment, Integer waitingNumber) {
    return new EnrollmentResponse(
        enrollment.getId(),
        enrollment.getSession().getId(),
        enrollment.getCourseId(),
        enrollment.getEmployeeId(),
        enrollment.getStatus().name(),
        waitingNumber,
        enrollment.getEnrolledAt().atZoneSameInstant(KST).toOffsetDateTime());
  }
}
