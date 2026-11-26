package com.example.eduhub.enrollment.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.eduhub.course.domain.Course;
import com.example.eduhub.course.domain.CourseRepository;
import com.example.eduhub.course.domain.CourseSession;
import com.example.eduhub.course.domain.CourseSessionRepository;
import com.example.eduhub.enrollment.api.EnrollmentResponse;
import com.example.eduhub.enrollment.domain.CancelPeriodEndedException;
import com.example.eduhub.enrollment.domain.DuplicateEnrollmentException;
import com.example.eduhub.enrollment.domain.EnrollmentNotCancelableException;
import com.example.eduhub.enrollment.domain.EnrollmentNotFoundException;
import com.example.eduhub.enrollment.domain.EnrollmentPeriodEndedException;
import com.example.eduhub.enrollment.domain.EnrollmentRepository;
import com.example.eduhub.enrollment.domain.EnrollmentStatus;
import com.example.eduhub.enrollment.domain.SessionClosedException;
import com.example.eduhub.enrollment.domain.SessionNotFoundException;
import com.example.eduhub.enrollment.domain.WaitlistFullException;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.EMBEDDED)
@ActiveProfiles("test")
@Import({EnrollmentService.class, EnrollmentServiceTest.FixedClockConfig.class})
class EnrollmentServiceTest {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  /** 테스트 시계의 기준 "오늘" (KST). 실제 날짜와 무관하게 고정한다. */
  private static final LocalDate TODAY = LocalDate.of(2030, 1, 15);

  private static final LocalDate NEXT_MONTH = TODAY.plusMonths(1);

  /** 테스트에서 시각을 바꿀 수 있는 KST 시계. */
  static class MutableClock extends Clock {
    private volatile Instant instant;

    MutableClock(Instant instant) {
      this.instant = instant;
    }

    void set(ZonedDateTime time) {
      this.instant = time.toInstant();
    }

    @Override
    public ZoneId getZone() {
      return KST;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return Clock.fixed(instant, zone);
    }

    @Override
    public Instant instant() {
      return instant;
    }
  }

  @TestConfiguration
  static class FixedClockConfig {
    @Bean
    MutableClock clock() {
      return new MutableClock(TODAY.atTime(10, 0).atZone(KST).toInstant());
    }
  }

  @Autowired private MutableClock clock;

  @BeforeEach
  void resetClock() {
    clock.set(TODAY.atTime(10, 0).atZone(KST));
  }

  @Autowired private EnrollmentService service;
  @Autowired private CourseRepository courseRepository;
  @Autowired private CourseSessionRepository sessionRepository;
  @Autowired private EnrollmentRepository enrollmentRepository;
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
    CourseSession session = newSession(newCourse(), NEXT_MONTH, 2);
    service.enroll(session.getId(), 1L);
    service.enroll(session.getId(), 2L);

    EnrollmentResponse third = service.enroll(session.getId(), 3L);

