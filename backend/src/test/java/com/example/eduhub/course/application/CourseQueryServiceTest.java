package com.example.eduhub.course.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.example.eduhub.common.api.PageResponse;
import com.example.eduhub.course.api.CourseDetailResponse;
import com.example.eduhub.course.api.CourseSummaryResponse;
import com.example.eduhub.course.api.SessionResponse;
import com.example.eduhub.course.domain.Course;
import com.example.eduhub.course.domain.CourseRepository;
import com.example.eduhub.course.domain.CourseSession;
import com.example.eduhub.course.domain.CourseSessionRepository;
import com.example.eduhub.enrollment.domain.Enrollment;
import com.example.eduhub.enrollment.domain.EnrollmentRepository;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.EMBEDDED)
@ActiveProfiles("test")
@Import(CourseQueryService.class)
class CourseQueryServiceTest {

  private static final OffsetDateTime NOW = OffsetDateTime.now();

  @Autowired private CourseQueryService service;
  @Autowired private CourseRepository courseRepository;
  @Autowired private CourseSessionRepository sessionRepository;
  @Autowired private EnrollmentRepository enrollmentRepository;
  @Autowired private TestEntityManager em;

  @Test
  @DisplayName("확정 2명인 정원 20 회차의 남은 자리는 18이고, 신청 없는 회차는 정원 그대로다")
  void get_remainingSeatsSubtractsConfirmed() {
    Course course = courseRepository.save(new Course("Spring Boot 입문", "BACKEND", "기초"));
    LocalDate start = LocalDate.now().plusMonths(1);
    CourseSession withEnrollments = sessionRepository.save(course.addSession(start, "A동", 20));
    CourseSession empty = sessionRepository.save(course.addSession(start.plusWeeks(1), "B동", 10));
    em.flush();
    enrollmentRepository.save(Enrollment.confirm(withEnrollments, 1L, NOW));
    enrollmentRepository.save(Enrollment.confirm(withEnrollments, 2L, NOW));
    enrollmentRepository.save(Enrollment.waiting(withEnrollments, 3L, NOW));
    em.flush();
    em.clear();

    CourseDetailResponse detail = service.get(course.getId());

    assertThat(detail.sessions())
        .extracting(SessionResponse::id, SessionResponse::remainingSeats)
        .containsExactly(tuple(withEnrollments.getId(), 18), tuple(empty.getId(), 10));
  }

  @Test
  @DisplayName("상태 조건: OPEN은 운영 중만, CLOSED는 종료만, ALL은 전체를 돌려준다")
  void search_filtersByStatus() {
    courseRepository.save(new Course("운영 과정", "개발", null));
    Course closed = courseRepository.save(new Course("종료 과정", "개발", null));
    closed.close();
    em.flush();
    em.clear();
    PageRequest page = PageRequest.of(0, 10, Sort.by("id"));

    assertThat(titles(service.search("", "", CourseListFilter.OPEN, page)))
        .containsExactly("운영 과정");
    assertThat(titles(service.search("", "", CourseListFilter.CLOSED, page)))
        .containsExactly("종료 과정");
    assertThat(titles(service.search("", "", CourseListFilter.ALL, page)))
        .containsExactly("운영 과정", "종료 과정");
    assertThat(titles(service.search("종료", "", CourseListFilter.ALL, page)))
        .containsExactly("종료 과정");
  }

  private static List<String> titles(PageResponse<CourseSummaryResponse> page) {
    return page.content().stream().map(CourseSummaryResponse::title).toList();
  }
}
