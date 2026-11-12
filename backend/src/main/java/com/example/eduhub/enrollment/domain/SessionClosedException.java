package com.example.eduhub.enrollment.domain;

/** 종료된 회차 또는 과정에 신청할 때 (FR-04). */
public class SessionClosedException extends RuntimeException {

  public SessionClosedException(Long sessionId) {
    super("종료된 회차에는 신청할 수 없습니다. id=" + sessionId);
  }
}
