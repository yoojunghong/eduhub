package com.example.eduhub.enrollment.domain;

import java.util.List;
import java.util.Optional;
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

  /** 회차별 대기 인원을 한 번에 집계한다 (N+1 방지). 대기자가 없는 회차는 결과에 없다. */
  @Query(
      """
      select new com.example.eduhub.enrollment.domain.SessionCount(e.session.id, count(e))
      from Enrollment e
      where e.session.id in :sessionIds
        and e.status = com.example.eduhub.enrollment.domain.EnrollmentStatus.WAITING
      group by e.session.id
      """)
  List<SessionCount> countWaitingBySessionIds(@Param("sessionIds") List<Long> sessionIds);

  /** 같은 회차 대기자 중 id가 이 신청 이하인 수 = 대기 순번 (자기 자신 포함). */
  @Query(
      """
      select count(e) from Enrollment e
      where e.session.id = :sessionId and e.id <= :id
        and e.status = com.example.eduhub.enrollment.domain.EnrollmentStatus.WAITING
      """)
  long countWaitingAhead(@Param("sessionId") Long sessionId, @Param("id") Long id);

  /** 서비스는 findFirstWaiting을 쓴다. 회차 락 안에서만 호출한다. */
  Optional<Enrollment> findFirstBySessionIdAndStatusOrderByIdAsc(
      Long sessionId, EnrollmentStatus status);

  /** 승급 대상: 같은 회차 대기자 중 id가 가장 작은 1건. */
  default Optional<Enrollment> findFirstWaiting(Long sessionId) {
    return findFirstBySessionIdAndStatusOrderByIdAsc(sessionId, EnrollmentStatus.WAITING);
  }

  /** 락 전 1단계 조회용 스칼라 쿼리: 엔티티를 영속성 컨텍스트에 올리지 않는다 (본인 신청만). */
  @Query("select e.session.id from Enrollment e where e.id = :id and e.employeeId = :employeeId")
  Optional<Long> findSessionIdByIdAndEmployeeId(
      @Param("id") Long id, @Param("employeeId") Long employeeId);
}
