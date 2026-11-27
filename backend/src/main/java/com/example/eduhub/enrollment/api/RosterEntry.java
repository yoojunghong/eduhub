package com.example.eduhub.enrollment.api;

import com.example.eduhub.enrollment.domain.RosterRow;
import io.swagger.v3.oas.annotations.media.Schema;

/** 명단 한 줄 (FR-08). 사원 행이 없으면 employeeNo·name·department는 null. */
public record RosterEntry(
    Long enrollmentId,
    Long employeeId,
    String employeeNo,
    String name,
    String department,
    @Schema(allowableValues = {"CONFIRMED", "WAITING", "COMPLETED", "INCOMPLETE"}) String status,
    @Schema(description = "대기자만 값이 있다 (확정·처리 행은 null)") Integer waitingNumber) {

  public static RosterEntry from(RosterRow row) {
    return new RosterEntry(
        row.enrollmentId(),
        row.employeeId(),
        row.employeeNo(),
        row.name(),
        row.department(),
        row.status().name(),
        row.waitingNumber() == null ? null : row.waitingNumber().intValue());
  }
}
