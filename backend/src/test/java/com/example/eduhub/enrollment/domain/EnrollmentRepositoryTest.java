package com.example.eduhub.enrollment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.eduhub.course.domain.Course;
import com.example.eduhub.course.domain.CourseRepository;
import com.example.eduhub.course.domain.CourseSession;
import com.example.eduhub.course.domain.CourseSessionRepository;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.EMBEDDED)
@ActiveProfiles("test")
class EnrollmentRepositoryTest {

  private static final OffsetDateTime NOW = OffsetDateTime.now();

  @Autowired private EnrollmentRepository enrollmentRepository;
  @Autowired private CourseRepository courseRepository;
  @Autowired private CourseSessionRepository sessionRepository;
  @Autowired private TestEntityManager em;

  private Course newCourse() {
    return courseRepository.save(new Course("Spring Boot 입문", "BACKEND", "기초"));
  }

  private CourseSession newSession(Course course) {
    CourseSession session = course.addSession(LocalDate.now().plusMonths(1), "A동", 20);
    return sessionRepository.saveAndFlush(session);
  }

  @Test
  @DisplayName("확정 신청 수를 회차·상태별로 센다")
  void countBySessionIdAndStatus_countsOnlyGivenStatus() {
    CourseSession session = newSession(newCourse());
    enrollmentRepository.save(Enrollment.confirm(session, 1L, NOW));
    enrollmentRepository.save(Enrollment.confirm(session, 2L, NOW));
    enrollmentRepository.saveAndFlush(Enrollment.waiting(session, 3L, NOW));

    assertThat(
            enrollmentRepository.countBySessionIdAndStatus(
                session.getId(), EnrollmentStatus.CONFIRMED))
        .isEqualTo(2);
    assertThat(
            enrollmentRepository.countBySessionIdAndStatus(
                session.getId(), EnrollmentStatus.WAITING))
        .isEqualTo(1);
  }

  @Test
  @DisplayName("같은 과정에 진행 중 신청이 있으면 존재한다고 답한다")
  void existsActiveInCourse_trueForActive() {
    Course course = newCourse();
    CourseSession session = newSession(course);
    enrollmentRepository.saveAndFlush(Enrollment.waiting(session, 1L, NOW));

    assertThat(enrollmentRepository.existsActiveInCourse(course.getId(), 1L)).isTrue();
    assertThat(enrollmentRepository.existsActiveInCourse(course.getId(), 2L)).isFalse();
  }

  @Test
  @DisplayName("회차별 확정 수를 한 번에 집계한다")
  void countConfirmedBySessionIds_groupsBySession() {
    Course course = newCourse();
    CourseSession s1 = newSession(course);
    CourseSession s2 = newSession(course);
    enrollmentRepository.save(Enrollment.confirm(s1, 1L, NOW));
    enrollmentRepository.save(Enrollment.confirm(s1, 2L, NOW));
    enrollmentRepository.saveAndFlush(Enrollment.waiting(s1, 3L, NOW));

    List<SessionCount> counts =
        enrollmentRepository.countConfirmedBySessionIds(List.of(s1.getId(), s2.getId()));

    assertThat(counts).containsExactly(new SessionCount(s1.getId(), 2));
  }

  @Test
  @DisplayName("같은 과정에 진행 중 신청 2건을 저장하면 유일 인덱스 위반이다")
  void save_twoActiveInSameCourse_violatesUniqueIndex() {
    Course course = newCourse();
    CourseSession s1 = newSession(course);
    CourseSession s2 = newSession(course);
    enrollmentRepository.saveAndFlush(Enrollment.confirm(s1, 1L, NOW));

    assertThatThrownBy(() -> enrollmentRepository.saveAndFlush(Enrollment.waiting(s2, 1L, NOW)))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @DisplayName("취소된 신청은 유일 인덱스 대상이 아니어서 다시 신청할 수 있다")
  void save_afterCanceled_succeeds() {
    Course course = newCourse();
    CourseSession session = newSession(course);
    Enrollment first = enrollmentRepository.saveAndFlush(Enrollment.confirm(session, 1L, NOW));
    em.getEntityManager()
        .createNativeQuery("update enrollment set status = 'CANCELED' where id = :id")
        .setParameter("id", first.getId())
        .executeUpdate();
    em.clear();

    Enrollment again = enrollmentRepository.saveAndFlush(Enrollment.confirm(session, 1L, NOW));

    assertThat(again.getId()).isNotNull();
    assertThat(enrollmentRepository.existsActiveInCourse(course.getId(), 1L)).isTrue();
  }
}
