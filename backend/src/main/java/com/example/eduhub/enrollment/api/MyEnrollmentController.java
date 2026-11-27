package com.example.eduhub.enrollment.api;

import com.example.eduhub.auth.api.CurrentUser;
import com.example.eduhub.common.api.ErrorResponse;
import com.example.eduhub.enrollment.application.MyEnrollmentQueryService;
import com.example.eduhub.enrollment.domain.EnrollmentStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Enrollment", description = "수강 신청")
@RestController
public class MyEnrollmentController {

  private final MyEnrollmentQueryService myEnrollmentQueryService;

  public MyEnrollmentController(MyEnrollmentQueryService myEnrollmentQueryService) {
    this.myEnrollmentQueryService = myEnrollmentQueryService;
  }

  @Operation(
      summary = "내 신청 내역",
      description =
          "로그인한 사원의 신청 내역을 최근 신청순으로 준다 (FR-07). status로 거를 수 있다. "
              + "waitingNumber는 대기일 때만 값이 있고, cancelable은 확정·대기이면서 오늘이 시작일 전일 때 true (D-07).")
  @ApiResponse(responseCode = "200", description = "조회 성공 (신청이 없으면 빈 배열)")
  @ApiResponse(
      responseCode = "400",
      description = "status 형식 오류 (INVALID_REQUEST)",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "401",
      description = "로그인 필요 (UNAUTHORIZED)",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @GetMapping("/api/v1/me/enrollments")
  public List<MyEnrollmentResponse> myEnrollments(
      @RequestParam(required = false) EnrollmentStatus status,
      @AuthenticationPrincipal @Parameter(hidden = true) Jwt jwt) {
    Long employeeId = CurrentUser.from(jwt).employeeId();
    return myEnrollmentQueryService.findMyEnrollments(employeeId, status);
  }
}
