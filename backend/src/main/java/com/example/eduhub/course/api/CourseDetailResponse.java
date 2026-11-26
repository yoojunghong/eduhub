package com.example.eduhub.course.api;

import com.example.eduhub.course.domain.Course;
import com.example.eduhub.course.domain.CourseSession;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public record CourseDetailResponse(
    Long id,
    String title,
    String category,
    String description,
    String status,
    List<SessionResponse> sessions) {

  /** confirmedBySession, waitingBySession: 회차 id별 확정·대기 신청 수. 맵에 없는 회차는 0으로 본다. */
  public static CourseDetailResponse from(
      Course course, Map<Long, Long> confirmedBySession, Map<Long, Long> waitingBySession) {
    List<SessionResponse> sessions =
        course.getSessions().stream()
            .sorted(Comparator.comparing(CourseSession::getStartDate))
            .map(s ->
                SessionResponse.from(
                    s,
                    confirmedBySession.getOrDefault(s.getId(), 0L),
                    waitingBySession.getOrDefault(s.getId(), 0L)))
            .toList();
    return new CourseDetailResponse(
        course.getId(),
        course.getTitle(),
        course.getCategory(),
        course.getDescription(),
        course.getStatus().name(),
        sessions);
  }
}
