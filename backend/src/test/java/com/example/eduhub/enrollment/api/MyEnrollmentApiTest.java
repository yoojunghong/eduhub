package com.example.eduhub.enrollment.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.eduhub.TestAuth;
import com.example.eduhub.course.domain.Course;
import com.example.eduhub.course.domain.CourseRepository;
import com.example.eduhub.course.domain.CourseSession;
import com.example.eduhub.course.domain.CourseSessionRepository;
import com.example.eduhub.enrollment.application.EnrollmentService;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.time.ZoneId;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** FR-07: 내 신청 내역 API (실제 보안 필터 + 서비스 + DB). 설계 6장 FR-07 행. */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.EMBEDDED)
@ActiveProfiles("test")
class MyEnrollmentApiTest {

  private static final String URL = "/api/v1/me/enrollments";

  @Autowired private MockMvc mockMvc;
  @Autowired private CourseRepository courseRepository;
  @Autowired private CourseSessionRepository sessionRepository;
  @Autowired private EnrollmentService enrollmentService;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private EntityManagerFactory entityManagerFactory;

  private static long newEmployeeId() {
    return System.nanoTime();
  }

  private static LocalDate today() {
    return LocalDate.now(ZoneId.of("Asia/Seoul"));
  }

  private CourseSession newSession(int capacity, LocalDate startDate) {
    Course course = courseRepository.save(new Course("내역 과정 " + System.nanoTime(), "BACKEND", "d"));
    return sessionRepository.save(course.addSession(startDate, "본사 3층", capacity));
  }

