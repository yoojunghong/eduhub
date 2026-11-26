package com.example.eduhub.auth.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** HS256 공유 비밀키로 JWT를 발급·검증한다. 비밀이 UTF-8 32바이트 미만이면 시작에 실패한다. */
@Configuration
public class JwtConfig {

  static final int MIN_SECRET_BYTES = 32;

  private final SecretKey key;

  public JwtConfig(@Value("${eduhub.jwt.secret}") String secret) {
    byte[] bytes = secret == null ? new byte[0] : secret.getBytes(StandardCharsets.UTF_8);
    if (bytes.length < MIN_SECRET_BYTES) {
      throw new IllegalStateException(
          "EDUHUB_JWT_SECRET은 UTF-8 기준 " + MIN_SECRET_BYTES + "바이트 이상이어야 합니다.");
    }
    this.key = new SecretKeySpec(bytes, "HmacSHA256");
  }

  @Bean
  public JwtEncoder jwtEncoder() {
    return new NimbusJwtEncoder(new ImmutableSecret<>(key));
  }

  @Bean
  public JwtDecoder jwtDecoder() {
    NimbusJwtDecoder decoder =
        NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    // 기본 만료 검사(허용 오차 60초) + 발급자 검사
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("eduhub"));
    return decoder;
  }
}
