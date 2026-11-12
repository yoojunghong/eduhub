package com.example.eduhub.enrollment;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.eduhub.course.domain.Course;
import com.example.eduhub.course.domain.CourseRepository;
import com.example.eduhub.course.domain.CourseSession;
import com.example.eduhub.course.domain.CourseSessionRepository;
import com.example.eduhub.enrollment.application.EnrollmentService;
import com.example.eduhub.enrollment.domain.DuplicateEnrollmentException;
import com.example.eduhub.enrollment.domain.EnrollmentRepository;
import com.example.eduhub.enrollment.domain.EnrollmentStatus;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/** NFR-01: 동시 신청에도 정원을 넘지 않고, 같은 과정에 진행 중 신청은 1건만 남는다. */
@SpringBootTest
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.EMBEDDED)
@Import(PrefilledPoolTestConfig.class)
@ActiveProfiles("test")
class EnrollmentConcurrencyTest {

  private static final LocalDate START = LocalDate.now().plusDays(7);

  @Autowired private EnrollmentService enrollmentService;
  @Autowired private EnrollmentRepository enrollmentRepository;
  @Autowired private CourseRepository courseRepository;
  @Autowired private CourseSessionRepository sessionRepository;
  @Autowired private TransactionTemplate tx;

  /** 테스트마다 새 과정을 만들어 서로 간섭하지 않게 한다. 회차 id 목록을 돌려준다. */
  private List<Long> createSessions(int... capacities) {
    return tx.execute(status -> {
      Course course = courseRepository.save(new Course("동시성 테스트 과정", "BACKEND", "설명"));
      List<Long> ids = new ArrayList<>();
      for (int capacity : capacities) {
        CourseSession session = sessionRepository.save(course.addSession(START, "A동", capacity));
        ids.add(session.getId());
      }
      return ids;
    });
  }

  /** 모든 작업이 준비된 뒤 동시에 출발시키고, 각 결과(성공이면 null, 실패면 예외)를 모은다. */
  private List<Throwable> runConcurrently(List<Runnable> jobs) throws Exception {
    int threads = Math.min(32, jobs.size());
    ExecutorService pool = Executors.newFixedThreadPool(threads);
    // 스레드 수만큼만 동시에 대기할 수 있다. 나머지 작업은 앞선 작업이 끝나는 대로 이어서 실행된다
    CountDownLatch ready = new CountDownLatch(threads);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<Throwable>> futures = new ArrayList<>();
      for (Runnable job : jobs) {
        Callable<Throwable> task =
            () -> {
              ready.countDown();
              start.await();
              try {
                job.run();
                return null;
              } catch (Throwable t) {
                return t;
              }
            };
        futures.add(pool.submit(task));
      }
      assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      List<Throwable> results = new ArrayList<>();
      for (Future<Throwable> f : futures) {
        results.add(f.get(60, TimeUnit.SECONDS));
      }
      return results;
    } finally {
      pool.shutdownNow();
    }
  }

  @Test
  @DisplayName("정원 10 회차에 서로 다른 100명이 동시에 신청해도 확정 10, 대기 90")
  void hundredEmployees_capacityTen_confirmedTen() throws Exception {
    Long sessionId = createSessions(10).get(0);
    List<Runnable> jobs = new ArrayList<>();
    for (long employeeId = 1; employeeId <= 100; employeeId++) {
      long id = employeeId;
      jobs.add(() -> enrollmentService.enroll(sessionId, id));
    }

    List<Throwable> failures = runConcurrently(jobs).stream().filter(t -> t != null).toList();

    assertThat(failures).isEmpty();
    assertThat(
            enrollmentRepository.countBySessionIdAndStatus(sessionId, EnrollmentStatus.CONFIRMED))
        .isEqualTo(10);
    assertThat(enrollmentRepository.countBySessionIdAndStatus(sessionId, EnrollmentStatus.WAITING))
        .isEqualTo(90);
  }

  @Test
  @DisplayName("같은 직원이 같은 회차에 10번 동시에 신청하면 진행 중 1건만 남고 나머지는 중복 예외")
  void sameEmployee_tenTimes_onlyOneActive() throws Exception {
    Long sessionId = createSessions(10).get(0);
    List<Runnable> jobs = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      jobs.add(() -> enrollmentService.enroll(sessionId, 7L));
    }

    List<Throwable> failures = runConcurrently(jobs).stream().filter(t -> t != null).toList();

    assertThat(failures).hasSize(9).allMatch(t -> t instanceof DuplicateEnrollmentException);
    long active =
        enrollmentRepository.countBySessionIdAndStatus(sessionId, EnrollmentStatus.CONFIRMED)
            + enrollmentRepository.countBySessionIdAndStatus(sessionId, EnrollmentStatus.WAITING);
    assertThat(active).isEqualTo(1);
  }

  @Test
  @DisplayName("같은 직원이 같은 과정의 다른 회차 둘에 동시에 신청하면 1건만 성공하고 나머지는 중복 예외")
  void sameEmployee_differentSessionsOfSameCourse_onlyOneActive() throws Exception {
    List<Long> sessionIds = createSessions(10, 10);
    List<Runnable> jobs = new ArrayList<>();
    for (int i = 0; i < 10; i++) {
      Long sessionId = sessionIds.get(i % 2);
      jobs.add(() -> enrollmentService.enroll(sessionId, 9L));
    }

    List<Throwable> failures = runConcurrently(jobs).stream().filter(t -> t != null).toList();

    assertThat(failures).hasSize(9).allMatch(t -> t instanceof DuplicateEnrollmentException);
    long active = 0;
    for (Long sessionId : sessionIds) {
      active +=
          enrollmentRepository.countBySessionIdAndStatus(sessionId, EnrollmentStatus.CONFIRMED);
    }
    assertThat(active).isEqualTo(1);
  }
}
