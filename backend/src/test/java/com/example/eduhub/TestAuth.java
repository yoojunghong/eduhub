package com.example.eduhub;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

/** 테스트용 로그인 사용자. sub=사원 id, role 클레임, ROLE_ 권한을 가진 JWT 인증을 요청에 붙인다. */
public final class TestAuth {

  private TestAuth() {}

  public static JwtRequestPostProcessor as(long employeeId, String role) {
    return jwt()
        .jwt(j -> j.subject(String.valueOf(employeeId)).claim("role", role))
        .authorities(new SimpleGrantedAuthority("ROLE_" + role));
  }

  public static JwtRequestPostProcessor employee(long employeeId) {
    return as(employeeId, "EMPLOYEE");
  }

  public static JwtRequestPostProcessor admin(long employeeId) {
    return as(employeeId, "ADMIN");
  }
}
