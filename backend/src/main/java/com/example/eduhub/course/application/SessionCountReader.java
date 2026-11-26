package com.example.eduhub.course.application;

import com.example.eduhub.course.domain.Course;
import com.example.eduhub.course.domain.CourseSession;
import com.example.eduhub.enrollment.domain.EnrollmentRepository;
import com.example.eduhub.enrollment.domain.SessionCount;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** 과정의 회차별 확정·대기 수를 각각 쿼리 한 번으로 집계한다 (N+1 방지). 없는 회차는 맵에 없다. */
@Component
class SessionCountReader {

  /** 회차 id별 확정 수와 대기 수. */
  record Counts(Map<Long, Long> confirmed, Map<Long, Long> waiting) {}

  private final EnrollmentRepository enrollmentRepository;

  SessionCountReader(EnrollmentRepository enrollmentRepository) {
    this.enrollmentRepository = enrollmentRepository;
  }

  Counts read(Course course) {
    List<Long> ids = course.getSessions().stream().map(CourseSession::getId).toList();
    if (ids.isEmpty()) {
      return new Counts(Map.of(), Map.of());
    }
    return new Counts(
        toMap(enrollmentRepository.countConfirmedBySessionIds(ids)),
        toMap(enrollmentRepository.countWaitingBySessionIds(ids)));
  }

  private static Map<Long, Long> toMap(List<SessionCount> counts) {
    return counts.stream().collect(Collectors.toMap(SessionCount::sessionId, SessionCount::count));
  }
}
