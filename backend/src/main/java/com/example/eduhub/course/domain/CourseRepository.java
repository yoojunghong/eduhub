package com.example.eduhub.course.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CourseRepository extends JpaRepository<Course, Long> {

  /**
   * 과정 목록 검색 (FR-01). keyword는 제목 부분 일치(대소문자 무시), category는 정확히 일치. 조건을 쓰지 않을 때는 null 대신 빈 문자열을 넘긴다
   * (PostgreSQL이 null 파라미터의 타입을 추론하지 못하는 문제를 피한다).
   */
  @Query(
      value =
          """
          select new com.example.eduhub.course.domain.CourseSummaryRow(
              c.id, c.title, c.category, c.status, count(s))
          from Course c left join c.sessions s
          where c.status in :statuses
            and lower(c.title) like lower(concat('%', :keyword, '%'))
            and (:category = '' or c.category = :category)
          group by c.id, c.title, c.category, c.status
          """,
      countQuery =
          """
          select count(c) from Course c
          where c.status in :statuses
            and lower(c.title) like lower(concat('%', :keyword, '%'))
            and (:category = '' or c.category = :category)
          """)
  Page<CourseSummaryRow> search(
      @Param("statuses") Collection<CourseStatus> statuses,
      @Param("keyword") String keyword,
      @Param("category") String category,
      Pageable pageable);

  /** 한 상태만 검색한다 (임직원 목록은 OPEN). */
  default Page<CourseSummaryRow> search(
      CourseStatus status, String keyword, String category, Pageable pageable) {
    return search(List.of(status), keyword, category, pageable);
  }

  @Query("select c from Course c left join fetch c.sessions where c.id = :id")
  Optional<Course> findWithSessionsById(@Param("id") Long id);
}
