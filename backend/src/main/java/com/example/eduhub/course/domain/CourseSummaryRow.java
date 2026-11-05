package com.example.eduhub.course.domain;

/** 과정 목록 조회 결과 한 줄. 회차 수를 쿼리에서 함께 세어 N+1 을 피한다 (NFR-02). */
public record CourseSummaryRow(
    Long id, String title, String category, CourseStatus status, long sessionCount) {}
