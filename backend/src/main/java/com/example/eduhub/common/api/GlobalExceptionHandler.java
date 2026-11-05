package com.example.eduhub.common.api;

import com.example.eduhub.course.domain.CourseNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(CourseNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ErrorResponse handleCourseNotFound(CourseNotFoundException e) {
    return new ErrorResponse("COURSE_NOT_FOUND", e.getMessage());
  }
}
