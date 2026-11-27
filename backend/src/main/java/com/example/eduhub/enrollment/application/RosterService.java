package com.example.eduhub.enrollment.application;

import com.example.eduhub.course.domain.CourseSession;
import com.example.eduhub.course.domain.CourseSessionRepository;
import com.example.eduhub.enrollment.api.CompletionResult;
import com.example.eduhub.enrollment.api.RosterResponse;
import com.example.eduhub.enrollment.domain.CompletionNotOpenException;
import com.example.eduhub.enrollment.domain.CompletionOutcome;
import com.example.eduhub.enrollment.domain.Enrollment;
import com.example.eduhub.enrollment.domain.EnrollmentRepository;
import com.example.eduhub.enrollment.domain.EnrollmentStatus;
import com.example.eduhub.enrollment.domain.RosterRow;
import com.example.eduhub.enrollment.domain.SessionNotFoundException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 회차 명단 조회와 수료 처리 (FR-08, D-09, D-10). */
@Service
public class RosterService {

  private final CourseSessionRepository sessionRepository;
  private final EnrollmentRepository enrollmentRepository;
  private final Clock clock;

  public RosterService(
      CourseSessionRepository sessionRepository,
      EnrollmentRepository enrollmentRepository,
      Clock clock) {
    this.sessionRepository = sessionRepository;
    this.enrollmentRepository = enrollmentRepository;
    this.clock = clock;
  }

  @Transactional(readOnly = true)
  public RosterResponse roster(Long sessionId) {
    CourseSession session =
        sessionRepository
            .findById(sessionId)
            .orElseThrow(() -> new SessionNotFoundException(sessionId));
    List<RosterRow> confirmed =
        enrollmentRepository.findRoster(
            sessionId,
            List.of(
                EnrollmentStatus.CONFIRMED,
                EnrollmentStatus.COMPLETED,
                EnrollmentStatus.INCOMPLETE));
    List<RosterRow> waiting =
        new ArrayList<>(
            enrollmentRepository.findRoster(sessionId, List.of(EnrollmentStatus.WAITING)));
    // 순번 순 (id 순과 같지만 명시)
    waiting.sort(
        java.util.Comparator.comparing(
            RosterRow::waitingNumber, java.util.Comparator.nullsLast(Long::compare)));
    return new RosterResponse(
        session.getId(),
        session.getCourse().getId(),
        session.getCourse().getTitle(),
        session.getStartDate(),
        session.getPlace(),
        session.getCapacity(),
        session.getStatus().name(),
        isCompletionOpen(session),
        confirmed,
        waiting);
  }

  /** 수료 처리. 회차 락을 먼저 잡고(모든 상태 쓰기는 회차 락 먼저), 처리 가능한 확정자만 바꾸며 나머지는 사유로 모아 돌려준다. 사유 때문에 롤백하지 않는다. */
  @Transactional
  public CompletionResult complete(
      Long sessionId, Collection<Long> employeeIds, CompletionOutcome outcome) {
    sessionRepository.setLockTimeout(EnrollmentService.LOCK_TIMEOUT);
    CourseSession session =
        sessionRepository
            .findByIdForUpdate(sessionId)
            .orElseThrow(() -> new SessionNotFoundException(sessionId));
    if (!isCompletionOpen(session)) {
      throw new CompletionNotOpenException(sessionId);
    }

    LinkedHashSet<Long> ids = new LinkedHashSet<>(employeeIds);
    Map<Long, List<Enrollment>> byEmployee = new HashMap<>();
    // 락을 잡은 뒤에 읽어야 최신 상태다
    for (Enrollment e : enrollmentRepository.findBySessionIdAndEmployeeIdIn(sessionId, ids)) {
      byEmployee.computeIfAbsent(e.getEmployeeId(), k -> new ArrayList<>()).add(e);
    }

    List<Long> processed = new ArrayList<>();
    List<CompletionResult.Failure> failed = new ArrayList<>();
    for (Long employeeId : ids) {
      List<Enrollment> list = byEmployee.getOrDefault(employeeId, List.of());
      Enrollment confirmed = find(list, EnrollmentStatus.CONFIRMED);
      if (confirmed != null) {
        if (outcome == CompletionOutcome.COMPLETED) {
          confirmed.complete();
        } else {
          confirmed.markIncomplete();
        }
        processed.add(employeeId);
      } else if (find(list, EnrollmentStatus.WAITING) != null) {
        failed.add(new CompletionResult.Failure(employeeId, "NOT_CONFIRMED"));
      } else if (find(list, EnrollmentStatus.COMPLETED) != null
          || find(list, EnrollmentStatus.INCOMPLETE) != null) {
        failed.add(new CompletionResult.Failure(employeeId, "ALREADY_PROCESSED"));
      } else {
        failed.add(new CompletionResult.Failure(employeeId, "NOT_ENROLLED"));
      }
    }
    return new CompletionResult(processed, failed);
  }

  private static Enrollment find(List<Enrollment> list, EnrollmentStatus status) {
    for (Enrollment e : list) {
      if (e.getStatus() == status) {
        return e;
      }
    }
    return null;
  }

  /** D-09: 회차는 하루 교육으로 보고 시작일 당일부터 처리할 수 있다. */
  private boolean isCompletionOpen(CourseSession session) {
    return !LocalDate.now(clock).isBefore(session.getStartDate());
  }
}
