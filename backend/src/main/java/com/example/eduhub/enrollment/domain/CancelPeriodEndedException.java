package com.example.eduhub.enrollment.domain;

/** 회차 시작일 당일부터는 취소할 수 없다 (D-02, FR-06). */
public class CancelPeriodEndedException extends RuntimeException {

  public CancelPeriodEndedException(Long sessionId) {
    super("취소 기간이 지났습니다. sessionId=" + sessionId);
  }
}
