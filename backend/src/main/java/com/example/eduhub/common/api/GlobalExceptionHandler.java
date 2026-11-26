package com.example.eduhub.common.api;

import com.example.eduhub.auth.application.InvalidCredentialsException;
import com.example.eduhub.course.domain.CourseClosedException;
import com.example.eduhub.course.domain.CourseNotFoundException;
import com.example.eduhub.enrollment.domain.CancelPeriodEndedException;
import com.example.eduhub.enrollment.domain.DuplicateEnrollmentException;
import com.example.eduhub.enrollment.domain.EnrollmentNotCancelableException;
import com.example.eduhub.enrollment.domain.EnrollmentNotFoundException;
import com.example.eduhub.enrollment.domain.EnrollmentPeriodEndedException;
import com.example.eduhub.enrollment.domain.SessionClosedException;
import com.example.eduhub.enrollment.domain.SessionNotFoundException;
import com.example.eduhub.enrollment.domain.WaitlistFullException;
import jakarta.validation.ConstraintViolationException;
import java.util.stream.Collectors;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(InvalidCredentialsException.class)
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  public ErrorResponse handleInvalidCredentials(InvalidCredentialsException e) {
    return new ErrorResponse("INVALID_CREDENTIALS", e.getMessage());
  }

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

  @ExceptionHandler(SessionNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ErrorResponse handleSessionNotFound(SessionNotFoundException e) {
    return new ErrorResponse("SESSION_NOT_FOUND", e.getMessage());
  }

  @ExceptionHandler(SessionClosedException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ErrorResponse handleSessionClosed(SessionClosedException e) {
    return new ErrorResponse("SESSION_CLOSED", e.getMessage());
  }

  @ExceptionHandler(EnrollmentPeriodEndedException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ErrorResponse handlePeriodEnded(EnrollmentPeriodEndedException e) {
    return new ErrorResponse("ENROLLMENT_PERIOD_ENDED", e.getMessage());
  }

  @ExceptionHandler(DuplicateEnrollmentException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ErrorResponse handleDuplicateEnrollment(DuplicateEnrollmentException e) {
    return new ErrorResponse("DUPLICATE_ENROLLMENT", e.getMessage());
  }

  @ExceptionHandler(EnrollmentNotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public ErrorResponse handleEnrollmentNotFound(EnrollmentNotFoundException e) {
    return new ErrorResponse("ENROLLMENT_NOT_FOUND", e.getMessage());
  }

  @ExceptionHandler(EnrollmentNotCancelableException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ErrorResponse handleNotCancelable(EnrollmentNotCancelableException e) {
    return new ErrorResponse("ENROLLMENT_NOT_CANCELABLE", e.getMessage());
  }

  @ExceptionHandler(CancelPeriodEndedException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ErrorResponse handleCancelPeriodEnded(CancelPeriodEndedException e) {
    return new ErrorResponse("CANCEL_PERIOD_ENDED", e.getMessage());
  }

  @ExceptionHandler(WaitlistFullException.class)
  @ResponseStatus(HttpStatus.CONFLICT)
  public ErrorResponse handleWaitlistFull(WaitlistFullException e) {
    return new ErrorResponse("WAITLIST_FULL", e.getMessage());
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorResponse handleTypeMismatch(MethodArgumentTypeMismatchException e) {
    return new ErrorResponse("INVALID_REQUEST", "형식이 올바르지 않습니다: " + e.getName());
  }

  /** 파라미터 직접 검증({@code @Positive} 등) 실패. */
  @ExceptionHandler(HandlerMethodValidationException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorResponse handleMethodValidation(HandlerMethodValidationException e) {
    return new ErrorResponse("INVALID_REQUEST", "요청 값이 올바르지 않습니다.");
  }

  @ExceptionHandler(ConstraintViolationException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public ErrorResponse handleConstraintViolation(ConstraintViolationException e) {
    return new ErrorResponse("INVALID_REQUEST", "요청 값이 올바르지 않습니다.");
  }

  /** 회차 락을 제한 시간 안에 얻지 못함 (신청이 몰림). */
  @ExceptionHandler(PessimisticLockingFailureException.class)
  @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
  public ErrorResponse handleLockFailure(PessimisticLockingFailureException e) {
    return new ErrorResponse("SESSION_BUSY", "신청이 몰려 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.");
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
