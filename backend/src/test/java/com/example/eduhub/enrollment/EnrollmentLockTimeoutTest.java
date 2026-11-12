package com.example.eduhub.enrollment;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.eduhub.course.domain.Course;
import com.example.eduhub.course.domain.CourseRepository;
import com.example.eduhub.course.domain.CourseSessionRepository;
import com.example.eduhub.enrollment.application.EnrollmentService;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import java.time.LocalDate;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/** 설계 2장·5장: 회차 락 대기는 약 3초까지만, 넘으면 PessimisticLockingFailureException(503 SESSION_BUSY). */
@SpringBootTest
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.EMBEDDED)
@ActiveProfiles("test")
class EnrollmentLockTimeoutTest {

  @Autowired private EnrollmentService enrollmentService;
  @Autowired private CourseRepository courseRepository;
  @Autowired private CourseSessionRepository sessionRepository;
  @Autowired private TransactionTemplate tx;

  @Test
  @DisplayName("다른 트랜잭션이 회차 락을 5초 쥐고 있으면 신청은 약 3초 뒤 락 실패 예외")
  void lockHeldLonger_enrollFailsAroundThreeSeconds() throws Exception {
    Long sessionId =
        tx.execute(s -> {
          Course course = courseRepository.save(new Course("락 타임아웃 과정", "BACKEND", "설명"));
          return sessionRepository
              .save(course.addSession(LocalDate.now().plusDays(7), "A동", 10))
              .getId();
        });

    CountDownLatch locked = new CountDownLatch(1);
    CompletableFuture<Void> holder =
        CompletableFuture.runAsync(() ->
            tx.executeWithoutResult(s -> {
              sessionRepository.findByIdForUpdate(sessionId).orElseThrow();
              locked.countDown();
              try {
                Thread.sleep(5000);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
            }));
    assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();

    long begin = System.nanoTime();
    Throwable thrown = null;
    try {
      enrollmentService.enroll(sessionId, 1L);
    } catch (Throwable t) {
      thrown = t;
    }
    long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin);
    holder.get(10, TimeUnit.SECONDS);

    assertThat(thrown).isInstanceOf(PessimisticLockingFailureException.class);
    assertThat(elapsedMs).isBetween(2000L, 4500L);
  }
}
