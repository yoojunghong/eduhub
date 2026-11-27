package com.example.eduhub.enrollment.api;

import com.example.eduhub.enrollment.domain.RosterRow;
import java.time.LocalDate;
import java.util.List;

/** 회차 명단 (FR-08). completionOpen은 지금 수료 처리가 가능한지(D-09). */
public record RosterResponse(
    Long sessionId,
    Long courseId,
    String courseTitle,
    LocalDate startDate,
    String place,
    int capacity,
    String sessionStatus,
    boolean completionOpen,
    List<RosterRow> confirmed,
    List<RosterRow> waiting) {}
