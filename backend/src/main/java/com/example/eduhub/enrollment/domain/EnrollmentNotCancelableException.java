package com.example.eduhub.enrollment.domain;

/** 이미 취소·수료된 신청을 취소할 때 (FR-06). */
public class EnrollmentNotCancelableException extends RuntimeException {

  public EnrollmentNotCancelableException(Long enrollmentId) {
    super("취소할 수 없는 신청입니다. id=" + enrollmentId);
  }
}
