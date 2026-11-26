package com.example.eduhub.enrollment.api;

import com.example.eduhub.auth.api.CurrentUser;
import com.example.eduhub.common.api.ErrorResponse;
import com.example.eduhub.enrollment.application.EnrollmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Enrollment", description = "수강 신청")
@RestController
public class EnrollmentController {

  private final EnrollmentService enrollmentService;

  public EnrollmentController(EnrollmentService enrollmentService) {
    this.enrollmentService = enrollmentService;
  }

  @Operation(
      summary = "수강 신청",
      description =
          "회차에 수강 신청한다. 자리가 있고 대기자가 없으면 CONFIRMED, 정원이 차면(또는 대기자가 있으면) "
              + "WAITING으로 등록하고 응답의 waitingNumber에 대기 순번(1부터)을 준다 (FR-04, FR-05). "
              + "대기 인원은 정원의 50%(내림)까지이며 넘으면 WAITLIST_FULL (D-03, D-06).")
  @ApiResponse(responseCode = "201", description = "신청 성공 (CONFIRMED 또는 WAITING)")
  @ApiResponse(
      responseCode = "401",
      description = "로그인 필요 (UNAUTHORIZED)",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "404",
      description = "회차가 없음",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "409",
      description = "종료된 회차, 신청 기간 종료, 같은 과정에 진행 중 신청이 있음, 또는 대기 인원이 가득 참 (WAITLIST_FULL)",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "503",
      description = "신청이 몰려 회차 락을 얻지 못함 (SESSION_BUSY). 잠시 후 다시 시도",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @PostMapping("/api/v1/sessions/{sessionId}/enrollments")
  public ResponseEntity<EnrollmentResponse> enroll(
      @PathVariable Long sessionId, @AuthenticationPrincipal @Parameter(hidden = true) Jwt jwt) {
    Long employeeId = CurrentUser.from(jwt).employeeId();
    EnrollmentResponse created = enrollmentService.enroll(sessionId, employeeId);
    return ResponseEntity.created(URI.create("/api/v1/enrollments/" + created.id())).body(created);
  }

  @Operation(
      summary = "수강 신청 취소",
      description =
          "본인의 신청(확정·대기)을 취소한다 (FR-06). 회차 시작일 당일부터는 취소할 수 없다 (D-02, D-07). "
              + "확정자가 취소하면 대기 1순위가 자동으로 확정되지만, 회차 시작 1일 전부터는 승급하지 않고 "
              + "자리만 빈다 (D-03). 응답에 승급된 사람 정보는 없다.")
  @ApiResponse(responseCode = "200", description = "취소 성공 (status: CANCELED)")
  @ApiResponse(
      responseCode = "401",
      description = "로그인 필요 (UNAUTHORIZED)",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "404",
      description = "신청이 없거나 본인 것이 아님 (ENROLLMENT_NOT_FOUND)",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "409",
      description = "이미 취소·수료된 신청 (ENROLLMENT_NOT_CANCELABLE) 또는 취소 기간 종료 (CANCEL_PERIOD_ENDED)",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "503",
      description = "요청이 몰려 회차 락을 얻지 못함 (SESSION_BUSY). 잠시 후 다시 시도",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @PostMapping("/api/v1/enrollments/{enrollmentId}/cancel")
  public EnrollmentResponse cancel(
      @PathVariable Long enrollmentId, @AuthenticationPrincipal @Parameter(hidden = true) Jwt jwt) {
    Long employeeId = CurrentUser.from(jwt).employeeId();
    return enrollmentService.cancel(enrollmentId, employeeId);
  }
}
