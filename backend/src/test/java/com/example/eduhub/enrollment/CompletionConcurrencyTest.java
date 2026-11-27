package com.example.eduhub.enrollment;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.eduhub.course.domain.Course;
import com.example.eduhub.course.domain.CourseRepository;
import com.example.eduhub.course.domain.CourseSessionRepository;
import com.example.eduhub.enrollment.api.CompletionResult;
import com.example.eduhub.enrollment.application.EnrollmentService;
import com.example.eduhub.enrollment.application.RosterService;
import com.example.eduhub.enrollment.domain.CompletionOutcome;
import com.example.eduhub.enrollment.domain.EnrollmentRepository;
import com.example.eduhub.enrollment.domain.EnrollmentStatus;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/** FR-08 수료 처리 동시성: 같은 요청 2개가 겹쳐도 한쪽만 처리하고 다른 쪽은 ALREADY_PROCESSED. */
@SpringBootTest
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.EMBEDDED)
@Import(PrefilledPoolTestConfig.class)
@ActiveProfiles("test")
class CompletionConcurrencyTest {

  private static LocalDate today() {
    return LocalDate.now(ZoneId.of("Asia/Seoul"));
  }

  @Autowired private RosterService rosterService;
  @Autowired private EnrollmentService enrollmentService;
  @Autowired private EnrollmentRepository enrollmentRepository;
  @Autowired private CourseRepository courseRepository;
  @Autowired private CourseSessionRepository sessionRepository;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private TransactionTemplate tx;

  @Test
  @DisplayName("같은 처리 요청 2개 동시: 한쪽 processed, 다른 쪽 ALREADY_PROCESSED, 상태는 한 번만 바뀐다")
  void sameRequestTwice() throws Exception {
    Long sessionId =
        tx.execute(s -> {
          Course course = courseRepository.save(new Course("수료 동시성 과정", "BACKEND", "d"));
          return sessionRepository.save(course.addSession(today().plusDays(1), "A동", 10)).getId();
        });
    long employeeId = System.nanoTime();
    Long enrollmentId = enrollmentService.enroll(sessionId, employeeId).id();
    jdbcTemplate.update(
        "UPDATE course_session SET start_date = ? WHERE id = ?",
        java.sql.Date.valueOf(today()),
        sessionId);

    ExecutorService pool = Executors.newFixedThreadPool(2);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch go = new CountDownLatch(1);
    try {
      List<Future<CompletionResult>> futures = new ArrayList<>();
      for (int i = 0; i < 2; i++) {
        futures.add(
            pool.submit(() -> {
              ready.countDown();
              go.await();
              return rosterService.complete(
                  sessionId, List.of(employeeId), CompletionOutcome.COMPLETED);
            }));
      }
      assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
      go.countDown();
      List<CompletionResult> results = new ArrayList<>();
      for (Future<CompletionResult> f : futures) {
        results.add(f.get(60, TimeUnit.SECONDS));
      }

      long processedCount = results.stream().filter(r -> r.processed().size() == 1).count();
      List<CompletionResult> failedOnes =
          results.stream().filter(r -> r.processed().isEmpty()).toList();
      assertThat(processedCount).isEqualTo(1);
      assertThat(failedOnes).hasSize(1);
      assertThat(failedOnes.get(0).failed()).hasSize(1);
      assertThat(failedOnes.get(0).failed().get(0).reason()).isEqualTo("ALREADY_PROCESSED");
      assertThat(enrollmentRepository.findById(enrollmentId).orElseThrow().getStatus())
          .isEqualTo(EnrollmentStatus.COMPLETED);
    } finally {
      pool.shutdownNow();
    }
  }
}
