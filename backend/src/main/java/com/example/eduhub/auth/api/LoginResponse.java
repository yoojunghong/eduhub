package com.example.eduhub.auth.api;

import com.example.eduhub.auth.application.LoginResult;

public record LoginResponse(
    String accessToken, String tokenType, long expiresIn, CurrentUser user) {

  public static LoginResponse from(LoginResult result) {
    return new LoginResponse(
        result.accessToken(),
        "Bearer",
        result.ttlSeconds(),
        new CurrentUser(
            result.user().id(),
            result.user().employeeNo(),
            result.user().name(),
            result.user().role()));
  }
}
