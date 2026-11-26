package com.example.eduhub.enrollment;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.eduhub.course.domain.Course;
import com.example.eduhub.course.domain.CourseRepository;
import com.example.eduhub.course.domain.CourseSession;
import com.example.eduhub.course.domain.CourseSessionRepository;
import com.example.eduhub.enrollment.api.EnrollmentResponse;
import com.example.eduhub.enrollment.application.EnrollmentService;
import com.example.eduhub.enrollment.domain.Enrollment;
import com.example.eduhub.enrollment.domain.EnrollmentRepository;
import com.example.eduhub.enrollment.domain.EnrollmentStatus;
import com.example.eduhub.enrollment.domain.WaitlistFullException;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * FR-05/06 대기·취소 동시성: 대기 순번 중복 없음, 동시 취소 시 승급 정확성, 취소와 신청이 겹쳐도 새치기 없음.
 *
 * <p>테스트의 테스트: EnrollmentService.cancel의 findByIdForUpdate를 findById로 바꾸면 ②③이 실패해야 한다(개발자가 직접 확인).
 * 단 ②는 락을 빼면 같은 대기자가 중복 승급되어 실패할 수 있으나 타이밍 의존이므로 3회 이상 반복해 판단한다. ③은 취소·승급이 한 트랜잭션에서 원자적으로 커밋되어 락
 * 없이도 새치기가 생기지 않을 수 있으므로, 락 제거 검출용이라기보다 "어느 순서든 새치기 없음"을 검증하는 시나리오다.
 */
@SpringBootTest
@AutoConfigureEmbeddedDatabase(provider = AutoConfigureEmbeddedDatabase.DatabaseProvider.EMBEDDED)
@Import(PrefilledPoolTestConfig.class)
@ActiveProfiles("test")
class EnrollmentWaitlistConcurrencyTest {

  /** 승급 기간 안 (시작 1일 전보다 앞) 이도록 오늘(KST)+7일. */
  private static final LocalDate START = LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(7);

  @Autowired private EnrollmentService enrollmentService;
  @Autowired private EnrollmentRepository enrollmentRepository;
  @Autowired private CourseRepository courseRepository;
  @Autowired private CourseSessionRepository sessionRepository;
  @Autowired private TransactionTemplate tx;

  private Long createSession(int capacity) {
    return tx.execute(status -> {
      Course course = courseRepository.save(new Course("대기 동시성 테스트 과정", "BACKEND", "설명"));
      CourseSession session = sessionRepository.save(course.addSession(START, "A동", capacity));
      return session.getId();
    });
  }

  /** 직원 id from..to를 차례로 신청시켜 결과를 돌려준다 (사전 준비, 순차). */
  private List<EnrollmentResponse> enrollSequentially(Long sessionId, long from, long to) {
    List<EnrollmentResponse> result = new ArrayList<>();
    for (long employeeId = from; employeeId <= to; employeeId++) {
      result.add(enrollmentService.enroll(sessionId, employeeId));
    }
    return result;
  }

