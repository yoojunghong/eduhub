package com.example.eduhub.course.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import java.time.LocalDate;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

// Docker 없이 실제 PostgreSQL을 테스트 중에만 띄운다. Flyway V1 적용 후 ddl-auto=validate 로 엔티티와 스키마를 대조한다
@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.EMBEDDED)
@ActiveProfiles("test")
class CourseRepositoryTest {

  @Autowired private CourseRepository courseRepository;
  @Autowired private TestEntityManager em;

  @Test
  void savesCourseWithSessionAndFindsIt() {
    Course course = new Course("Spring Boot 입문", "BACKEND", "스프링 부트 기초");
    CourseSession session = course.addSession(LocalDate.of(2026, 12, 1), "본사 3층 교육장", 20);
    courseRepository.save(course);
    em.persist(session);
    em.flush();
    em.clear();

    Course found = courseRepository.findById(course.getId()).orElseThrow();

    assertThat(found.getTitle()).isEqualTo("Spring Boot 입문");
    assertThat(found.getStatus()).isEqualTo(CourseStatus.OPEN);
    assertThat(found.getSessions()).hasSize(1);
    CourseSession foundSession = found.getSessions().get(0);
    assertThat(foundSession.getPlace()).isEqualTo("본사 3층 교육장");
    assertThat(foundSession.getCapacity()).isEqualTo(20);
    assertThat(foundSession.getStatus()).isEqualTo(SessionStatus.OPEN);
  }

  @Test
  void searchMatchesTitleIgnoringCaseAndPartially() {
    persistCourse("Spring Boot 입문", "BACKEND", 0);
    persistCourse("JPA 실전", "BACKEND", 0);

    Page<CourseSummaryRow> result =
        courseRepository.search(CourseStatus.OPEN, "spring", "", PageRequest.of(0, 10));

    assertThat(result.getContent())
        .extracting(CourseSummaryRow::title)
        .containsExactly("Spring Boot 입문");
  }

  @Test
  void searchFiltersByCategory() {
    persistCourse("Spring Boot 입문", "BACKEND", 0);
    persistCourse("React 기초", "FRONTEND", 0);

    Page<CourseSummaryRow> result =
        courseRepository.search(CourseStatus.OPEN, "", "FRONTEND", PageRequest.of(0, 10));

    assertThat(result.getContent()).extracting(CourseSummaryRow::title).containsExactly("React 기초");
  }

  @Test
  void searchWithoutConditionsReturnsOnlyOpenCourses() {
    persistCourse("Spring Boot 입문", "BACKEND", 0);
    persistCourse("React 기초", "FRONTEND", 0).close();
    em.flush();

    Page<CourseSummaryRow> result =
        courseRepository.search(CourseStatus.OPEN, "", "", PageRequest.of(0, 10));

    assertThat(result.getContent())
        .extracting(CourseSummaryRow::title)
        .containsExactly("Spring Boot 입문");
  }

  @Test
  void searchPagesResultsAndCountsTotal() {
    for (int i = 1; i <= 12; i++) {
      persistCourse("과정 " + i, "BACKEND", 0);
    }

    Page<CourseSummaryRow> result =
        courseRepository.search(CourseStatus.OPEN, "", "", PageRequest.of(1, 5, Sort.by("id")));

    assertThat(result.getContent())
        .extracting(CourseSummaryRow::title)
        .containsExactly("과정 6", "과정 7", "과정 8", "과정 9", "과정 10");
    assertThat(result.getTotalElements()).isEqualTo(12);
  }

  @Test
  void searchCountsSessionsWithoutLoadingThemPerCourse() {
    persistCourse("Spring Boot 입문", "BACKEND", 3);
    persistCourse("JPA 실전", "BACKEND", 1);
    persistCourse("React 기초", "FRONTEND", 0);
    em.flush();
    em.clear();
    Statistics statistics =
        em.getEntityManager()
            .getEntityManagerFactory()
            .unwrap(SessionFactory.class)
            .getStatistics();
    statistics.clear();

    Page<CourseSummaryRow> result =
        courseRepository.search(CourseStatus.OPEN, "", "", PageRequest.of(0, 10, Sort.by("id")));

    assertThat(result.getContent())
        .extracting(CourseSummaryRow::sessionCount)
        .containsExactly(3L, 1L, 0L);
    // 목록 1번 + (필요하면) 개수 1번. 과정 수만큼 회차 조회가 더 나가면 N+1 이다 (NFR-02)
    assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(2);
  }

  @Test
  void searchWithAllStatusesIncludesClosedCourses() {
    persistCourse("Spring Boot 입문", "BACKEND", 1);
    persistCourse("리더십 101", "COMMON", 0).close();
    em.flush();
    em.clear();

    Page<CourseSummaryRow> open =
        courseRepository.search(CourseStatus.OPEN, "", "", PageRequest.of(0, 10, Sort.by("id")));
    Page<CourseSummaryRow> all =
        courseRepository.search(
            List.of(CourseStatus.values()), "", "", PageRequest.of(0, 10, Sort.by("id")));

    assertThat(open.getContent())
        .extracting(CourseSummaryRow::title)
        .containsExactly("Spring Boot 입문");
    assertThat(all.getContent())
        .extracting(CourseSummaryRow::title)
        .containsExactly("Spring Boot 입문", "리더십 101");
    assertThat(all.getTotalElements()).isEqualTo(2);
  }

  private Course persistCourse(String title, String category, int sessionCount) {
    Course course = new Course(title, category, null);
    em.persist(course);
    for (int i = 0; i < sessionCount; i++) {
      em.persist(course.addSession(LocalDate.of(2026, 12, 1).plusDays(i), "교육장", 20));
    }
    return course;
  }
}
