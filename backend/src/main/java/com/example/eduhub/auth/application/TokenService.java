package com.example.eduhub.auth.application;

import com.example.eduhub.employee.domain.Employee;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** 액세스 토큰 발급. 발급 시각은 주입된 Clock을 쓴다. */
@Service
public class TokenService {

  private final JwtEncoder encoder;
  private final Clock clock;
  private final Duration ttl;

  public TokenService(
      JwtEncoder encoder, Clock clock, @Value("${eduhub.jwt.ttl-minutes:60}") long ttlMinutes) {
    if (ttlMinutes < 1) {
      throw new IllegalStateException("eduhub.jwt.ttl-minutes는 1 이상이어야 합니다: " + ttlMinutes);
    }
    this.encoder = encoder;
    this.clock = clock;
    this.ttl = Duration.ofMinutes(ttlMinutes);
  }

  public String issue(Employee employee) {
    Instant now = clock.instant();
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer("eduhub")
            .subject(String.valueOf(employee.getId()))
            .claim("employeeNo", employee.getEmployeeNo())
            .claim("name", employee.getName())
            .claim("role", employee.getRole().name())
            .issuedAt(now)
            .expiresAt(now.plus(ttl))
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  public long ttlSeconds() {
    return ttl.toSeconds();
  }
}