  @Test
  @DisplayName("내 신청만 최근 신청순으로 나온다")
  void onlyMine_recentFirst() throws Exception {
    long a = newEmployeeId();
    long b = a + 1;
    CourseSession s1 = newSession(10, today().plusDays(30));
    CourseSession s2 = newSession(10, today().plusDays(30));
    Long first = enrollmentService.enroll(s1.getId(), a).id();
    enrollmentService.enroll(s1.getId(), b);
    Long second = enrollmentService.enroll(s2.getId(), a).id();

    mockMvc
        .perform(get(URL).with(TestAuth.employee(a)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].enrollmentId").value(second))
        .andExpect(jsonPath("$[1].enrollmentId").value(first))
        .andExpect(jsonPath("$[0].sessionId").value(s2.getId()))
        .andExpect(jsonPath("$[0].courseTitle").value(s2.getCourse().getTitle()))
        .andExpect(jsonPath("$[0].place").value("본사 3층"))
        .andExpect(jsonPath("$[0].status").value("CONFIRMED"))
        .andExpect(jsonPath("$[0].waitingNumber").doesNotExist())
        .andExpect(jsonPath("$[0].enrolledAt").value(org.hamcrest.Matchers.endsWith("+09:00")));
  }

  @Test
  @DisplayName("status=WAITING은 대기만, 앞사람이 취소하면 waitingNumber가 당겨진다")
  void waitingFilter_andNumberShift() throws Exception {
    long base = newEmployeeId();
    CourseSession session = newSession(4, today().plusDays(30));
    Long firstConfirmed = null;
    for (int i = 0; i < 4; i++) {
      Long id = enrollmentService.enroll(session.getId(), base + i).id();
      if (i == 0) {
        firstConfirmed = id;
      }
    }
    long w1 = base + 10;
    long w2 = base + 11;
    enrollmentService.enroll(session.getId(), w1);
    enrollmentService.enroll(session.getId(), w2);

    mockMvc
        .perform(get(URL).param("status", "WAITING").with(TestAuth.employee(w2)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].status").value("WAITING"))
        .andExpect(jsonPath("$[0].waitingNumber").value(2));

    // 확정자 취소 -> w1 승급, w2는 1번으로
    enrollmentService.cancel(firstConfirmed, base);

    mockMvc
        .perform(get(URL).param("status", "WAITING").with(TestAuth.employee(w2)))
        .andExpect(jsonPath("$.length()").value(1))
        .andExpect(jsonPath("$[0].waitingNumber").value(1));
    mockMvc
        .perform(get(URL).param("status", "WAITING").with(TestAuth.employee(w1)))
        .andExpect(jsonPath("$.length()").value(0));
    mockMvc
        .perform(get(URL).param("status", "CONFIRMED").with(TestAuth.employee(w1)))
        .andExpect(jsonPath("$.length()").value(1));
  }

  @Test
  @DisplayName("cancelable: 확정·대기 + 시작 전날 true, 시작일 당일·취소 false")
  void cancelable() throws Exception {
    long a = newEmployeeId();
    long filler = a + 1;
    // 확정, 대기: 시작 전날(내일)
    CourseSession confirmedSession = newSession(1, today().plusDays(1));
    CourseSession waitingSession = newSession(2, today().plusDays(1));
    enrollmentService.enroll(waitingSession.getId(), filler);
    enrollmentService.enroll(waitingSession.getId(), filler + 1);
    enrollmentService.enroll(confirmedSession.getId(), a);
    // 대기: 정원 1짜리는 대기 한도 0이므로 정원 2 회차를 채운 뒤 a가 대기
    Long waitingId = enrollmentService.enroll(waitingSession.getId(), a).id();
    // 취소됨
    CourseSession canceledSession = newSession(10, today().plusDays(30));
    Long canceledId = enrollmentService.enroll(canceledSession.getId(), a).id();
    enrollmentService.cancel(canceledId, a);
    // 시작일 당일: 내일 회차에 신청한 뒤 시작일을 오늘로 바꾼다
    CourseSession todaySession = newSession(10, today().plusDays(1));
    Long todayId = enrollmentService.enroll(todaySession.getId(), a).id();
    jdbcTemplate.update(
        "UPDATE course_session SET start_date = ? WHERE id = ?",
        java.sql.Date.valueOf(today()),
        todaySession.getId());

    String body =
        mockMvc
            .perform(get(URL).with(TestAuth.employee(a)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    var tree = new com.fasterxml.jackson.databind.ObjectMapper().readTree(body);
    java.util.Map<Long, com.fasterxml.jackson.databind.JsonNode> byId = new java.util.HashMap<>();
    tree.forEach(n -> byId.put(n.get("enrollmentId").asLong(), n));

    assertThat(byId).hasSize(4);
    assertThat(byId.get(waitingId).get("status").asText()).isEqualTo("WAITING");
    assertThat(byId.get(waitingId).get("cancelable").asBoolean()).isTrue();
    long confirmedCount =
        byId.values().stream()
            .filter(n -> n.get("sessionId").asLong() == confirmedSession.getId())
            .filter(n -> n.get("status").asText().equals("CONFIRMED"))
            .filter(n -> n.get("cancelable").asBoolean())
            .count();
    assertThat(confirmedCount).isEqualTo(1);
    assertThat(byId.get(canceledId).get("status").asText()).isEqualTo("CANCELED");
    assertThat(byId.get(canceledId).get("cancelable").asBoolean()).isFalse();
    assertThat(byId.get(todayId).get("status").asText()).isEqualTo("CONFIRMED");
    assertThat(byId.get(todayId).get("cancelable").asBoolean()).isFalse();
  }

  @Test
  @DisplayName("토큰 없이 조회하면 401")
  void withoutToken_401() throws Exception {
    mockMvc
        .perform(get(URL))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  @DisplayName("잘못된 status는 400 INVALID_REQUEST")
  void invalidStatus_400() throws Exception {
    mockMvc
        .perform(get(URL).param("status", "NOPE").with(TestAuth.employee(1)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test
  @DisplayName("신청이 5건이어도 내역 조회 SQL은 1번")
  void queryCount_isOne() throws Exception {
    long a = newEmployeeId();
    for (int i = 0; i < 5; i++) {
      enrollmentService.enroll(newSession(10, today().plusDays(30)).getId(), a);
    }
    Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
    statistics.setStatisticsEnabled(true);
    statistics.clear();

    mockMvc
        .perform(get(URL).with(TestAuth.employee(a)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(5));

    assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
  }
}
