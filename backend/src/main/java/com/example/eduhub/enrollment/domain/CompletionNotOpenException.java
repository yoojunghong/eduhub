package com.example.eduhub.enrollment.domain;

/** 시작일 전에 수료 처리를 하려 할 때 (D-09). */
public class CompletionNotOpenException extends RuntimeException {

  public CompletionNotOpenException(Long sessionId) {
    super("수료 처리는 회차 시작일부터 할 수 있습니다. sessionId=" + sessionId);
  }
}
