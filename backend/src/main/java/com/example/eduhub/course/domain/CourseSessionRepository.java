package com.example.eduhub.course.domain;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

/** 회차 저장용. 회차 생성은 {@link Course#addSession}으로 하고, 저장만 이 저장소로 한다. */
public interface CourseSessionRepository extends JpaRepository<CourseSession, Long> {

  /** 수강 신청용 회차 행 잠금 (SELECT … FOR UPDATE, 대기 최대 3초). 과정도 함께 가져온다. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "3000"))
  @Query("select s from CourseSession s join fetch s.course where s.id = :id")
  Optional<CourseSession> findByIdForUpdate(@Param("id") Long id);
}
