package com.example.eduhub.course.domain;

public class CourseNotFoundException extends RuntimeException {

  public CourseNotFoundException(Long courseId) {
    super("과정을 찾을 수 없습니다. id=" + courseId);
  }
}
