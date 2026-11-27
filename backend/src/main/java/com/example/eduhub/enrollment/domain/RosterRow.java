package com.example.eduhub.enrollment.domain;

/** 회차 명단 행 (FR-08). 사원 행이 없으면 employeeNo·name·department는 null. */
public record RosterRow(
    Long enrollmentId,
    Long employeeId,
    String employeeNo,
    String name,
    String department,
    EnrollmentStatus status,
    Long waitingNumber) {}
