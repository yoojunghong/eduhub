package com.example.eduhub.course.api;

import com.example.eduhub.course.domain.CourseSession;
import java.time.LocalDate;

/** 회차 정보. remainingSeats는 수강 신청(3주차) 전까지 정원과 같다. */
public record SessionResponse(
    Long id, LocalDate startDate, String place, int capacity, int remainingSeats, String status) {

  public static SessionResponse from(CourseSession session) {
    return new SessionResponse(
        session.getId(),
        session.getStartDate(),
        session.getPlace(),
        session.getCapacity(),
        session.getCapacity(),
        session.getStatus().name());
  }
}
