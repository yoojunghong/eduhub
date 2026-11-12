package com.example.eduhub.enrollment.api;

import com.example.eduhub.common.api.ErrorResponse;
import com.example.eduhub.enrollment.application.EnrollmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
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
      description = "회차에 수강 신청한다. 자리가 있으면 CONFIRMED, 정원이 차면 WAITING으로 등록한다 (FR-04)")
  @ApiResponse(responseCode = "201", description = "신청 성공 (CONFIRMED 또는 WAITING)")
  @ApiResponse(
      responseCode = "400",
      description = "X-Employee-Id 헤더 누락·형식 오류",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "404",
      description = "회차가 없음",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "409",
      description = "종료된 회차, 신청 기간 종료, 또는 같은 과정에 진행 중 신청이 있음",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "503",
      description = "신청이 몰려 회차 락을 얻지 못함 (SESSION_BUSY). 잠시 후 다시 시도",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @PostMapping("/api/v1/sessions/{sessionId}/enrollments")
  public ResponseEntity<EnrollmentResponse> enroll(
      @PathVariable Long sessionId,
      // TODO(NFR-03) 인증 도입 시 로그인 사용자 정보로 교체
      @RequestHeader("X-Employee-Id") @Positive Long employeeId) {
    EnrollmentResponse created = enrollmentService.enroll(sessionId, employeeId);
    return ResponseEntity.created(URI.create("/api/v1/enrollments/" + created.id())).body(created);
  }
}
