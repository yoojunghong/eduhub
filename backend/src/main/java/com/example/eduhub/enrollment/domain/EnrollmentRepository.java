package com.example.eduhub.enrollment.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EnrollmentRepository extends JpaRepository<Enrollment, Long> {

  /** 같은 과정에 진행 중(확정·대기) 신청이 있는지 (D-01). */
  @Query(
      """
      select count(e) > 0 from Enrollment e
      where e.courseId = :courseId and e.employeeId = :employeeId
        and e.status in (com.example.eduhub.enrollment.domain.EnrollmentStatus.CONFIRMED,
                         com.example.eduhub.enrollment.domain.EnrollmentStatus.WAITING)
      """)
  boolean existsActiveInCourse(
      @Param("courseId") Long courseId, @Param("employeeId") Long employeeId);

  @Query("select count(e) from Enrollment e where e.session.id = :sessionId and e.status = :status")
  long countBySessionIdAndStatus(
      @Param("sessionId") Long sessionId, @Param("status") EnrollmentStatus status);

  /** 회차별 확정 인원을 한 번에 집계한다 (N+1 방지). 확정이 없는 회차는 결과에 없다. */
  @Query(
      """
      select new com.example.eduhub.enrollment.domain.SessionCount(e.session.id, count(e))
      from Enrollment e
      where e.session.id in :sessionIds
        and e.status = com.example.eduhub.enrollment.domain.EnrollmentStatus.CONFIRMED
      group by e.session.id
      """)
  List<SessionCount> countConfirmedBySessionIds(@Param("sessionIds") List<Long> sessionIds);
}
