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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
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
@SpringBootTest
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

  /** 신청은 전날까지만 되므로, 내일 회차를 만들어 신청시킨 뒤 시작일을 오늘로 옮긴다. */
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

  private static String body(List<Long> ids, String outcome) {
    String idsJson = ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    String outcomeJson = outcome == null ? "" : ",\"outcome\":\"" + outcome + "\"";
    return "{\"employeeIds\":[" + idsJson + "]" + outcomeJson + "}";
  }

  @Test
  @DisplayName("명단: 확정 2·대기 2·취소 1 -> 확정 2(사번·이름), 대기 2(순번 순), 취소 없음")
  void roster_confirmedWaitingCanceled() throws Exception {
    CourseSession session = newSession(2, today().plusDays(30));
    Employee a = newEmployee("김사원");
    Employee b = newEmployee("이사원");
    Employee w1 = newEmployee("대기1");
    Employee w2 = newEmployee("대기2");
    Employee c = newEmployee("취소자");
    Long aId = enrollmentService.enroll(session.getId(), a.getId()).id();
    enrollmentService.enroll(session.getId(), b.getId());
    enrollmentService.enroll(session.getId(), w1.getId());
    // 대기 한도(정원의 50%)가 1명이라 두 번째 대기 행은 직접 넣는다
    jdbcTemplate.update(
        "INSERT INTO enrollment (session_id, course_id, employee_id, status, enrolled_at) "
            + "SELECT session_id, course_id, ?, 'WAITING', now() FROM enrollment WHERE id = ?",
        w2.getId(),
        aId);
    jdbcTemplate.update(
        "INSERT INTO enrollment (session_id, course_id, employee_id, status, enrolled_at) "
            + "SELECT session_id, course_id, ?, 'CANCELED', now() FROM enrollment WHERE id = ?",
        c.getId(),
        aId);

    mockMvc
        .perform(get(rosterUrl(session.getId())).with(TestAuth.admin(1)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sessionId").value(session.getId()))
        .andExpect(jsonPath("$.courseId").value(session.getCourse().getId()))
        .andExpect(jsonPath("$.courseTitle").value(session.getCourse().getTitle()))
        .andExpect(jsonPath("$.place").value("본사 3층"))
        .andExpect(jsonPath("$.capacity").value(2))
        .andExpect(jsonPath("$.sessionStatus").value("OPEN"))
        .andExpect(jsonPath("$.completionOpen").value(false))
        .andExpect(jsonPath("$.confirmed.length()").value(2))
        .andExpect(jsonPath("$.confirmed[0].employeeNo").value(a.getEmployeeNo()))
        .andExpect(jsonPath("$.confirmed[0].name").value("김사원"))
        .andExpect(jsonPath("$.confirmed[0].department").value("개발1팀"))
        .andExpect(jsonPath("$.confirmed[0].status").value("CONFIRMED"))
        .andExpect(jsonPath("$.confirmed[1].name").value("이사원"))
        .andExpect(jsonPath("$.waiting.length()").value(2))
        .andExpect(jsonPath("$.waiting[0].name").value("대기1"))
        .andExpect(jsonPath("$.waiting[0].waitingNumber").value(1))
        .andExpect(jsonPath("$.waiting[1].name").value("대기2"))
        .andExpect(jsonPath("$.waiting[1].waitingNumber").value(2));
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
    mockMvc
        .perform(
            post(completionUrl(999_999_999L))
                .with(TestAuth.admin(1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(List.of(1L), "COMPLETED")))
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
    mockMvc
        .perform(
            post(completionUrl(session.getId()))
                .with(TestAuth.admin(1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(List.of(a, b, a), "COMPLETED")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.processed.length()").value(2))
        .andExpect(jsonPath("$.failed.length()").value(0));

    assertThat(statusOf(aId)).isEqualTo(EnrollmentStatus.COMPLETED);
    assertThat(statusOf(bId)).isEqualTo(EnrollmentStatus.COMPLETED);

    // 처리 후 명단에는 COMPLETED로 남는다
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
  @DisplayName("outcome=INCOMPLETE는 상태 INCOMPLETE")
  void complete_incomplete() throws Exception {
    CourseSession session = newSession(5, today().plusDays(1));
    long a = System.nanoTime();
    Long aId = enrollmentService.enroll(session.getId(), a).id();
    moveStartToToday(session);

    mockMvc
        .perform(
            post(completionUrl(session.getId()))
                .with(TestAuth.admin(1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(List.of(a), "INCOMPLETE")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.processed[0]").value(a));

    assertThat(statusOf(aId)).isEqualTo(EnrollmentStatus.INCOMPLETE);
    mockMvc
        .perform(get("/api/v1/me/enrollments").with(TestAuth.employee(a)))
        .andExpect(jsonPath("$[0].status").value("INCOMPLETE"))
        .andExpect(jsonPath("$[0].cancelable").value(false));
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
    jdbcTemplate.update("UPDATE enrollment SET status = 'COMPLETED' WHERE id = ?", doneId);

    mockMvc
        .perform(
            post(completionUrl(session.getId()))
                .with(TestAuth.admin(1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(List.of(confirmed, waiting, none, done), "COMPLETED")))
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
  @DisplayName("시작일 전이면 409 COMPLETION_NOT_OPEN이고 아무것도 바뀌지 않는다")
  void complete_beforeStart_409() throws Exception {
    CourseSession session = newSession(5, today().plusDays(1));
    long a = System.nanoTime();
    Long aId = enrollmentService.enroll(session.getId(), a).id();

    mockMvc
        .perform(
            post(completionUrl(session.getId()))
                .with(TestAuth.admin(1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(body(List.of(a), "COMPLETED")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("COMPLETION_NOT_OPEN"));

    assertThat(statusOf(aId)).isEqualTo(EnrollmentStatus.CONFIRMED);
  }

  @Test
  @DisplayName("빈 목록·101개·outcome 없음은 400 INVALID_REQUEST")
  void invalidRequests_400() throws Exception {
    CourseSession session = newSession(5, today().plusDays(1));
    moveStartToToday(session);
    List<Long> over = LongStream.rangeClosed(1, 101).boxed().toList();

    for (String json :
        List.of(body(List.of(), "COMPLETED"), body(over, "COMPLETED"), body(List.of(1L), null))) {
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
}
