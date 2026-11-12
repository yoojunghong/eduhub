package com.example.eduhub.course.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.eduhub.course.api.CourseCreateRequest;
import com.example.eduhub.course.api.CourseDetailResponse;
import com.example.eduhub.course.api.CourseUpdateRequest;
import com.example.eduhub.course.api.SessionCreateRequest;
import com.example.eduhub.course.api.SessionResponse;
import com.example.eduhub.course.domain.Course;
import com.example.eduhub.course.domain.CourseClosedException;
import com.example.eduhub.course.domain.CourseNotFoundException;
import com.example.eduhub.course.domain.CourseRepository;
import com.example.eduhub.course.domain.CourseSession;
import com.example.eduhub.course.domain.CourseStatus;
import com.example.eduhub.course.domain.SessionStatus;
import com.example.eduhub.enrollment.domain.Enrollment;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

// 실제 PostgreSQL에 저장한 뒤 flush·clear 하고 다시 읽어서, 변경 감지로 DB에 반영됐는지 확인한다
@DataJpaTest
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.EMBEDDED)
@ActiveProfiles("test")
@Import(CourseCommandService.class)
class CourseCommandServiceTest {

  private static final LocalDate NEXT_MONTH = LocalDate.now().plusMonths(1);

  @Autowired private CourseCommandService service;
  @Autowired private CourseRepository courseRepository;
  @Autowired private TestEntityManager em;

  @Test
  @DisplayName("과정을 등록하면 OPEN 상태로 저장된다")
  void create_savesOpenCourse() {
    CourseDetailResponse created =
        service.create(new CourseCreateRequest("Spring Boot 입문", "BACKEND", "기초"));
    flushAndClear();

    Course found = courseRepository.findById(created.id()).orElseThrow();
    assertThat(found.getTitle()).isEqualTo("Spring Boot 입문");
    assertThat(found.getStatus()).isEqualTo(CourseStatus.OPEN);
    assertThat(created.sessions()).isEmpty();
  }

  @Test
  @DisplayName("과정을 수정하면 바뀐 값이 저장된다")
  void update_changesFields() {
    Long id = createCourse();

    service.update(id, new CourseUpdateRequest("JPA 실전", "BACKEND", "N+1"));
    flushAndClear();

    Course found = courseRepository.findById(id).orElseThrow();
    assertThat(found.getTitle()).isEqualTo("JPA 실전");
    assertThat(found.getDescription()).isEqualTo("N+1");
  }

  @Test
  @DisplayName("종료된 과정은 수정할 수 없다")
  void update_closedCourse_throws() {
    Long id = createCourse();
    service.close(id);

    assertThatThrownBy(() -> service.update(id, new CourseUpdateRequest("새 제목", "BACKEND", null)))
        .isInstanceOf(CourseClosedException.class);
  }

  @Test
  @DisplayName("없는 과정을 수정하면 CourseNotFoundException")
  void update_notFound_throws() {
    assertThatThrownBy(() -> service.update(999L, new CourseUpdateRequest("t", "BACKEND", null)))
        .isInstanceOf(CourseNotFoundException.class);
  }

  @Test
  @DisplayName("회차를 추가하면 id가 붙은 회차를 돌려주고 과정에 저장된다")
  void addSession_savesSession() {
    Long id = createCourse();

    SessionResponse session =
        service.addSession(id, new SessionCreateRequest(NEXT_MONTH, "본사 3층", 20));
    flushAndClear();

    assertThat(session.id()).isNotNull();
    assertThat(session.capacity()).isEqualTo(20);
    assertThat(courseRepository.findWithSessionsById(id).orElseThrow().getSessions()).hasSize(1);
  }

  @Test
  @DisplayName("종료된 과정에는 회차를 추가할 수 없다")
  void addSession_closedCourse_throws() {
    Long id = createCourse();
    service.close(id);

    assertThatThrownBy(
            () -> service.addSession(id, new SessionCreateRequest(NEXT_MONTH, "본사 3층", 20)))
        .isInstanceOf(CourseClosedException.class);
  }

  @Test
  @DisplayName("과정을 종료하면 회차도 모두 종료된다")
  void close_closesCourseAndSessions() {
    Long id = createCourse();
    service.addSession(id, new SessionCreateRequest(NEXT_MONTH, "본사 3층", 20));
    service.addSession(id, new SessionCreateRequest(NEXT_MONTH.plusWeeks(1), "본사 4층", 10));
    flushAndClear();

    CourseDetailResponse closed = service.close(id);
    flushAndClear();

    assertThat(closed.status()).isEqualTo("CLOSED");
    Course found = courseRepository.findWithSessionsById(id).orElseThrow();
    assertThat(found.getStatus()).isEqualTo(CourseStatus.CLOSED);
    assertThat(found.getSessions())
        .extracting(CourseSession::getStatus)
        .containsOnly(SessionStatus.CLOSED);
  }

  @Test
  @DisplayName("이미 종료된 과정을 다시 종료해도 오류가 나지 않는다")
  void close_twice_isIdempotent() {
    Long id = createCourse();
    service.close(id);

    assertThat(service.close(id).status()).isEqualTo("CLOSED");
  }

  @Test
  @DisplayName("확정 신청이 있는 회차의 update 응답 remainingSeats는 정원에서 확정 수를 뺀 값이다 (close도 같다)")
  void updateAndClose_remainingSeatsReflectConfirmed() {
    Long id = createCourse();
    Long sessionId = service.addSession(id, new SessionCreateRequest(NEXT_MONTH, "본사 3층", 5)).id();
    flushAndClear();
    CourseSession session = em.find(CourseSession.class, sessionId);
    OffsetDateTime now = OffsetDateTime.now();
    em.persist(Enrollment.confirm(session, 1L, now));
    em.persist(Enrollment.confirm(session, 2L, now));
    em.persist(Enrollment.waiting(session, 3L, now));
    flushAndClear();

    CourseDetailResponse updated =
        service.update(id, new CourseUpdateRequest("JPA 실전", "BACKEND", null));
    flushAndClear();
    CourseDetailResponse closed = service.close(id);

    assertThat(updated.sessions()).extracting(SessionResponse::remainingSeats).containsExactly(3);
    assertThat(closed.sessions()).extracting(SessionResponse::remainingSeats).containsExactly(3);
  }

  private Long createCourse() {
    Long id = service.create(new CourseCreateRequest("Spring Boot 입문", "BACKEND", null)).id();
    flushAndClear();
    return id;
  }

  private void flushAndClear() {
    em.flush();
    em.clear();
  }
}
