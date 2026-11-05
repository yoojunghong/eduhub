package com.example.eduhub.course.api;

import com.example.eduhub.course.domain.CourseSummaryRow;

public record CourseSummaryResponse(
    Long id, String title, String category, String status, long sessionCount) {

  public static CourseSummaryResponse from(CourseSummaryRow row) {
    return new CourseSummaryResponse(
        row.id(), row.title(), row.category(), row.status().name(), row.sessionCount());
  }
}
