package com.example.eduhub.enrollment.domain;

/** 회차별 신청 수 집계 행. */
public record SessionCount(Long sessionId, long count) {}
