package com.example.eduhub.auth.api;

import com.example.eduhub.auth.application.LoginService;
import com.example.eduhub.common.api.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "로그인·내 정보")
@RestController
public class AuthController {

  private final LoginService loginService;

  public AuthController(LoginService loginService) {
    this.loginService = loginService;
  }

  @Operation(
      summary = "로그인",
      description = "사번과 비밀번호로 액세스 토큰(JWT)을 받는다. 사번이 없든 비밀번호가 틀리든 같은 401을 돌려준다 (NFR-03)")
  @SecurityRequirements
  @ApiResponse(responseCode = "200", description = "로그인 성공")
  @ApiResponse(
      responseCode = "400",
      description = "사번 또는 비밀번호가 비어 있음 (INVALID_REQUEST)",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "401",
      description = "사번 또는 비밀번호가 올바르지 않음 (INVALID_CREDENTIALS)",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @PostMapping("/api/v1/auth/login")
  public LoginResponse login(@Valid @RequestBody LoginRequest request) {
    return LoginResponse.from(loginService.login(request.employeeNo(), request.password()));
  }

  @Operation(summary = "내 정보", description = "토큰의 사용자 정보를 돌려준다")
  @ApiResponse(responseCode = "200", description = "조회 성공")
  @ApiResponse(
      responseCode = "401",
      description = "토큰이 없거나 만료·위조 (UNAUTHORIZED)",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @GetMapping("/api/v1/me")
  public CurrentUser me(@AuthenticationPrincipal Jwt jwt) {
    return CurrentUser.from(jwt);
  }
}
