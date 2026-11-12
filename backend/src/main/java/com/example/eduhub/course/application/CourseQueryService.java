package com.example.eduhub.course.application;

import com.example.eduhub.common.api.PageResponse;
import com.example.eduhub.course.api.CourseDetailResponse;
import com.example.eduhub.course.api.CourseSummaryResponse;
import com.example.eduhub.course.domain.Course;
import com.example.eduhub.course.domain.CourseNotFoundException;
import com.example.eduhub.course.domain.CourseRepository;
import com.example.eduhub.course.domain.CourseSession;
import com.example.eduhub.course.domain.CourseStatus;
import com.example.eduhub.enrollment.domain.EnrollmentRepository;
import com.example.eduhub.enrollment.domain.SessionCount;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CourseQueryService {

  private final CourseRepository courseRepository;
  private final EnrollmentRepository enrollmentRepository;

  public CourseQueryService(
      CourseRepository courseRepository, EnrollmentRepository enrollmentRepository) {
    this.courseRepository = courseRepository;
    this.enrollmentRepository = enrollmentRepository;
  }

  /** 목록에는 운영 중(OPEN)인 과정만 보여 준다. */
  public PageResponse<CourseSummaryResponse> search(
      String keyword, String category, Pageable pageable) {
    return PageResponse.from(
        courseRepository
            .search(CourseStatus.OPEN, blankToEmpty(keyword), blankToEmpty(category), pageable)
            .map(CourseSummaryResponse::from));
  }

  /** 상세는 종료된 과정도 조회할 수 있다. */
  public CourseDetailResponse get(Long id) {
    return courseRepository
        .findWithSessionsById(id)
        .map(course -> CourseDetailResponse.from(course, confirmedBySession(course)))
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

  private static String blankToEmpty(String value) {
    return value == null ? "" : value.strip();
  }
}
