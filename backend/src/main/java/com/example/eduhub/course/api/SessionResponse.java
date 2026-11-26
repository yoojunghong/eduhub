package com.example.eduhub.course.api;

import com.example.eduhub.course.domain.CourseSession;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/** 회차 정보. remainingSeats는 정원에서 확정 신청 수를 뺀 값이다 (FR-02, FR-04). */
public record SessionResponse(
    Long id,
    LocalDate startDate,
    String place,
    int capacity,
    int remainingSeats,
    String status,
    @Schema(description = "현재 대기 인원 (WAITING 상태 신청 수)") int waitingCount,
    @Schema(description = "대기 상한 = 정원 / 2 (내림, D-06)") int waitlistLimit) {

  public static SessionResponse from(
      CourseSession session, long confirmedCount, long waitingCount) {
    return new SessionResponse(
        session.getId(),
        session.getStartDate(),
        session.getPlace(),
        session.getCapacity(),
        (int) Math.max(0, session.getCapacity() - confirmedCount),
        session.getStatus().name(),
        (int) waitingCount,
        session.waitlistLimit());
  }
}
