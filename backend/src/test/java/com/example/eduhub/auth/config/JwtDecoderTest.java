package com.example.eduhub.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;

class JwtDecoderTest {

  private static final String SECRET =
      "test-only-decoder-secret-0123456789-abcdef-0123456789-0123456789-xyz";
  private static final String OTHER_SECRET = "test-only-other-secret-0123456789-abcdefg";

  private final JwtConfig config = new JwtConfig(SECRET);
  private final JwtDecoder decoder = config.jwtDecoder();

  private String token(JwtEncoder encoder, MacAlgorithm alg, String issuer, Instant exp) {
    Instant iat = exp.minusSeconds(3600);
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(issuer)
            .subject("1")
            .claim("role", "EMPLOYEE")
            .issuedAt(iat)
            .expiresAt(exp)
            .build();
    return encoder
        .encode(JwtEncoderParameters.from(JwsHeader.with(alg).build(), claims))
        .getTokenValue();
  }

  private String valid() {
    return token(config.jwtEncoder(), MacAlgorithm.HS256, "eduhub", Instant.now().plusSeconds(600));
  }

  @Test
  void 정상_토큰은_통과() {
    assertThat(decoder.decode(valid()).getSubject()).isEqualTo("1");
  }

  @Test
  void 다른_키로_서명한_토큰은_거부() {
    String forged =
        token(
            new JwtConfig(OTHER_SECRET).jwtEncoder(),
            MacAlgorithm.HS256,
            "eduhub",
            Instant.now().plusSeconds(600));
    assertThatThrownBy(() -> decoder.decode(forged)).isInstanceOf(JwtException.class);
  }

  @Test
  void HS512로_서명한_토큰은_거부() {
    String hs512 =
        token(config.jwtEncoder(), MacAlgorithm.HS512, "eduhub", Instant.now().plusSeconds(600));
    assertThatThrownBy(() -> decoder.decode(hs512)).isInstanceOf(JwtException.class);
  }

  @Test
  void alg_none_토큰은_거부() {
    Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
    String header = b64.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8));
    String payload =
        b64.encodeToString(
            ("{\"iss\":\"eduhub\",\"sub\":\"1\",\"role\":\"ADMIN\",\"exp\":"
                    + Instant.now().plusSeconds(600).getEpochSecond()
                    + "}")
                .getBytes(StandardCharsets.UTF_8));
    String none = header + "." + payload + ".";
    assertThatThrownBy(() -> decoder.decode(none)).isInstanceOf(JwtException.class);
  }

  @Test
  void 허용_오차보다_오래_만료된_토큰은_거부() {
    String expired =
        token(config.jwtEncoder(), MacAlgorithm.HS256, "eduhub", Instant.now().minusSeconds(180));
    assertThatThrownBy(() -> decoder.decode(expired)).isInstanceOf(JwtException.class);
  }

  @Test
  void 발급자가_다른_토큰은_거부() {
    String other =
        token(config.jwtEncoder(), MacAlgorithm.HS256, "other", Instant.now().plusSeconds(600));
    assertThatThrownBy(() -> decoder.decode(other)).isInstanceOf(JwtException.class);
  }
}
