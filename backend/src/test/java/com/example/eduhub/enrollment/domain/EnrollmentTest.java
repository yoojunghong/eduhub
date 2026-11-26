package com.example.eduhub.enrollment.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.eduhub.course.domain.Course;
import com.example.eduhub.course.domain.CourseSession;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.util.ReflectionTestUtils;

class EnrollmentTest {

  private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-26T10:00:00+09:00");

  private CourseSession session(int capacity) {
    Course course = new Course("Spring Boot 입문", "BACKEND", "기초");
    return course.addSession(LocalDate.of(2026, 12, 10), "본관", capacity);
  }

  @Test
  void 확정_신청을_취소하면_CANCELED() {
    Enrollment e = Enrollment.confirm(session(10), 1L, NOW);

    e.cancel();

    assertThat(e.getStatus()).isEqualTo(EnrollmentStatus.CANCELED);
  }

  @Test
  void 대기_신청을_취소하면_CANCELED() {
    Enrollment e = Enrollment.waiting(session(10), 1L, NOW);

    e.cancel();

    assertThat(e.getStatus()).isEqualTo(EnrollmentStatus.CANCELED);
  }

  @Test
  void 이미_취소된_신청은_다시_취소할_수_없다() {
    Enrollment e = Enrollment.confirm(session(10), 1L, NOW);
    e.cancel();

    assertThatThrownBy(e::cancel).isInstanceOf(EnrollmentNotCancelableException.class);
    assertThat(e.getStatus()).isEqualTo(EnrollmentStatus.CANCELED);
  }

  @Test
  void 대기_신청을_승급하면_CONFIRMED() {
    Enrollment e = Enrollment.waiting(session(10), 1L, NOW);

    e.promote();

    assertThat(e.getStatus()).isEqualTo(EnrollmentStatus.CONFIRMED);
    assertThat(e.isWaiting()).isFalse();
  }

  @Test
  void 확정_신청은_승급할_수_없다() {
    Enrollment e = Enrollment.confirm(session(10), 1L, NOW);

    assertThatThrownBy(e::promote).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void 취소된_신청은_승급할_수_없다() {
    Enrollment e = Enrollment.waiting(session(10), 1L, NOW);
    e.cancel();

    assertThatThrownBy(e::promote).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void isWaiting은_대기_상태만_true() {
    assertThat(Enrollment.waiting(session(10), 1L, NOW).isWaiting()).isTrue();
    assertThat(Enrollment.confirm(session(10), 1L, NOW).isWaiting()).isFalse();
  }

  @Test
  void 수료된_신청은_취소할_수_없다() {
    Enrollment e = Enrollment.confirm(session(10), 1L, NOW);
    ReflectionTestUtils.setField(e, "status", EnrollmentStatus.COMPLETED);

    assertThatThrownBy(e::cancel).isInstanceOf(EnrollmentNotCancelableException.class);
    assertThat(e.getStatus()).isEqualTo(EnrollmentStatus.COMPLETED);
  }

  @Test
  void 수료된_신청은_승급할_수_없다() {
    Enrollment e = Enrollment.confirm(session(10), 1L, NOW);
    ReflectionTestUtils.setField(e, "status", EnrollmentStatus.COMPLETED);

    assertThatThrownBy(e::promote).isInstanceOf(IllegalStateException.class);
  }

  @ParameterizedTest
  @CsvSource({"1,0", "2,1", "3,1", "15,7", "20,10"})
  void 대기_상한은_정원의_절반_내림(int capacity, int expected) {
    assertThat(session(capacity).waitlistLimit()).isEqualTo(expected);
  }
}
