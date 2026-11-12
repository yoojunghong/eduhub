package com.example.eduhub.enrollment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.eduhub.course.domain.Course;
import com.example.eduhub.course.domain.CourseRepository;
import com.example.eduhub.course.domain.CourseSession;
import com.example.eduhub.course.domain.CourseSessionRepository;
import com.example.eduhub.enrollment.application.EnrollmentService;
import com.example.eduhub.enrollment.domain.DuplicateEnrollmentException;
import com.example.eduhub.enrollment.domain.Enrollment;
import com.example.eduhub.enrollment.domain.EnrollmentRepository;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 유일 인덱스(ux_enrollment_active_course)가 최종 방어선임을 결정적으로 확인한다. A 트랜잭션이 같은 과정 회차1에 INSERT 후 커밋하지 않고
 * 기다리는 동안 B가 회차2에 같은 직원으로 신청한다. B는 existsActiveInCourse 를 통과한 뒤 유일 인덱스에서 대기하다가, A 커밋 후 위반으로 끝나야 한다.
 */
@SpringBootTest
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.EMBEDDED)
@Import(PrefilledPoolTestConfig.class)
@ActiveProfiles("test")
class EnrollmentUniqueIndexConflictTest {

  @Autowired private EnrollmentService enrollmentService;
  @Autowired private EnrollmentRepository enrollmentRepository;
  @Autowired private CourseRepository courseRepository;
  @Autowired private CourseSessionRepository sessionRepository;
  @Autowired private TransactionTemplate tx;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("다른 회차 동시 신청: 유일 인덱스에서 기다리다 A 커밋 후 DuplicateEnrollmentException")
  void uncommittedInsert_otherSession_ends409() throws Exception {
    List<Long> ids =
        tx.execute(status -> {
          Course course = courseRepository.save(new Course("유일 인덱스 과정", "BACKEND", "설명"));
          LocalDate start = LocalDate.now().plusDays(30);
          CourseSession s1 = sessionRepository.save(course.addSession(start, "A동", 10));
          CourseSession s2 = sessionRepository.save(course.addSession(start, "B동", 10));
          return List.of(s1.getId(), s2.getId());
        });
    long employeeId = 4242L;

    CountDownLatch inserted = new CountDownLatch(1);
    CountDownLatch commit = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<?> a =
          pool.submit(() ->
              tx.executeWithoutResult(status -> {
                CourseSession s1 = sessionRepository.findById(ids.get(0)).orElseThrow();
                enrollmentRepository.saveAndFlush(
                    Enrollment.confirm(s1, employeeId, OffsetDateTime.now()));
                inserted.countDown();
                try {
                  commit.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
              }));
      assertThat(inserted.await(10, TimeUnit.SECONDS)).isTrue();

      Future<?> b = pool.submit(() -> enrollmentService.enroll(ids.get(1), employeeId));
      // A 커밋 전에 B가 A의 트랜잭션 ID 락을 기다리는 중임을 DB에서 확인한다 (lock_timeout 3s 안에서 최대 2.5초)
      long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(2500);
      boolean waiting = false;
      while (System.nanoTime() < deadline) {
        Integer count =
            jdbcTemplate.queryForObject(
                "select count(*) from pg_stat_activity where wait_event_type = 'Lock'"
                    + " and wait_event = 'transactionid' and datname = current_database()",
                Integer.class);
        if (count != null && count >= 1) {
          waiting = true;
          break;
        }
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(50));
      }
      assertThat(waiting).as("B가 A의 트랜잭션을 기다리는 상태가 보여야 한다").isTrue();
      assertThat(b.isDone()).as("A 커밋 전에는 B가 끝나지 않아야 한다").isFalse();

      commit.countDown();
      a.get(10, TimeUnit.SECONDS);

      assertThatThrownBy(() -> b.get(10, TimeUnit.SECONDS))
          .cause()
          .isInstanceOf(DuplicateEnrollmentException.class)
          // 기존 존재 확인 경로는 cause 가 없다. 유일 인덱스 경로만 DataIntegrityViolationException 이 붙는다
          .hasCauseInstanceOf(DataIntegrityViolationException.class);
    } finally {
      commit.countDown();
      pool.shutdownNow();
    }
  }
}
