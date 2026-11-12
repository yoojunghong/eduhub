package com.example.eduhub.enrollment.domain;

/** 같은 과정에 진행 중(확정·대기) 신청이 이미 있을 때 (D-01). */
public class DuplicateEnrollmentException extends RuntimeException {

  public DuplicateEnrollmentException(Long courseId) {
    super("이미 이 과정에 진행 중인 신청이 있습니다. courseId=" + courseId);
  }

  /** 유일 인덱스 위반처럼 원인이 있는 경우. */
  public DuplicateEnrollmentException(Long courseId, Throwable cause) {
    super("이미 이 과정에 진행 중인 신청이 있습니다. courseId=" + courseId, cause);
  }
}
