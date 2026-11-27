package com.example.eduhub.enrollment.application;

import com.example.eduhub.enrollment.api.MyEnrollmentResponse;
import com.example.eduhub.enrollment.domain.EnrollmentRepository;
import com.example.eduhub.enrollment.domain.EnrollmentStatus;
import com.example.eduhub.enrollment.domain.MyEnrollmentRow;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 내 신청 내역 조회 (FR-07). */
@Service
@Transactional(readOnly = true)
public class MyEnrollmentQueryService {

  private static final ZoneId KST = ZoneId.of("Asia/Seoul");

  private final EnrollmentRepository enrollmentRepository;
  private final Clock clock;

  public MyEnrollmentQueryService(EnrollmentRepository enrollmentRepository, Clock clock) {
    this.enrollmentRepository = enrollmentRepository;
    this.clock = clock;
  }

  public List<MyEnrollmentResponse> findMyEnrollments(Long employeeId, EnrollmentStatus status) {
    LocalDate today = LocalDate.now(clock);
    return enrollmentRepository.findMyEnrollments(employeeId, status).stream()
        .map(row -> toResponse(row, today))
        .toList();
  }

  private static MyEnrollmentResponse toResponse(MyEnrollmentRow row, LocalDate today) {
    boolean active =
        row.status() == EnrollmentStatus.CONFIRMED || row.status() == EnrollmentStatus.WAITING;
    return new MyEnrollmentResponse(
        row.enrollmentId(),
        row.sessionId(),
        row.courseId(),
        row.courseTitle(),
        row.startDate(),
        row.place(),
        row.status().name(),
        row.waitingNumber() == null ? null : Math.toIntExact(row.waitingNumber()),
        row.enrolledAt().atZoneSameInstant(KST).toOffsetDateTime(),
        active && today.isBefore(row.startDate()));
  }
}
