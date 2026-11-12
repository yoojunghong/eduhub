package com.example.eduhub.course.application;

import com.example.eduhub.course.api.CourseCreateRequest;
import com.example.eduhub.course.api.CourseDetailResponse;
import com.example.eduhub.course.api.CourseUpdateRequest;
import com.example.eduhub.course.api.SessionCreateRequest;
import com.example.eduhub.course.api.SessionResponse;
import com.example.eduhub.course.domain.Course;
import com.example.eduhub.course.domain.CourseNotFoundException;
import com.example.eduhub.course.domain.CourseRepository;
import com.example.eduhub.course.domain.CourseSession;
import com.example.eduhub.course.domain.CourseSessionRepository;
import com.example.eduhub.enrollment.domain.EnrollmentRepository;
import com.example.eduhub.enrollment.domain.SessionCount;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 과정·회차 등록, 수정, 종료 (FR-03). 인증·관리자 권한 확인은 4주차 이후에 붙인다. */
@Service
@Transactional
public class CourseCommandService {

  private final CourseRepository courseRepository;
  private final CourseSessionRepository sessionRepository;
  private final EnrollmentRepository enrollmentRepository;

  public CourseCommandService(
      CourseRepository courseRepository,
      CourseSessionRepository sessionRepository,
      EnrollmentRepository enrollmentRepository) {
    this.courseRepository = courseRepository;
    this.sessionRepository = sessionRepository;
    this.enrollmentRepository = enrollmentRepository;
  }

  public CourseDetailResponse create(CourseCreateRequest request) {
    Course course =
        courseRepository.save(
            new Course(request.title(), request.category(), request.description()));
    return CourseDetailResponse.from(course, Map.of()); // 새 과정은 회차가 없다
  }

  public CourseDetailResponse update(Long id, CourseUpdateRequest request) {
    Course course = findWithSessions(id);
    course.update(request.title(), request.category(), request.description());
    return CourseDetailResponse.from(course, confirmedBySession(course));
  }

  public SessionResponse addSession(Long id, SessionCreateRequest request) {
    Course course = findWithSessions(id);
    // 방금 만든 회차는 신청이 없으므로 확정 수 0
    return SessionResponse.from(
        sessionRepository.save(
            course.addSession(request.startDate(), request.place(), request.capacity())),
        0);
  }

  /** 과정과 모든 회차를 종료한다. 이미 종료된 과정은 그대로 둔다. */
  public CourseDetailResponse close(Long id) {
    Course course = findWithSessions(id);
    course.close();
    return CourseDetailResponse.from(course, confirmedBySession(course));
  }

  private Course findWithSessions(Long id) {
    return courseRepository
        .findWithSessionsById(id)
        .orElseThrow(() -> new CourseNotFoundException(id));
  }

  /** 회차별 확정 수를 쿼리 한 번으로 집계한다 (N+1 방지). 확정이 없는 회차는 맵에 없다. */
  private Map<Long, Long> confirmedBySession(Course course) {
    List<Long> ids = course.getSessions().stream().map(CourseSession::getId).toList();
    if (ids.isEmpty()) {
      return Map.of();
    }
    return enrollmentRepository.countConfirmedBySessionIds(ids).stream()
        .collect(Collectors.toMap(SessionCount::sessionId, SessionCount::count));
  }
}
