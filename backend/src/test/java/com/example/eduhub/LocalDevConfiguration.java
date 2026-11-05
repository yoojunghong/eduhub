package com.example.eduhub;

import com.example.eduhub.course.domain.Course;
import com.example.eduhub.course.domain.CourseRepository;
import com.example.eduhub.course.domain.CourseSession;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import jakarta.persistence.EntityManager;
import java.io.IOException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.support.TransactionTemplate;

/** {@link LocalEduhubApplication} 전용 설정: 내장 PostgreSQL과 화면 확인용 샘플 과정. */
@TestConfiguration(proxyBeanMethods = false)
class LocalDevConfiguration {

  @Bean(destroyMethod = "close")
  EmbeddedPostgres embeddedPostgres() throws IOException {
    return EmbeddedPostgres.builder().start();
  }

  @Bean
  DataSource dataSource(EmbeddedPostgres embeddedPostgres) {
    return embeddedPostgres.getPostgresDatabase();
  }

  @Bean
  ApplicationRunner sampleCourses(
      CourseRepository courseRepository, EntityManager em, TransactionTemplate tx) {
    return args ->
        tx.executeWithoutResult(status -> {
          if (courseRepository.count() > 0) {
            return;
          }
          String[][] samples = {
            {"Spring Boot 입문", "BACKEND", "스프링 부트로 REST API 만들기"},
            {"JPA 실전", "BACKEND", "연관관계, N+1, 페치 조인"},
            {"React 기초", "FRONTEND", "컴포넌트와 상태 관리"},
            {"TypeScript 핵심", "FRONTEND", "타입으로 안전한 프론트엔드"},
            {"Docker와 배포", "DEVOPS", "컨테이너 기초와 CI/CD"},
            {"AI 코딩 도구 활용", "AI", "Claude Code로 개발 생산성 높이기"},
            {"정보보안 기본", "COMMON", "임직원 필수 보안 교육"},
            {"리더십 101", "COMMON", "신임 팀장 과정"},
            {"SQL 튜닝", "BACKEND", "실행 계획 읽기와 인덱스"},
            {"테스트 주도 개발", "BACKEND", "JUnit 5로 TDD 연습"},
            {"MUI 디자인 시스템", "FRONTEND", "MUI로 화면 빠르게 만들기"},
            {"Kubernetes 입문", "DEVOPS", "파드, 서비스, 디플로이먼트"},
          };
          List<CourseSession> sessions = new ArrayList<>();
          for (int i = 0; i < samples.length; i++) {
            Course course = new Course(samples[i][0], samples[i][1], samples[i][2]);
            for (int s = 0; s < i % 3 + 1; s++) {
              sessions.add(
                  course.addSession(
                      LocalDate.now().plusWeeks(s * 2L + 1), "본사 " + (s + 2) + "층 교육장", 20));
            }
            courseRepository.save(course);
          }
          sessions.forEach(em::persist);
          courseRepository.findAll().stream()
              .filter(c -> c.getTitle().equals("리더십 101"))
              .forEach(Course::close); // 종료된 과정은 목록에 나오지 않는지 확인용
        });
  }
}
