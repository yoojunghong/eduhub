package com.example.eduhub.enrollment.domain;

/** 대기 인원이 상한(정원의 50%, D-03·D-06)에 찼을 때 (FR-05). */
public class WaitlistFullException extends RuntimeException {

  public WaitlistFullException(Long sessionId) {
    super("대기 인원이 가득 찼습니다. sessionId=" + sessionId);
  }
}
