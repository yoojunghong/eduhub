package com.example.eduhub.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** NFR-03: 권한 규칙(실제 보안 필터 체인 + 실제 서비스·DB). 설계 6장 권한·신청·취소 행. */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.EMBEDDED)
@ActiveProfiles("test")
class AuthorizationRulesTest {

  private static final String COURSE_JSON =
      """
      {"title": "권한 테스트 과정", "category": "BACKEND"}
      """;

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private CourseRepository courseRepository;
  @Autowired private CourseSessionRepository sessionRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private EmployeeRepository employeeRepository;
  @Autowired private PasswordEncoder passwordEncoder;

  private Long courseId;
  private Long sessionId;

  @BeforeEach
  void setUp() {
    Course course = courseRepository.save(new Course("권한 규칙 과정", "BACKEND", "설명"));
    CourseSession session =
        sessionRepository.save(course.addSession(LocalDate.now().plusDays(30), "본사", 10));
    courseId = course.getId();
    sessionId = session.getId();
  }

  private static long newEmployeeId() {
    return System.nanoTime();
  }

  @Test
  @DisplayName("과정 목록·상세 조회는 토큰 없이 200")
  void courseReads_arePublic() throws Exception {
    mockMvc.perform(get("/api/v1/courses")).andExpect(status().isOk());
    mockMvc.perform(get("/api/v1/courses/" + courseId)).andExpect(status().isOk());
  }

  @Test
  @DisplayName("과정 등록: 토큰 없음 401, 사원 403, 관리자 201")
  void createCourse_adminOnly() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/courses").contentType(MediaType.APPLICATION_JSON).content(COURSE_JSON))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    mockMvc
        .perform(
            post("/api/v1/courses")
                .with(TestAuth.employee(1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(COURSE_JSON))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    mockMvc
        .perform(
            post("/api/v1/courses")
                .with(TestAuth.admin(2))
                .contentType(MediaType.APPLICATION_JSON)
                .content(COURSE_JSON))
        .andExpect(status().isCreated());
  }

  @Test
  @DisplayName("과정 수정·회차 추가·종료는 사원이면 403")
  void courseWrites_forbiddenForEmployee() throws Exception {
    mockMvc
        .perform(
            put("/api/v1/courses/" + courseId)
                .with(TestAuth.employee(1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(COURSE_JSON))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/api/v1/courses/" + courseId + "/sessions")
                .with(TestAuth.employee(1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"startDate": "%s", "place": "본사", "capacity": 10}
                    """
                        .formatted(LocalDate.now().plusDays(40))))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(post("/api/v1/courses/" + courseId + "/close").with(TestAuth.employee(1)))
        .andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("/api/v1/admin/** 는 사원이면 403")
  void adminPath_forbiddenForEmployee() throws Exception {
    mockMvc
        .perform(get("/api/v1/admin/anything").with(TestAuth.employee(1)))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  @DisplayName("없는 경로: 비로그인은 anyRequest 규칙으로 401(존재 여부를 알리지 않음), 로그인하면 404")
  void unknownPath() throws Exception {
    mockMvc.perform(get("/api/v1/no-such-path")).andExpect(status().isUnauthorized());
    mockMvc
        .perform(get("/api/v1/no-such-path").with(TestAuth.employee(1)))
        .andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("토큰 없이 신청하면 401")
  void enroll_withoutToken_401() throws Exception {
    mockMvc
        .perform(post("/api/v1/sessions/" + sessionId + "/enrollments"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  @DisplayName("사원 A 토큰으로 신청하면 저장된 employee_id는 A")
  void enroll_savesTokenEmployee() throws Exception {
    long a = newEmployeeId();
    mockMvc
        .perform(post("/api/v1/sessions/" + sessionId + "/enrollments").with(TestAuth.employee(a)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.employeeId").value(a));

    Long saved =
        jdbcTemplate.queryForObject(
            "SELECT employee_id FROM enrollment WHERE session_id = ?", Long.class, sessionId);
    assertThat(saved).isEqualTo(a);
  }

  @Test
  @DisplayName("A의 신청을 B 토큰으로 취소하면 404, A 토큰으로 취소하면 200")
  void cancel_onlyOwner() throws Exception {
    long a = newEmployeeId();
    long b = a + 1;
    String response =
        mockMvc
            .perform(
                post("/api/v1/sessions/" + sessionId + "/enrollments").with(TestAuth.employee(a)))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long enrollmentId = objectMapper.readTree(response).get("id").asLong();
    String cancelUrl = "/api/v1/enrollments/" + enrollmentId + "/cancel";

    mockMvc
        .perform(post(cancelUrl).with(TestAuth.employee(b)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ENROLLMENT_NOT_FOUND"));
    mockMvc
        .perform(post(cancelUrl).with(TestAuth.employee(a)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELED"));
  }

  private Employee saveEmployee(String no, EmployeeRole role) {
    return employeeRepository
        .findByEmployeeNo(no)
        .orElseGet(() ->
            employeeRepository.save(
                new Employee(no, no, null, passwordEncoder.encode("pw-" + no), role)));
  }

  private String login(String no) throws Exception {
    String body =
        objectMapper.writeValueAsString(java.util.Map.of("employeeNo", no, "password", "pw-" + no));
    String response =
        mockMvc
            .perform(
                post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return "Bearer " + objectMapper.readTree(response).get("accessToken").asText();
  }

  @Test
  @DisplayName("실제 서명 토큰: 사원 과정 등록 403, 관리자 201, 사원 신청은 토큰 sub가 employee_id")
  void realSignedTokens() throws Exception {
    Employee emp = saveEmployee("T-RULE-EMP", EmployeeRole.EMPLOYEE);
    saveEmployee("T-RULE-ADM", EmployeeRole.ADMIN);
    String empToken = login("T-RULE-EMP");
    String admToken = login("T-RULE-ADM");

    mockMvc
        .perform(
            post("/api/v1/courses")
                .header("Authorization", empToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(COURSE_JSON))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(
            post("/api/v1/courses")
                .header("Authorization", admToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(COURSE_JSON))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            post("/api/v1/sessions/" + sessionId + "/enrollments")
                .header("Authorization", empToken))
        .andExpect(status().isCreated());

    Long saved =
        jdbcTemplate.queryForObject(
            "SELECT employee_id FROM enrollment WHERE session_id = ?", Long.class, sessionId);
    assertThat(saved).isEqualTo(emp.getId());
  }
}
