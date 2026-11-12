package com.example.eduhub.course.domain;

/** 종료된 과정을 수정하거나 회차를 추가하려 할 때 (FR-03). */
public class CourseClosedException extends RuntimeException {

  public CourseClosedException(Long courseId) {
    super("종료된 과정은 수정할 수 없습니다. id=" + courseId);
  }
}
