package com.example.eduhub.enrollment.domain;

import com.example.eduhub.course.domain.CourseSession;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.OffsetDateTime;
import java.util.Objects;

/** 수강 신청. 생성은 {@link #confirm}, {@link #waiting} 정적 팩토리로만 한다. */
@Entity
public class Enrollment {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "session_id")
  private CourseSession session;

  // 과정 단위 유일 인덱스(D-01)를 위해 회차의 과정 id를 복사해 둔다
  @Column(nullable = false)
  private Long courseId;

  @Column(nullable = false)
  private Long employeeId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private EnrollmentStatus status;

  @Column(nullable = false)
  private OffsetDateTime enrolledAt;

  protected Enrollment() {}

  private Enrollment(
      CourseSession session, Long employeeId, EnrollmentStatus status, OffsetDateTime now) {
    Objects.requireNonNull(session, "session");
    Objects.requireNonNull(employeeId, "employeeId");
    Objects.requireNonNull(now, "now");
    this.session = session;
    this.courseId = session.getCourse().getId();
    this.employeeId = employeeId;
    this.status = status;
    this.enrolledAt = now;
  }

  public static Enrollment confirm(CourseSession session, Long employeeId, OffsetDateTime now) {
    return new Enrollment(session, employeeId, EnrollmentStatus.CONFIRMED, now);
  }

  public static Enrollment waiting(CourseSession session, Long employeeId, OffsetDateTime now) {
    return new Enrollment(session, employeeId, EnrollmentStatus.WAITING, now);
  }

  public boolean isConfirmed() {
    return status == EnrollmentStatus.CONFIRMED;
  }

  public boolean isWaiting() {
    return status == EnrollmentStatus.WAITING;
  }

  /** 확정·대기 신청을 취소한다. 그 밖의 상태면 예외. 기간(취소 마감·승급 중단) 검사는 서비스가 한다. */
  public void cancel() {
    if (status != EnrollmentStatus.CONFIRMED && status != EnrollmentStatus.WAITING) {
      throw new EnrollmentNotCancelableException(id);
    }
    this.status = EnrollmentStatus.CANCELED;
  }

  /** 대기 신청을 확정으로 올린다. 대기가 아니면 프로그래밍 오류. 기간(취소 마감·승급 중단) 검사는 서비스가 한다. */
  public void promote() {
    if (status != EnrollmentStatus.WAITING) {
      throw new IllegalStateException("대기 상태만 승급할 수 있습니다. id=" + id + ", status=" + status);
    }
    this.status = EnrollmentStatus.CONFIRMED;
  }

  /** 확정 신청을 수료로 바꾼다. 확정이 아니면 프로그래밍 오류(서비스가 먼저 상태로 사유를 가른다). */
  public void complete() {
    requireConfirmed("수료");
    this.status = EnrollmentStatus.COMPLETED;
  }

  /** 확정 신청을 미수료로 바꾼다. 확정이 아니면 프로그래밍 오류. */
  public void markIncomplete() {
    requireConfirmed("미수료");
    this.status = EnrollmentStatus.INCOMPLETE;
  }

  private void requireConfirmed(String action) {
    if (status != EnrollmentStatus.CONFIRMED) {
      throw new IllegalStateException(
          "확정 상태만 " + action + " 처리할 수 있습니다. id=" + id + ", status=" + status);
    }
  }

  public Long getId() {
    return id;
  }

  public CourseSession getSession() {
    return session;
  }

  public Long getCourseId() {
    return courseId;
  }

  public Long getEmployeeId() {
    return employeeId;
  }

  public EnrollmentStatus getStatus() {
    return status;
  }

  public OffsetDateTime getEnrolledAt() {
    return enrolledAt;
  }
}