  /** 모든 작업이 준비된 뒤 동시에 출발시키고, 각 결과(성공이면 null, 실패면 예외)를 모은다. */
  private List<Throwable> runConcurrently(List<Runnable> jobs) throws Exception {
    int threads = Math.min(32, jobs.size());
    ExecutorService pool = Executors.newFixedThreadPool(threads);
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

  private long count(Long sessionId, EnrollmentStatus status) {
    return enrollmentRepository.countBySessionIdAndStatus(sessionId, status);
  }

  private EnrollmentStatus statusOf(Long enrollmentId) {
    return enrollmentRepository.findById(enrollmentId).map(Enrollment::getStatus).orElseThrow();
  }

  @Test
  @DisplayName("정원 10, 서로 다른 30명 동시 신청: 확정 10, 대기 5(순번 1~5 중복 없음), WAITLIST_FULL 15")
  void thirtyEmployees_confirmedTen_waitingFiveWithDistinctNumbers() throws Exception {
    Long sessionId = createSession(10);
    ConcurrentLinkedQueue<EnrollmentResponse> responses = new ConcurrentLinkedQueue<>();
    List<Runnable> jobs = new ArrayList<>();
    for (long employeeId = 1; employeeId <= 30; employeeId++) {
      long id = employeeId;
      jobs.add(() -> responses.add(enrollmentService.enroll(sessionId, id)));
    }

    List<Throwable> failures = runConcurrently(jobs).stream().filter(t -> t != null).toList();

    assertThat(failures)
        .as("예상 밖 예외: %s", failures)
        .hasSize(15)
        .allMatch(t -> t instanceof WaitlistFullException);
    assertThat(responses).hasSize(15);
    assertThat(count(sessionId, EnrollmentStatus.CONFIRMED)).isEqualTo(10);
    assertThat(count(sessionId, EnrollmentStatus.WAITING)).isEqualTo(5);

    List<EnrollmentResponse> waiting =
        responses.stream().filter(r -> "WAITING".equals(r.status())).toList();
    assertThat(waiting).hasSize(5);
    Set<Integer> responseNumbers =
        waiting.stream().map(EnrollmentResponse::waitingNumber).collect(Collectors.toSet());
    assertThat(responseNumbers).containsExactlyInAnyOrder(1, 2, 3, 4, 5);
    Set<Long> aheadCounts =
        waiting.stream()
            .map(r -> enrollmentRepository.countWaitingAhead(sessionId, r.id()))
            .collect(Collectors.toSet());
    assertThat(aheadCounts).containsExactlyInAnyOrder(1L, 2L, 3L, 4L, 5L);
  }

  @Test
  @DisplayName("정원 10 꽉 참 + 대기 3에서 확정자 5명이 동시에 취소하면 확정 8, 대기 0, 대기자 3명 모두 확정")
  void fiveConfirmedCancelConcurrently_threeWaitersPromoted() throws Exception {
    Long sessionId = createSession(10);
    List<EnrollmentResponse> confirmed = enrollSequentially(sessionId, 1, 10);
    List<EnrollmentResponse> waiters = enrollSequentially(sessionId, 11, 13);
    assertThat(waiters).allMatch(r -> "WAITING".equals(r.status()));

    List<Runnable> jobs = new ArrayList<>();
    for (EnrollmentResponse r : confirmed.subList(0, 5)) {
      jobs.add(() -> enrollmentService.cancel(r.id(), r.employeeId()));
    }

    List<Throwable> failures = runConcurrently(jobs).stream().filter(t -> t != null).toList();

    assertThat(failures).as("예상 밖 예외: %s", failures).isEmpty();
    assertThat(count(sessionId, EnrollmentStatus.CONFIRMED)).isEqualTo(8);
    assertThat(count(sessionId, EnrollmentStatus.WAITING)).isZero();
    assertThat(count(sessionId, EnrollmentStatus.CANCELED)).isEqualTo(5);
    for (EnrollmentResponse w : waiters) {
      assertThat(statusOf(w.id())).isEqualTo(EnrollmentStatus.CONFIRMED);
    }
  }

  @Test
  @DisplayName("정원 10 꽉 참 + 대기 1에서 확정자 1명 취소와 새 신청 10명이 겹쳐도 확정 10 유지, 대기자 승급, 새치기 없음")
  void cancelAndNewEnrollments_noQueueJumping() throws Exception {
    Long sessionId = createSession(10);
    List<EnrollmentResponse> confirmed = enrollSequentially(sessionId, 1, 10);
    EnrollmentResponse w = enrollSequentially(sessionId, 11, 11).get(0);
    assertThat(w.status()).isEqualTo("WAITING");

    ConcurrentLinkedQueue<EnrollmentResponse> newResponses = new ConcurrentLinkedQueue<>();
    List<Runnable> jobs = new ArrayList<>();
    EnrollmentResponse canceling = confirmed.get(0);
    jobs.add(() -> enrollmentService.cancel(canceling.id(), canceling.employeeId()));
    for (long employeeId = 101; employeeId <= 110; employeeId++) {
      long id = employeeId;
      jobs.add(() -> newResponses.add(enrollmentService.enroll(sessionId, id)));
    }

    List<Throwable> failures = runConcurrently(jobs).stream().filter(t -> t != null).toList();

    // 취소는 성공해야 하고, 신청 실패는 WaitlistFullException뿐이다 (503 SESSION_BUSY 등은 실패)
    assertThat(failures)
        .as("예상 밖 예외: %s", failures)
        .allMatch(t -> t instanceof WaitlistFullException);
    assertThat(newResponses).allMatch(r -> "WAITING".equals(r.status()));

    assertThat(statusOf(canceling.id())).isEqualTo(EnrollmentStatus.CANCELED);
    assertThat(statusOf(w.id())).isEqualTo(EnrollmentStatus.CONFIRMED);
    assertThat(count(sessionId, EnrollmentStatus.CONFIRMED)).isEqualTo(10);

    // 순서와 무관한 조건: 신청 10명이 모두 취소보다 먼저면 W 포함 상한 5라 4명만 대기(최종 4),
    // 취소가 먼저면 W가 이미 승급되어 5명이 대기(최종 5)
    long finalWaiting = count(sessionId, EnrollmentStatus.WAITING);
    assertThat(finalWaiting).isIn(4L, 5L);
    assertThat((long) newResponses.size()).isEqualTo(finalWaiting);
    assertThat((long) failures.size()).isEqualTo(10 - finalWaiting);
    Set<Long> aheadCounts =
        newResponses.stream()
            .map(r -> enrollmentRepository.countWaitingAhead(sessionId, r.id()))
            .collect(Collectors.toSet());
    Set<Long> expected = new java.util.HashSet<>();
    for (long n = 1; n <= finalWaiting; n++) {
      expected.add(n);
    }
    assertThat(aheadCounts).isEqualTo(expected);
    for (EnrollmentResponse r : newResponses) {
      assertThat(statusOf(r.id())).isNotEqualTo(EnrollmentStatus.CONFIRMED);
    }
  }
}
