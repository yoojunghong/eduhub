package com.example.eduhub.course.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

// Docker 없이 실제 PostgreSQL을 테스트 중에만 띄운다. Flyway V1 적용 후 ddl-auto=validate 로 엔티티와 스키마를 대조한다
@DataJpaTest
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
}
