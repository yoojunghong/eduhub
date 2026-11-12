package com.example.eduhub.course.application;

import com.example.eduhub.course.api.CourseCreateRequest;
import com.example.eduhub.course.api.CourseDetailResponse;
import com.example.eduhub.course.api.CourseUpdateRequest;
import com.example.eduhub.course.api.SessionCreateRequest;
import com.example.eduhub.course.api.SessionResponse;
import com.example.eduhub.course.domain.Course;
import com.example.eduhub.course.domain.CourseNotFoundException;
import com.example.eduhub.course.domain.CourseRepository;
import com.example.eduhub.course.domain.CourseSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 과정·회차 등록, 수정, 종료 (FR-03). 인증·관리자 권한 확인은 4주차 이후에 붙인다. */
@Service
@Transactional
public class CourseCommandService {

  private final CourseRepository courseRepository;
  private final CourseSessionRepository sessionRepository;

  public CourseCommandService(
      CourseRepository courseRepository, CourseSessionRepository sessionRepository) {
    this.courseRepository = courseRepository;
    this.sessionRepository = sessionRepository;
  }

  public CourseDetailResponse create(CourseCreateRequest request) {
    Course course =
        courseRepository.save(
            new Course(request.title(), request.category(), request.description()));
    return CourseDetailResponse.from(course);
  }

  public CourseDetailResponse update(Long id, CourseUpdateRequest request) {
    Course course = findWithSessions(id);
    course.update(request.title(), request.category(), request.description());
    return CourseDetailResponse.from(course);
  }

  public SessionResponse addSession(Long id, SessionCreateRequest request) {
    Course course = findWithSessions(id);
    return SessionResponse.from(
        sessionRepository.save(
            course.addSession(request.startDate(), request.place(), request.capacity())));
  }

  /** 과정과 모든 회차를 종료한다. 이미 종료된 과정은 그대로 둔다. */
  public CourseDetailResponse close(Long id) {
    Course course = findWithSessions(id);
    course.close();
    return CourseDetailResponse.from(course);
  }

  private Course findWithSessions(Long id) {
    return courseRepository
        .findWithSessionsById(id)
        .orElseThrow(() -> new CourseNotFoundException(id));
  }
}
