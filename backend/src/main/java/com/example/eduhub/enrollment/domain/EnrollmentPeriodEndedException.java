package com.example.eduhub.enrollment.domain;

/** 회차 시작 전날까지만 신청할 수 있다 (D-01). */
public class EnrollmentPeriodEndedException extends RuntimeException {

  public EnrollmentPeriodEndedException(Long sessionId) {
    super("신청 기간이 끝났습니다. sessionId=" + sessionId);
  }
}
