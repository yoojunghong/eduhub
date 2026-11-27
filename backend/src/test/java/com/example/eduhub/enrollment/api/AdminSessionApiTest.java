package com.example.eduhub.enrollment.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.eduhub.TestAuth;
import com.example.eduhub.course.domain.Course;
import com.example.eduhub.course.domain.CourseRepository;
import com.example.eduhub.course.domain.CourseSession;
import com.example.eduhub.course.domain.CourseSessionRepository;
import com.example.eduhub.employee.domain.Employee;
import com.example.eduhub.employee.domain.EmployeeRepository;
import com.example.eduhub.employee.domain.EmployeeRole;
import com.example.eduhub.enrollment.application.EnrollmentService;
import com.example.eduhub.enrollment.domain.EnrollmentRepository;
import com.example.eduhub.enrollment.domain.EnrollmentStatus;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** FR-08: 회차 명단·수료 처리 API (실제 보안 필터 + 서비스 + DB). 설계 6장 FR-08 행. */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.EMBEDDED)
@ActiveProfiles("test")
class AdminSessionApiTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private CourseRepository courseRepository;
  @Autowired private CourseSessionRepository sessionRepository;
  @Autowired private EmployeeRepository employeeRepository;
  @Autowired private EnrollmentRepository enrollmentRepository;
  @Autowired private EnrollmentService enrollmentService;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private EntityManagerFactory entityManagerFactory;

  private static LocalDate today() {
    return LocalDate.now(ZoneId.of("Asia/Seoul"));
  }

  private static String rosterUrl(Long sessionId) {
    return "/api/v1/admin/sessions/" + sessionId + "/roster";
  }

  private static String completionUrl(Long sessionId) {
    return "/api/v1/admin/sessions/" + sessionId + "/completions";
  }

  private CourseSession newSession(int capacity, LocalDate startDate) {
    Course course = courseRepository.save(new Course("명단 과정 " + System.nanoTime(), "BACKEND", "d"));
    return sessionRepository.save(course.addSession(startDate, "본사 3층", capacity));
  }

  /** 신청·취소는 전날까지만 되므로 서비스로 만들 수 없다. 내일 회차에 신청시킨 뒤 시작일만 오늘로 옮긴다. */
  private void moveStartToToday(CourseSession session) {
    jdbcTemplate.update(
        "UPDATE course_session SET start_date = ? WHERE id = ?",
        java.sql.Date.valueOf(today()),
        session.getId());
  }

  private Employee newEmployee(String name) {
    String no = "T" + System.nanoTime();
    return employeeRepository.save(
        new Employee(
            no.substring(0, Math.min(no.length(), 20)), name, "개발1팀", "x", EmployeeRole.EMPLOYEE));
  }

  private EnrollmentStatus statusOf(Long enrollmentId) {
    return enrollmentRepository.findById(enrollmentId).orElseThrow().getStatus();
  }

  private static String body(List<?> ids, String outcome) {
    String idsJson = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    String outcomeJson = outcome == null ? "" : ",\"outcome\":\"" + outcome + "\"";
    return "{\"employeeIds\":[" + idsJson + "]" + outcomeJson + "}";
  }

  private org.springframework.test.web.servlet.ResultActions complete(
      Long sessionId, List<?> ids, String outcome) throws Exception {
    return mockMvc.perform(
        post(completionUrl(sessionId))
            .with(TestAuth.admin(1))
            .contentType(MediaType.APPLICATION_JSON)
            .content(body(ids, outcome)));
  }

  @Test
  @DisplayName("명단: 확정 2·대기 2·취소(확정 취소 2) -> 확정 2(사번·이름), 대기 2(순번 순), 취소 없음")
  void roster_confirmedWaitingCanceled() throws Exception {
    // 시작 전날 회차: 확정자 취소는 되지만(D-02) 승급은 없다(D-03)
    CourseSession session = newSession(4, today().plusDays(1));
    Employee a = newEmployee("김사원");
    Employee b = newEmployee("이사원");
    Employee x = newEmployee("취소1");
    Employee y = newEmployee("취소2");
    Employee w1 = newEmployee("대기1");
    Employee w2 = newEmployee("대기2");
    enrollmentService.enroll(session.getId(), a.getId());
    enrollmentService.enroll(session.getId(), b.getId());
    Long xId = enrollmentService.enroll(session.getId(), x.getId()).id();
    Long yId = enrollmentService.enroll(session.getId(), y.getId()).id();
    enrollmentService.enroll(session.getId(), w1.getId());
    enrollmentService.enroll(session.getId(), w2.getId());
    enrollmentService.cancel(xId, x.getId());
    enrollmentService.cancel(yId, y.getId());

    mockMvc
        .perform(get(rosterUrl(session.getId())).with(TestAuth.admin(1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sessionId").value(session.getId()))
        .andExpect(jsonPath("$.courseId").value(session.getCourse().getId()))
        .andExpect(jsonPath("$.courseTitle").value(session.getCourse().getTitle()))
        .andExpect(jsonPath("$.place").value("본사 3층"))
        .andExpect(jsonPath("$.capacity").value(4))
        .andExpect(jsonPath("$.sessionStatus").value("OPEN"))
        .andExpect(jsonPath("$.completionOpen").value(false))
        .andExpect(jsonPath("$.confirmed.length()").value(2))
        .andExpect(jsonPath("$.confirmed[0].employeeNo").value(a.getEmployeeNo()))
        .andExpect(jsonPath("$.confirmed[0].name").value("김사원"))
        .andExpect(jsonPath("$.confirmed[0].department").value("개발1팀"))
        .andExpect(jsonPath("$.confirmed[0].status").value("CONFIRMED"))
        .andExpect(jsonPath("$.confirmed[0].waitingNumber").doesNotExist())
        .andExpect(jsonPath("$.confirmed[1].name").value("이사원"))
        .andExpect(jsonPath("$.waiting.length()").value(2))
        .andExpect(jsonPath("$.waiting[0].name").value("대기1"))
        .andExpect(jsonPath("$.waiting[0].status").value("WAITING"))
        .andExpect(jsonPath("$.waiting[0].waitingNumber").value(1))
        .andExpect(jsonPath("$.waiting[1].name").value("대기2"))
        .andExpect(jsonPath("$.waiting[1].waitingNumber").value(2));
  }

  @Test
  @DisplayName("명단 조회 전체 SQL은 2번 이하 (회차+과정 1번, 명단 1번)")
  void roster_queryCount() throws Exception {
    CourseSession session = newSession(4, today().plusDays(30));
    long base = System.nanoTime();
    for (int i = 0; i < 6; i++) {
      enrollmentService.enroll(session.getId(), base + i);
    }
    Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    statistics.setStatisticsEnabled(true);
    statistics.clear();

    mockMvc
        .perform(get(rosterUrl(session.getId())).with(TestAuth.admin(1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.confirmed.length()").value(4))
        .andExpect(jsonPath("$.waiting.length()").value(2));

    assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(2);
  }

  @Test
  @DisplayName("명단: 사원 행이 없는 신청은 사번·이름 null로 나오고 오류가 없다")
  void roster_missingEmployeeRow() throws Exception {
    CourseSession session = newSession(5, today().plusDays(30));
    long ghost = System.nanoTime();
    enrollmentService.enroll(session.getId(), ghost);

    mockMvc
        .perform(get(rosterUrl(session.getId())).with(TestAuth.admin(1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.confirmed.length()").value(1))
        .andExpect(jsonPath("$.confirmed[0].employeeId").value(ghost))
        .andExpect(jsonPath("$.confirmed[0].employeeNo").doesNotExist())
        .andExpect(jsonPath("$.confirmed[0].name").doesNotExist());
  }

  @Test
  @DisplayName("사원 토큰은 명단·처리 모두 403, 토큰 없으면 401")
  void employee_forbidden() throws Exception {
    CourseSession session = newSession(5, today().plusDays(30));
    mockMvc
        .perform(get(rosterUrl(session.getId())).with(TestAuth.employee(1)))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post(completionUrl(session.getId()))
                .with(TestAuth.employee(1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(List.of(1L), "COMPLETED")))
        .andExpect(status().isForbidden());
    mockMvc.perform(get(rosterUrl(session.getId()))).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("없는 회차는 명단·처리 모두 404 SESSION_NOT_FOUND")
  void sessionNotFound() throws Exception {
    mockMvc
        .perform(get(rosterUrl(999_999_999L)).with(TestAuth.admin(1)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
    complete(999_999_999L, List.of(1L), "COMPLETED")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
  }

  @Test
  @DisplayName("시작일이 오늘이면 completionOpen=true, 확정 2명 COMPLETED 처리 -> 상태 COMPLETED")
  void complete_two() throws Exception {
    CourseSession session = newSession(5, today().plusDays(1));
    long a = System.nanoTime();
    long b = a + 1;
    Long aId = enrollmentService.enroll(session.getId(), a).id();
    Long bId = enrollmentService.enroll(session.getId(), b).id();
    moveStartToToday(session);

    mockMvc
        .perform(get(rosterUrl(session.getId())).with(TestAuth.admin(1)))
        .andExpect(jsonPath("$.completionOpen").value(true));
    complete(session.getId(), List.of(a, b, a), "COMPLETED")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.processed.length()").value(2))
        .andExpect(jsonPath("$.failed.length()").value(0));

    assertThat(statusOf(aId)).isEqualTo(EnrollmentStatus.COMPLETED);
    assertThat(statusOf(bId)).isEqualTo(EnrollmentStatus.COMPLETED);

    mockMvc
        .perform(get(rosterUrl(session.getId())).with(TestAuth.admin(1)))
        .andExpect(jsonPath("$.confirmed.length()").value(2))
        .andExpect(jsonPath("$.confirmed[0].status").value("COMPLETED"));

    // FR-07: 내 내역에 COMPLETED, cancelable=false, status 필터
    mockMvc
        .perform(
            get("/api/v1/me/enrollments").param("status", "COMPLETED").with(TestAuth.employee(a)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].status").value("COMPLETED"))
        .andExpect(jsonPath("$[0].cancelable").value(false));
    mockMvc
        .perform(
            get("/api/v1/me/enrollments").param("status", "CONFIRMED").with(TestAuth.employee(a)))
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  @DisplayName("outcome=INCOMPLETE는 상태 INCOMPLETE, 같은 사원을 다시 처리하면 ALREADY_PROCESSED")
  void complete_incomplete_thenReprocess() throws Exception {
    CourseSession session = newSession(5, today().plusDays(1));
    long a = System.nanoTime();
    Long aId = enrollmentService.enroll(session.getId(), a).id();
    moveStartToToday(session);

    complete(session.getId(), List.of(a), "INCOMPLETE")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.processed[0]").value(a));
    assertThat(statusOf(aId)).isEqualTo(EnrollmentStatus.INCOMPLETE);
    mockMvc
        .perform(get("/api/v1/me/enrollments").with(TestAuth.employee(a)))
        .andExpect(jsonPath("$[0].status").value("INCOMPLETE"))
        .andExpect(jsonPath("$[0].cancelable").value(false));

    complete(session.getId(), List.of(a), "COMPLETED")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.processed.length()").value(0))
        .andExpect(jsonPath("$.failed[0].employeeId").value(a))
        .andExpect(jsonPath("$.failed[0].reason").value("ALREADY_PROCESSED"));
    assertThat(statusOf(aId)).isEqualTo(EnrollmentStatus.INCOMPLETE);
  }

  @Test
  @DisplayName("확정 1 + 대기 1 + 신청 없음 1 + 이미 수료 1 -> processed 1, failed 3(사유 각각)")
  void complete_mixedFailures() throws Exception {
    CourseSession session = newSession(4, today().plusDays(1));
    long base = System.nanoTime();
    long confirmed = base;
    long done = base + 1;
    long waiting = base + 4;
    long none = base + 5;
    Long confirmedId = enrollmentService.enroll(session.getId(), confirmed).id();
    Long doneId = enrollmentService.enroll(session.getId(), done).id();
    enrollmentService.enroll(session.getId(), base + 2);
    enrollmentService.enroll(session.getId(), base + 3);
    Long waitingId = enrollmentService.enroll(session.getId(), waiting).id();
    assertThat(statusOf(waitingId)).isEqualTo(EnrollmentStatus.WAITING);
    moveStartToToday(session);
    // 이미 수료: 같은 처리 요청을 먼저 한 번 보낸다
    complete(session.getId(), List.of(done), "COMPLETED")
        .andExpect(jsonPath("$.processed.length()").value(1));
    assertThat(statusOf(doneId)).isEqualTo(EnrollmentStatus.COMPLETED);

    complete(session.getId(), List.of(confirmed, waiting, none, done), "COMPLETED")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.processed.length()").value(1))
        .andExpect(jsonPath("$.processed[0]").value(confirmed))
        .andExpect(jsonPath("$.failed.length()").value(3))
        .andExpect(jsonPath("$.failed[0].employeeId").value(waiting))
        .andExpect(jsonPath("$.failed[0].reason").value("NOT_CONFIRMED"))
        .andExpect(jsonPath("$.failed[1].employeeId").value(none))
        .andExpect(jsonPath("$.failed[1].reason").value("NOT_ENROLLED"))
        .andExpect(jsonPath("$.failed[2].employeeId").value(done))
        .andExpect(jsonPath("$.failed[2].reason").value("ALREADY_PROCESSED"));

    assertThat(statusOf(confirmedId)).isEqualTo(EnrollmentStatus.COMPLETED);
    assertThat(statusOf(waitingId)).isEqualTo(EnrollmentStatus.WAITING);
  }

  @Test
  @DisplayName("취소 후 재신청해 CANCELED+CONFIRMED 행을 가진 사원도 처리된다")
  void complete_canceledThenReenrolled() throws Exception {
    CourseSession session = newSession(5, today().plusDays(1));
    long a = System.nanoTime();
    Long first = enrollmentService.enroll(session.getId(), a).id();
    enrollmentService.cancel(first, a);
    Long second = enrollmentService.enroll(session.getId(), a).id();
    moveStartToToday(session);

    complete(session.getId(), List.of(a), "COMPLETED")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.processed[0]").value(a))
        .andExpect(jsonPath("$.failed.length()").value(0));

    assertThat(statusOf(first)).isEqualTo(EnrollmentStatus.CANCELED);
    assertThat(statusOf(second)).isEqualTo(EnrollmentStatus.COMPLETED);
  }

  @Test
  @DisplayName("취소만 있는 사원은 NOT_ENROLLED")
  void complete_onlyCanceled_notEnrolled() throws Exception {
    CourseSession session = newSession(5, today().plusDays(1));
    long a = System.nanoTime();
    Long id = enrollmentService.enroll(session.getId(), a).id();
    enrollmentService.cancel(id, a);
    moveStartToToday(session);

    complete(session.getId(), List.of(a), "COMPLETED")
        .andExpect(jsonPath("$.failed[0].reason").value("NOT_ENROLLED"));
    assertThat(statusOf(id)).isEqualTo(EnrollmentStatus.CANCELED);
  }

  @Test
  @DisplayName("시작일 전이면 409 COMPLETION_NOT_OPEN이고 아무것도 바뀌지 않는다")
  void complete_beforeStart_409() throws Exception {
    CourseSession session = newSession(5, today().plusDays(1));
    long a = System.nanoTime();
    Long aId = enrollmentService.enroll(session.getId(), a).id();

    complete(session.getId(), List.of(a), "COMPLETED")
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("COMPLETION_NOT_OPEN"));

    assertThat(statusOf(aId)).isEqualTo(EnrollmentStatus.CONFIRMED);
  }

  @Test
  @DisplayName("빈 목록·101개·outcome 없음·null/0 원소는 400 INVALID_REQUEST")
  void invalidRequests_400() throws Exception {
    CourseSession session = newSession(5, today().plusDays(1));
    moveStartToToday(session);
    List<Long> over = LongStream.rangeClosed(1, 101).boxed().toList();
    List<Object> withNull = new ArrayList<>();
    withNull.add(null);

    for (String json :
        List.of(
            body(List.of(), "COMPLETED"),
            body(over, "COMPLETED"),
            body(List.of(1L), null),
            body(withNull, "COMPLETED"),
            body(List.of(0L), "COMPLETED"))) {
      mockMvc
          .perform(
              post(completionUrl(session.getId()))
                  .with(TestAuth.admin(1))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(json))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
  }

  @Test
  @DisplayName("employeeIds 정확히 100개는 400이 아니다")
  void exactlyHundred_ok() throws Exception {
    CourseSession session = newSession(5, today().plusDays(1));
    moveStartToToday(session);
    List<Long> hundred = LongStream.rangeClosed(1, 100).boxed().toList();

    complete(session.getId(), hundred, "COMPLETED")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.failed.length()").value(100));
  }
}