    assertThat(third.status()).isEqualTo("WAITING");
  }

  @Test
  @DisplayName("정원 4(대기 상한 2)에서 5·6번째 신청은 WAITING, 순번 1·2, 확정은 순번 null")
  void enroll_waitingNumbers() {
    CourseSession session = newSession(newCourse(), NEXT_MONTH, 4);
    EnrollmentResponse first = service.enroll(session.getId(), 1L);
    for (long e = 2; e <= 4; e++) {
      service.enroll(session.getId(), e);
    }

    EnrollmentResponse w1 = service.enroll(session.getId(), 5L);
    EnrollmentResponse w2 = service.enroll(session.getId(), 6L);

    assertThat(first.waitingNumber()).isNull();
    assertThat(w1.status()).isEqualTo("WAITING");
    assertThat(w1.waitingNumber()).isEqualTo(1);
    assertThat(w2.status()).isEqualTo("WAITING");
    assertThat(w2.waitingNumber()).isEqualTo(2);
  }

  @Test
  @DisplayName("정원 2(대기 상한 1)에서 4번째 신청은 WAITLIST_FULL이고 저장되지 않는다")
  void enroll_waitlistFull_notSaved() {
    CourseSession session = newSession(newCourse(), NEXT_MONTH, 2);
    service.enroll(session.getId(), 1L);
    service.enroll(session.getId(), 2L);
    assertThat(service.enroll(session.getId(), 3L).waitingNumber()).isEqualTo(1);

    assertThatThrownBy(() -> service.enroll(session.getId(), 4L))
        .isInstanceOf(WaitlistFullException.class);

    assertThat(enrollmentCount(session.getId())).isEqualTo(3L);
  }

  @Test
  @DisplayName("정원 1(대기 상한 0)에서 2번째 신청은 WAITLIST_FULL")
  void enroll_capacityOne_secondWaitlistFull() {
    CourseSession session = newSession(newCourse(), NEXT_MONTH, 1);
    service.enroll(session.getId(), 1L);

    assertThatThrownBy(() -> service.enroll(session.getId(), 2L))
        .isInstanceOf(WaitlistFullException.class);

    assertThat(enrollmentCount(session.getId())).isEqualTo(1L);
  }

  private long enrollmentCount(Long sessionId) {
    em.flush();
    return ((Number)
            em.getEntityManager()
                .createQuery("select count(e) from Enrollment e where e.session.id = :id")
                .setParameter("id", sessionId)
                .getSingleResult())
        .longValue();
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
    CourseSession today = newSession(newCourse(), TODAY, 10);
    CourseSession past = newSession(newCourse(), TODAY.minusDays(3), 10);
    CourseSession tomorrow = newSession(newCourse(), TODAY.plusDays(1), 10);

    assertThatThrownBy(() -> service.enroll(today.getId(), 1L))
        .isInstanceOf(EnrollmentPeriodEndedException.class);
    assertThatThrownBy(() -> service.enroll(past.getId(), 1L))
        .isInstanceOf(EnrollmentPeriodEndedException.class);
    // 시작 전날까지는 신청 가능 (D-01)
    assertThat(service.enroll(tomorrow.getId(), 1L).status()).isEqualTo("CONFIRMED");
  }

  @Test
  @DisplayName("시작 전날 23:59:59.999 KST 는 신청 가능, 시작 당일 00:00 KST 는 ENROLLMENT_PERIOD_ENDED")
  void enroll_kstBoundary() {
    LocalDate start = TODAY.plusDays(10);
    CourseSession s1 = newSession(newCourse(), start, 10);
    CourseSession s2 = newSession(newCourse(), start, 10);

    clock.set(start.minusDays(1).atTime(23, 59, 59, 999_000_000).atZone(KST));
    assertThat(service.enroll(s1.getId(), 1L).status()).isEqualTo("CONFIRMED");

    clock.set(start.atStartOfDay(KST));
    assertThatThrownBy(() -> service.enroll(s2.getId(), 2L))
        .isInstanceOf(EnrollmentPeriodEndedException.class);
  }

  @Test
  @DisplayName("유일 인덱스 위반만 중복으로 판별하고 다른 제약 위반은 아니다")
  void isActiveEnrollmentViolation_onlyUniqueIndex() {
    var other =
        new DataIntegrityViolationException(
            "x",
            new ConstraintViolationException(
                "other", new SQLException("fk"), "fk_enrollment_session"));
    var unique =
        new DataIntegrityViolationException(
            "y",
            new ConstraintViolationException(
                "dup", new SQLException("dup"), "ux_enrollment_active_course"));
    var uniqueByMessage =
        new DataIntegrityViolationException(
            "duplicate key value violates unique constraint \"ux_enrollment_active_course\"");

    assertThat(EnrollmentService.isActiveEnrollmentViolation(other)).isFalse();
    assertThat(EnrollmentService.isActiveEnrollmentViolation(unique)).isTrue();
    assertThat(EnrollmentService.isActiveEnrollmentViolation(uniqueByMessage)).isTrue();
  }

  private String statusOf(Long enrollmentId) {
    em.flush();
    em.clear();
    return enrollmentRepository.findById(enrollmentId).orElseThrow().getStatus().name();
  }

  @Test
  @DisplayName("확정자 취소(시작 7일 전): 대기 1순위가 확정되고 2순위 순번이 1로 당겨진다")
  void cancel_confirmed_promotesFirstWaiting() {
    CourseSession session = newSession(newCourse(), TODAY.plusDays(7), 4);
    EnrollmentResponse first = service.enroll(session.getId(), 1L);
    for (long e = 2; e <= 4; e++) {
      service.enroll(session.getId(), e);
    }
    EnrollmentResponse w1 = service.enroll(session.getId(), 5L);
    EnrollmentResponse w2 = service.enroll(session.getId(), 6L);

    EnrollmentResponse canceled = service.cancel(first.id(), 1L);

    assertThat(canceled.status()).isEqualTo("CANCELED");
    assertThat(canceled.waitingNumber()).isNull();
    assertThat(statusOf(w1.id())).isEqualTo("CONFIRMED");
    assertThat(statusOf(w2.id())).isEqualTo("WAITING");
    assertThat(enrollmentRepository.countWaitingAhead(session.getId(), w2.id())).isEqualTo(1L);
  }

  @Test
  @DisplayName("대기자 취소: 아무도 승급하지 않고 뒤 대기자 순번이 당겨진다")
  void cancel_waiting_noPromotion() {
    CourseSession session = newSession(newCourse(), TODAY.plusDays(7), 4);
    for (long e = 1; e <= 4; e++) {
      service.enroll(session.getId(), e);
    }
    EnrollmentResponse w1 = service.enroll(session.getId(), 5L);
    EnrollmentResponse w2 = service.enroll(session.getId(), 6L);

    EnrollmentResponse canceled = service.cancel(w1.id(), 5L);

    assertThat(canceled.status()).isEqualTo("CANCELED");
    assertThat(statusOf(w2.id())).isEqualTo("WAITING");
    assertThat(enrollmentRepository.countWaitingAhead(session.getId(), w2.id())).isEqualTo(1L);
    assertThat(
            enrollmentRepository.countBySessionIdAndStatus(
                session.getId(), EnrollmentStatus.CONFIRMED))
        .isEqualTo(4L);
  }

  @Test
  @DisplayName("오늘 = 시작일 - 1: 확정자 취소는 성공하지만 승급하지 않는다")
  void cancel_dayBeforeStart_noPromotion() {
    LocalDate start = TODAY.plusDays(7);
    CourseSession session = newSession(newCourse(), start, 2);
    EnrollmentResponse first = service.enroll(session.getId(), 1L);
    service.enroll(session.getId(), 2L);
    EnrollmentResponse w = service.enroll(session.getId(), 3L);
    clock.set(start.minusDays(1).atTime(10, 0).atZone(KST));

    EnrollmentResponse canceled = service.cancel(first.id(), 1L);

    assertThat(canceled.status()).isEqualTo("CANCELED");
    assertThat(statusOf(w.id())).isEqualTo("WAITING");
  }

  @Test
  @DisplayName("오늘 = 시작일 - 2 (23:59): 승급 기간의 마지막 날이라 대기 1순위가 확정된다")
  void cancel_twoDaysBeforeStart_promotes() {
    LocalDate start = TODAY.plusDays(7);
    CourseSession session = newSession(newCourse(), start, 2);
    EnrollmentResponse first = service.enroll(session.getId(), 1L);
    service.enroll(session.getId(), 2L);
    EnrollmentResponse w = service.enroll(session.getId(), 3L);
    clock.set(start.minusDays(2).atTime(23, 59, 59).atZone(KST));

    service.cancel(first.id(), 1L);

    assertThat(statusOf(w.id())).isEqualTo("CONFIRMED");
  }

  @Test
  @DisplayName("빈 자리 + 대기자 있음(전날 취소 후): 새 신청은 새치기하지 못하고 WAITING")
  void enroll_afterNoPromotionCancel_waiting() {
    LocalDate start = TODAY.plusDays(7);
    CourseSession session = newSession(newCourse(), start, 4);
    EnrollmentResponse first = service.enroll(session.getId(), 1L);
    for (long e = 2; e <= 4; e++) {
      service.enroll(session.getId(), e);
    }
    EnrollmentResponse w = service.enroll(session.getId(), 5L);
    clock.set(start.minusDays(1).atTime(10, 0).atZone(KST));
    service.cancel(first.id(), 1L);

    EnrollmentResponse late = service.enroll(session.getId(), 6L);

    assertThat(late.status()).isEqualTo("WAITING");
    assertThat(late.waitingNumber()).isEqualTo(2);
    assertThat(statusOf(w.id())).isEqualTo("WAITING");
  }

  @Test
  @DisplayName("오늘 = 시작일에 취소하면 CancelPeriodEndedException")
  void cancel_onStartDate_throws() {
    LocalDate start = TODAY.plusDays(3);
    CourseSession session = newSession(newCourse(), start, 2);
    EnrollmentResponse e = service.enroll(session.getId(), 1L);
    clock.set(start.atTime(9, 0).atZone(KST));

    assertThatThrownBy(() -> service.cancel(e.id(), 1L))
        .isInstanceOf(CancelPeriodEndedException.class);
    assertThat(statusOf(e.id())).isEqualTo("CONFIRMED");
  }

  @Test
  @DisplayName("남의 신청이나 없는 신청을 취소하면 EnrollmentNotFoundException")
  void cancel_notOwnerOrMissing_throws() {
    CourseSession session = newSession(newCourse(), NEXT_MONTH, 2);
    EnrollmentResponse e = service.enroll(session.getId(), 1L);

    assertThatThrownBy(() -> service.cancel(e.id(), 2L))
        .isInstanceOf(EnrollmentNotFoundException.class);
    assertThatThrownBy(() -> service.cancel(999_999L, 1L))
        .isInstanceOf(EnrollmentNotFoundException.class);
    assertThat(statusOf(e.id())).isEqualTo("CONFIRMED");
  }

  @Test
  @DisplayName("이미 취소된 신청을 다시 취소하면 EnrollmentNotCancelableException")
  void cancel_twice_throws() {
    CourseSession session = newSession(newCourse(), NEXT_MONTH, 2);
    EnrollmentResponse e = service.enroll(session.getId(), 1L);
    service.cancel(e.id(), 1L);

    assertThatThrownBy(() -> service.cancel(e.id(), 1L))
        .isInstanceOf(EnrollmentNotCancelableException.class);
  }

  @Test
  @DisplayName("취소 후 같은 과정에 다시 신청할 수 있다")
  void enroll_afterCancel_succeeds() {
    CourseSession session = newSession(newCourse(), NEXT_MONTH, 2);
    EnrollmentResponse e = service.enroll(session.getId(), 1L);
    service.cancel(e.id(), 1L);

    EnrollmentResponse again = service.enroll(session.getId(), 1L);

    assertThat(again.status()).isEqualTo("CONFIRMED");
    assertThat(again.id()).isNotEqualTo(e.id());
  }

  @Test
  @DisplayName("없는 회차에 신청하면 SessionNotFoundException")
  void enroll_notFound_throws() {
    assertThatThrownBy(() -> service.enroll(999_999L, 1L))
        .isInstanceOf(SessionNotFoundException.class);
  }
}
