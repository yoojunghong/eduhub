package com.example.eduhub.common.api;

import com.example.eduhub.course.domain.CourseClosedException;
import com.example.eduhub.course.domain.CourseNotFoundException;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
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

  @ExceptionHandler(CourseClosedException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ErrorResponse handleCourseClosed(CourseClosedException e) {
    return new ErrorResponse("COURSE_CLOSED", e.getMessage());
  }

  /** {@code @Valid} 검증 실패. 필드별 메시지를 "필드: 메시지" 형태로 이어 붙인다. */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorResponse handleInvalid(MethodArgumentNotValidException e) {
    String message =
        e.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .sorted()
            .collect(Collectors.joining(", "));
    return new ErrorResponse("INVALID_REQUEST", message);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorResponse handleUnreadable(HttpMessageNotReadableException e) {
    return new ErrorResponse("INVALID_REQUEST", "요청 본문을 읽을 수 없습니다.");
  }
}
