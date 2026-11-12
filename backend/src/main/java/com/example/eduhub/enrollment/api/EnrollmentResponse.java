package com.example.eduhub.enrollment.api;

import com.example.eduhub.enrollment.domain.Enrollment;
import java.time.OffsetDateTime;

public record EnrollmentResponse(
    Long id,
    Long sessionId,
    Long courseId,
    Long employeeId,
    String status,
    OffsetDateTime enrolledAt) {

  public static EnrollmentResponse from(Enrollment enrollment) {
    return new EnrollmentResponse(
        enrollment.getId(),
        enrollment.getSession().getId(),
        enrollment.getCourseId(),
        enrollment.getEmployeeId(),
        enrollment.getStatus().name(),
        enrollment.getEnrolledAt());
  }
}
