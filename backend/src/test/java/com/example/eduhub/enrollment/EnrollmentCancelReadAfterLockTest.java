package com.example.eduhub.enrollment;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.eduhub.course.domain.Course;
import com.example.eduhub.course.domain.CourseRepository;
import com.example.eduhub.course.domain.CourseSession;
import com.example.eduhub.course.domain.CourseSessionRepository;
import com.example.eduhub.enrollment.application.EnrollmentService;
import com.example.eduhub.enrollment.domain.EnrollmentRepository;
import com.example.eduhub.enrollment.domain.EnrollmentStatus;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import java.time.LocalDate;
import java.time.ZoneId;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * cancel()이 "회차 락을 잡은 뒤에" 신청을 읽는지 결정적으로 확인한다 (설계 5-2의 3단계, 7장 테스트 한계).
 *
 * <p>실패 시나리오: cancel이 락 전에 엔티티를 읽으면 B는 W를 WAITING으로 본다. 그러면 A가 커밋해 W가 확정이 된 뒤에도 B는 대기 취소로 처리해 승급하지
 * 않는다. 결과적으로 W2가 WAITING으로 남고 확정 수가 정원(4)보다 작아져 이 테스트가 실패한다.
 */
@SpringBootTest
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.EMBEDDED)
@Import(PrefilledPoolTestConfig.class)
@ActiveProfiles("test")
class EnrollmentCancelReadAfterLockTest {

  @Autowired private EnrollmentService service;
  @Autowired private EnrollmentRepository enrollmentRepository;
  @Autowired private CourseRepository courseRepository;
  @Autowired private CourseSessionRepository sessionRepository;
  @Autowired private TransactionTemplate tx;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("A가 회차 락을 쥐고 W를 확정으로 올린 상태에서 B가 W를 취소하면, 커밋 후 W를 확정자로 보고 W2를 승급한다")
  void cancel_readsEnrollmentAfterLock() throws Exception {
    LocalDate start = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(7);
    Long sessionId =
        tx.execute(status -> {
          Course course = courseRepository.save(new Course("락 후 읽기 과정", "BACKEND", "설명"));
          return sessionRepository.save(course.addSession(start, "A동", 4)).getId();
        });
    Long c1 = service.enroll(sessionId, 7001L).id();
    for (long e = 7002L; e <= 7004L; e++) {
      service.enroll(sessionId, e);
    }
    Long w = service.enroll(sessionId, 7005L).id();
    Long w2 = service.enroll(sessionId, 7006L).id();

    CountDownLatch locked = new CountDownLatch(1);
    CountDownLatch commit = new CountDownLatch(1);
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try {
      Future<?> a =
          pool.submit(() ->
              tx.executeWithoutResult(status -> {
                CourseSession session =
                    sessionRepository.findByIdForUpdate(sessionId).orElseThrow();
                assertThat(session.getId()).isEqualTo(sessionId);
                // C1 취소로 자리를 비우고 W를 확정으로 올린다 (cancel 승급과 같은 효과)
                enrollmentRepository.findById(c1).orElseThrow().cancel();
                enrollmentRepository.findById(w).orElseThrow().promote();
                locked.countDown();
                try {
                  commit.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
              }));
      assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

      Future<?> b = pool.submit(() -> service.cancel(w, 7005L));

      // B가 A의 트랜잭션(회차 행 락)을 기다리는 중임을 DB에서 확인한다 (lock_timeout 3s 안에서 최대 2.5초)
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
      assertThat(waiting).as("B가 회차 락에서 기다리는 상태가 보여야 한다").isTrue();
      assertThat(b.isDone()).as("A 커밋 전에는 B가 끝나지 않아야 한다").isFalse();

      commit.countDown();
      a.get(10, TimeUnit.SECONDS);
      b.get(10, TimeUnit.SECONDS);
    } finally {
      commit.countDown();
      pool.shutdownNow();
    }

    assertThat(statusOf(c1)).isEqualTo(EnrollmentStatus.CANCELED);
    assertThat(statusOf(w)).isEqualTo(EnrollmentStatus.CANCELED);
    assertThat(statusOf(w2)).isEqualTo(EnrollmentStatus.CONFIRMED);
    Integer confirmed =
        jdbcTemplate.queryForObject(
            "select count(*) from enrollment where session_id = ? and status = 'CONFIRMED'",
            Integer.class,
            sessionId);
    assertThat(confirmed).isEqualTo(4);
  }

  private EnrollmentStatus statusOf(Long id) {
    return enrollmentRepository.findById(id).orElseThrow().getStatus();
  }
}
