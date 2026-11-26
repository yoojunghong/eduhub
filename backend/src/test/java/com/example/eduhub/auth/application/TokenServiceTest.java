package com.example.eduhub.auth.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.eduhub.auth.config.JwtConfig;
import com.example.eduhub.employee.domain.Employee;
import com.example.eduhub.employee.domain.EmployeeRole;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

class TokenServiceTest {

  private static final String SECRET = "test-only-secret-for-token-service-0123456789";

  // 디코더의 만료 검사와 충돌하지 않도록 고정 시각을 현재에 가깝게 잡는다
  private final Clock clock = Clock.fixed(Instant.now().minusSeconds(60), ZoneId.of("Asia/Seoul"));
  private final JwtConfig jwtConfig = new JwtConfig(SECRET);
  private final TokenService tokenService = new TokenService(jwtConfig.jwtEncoder(), clock, 60);

  @Test
  void 발급한_토큰을_디코더로_검증하면_클레임이_담겨_있다() {
    Employee employee = new Employee("E1001", "김사원", "교육팀", "hash", EmployeeRole.ADMIN);
    ReflectionTestUtils.setField(employee, "id", 7L);

    Jwt jwt = jwtConfig.jwtDecoder().decode(tokenService.issue(employee));

    assertThat(jwt.getClaimAsString("iss")).isEqualTo("eduhub");
    assertThat(jwt.getSubject()).isEqualTo("7");
    assertThat(jwt.getClaimAsString("employeeNo")).isEqualTo("E1001");
    assertThat(jwt.getClaimAsString("name")).isEqualTo("김사원");
    assertThat(jwt.getClaimAsString("role")).isEqualTo("ADMIN");
    assertThat(jwt.getIssuedAt())
        .isEqualTo(clock.instant().truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
    assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt()))
        .isEqualTo(Duration.ofMinutes(60));
  }

  @Test
  void ttlSeconds는_3600() {
    assertThat(tokenService.ttlSeconds()).isEqualTo(3600);
  }
}
