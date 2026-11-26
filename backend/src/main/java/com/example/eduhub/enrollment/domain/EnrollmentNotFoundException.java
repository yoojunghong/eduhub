package com.example.eduhub.enrollment.domain;

/** 없는 신청이거나 본인 신청이 아닐 때 (FR-06). */
public class EnrollmentNotFoundException extends RuntimeException {

  public EnrollmentNotFoundException(Long enrollmentId) {
    super("신청을 찾을 수 없습니다. id=" + enrollmentId);
  }
}
