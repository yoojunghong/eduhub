package com.example.eduhub.enrollment.application;

import com.example.eduhub.course.domain.CourseSession;
import com.example.eduhub.course.domain.CourseSessionRepository;
import com.example.eduhub.course.domain.CourseStatus;
import com.example.eduhub.course.domain.SessionStatus;
import com.example.eduhub.enrollment.api.EnrollmentResponse;
import com.example.eduhub.enrollment.domain.DuplicateEnrollmentException;
import com.example.eduhub.enrollment.domain.Enrollment;
import com.example.eduhub.enrollment.domain.EnrollmentPeriodEndedException;
import com.example.eduhub.enrollment.domain.EnrollmentRepository;
import com.example.eduhub.enrollment.domain.EnrollmentStatus;
import com.example.eduhub.enrollment.domain.SessionClosedException;
import com.example.eduhub.enrollment.domain.SessionNotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 수강 신청 (FR-04, NFR-01). 회차 행 잠금 → 확인 → 저장을 한 트랜잭션에서 한다. */
@Service
@Transactional
public class EnrollmentService {

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
    // 1. 회차 행 잠금. 같은 회차 신청은 여기서 줄을 선다
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

    // 5~6. 락 안에서 확정 수를 세어 확정/대기 결정
    long confirmed =
        enrollmentRepository.countBySessionIdAndStatus(sessionId, EnrollmentStatus.CONFIRMED);
    OffsetDateTime now = OffsetDateTime.now(clock);
    Enrollment enrollment =
        confirmed < session.getCapacity()
            ? Enrollment.confirm(session, employeeId, now)
            : Enrollment.waiting(session, employeeId, now);

    // 7. 다른 회차 동시 신청은 유일 인덱스가 막는다
    try {
      return EnrollmentResponse.from(enrollmentRepository.saveAndFlush(enrollment));
    } catch (DataIntegrityViolationException e) {
      throw new DuplicateEnrollmentException(courseId);
    }
  }
}
