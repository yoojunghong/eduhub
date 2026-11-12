package com.example.eduhub.course.api;

import com.example.eduhub.course.domain.CourseSession;
import java.time.LocalDate;

/** 회차 정보. remainingSeats는 정원에서 확정 신청 수를 뺀 값이다 (FR-02, FR-04). */
public record SessionResponse(
    Long id, LocalDate startDate, String place, int capacity, int remainingSeats, String status) {

  public static SessionResponse from(CourseSession session, long confirmedCount) {
    return new SessionResponse(
        session.getId(),
        session.getStartDate(),
        session.getPlace(),
        session.getCapacity(),
        (int) Math.max(0, session.getCapacity() - confirmedCount),
        session.getStatus().name());
  }
}
