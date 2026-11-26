package com.example.eduhub.auth.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.security.oauth2.jwt.Jwt;

/** 토큰의 사용자 정보. 사용자 정보는 이 클래스의 from(Jwt) 한 곳에서만 꺼낸다. */
public record CurrentUser(Long employeeId, String employeeNo, String name, String role) {

  public static CurrentUser from(Jwt jwt) {
    return new CurrentUser(
        Long.valueOf(jwt.getSubject()),
        jwt.getClaimAsString("employeeNo"),
        jwt.getClaimAsString("name"),
        jwt.getClaimAsString("role"));
  }

  @JsonIgnore
  public boolean isAdmin() {
    return "ADMIN".equals(role);
  }
}
