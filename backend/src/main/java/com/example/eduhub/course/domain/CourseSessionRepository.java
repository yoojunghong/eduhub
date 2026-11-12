package com.example.eduhub.course.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 회차 저장용. 회차 생성은 {@link Course#addSession}으로 하고, 저장만 이 저장소로 한다. */
public interface CourseSessionRepository extends JpaRepository<CourseSession, Long> {

  /**
   * 수강 신청용 회차 행 잠금 (SELECT … FOR UPDATE). 회차 행만 잠그도록 과정은 join fetch 하지 않고 lazy로 읽는다. PostgreSQL은
   * FOR UPDATE에 대기 시간을 지정할 수 없으므로, 락 조회 직전에 {@link #setLockTimeout}을 같은 트랜잭션에서 호출한다.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select s from CourseSession s where s.id = :id")
  Optional<CourseSession> findByIdForUpdate(@Param("id") Long id);

  /** 현재 트랜잭션에서만 락 대기 시간을 정한다 (SET LOCAL lock_timeout 과 같음). 예: "3s". */
  @Query(value = "select set_config('lock_timeout', :timeout, true)", nativeQuery = true)
  String setLockTimeout(@Param("timeout") String timeout);
}
