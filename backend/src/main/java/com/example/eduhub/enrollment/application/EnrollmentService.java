package com.example.eduhub.enrollment.application;

import com.example.eduhub.course.domain.CourseSession;
import com.example.eduhub.course.domain.CourseSessionRepository;
import com.example.eduhub.course.domain.CourseStatus;
import com.example.eduhub.course.domain.SessionStatus;
import com.example.eduhub.enrollment.api.EnrollmentResponse;
import com.example.eduhub.enrollment.domain.CancelPeriodEndedException;
import com.example.eduhub.enrollment.domain.DuplicateEnrollmentException;
import com.example.eduhub.enrollment.domain.Enrollment;
import com.example.eduhub.enrollment.domain.EnrollmentNotFoundException;
import com.example.eduhub.enrollment.domain.EnrollmentPeriodEndedException;
import com.example.eduhub.enrollment.domain.EnrollmentRepository;
import com.example.eduhub.enrollment.domain.EnrollmentStatus;
import com.example.eduhub.enrollment.domain.SessionClosedException;
import com.example.eduhub.enrollment.domain.SessionNotFoundException;
import com.example.eduhub.enrollment.domain.WaitlistFullException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 수강 신청 (FR-04, NFR-01). 회차 행 잠금 → 확인 → 저장을 한 트랜잭션에서 한다. */
@Service
@Transactional
public class EnrollmentService {

  /** 회차 락 대기 최대 시간 (PostgreSQL lock_timeout 형식). 넘으면 락 실패 예외 → 503 SESSION_BUSY. */
  static final String LOCK_TIMEOUT = "3s";

  private static final String ACTIVE_UNIQUE_INDEX = "ux_enrollment_active_course";

  private final CourseSessionRepository sessionRepository;
  private final EnrollmentRepository enrollmentRepository;
  private final Clock clock;

  public EnrollmentService(
      CourseSessionRepository sessionRepository,
      EnrollmentRepository enrollmentRepository,
      Clock clock) {
    this.sessionRepository = sessionRepository;
    this.enrollmentRepository = enrollmentRepository;
    this.clock = clock;
  }

  public EnrollmentResponse enroll(Long sessionId, Long employeeId) {
    // 1. 회차 행 잠금. 같은 회차 신청은 여기서 줄을 선다 (대기는 LOCK_TIMEOUT까지)
    sessionRepository.setLockTimeout(LOCK_TIMEOUT);
    CourseSession session =
        sessionRepository
            .findByIdForUpdate(sessionId)
            .orElseThrow(() -> new SessionNotFoundException(sessionId));

    // 2. 회차·과정이 열려 있는가
    if (session.getStatus() != SessionStatus.OPEN
        || session.getCourse().getStatus() != CourseStatus.OPEN) {
      throw new SessionClosedException(sessionId);
    }

    // 3. 신청은 시작 전날까지 (D-01)
    LocalDate today = LocalDate.now(clock);
    if (!today.isBefore(session.getStartDate())) {
      throw new EnrollmentPeriodEndedException(sessionId);
    }

    // 4. 같은 과정에 진행 중 신청이 있으면 중복
    Long courseId = session.getCourse().getId();
    if (enrollmentRepository.existsActiveInCourse(courseId, employeeId)) {
      throw new DuplicateEnrollmentException(courseId);
    }

    // 5~6. 락 안에서 확정 수와 대기 수를 세어 확정/대기/거절 결정 (D-06: 대기자가 있으면 새치기 금지)
    long confirmed =
        enrollmentRepository.countBySessionIdAndStatus(sessionId, EnrollmentStatus.CONFIRMED);
    long waiting =
        enrollmentRepository.countBySessionIdAndStatus(sessionId, EnrollmentStatus.WAITING);
    OffsetDateTime now = OffsetDateTime.now(clock);
    Enrollment enrollment;
    Integer waitingNumber = null;
    if (waiting == 0 && confirmed < session.getCapacity()) {
      enrollment = Enrollment.confirm(session, employeeId, now);
    } else if (waiting < session.waitlistLimit()) {
      enrollment = Enrollment.waiting(session, employeeId, now);
      waitingNumber = (int) waiting + 1;
    } else {
      throw new WaitlistFullException(sessionId);
    }

    // 7. 다른 회차 동시 신청은 유일 인덱스가 막는다
    try {
      return EnrollmentResponse.from(enrollmentRepository.saveAndFlush(enrollment), waitingNumber);
    } catch (DataIntegrityViolationException e) {
      // 진행 중 신청 유일 인덱스 위반만 중복으로 본다. 다른 제약 위반은 그대로 던진다
      if (isActiveEnrollmentViolation(e)) {
        throw new DuplicateEnrollmentException(courseId, e);
      }
      throw e;
    }
  }

  /** 신청 취소 (FR-06, D-02). 확정자가 취소하면 승급 기간 안에서 대기 1순위를 확정으로 올린다. */
  public EnrollmentResponse cancel(Long enrollmentId, Long employeeId) {
    // 1. 락 없이 회차 번호만 (스칼라 조회: 엔티티를 영속성 컨텍스트에 올리지 않는다)
    Long sessionId =
        enrollmentRepository
            .findSessionIdByIdAndEmployeeId(enrollmentId, employeeId)
            .orElseThrow(() -> new EnrollmentNotFoundException(enrollmentId));

    // 2. 신청과 같은 회차 행 잠금
    sessionRepository.setLockTimeout(LOCK_TIMEOUT);
    CourseSession session =
        sessionRepository
            .findByIdForUpdate(sessionId)
            .orElseThrow(() -> new SessionNotFoundException(sessionId));

    // 3. 락을 잡은 뒤에 읽어야 최신 상태다
    Enrollment enrollment =
        enrollmentRepository
            .findById(enrollmentId)
            .orElseThrow(() -> new EnrollmentNotFoundException(enrollmentId));

    // 4. 취소는 시작 전날까지 (D-02)
    LocalDate today = LocalDate.now(clock);
    LocalDate startDate = session.getStartDate();
    if (!today.isBefore(startDate)) {
      throw new CancelPeriodEndedException(sessionId);
    }

    // 5. 취소
    boolean wasConfirmed = enrollment.isConfirmed();
    enrollment.cancel();

    // 6. 확정자 취소이고 시작 1일 전 이전이면 대기 1순위 승급 (D-03)
    if (wasConfirmed && today.isBefore(startDate.minusDays(1))) {
      promoteNext(sessionId);
    }

    return EnrollmentResponse.from(enrollment);
  }

  /**
   * 승급 지점. 회차 락 안에서만 호출한다. 확정자 1명이 빠진 자리에만 부르므로 정원을 넘지 않는다. 정원 축소(D-04)가 생기면 승급 전에 {@code confirmed
   * < capacity}를 다시 확인해야 한다.
   */
  private void promoteNext(Long sessionId) {
    enrollmentRepository
        .findFirstWaiting(sessionId)
        .ifPresent(next -> {
          next.promote();
          // FR-09: 승급 알림 이벤트 자리
        });
  }

  static boolean isActiveEnrollmentViolation(Throwable e) {
    for (Throwable t = e; t != null; t = t.getCause()) {
      if (t instanceof ConstraintViolationException cve
          && ACTIVE_UNIQUE_INDEX.equalsIgnoreCase(cve.getConstraintName())) {
        return true;
      }
      if (t.getMessage() != null && t.getMessage().contains(ACTIVE_UNIQUE_INDEX)) {
        return true;
      }
      if (t.getCause() == t) {
        break;
      }
    }
    return false;
  }
}
