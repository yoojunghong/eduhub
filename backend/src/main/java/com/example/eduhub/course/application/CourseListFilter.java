package com.example.eduhub.course.application;

/** 과정 목록 상태 조건. OPEN: 운영 중만(임직원 목록, 기본), CLOSED: 종료만, ALL: 전체(관리 화면). */
public enum CourseListFilter {
  OPEN,
  CLOSED,
  ALL
}
