package com.example.eduhub.enrollment.api;

import com.example.eduhub.common.api.ErrorResponse;
import com.example.eduhub.enrollment.application.RosterService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Session", description = "관리자: 회차 명단·수료 처리")
@RestController
public class AdminSessionController {

  private final RosterService rosterService;

  public AdminSessionController(RosterService rosterService) {
    this.rosterService = rosterService;
  }

  @Operation(
      summary = "회차 명단",
      description =
          "회차의 확정·처리(수료·미수료) 명단과 대기 명단을 준다 (FR-08). 취소는 제외. "
              + "completionOpen은 지금 수료 처리가 가능한지(시작일 당일부터, D-09). "
              + "사원 정보가 없는 신청은 employeeNo·name이 null.")
  @ApiResponse(responseCode = "200", description = "조회 성공")
  @ApiResponse(
      responseCode = "401",
      description = "로그인 필요 (UNAUTHORIZED)",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "403",
      description = "관리자 권한 필요 (FORBIDDEN)",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "404",
      description = "회차가 없음 (SESSION_NOT_FOUND)",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @GetMapping("/api/v1/admin/sessions/{sessionId}/roster")
  public RosterResponse roster(@PathVariable Long sessionId) {
    return rosterService.roster(sessionId);
  }

  @Operation(
      summary = "수료 처리",
      description =
          "확정자를 수료(COMPLETED) 또는 미수료(INCOMPLETE)로 처리한다 (FR-08, D-05, D-09, D-10). "
              + "처리 가능한 사람만 처리하고 나머지는 failed에 사유(NOT_ENROLLED, NOT_CONFIRMED, "
              + "ALREADY_PROCESSED)와 함께 돌려준다. 한 번 처리한 신청은 다시 바꾸지 않는다.")
  @ApiResponse(responseCode = "200", description = "처리 결과 (일부 실패 포함)")
  @ApiResponse(
      responseCode = "400",
      description = "빈 목록, 100명 초과, outcome 없음 (INVALID_REQUEST)",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "401",
      description = "로그인 필요 (UNAUTHORIZED)",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "403",
      description = "관리자 권한 필요 (FORBIDDEN)",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "404",
      description = "회차가 없음 (SESSION_NOT_FOUND)",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "409",
      description = "시작일 전이라 처리할 수 없음 (COMPLETION_NOT_OPEN)",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "503",
      description = "회차 락을 얻지 못함 (SESSION_BUSY). 잠시 후 다시 시도",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @PostMapping("/api/v1/admin/sessions/{sessionId}/completions")
  public CompletionResult complete(
      @PathVariable Long sessionId, @Valid @RequestBody CompletionRequest request) {
    return rosterService.complete(sessionId, request.employeeIds(), request.outcome());
  }
}
