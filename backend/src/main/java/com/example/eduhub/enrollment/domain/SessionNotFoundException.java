package com.example.eduhub.enrollment.domain;

/** 없는 회차에 신청할 때 (FR-04). */
public class SessionNotFoundException extends RuntimeException {

  public SessionNotFoundException(Long sessionId) {
    super("회차를 찾을 수 없습니다. id=" + sessionId);
  }
}
