package com.example.eduhub.auth.application;

/** 사번이 없거나 비밀번호가 틀림. 어느 쪽인지 드러나지 않도록 같은 예외·메시지를 쓴다. */
public class InvalidCredentialsException extends RuntimeException {

  public InvalidCredentialsException() {
    super("사번 또는 비밀번호가 올바르지 않습니다.");
  }
}
