package com.example.eduhub.auth.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.eduhub.auth.config.JwtConfig;
import com.example.eduhub.employee.domain.Employee;
import com.example.eduhub.employee.domain.EmployeeRepository;
import com.example.eduhub.employee.domain.EmployeeRole;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** NFR-03: 로그인과 토큰 검증 (보안 필터 체인 포함). */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.EMBEDDED)
@ActiveProfiles("test")
class AuthApiTest {

  private static final String PASSWORD = "test-password";
  private static final String NO = "T-AUTH-1";

  @Autowired private MockMvc mockMvc;
  @Autowired private EmployeeRepository employeeRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private ObjectMapper objectMapper;

  @Value("${eduhub.jwt.secret}")
  private String secret;

  private Employee employee;

  @BeforeEach
  void setUp() {
    employee =
        employeeRepository
            .findByEmployeeNo(NO)
            .orElseGet(() ->
                employeeRepository.save(
                    new Employee(
                        NO,
                        "테스트사원",
                        "개발팀",
                        passwordEncoder.encode(PASSWORD),
                        EmployeeRole.EMPLOYEE)));
  }

  private String body(String no, String password) throws Exception {
    return objectMapper.writeValueAsString(new LoginRequest(no, password));
  }

  private String token(JwtEncoder encoder, Instant exp) {
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer("eduhub")
            .subject(String.valueOf(employee.getId()))
            .claim("employeeNo", NO)
            .claim("name", "테스트사원")
            .claim("role", "EMPLOYEE")
            .issuedAt(exp.minusSeconds(3600))
            .expiresAt(exp)
            .build();
    return encoder
        .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
  }

  @Test
  @DisplayName("맞는 사번·비밀번호로 로그인하면 토큰을 받고, 그 토큰으로 /me가 같은 사원을 돌려준다")
  void 로그인_후_me() throws Exception {
    String response =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(NO, PASSWORD)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(3600))
            .andExpect(jsonPath("$.user.employeeNo").value(NO))
            .andExpect(jsonPath("$.user.role").value("EMPLOYEE"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    JsonNode json = objectMapper.readTree(response);
    String accessToken = json.get("accessToken").asText();

    mockMvc
        .perform(get("/api/v1/me").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.employeeId").value(employee.getId()))
        .andExpect(jsonPath("$.employeeNo").value(NO))
        .andExpect(jsonPath("$.name").value("테스트사원"))
        .andExpect(jsonPath("$.role").value("EMPLOYEE"))
        .andExpect(jsonPath("$.admin").doesNotExist());
  }

  @Test
  @DisplayName("틀린 비밀번호와 없는 사번은 둘 다 401 INVALID_CREDENTIALS이고 메시지가 같다")
  void 로그인_실패는_구분되지_않는다() throws Exception {
    String wrongPassword =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body(NO, "wrong")))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
            .andExpect(jsonPath("$.message").value("사번 또는 비밀번호가 올바르지 않습니다."))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String unknownNo =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body("NO-SUCH", PASSWORD)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    org.assertj.core.api.Assertions.assertThat(unknownNo).isEqualTo(wrongPassword);
  }

  @Test
  @DisplayName("사번 또는 비밀번호가 비어 있으면 400이다")
  void 빈_값은_400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body("", "")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test
  @DisplayName("/me에 토큰이 없으면 401 UNAUTHORIZED 본문을 돌려준다")
  void 토큰_없으면_401() throws Exception {
    mockMvc
        .perform(get("/api/v1/me"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
        .andExpect(jsonPath("$.message").value("로그인이 필요합니다."));
  }

  @Test
  @DisplayName("다른 키로 서명한 토큰은 401이다")
  void 위조_토큰은_401() throws Exception {
    String forged =
        token(
            new JwtConfig("test-only-other-secret-0123456789-abcdefg").jwtEncoder(),
            Instant.now().plusSeconds(600));
    mockMvc
        .perform(get("/api/v1/me").header("Authorization", "Bearer " + forged))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  @DisplayName("만료된 토큰(허용 오차 60초 초과)은 401이다")
  void 만료_토큰은_401() throws Exception {
    String expired = token(new JwtConfig(secret).jwtEncoder(), Instant.now().minusSeconds(180));
    mockMvc
        .perform(get("/api/v1/me").header("Authorization", "Bearer " + expired))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }
}
