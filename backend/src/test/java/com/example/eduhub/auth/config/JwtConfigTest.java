package com.example.eduhub.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JwtConfigTest {

  @Test
  void 비밀이_31바이트면_시작_실패() {
    assertThatThrownBy(() -> new JwtConfig("a".repeat(31)))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void 비밀이_32바이트면_성공() {
    JwtConfig config = new JwtConfig("a".repeat(32));
    assertThat(config.jwtEncoder()).isNotNull();
    assertThat(config.jwtDecoder()).isNotNull();
  }

  @Test
  void 한글은_글자수가_아니라_UTF8_바이트로_센다() {
    // 한글 10자 = 30바이트 (실패), 11자 = 33바이트 (성공)
    assertThatThrownBy(() -> new JwtConfig("가".repeat(10)))
        .isInstanceOf(IllegalStateException.class);
    assertThat(new JwtConfig("가".repeat(11)).jwtDecoder()).isNotNull();
  }
}
