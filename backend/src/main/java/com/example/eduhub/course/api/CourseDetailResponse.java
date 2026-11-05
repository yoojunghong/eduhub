package com.example.eduhub.course.api;

import com.example.eduhub.course.domain.Course;
import com.example.eduhub.course.domain.CourseSession;
import java.util.Comparator;
import java.util.List;

public record CourseDetailResponse(
    Long id,
    String title,
    String category,
    String description,
    String status,
    List<SessionResponse> sessions) {

  public static CourseDetailResponse from(Course course) {
    List<SessionResponse> sessions =
        course.getSessions().stream()
            .sorted(Comparator.comparing(CourseSession::getStartDate))
            .map(SessionResponse::from)
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
