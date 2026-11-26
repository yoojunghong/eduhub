package com.example.eduhub.course.application;

import com.example.eduhub.common.api.PageResponse;
import com.example.eduhub.course.api.CourseDetailResponse;
import com.example.eduhub.course.api.CourseSummaryResponse;
import com.example.eduhub.course.domain.CourseNotFoundException;
import com.example.eduhub.course.domain.CourseRepository;
import com.example.eduhub.course.domain.CourseStatus;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CourseQueryService {

  private final CourseRepository courseRepository;
  private final SessionCountReader sessionCountReader;

  public CourseQueryService(
      CourseRepository courseRepository, SessionCountReader sessionCountReader) {
    this.courseRepository = courseRepository;
    this.sessionCountReader = sessionCountReader;
  }

  /** 임직원 목록: 운영 중(OPEN)인 과정만 보여 준다. */
  public PageResponse<CourseSummaryResponse> search(
      String keyword, String category, Pageable pageable) {
    return search(keyword, category, CourseListFilter.OPEN, pageable);
  }

  /** 관리 화면은 ALL(전체)·CLOSED(종료만)로 종료된 과정도 본다 (FR-03). */
  public PageResponse<CourseSummaryResponse> search(
      String keyword, String category, CourseListFilter filter, Pageable pageable) {
    List<CourseStatus> statuses =
        switch (filter) {
          case OPEN -> List.of(CourseStatus.OPEN);
          case CLOSED -> List.of(CourseStatus.CLOSED);
          case ALL -> List.of(CourseStatus.values());
        };
    return PageResponse.from(
        courseRepository
            .search(statuses, blankToEmpty(keyword), blankToEmpty(category), pageable)
            .map(CourseSummaryResponse::from));
  }

  /** 상세는 종료된 과정도 조회할 수 있다. */
  public CourseDetailResponse get(Long id) {
    return courseRepository
        .findWithSessionsById(id)
        .map(course -> {
          SessionCountReader.Counts counts = sessionCountReader.read(course);
          return CourseDetailResponse.from(course, counts.confirmed(), counts.waiting());
        })
        .orElseThrow(() -> new CourseNotFoundException(id));
  }

  private static String blankToEmpty(String value) {
    return value == null ? "" : value.strip();
  }
}
