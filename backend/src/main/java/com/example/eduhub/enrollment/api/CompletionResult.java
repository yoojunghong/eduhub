package com.example.eduhub.enrollment.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 수료 처리 결과 (FR-08). 처리된 사원 id와 실패한 사원·사유. */
public record CompletionResult(List<Long> processed, List<Failure> failed) {

  public record Failure(
      Long employeeId,
      @Schema(allowableValues = {"NOT_ENROLLED", "NOT_CONFIRMED", "ALREADY_PROCESSED"})
          String reason) {}
}
