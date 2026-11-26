package com.example.eduhub.enrollment.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.eduhub.course.domain.Course;
import com.example.eduhub.course.domain.CourseSession;
import com.example.eduhub.enrollment.domain.Enrollment;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EnrollmentResponseTest {

  @Test
  @DisplayName("enrolledAt은 UTC로 만든 값도 같은 시각의 KST(+09:00)로 나간다")
  void from_enrolledAtIsKst() {
    CourseSession session =
        new Course("과정", "BACKEND", null).addSession(LocalDate.now().plusDays(10), "A동", 10);
    OffsetDateTime utc = OffsetDateTime.of(2026, 11, 26, 0, 30, 0, 0, ZoneOffset.UTC);
    Enrollment enrollment = Enrollment.confirm(session, 1L, utc);

    OffsetDateTime result = EnrollmentResponse.from(enrollment).enrolledAt();

    assertThat(result.getOffset()).isEqualTo(ZoneOffset.ofHours(9));
    assertThat(result.toInstant()).isEqualTo(utc.toInstant());
    assertThat(result.getHour()).isEqualTo(9);
  }
}
