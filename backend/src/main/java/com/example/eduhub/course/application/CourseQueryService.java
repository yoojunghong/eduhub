package com.example.eduhub.course.application;

import com.example.eduhub.common.api.PageResponse;
import com.example.eduhub.course.api.CourseDetailResponse;
import com.example.eduhub.course.api.CourseSummaryResponse;
import com.example.eduhub.course.domain.CourseNotFoundException;
import com.example.eduhub.course.domain.CourseRepository;
import com.example.eduhub.course.domain.CourseStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CourseQueryService {

  private final CourseRepository courseRepository;

  public CourseQueryService(CourseRepository courseRepository) {
    this.courseRepository = courseRepository;
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
        .map(CourseDetailResponse::from)
        .orElseThrow(() -> new CourseNotFoundException(id));
  }

  private static String blankToEmpty(String value) {
    return value == null ? "" : value.strip();
  }
}
