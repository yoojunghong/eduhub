package com.example.eduhub.enrollment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.eduhub.common.config.ClockConfig;
import com.example.eduhub.course.domain.Course;
import com.example.eduhub.course.domain.CourseRepository;
import com.example.eduhub.course.domain.CourseSession;
import com.example.eduhub.course.domain.CourseSessionRepository;
import com.example.eduhub.enrollment.api.EnrollmentResponse;
import com.example.eduhub.enrollment.domain.DuplicateEnrollmentException;
import com.example.eduhub.enrollment.domain.EnrollmentPeriodEndedException;
import com.example.eduhub.enrollment.domain.SessionClosedException;
import com.example.eduhub.enrollment.domain.SessionNotFoundException;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.EMBEDDED)
@ActiveProfiles("test")
@Import({EnrollmentService.class, ClockConfig.class})
class EnrollmentServiceTest {

  private static final LocalDate NEXT_MONTH = LocalDate.now().plusMonths(1);

  @Autowired private EnrollmentService service;
  @Autowired private CourseRepository courseRepository;
  @Autowired private CourseSessionRepository sessionRepository;
  @Autowired private TestEntityManager em;

  private Course newCourse() {
    return courseRepository.save(new Course("Spring Boot 입문", "BACKEND", "기초"));
  }

  private CourseSession newSession(Course course, LocalDate startDate, int capacity) {
    return sessionRepository.saveAndFlush(course.addSession(startDate, "A동", capacity));
  }

  @Test
  @DisplayName("자리가 있으면 확정으로 신청된다")
  void enroll_withSeats_confirmed() {
    CourseSession session = newSession(newCourse(), NEXT_MONTH, 2);

    EnrollmentResponse response = service.enroll(session.getId(), 1001L);

    assertThat(response.id()).isNotNull();
    assertThat(response.status()).isEqualTo("CONFIRMED");
    assertThat(response.sessionId()).isEqualTo(session.getId());
    assertThat(response.courseId()).isEqualTo(session.getCourse().getId());
    assertThat(response.employeeId()).isEqualTo(1001L);
    assertThat(response.enrolledAt()).isNotNull();
  }

  @Test
  @DisplayName("정원이 차면 대기로 신청된다")
  void enroll_full_waiting() {
    CourseSession session = newSession(newCourse(), NEXT_MONTH, 1);
    service.enroll(session.getId(), 1L);

    EnrollmentResponse second = service.enroll(session.getId(), 2L);

    assertThat(second.status()).isEqualTo("WAITING");
  }

  @Test
  @DisplayName("같은 회차 재신청과 같은 과정 다른 회차 신청은 DUPLICATE")
  void enroll_duplicate_throws() {
    Course course = newCourse();
    CourseSession s1 = newSession(course, NEXT_MONTH, 10);
    CourseSession s2 = newSession(course, NEXT_MONTH.plusWeeks(1), 10);
    service.enroll(s1.getId(), 1L);

    assertThatThrownBy(() -> service.enroll(s1.getId(), 1L))
        .isInstanceOf(DuplicateEnrollmentException.class);
    assertThatThrownBy(() -> service.enroll(s2.getId(), 1L))
        .isInstanceOf(DuplicateEnrollmentException.class);
  }

  @Test
  @DisplayName("종료된 회차나 과정에는 신청할 수 없다")
  void enroll_closed_throws() {
    Course closedCourse = newCourse();
    CourseSession inClosedCourse = newSession(closedCourse, NEXT_MONTH, 10);
    closedCourse.close();
    CourseSession closedSession = newSession(newCourse(), NEXT_MONTH, 10);
    closedSession.close();
    em.flush();
    em.clear();

    assertThatThrownBy(() -> service.enroll(inClosedCourse.getId(), 1L))
        .isInstanceOf(SessionClosedException.class);
    assertThatThrownBy(() -> service.enroll(closedSession.getId(), 1L))
        .isInstanceOf(SessionClosedException.class);
  }

  @Test
  @DisplayName("시작일 당일이거나 지난 회차에는 신청할 수 없다")
  void enroll_periodEnded_throws() {
    CourseSession today = newSession(newCourse(), LocalDate.now(), 10);
    CourseSession past = newSession(newCourse(), LocalDate.now().minusDays(3), 10);
    CourseSession tomorrow = newSession(newCourse(), LocalDate.now().plusDays(1), 10);

    assertThatThrownBy(() -> service.enroll(today.getId(), 1L))
        .isInstanceOf(EnrollmentPeriodEndedException.class);
    assertThatThrownBy(() -> service.enroll(past.getId(), 1L))
        .isInstanceOf(EnrollmentPeriodEndedException.class);
    // 시작 전날까지는 신청 가능 (D-01)
    assertThat(service.enroll(tomorrow.getId(), 1L).status()).isEqualTo("CONFIRMED");
  }

  @Test
  @DisplayName("없는 회차에 신청하면 SessionNotFoundException")
  void enroll_notFound_throws() {
    assertThatThrownBy(() -> service.enroll(999_999L, 1L))
        .isInstanceOf(SessionNotFoundException.class);
  }
}
