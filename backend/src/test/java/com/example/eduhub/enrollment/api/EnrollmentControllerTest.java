package com.example.eduhub.enrollment.api;

import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.eduhub.TestAuth;
import com.example.eduhub.auth.config.JwtConfig;
import com.example.eduhub.auth.config.SecurityConfig;
import com.example.eduhub.enrollment.application.EnrollmentService;
import com.example.eduhub.enrollment.domain.CancelPeriodEndedException;
import com.example.eduhub.enrollment.domain.DuplicateEnrollmentException;
import com.example.eduhub.enrollment.domain.EnrollmentNotCancelableException;
import com.example.eduhub.enrollment.domain.EnrollmentNotFoundException;
import com.example.eduhub.enrollment.domain.SessionNotFoundException;
import com.example.eduhub.enrollment.domain.WaitlistFullException;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EnrollmentController.class)
@Import({SecurityConfig.class, JwtConfig.class})
@ActiveProfiles("test")
class EnrollmentControllerTest {

  private static final String URL = "/api/v1/sessions/10/enrollments";
  private static final String CANCEL_URL = "/api/v1/enrollments/5/cancel";

  @Autowired private MockMvc mockMvc;
  @MockitoBean private EnrollmentService enrollmentService;

  @Test
  @DisplayName("신청하면 201과 Location 헤더, 신청 결과를 돌려준다")
  void enroll_created() throws Exception {
    given(enrollmentService.enroll(10L, 1001L))
        .willReturn(
            new EnrollmentResponse(5L, 10L, 3L, 1001L, "CONFIRMED", null, OffsetDateTime.now()));

    mockMvc
        .perform(post(URL).with(TestAuth.employee(1001)))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/enrollments/5"))
        .andExpect(jsonPath("$.id").value(5))
        .andExpect(jsonPath("$.sessionId").value(10))
        .andExpect(jsonPath("$.status").value("CONFIRMED"));
  }

  @Test
  @DisplayName("토큰이 없으면 401 UNAUTHORIZED")
  void enroll_noToken_unauthorized() throws Exception {
    mockMvc
        .perform(post(URL))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    verifyNoInteractions(enrollmentService);
  }

  @Test
  @DisplayName("신청자는 요청이 아니라 토큰의 사원이다 (관리자 토큰이어도 그 사원 id)")
  void enroll_usesTokenEmployee() throws Exception {
    given(enrollmentService.enroll(10L, 2002L))
        .willReturn(
            new EnrollmentResponse(5L, 10L, 3L, 2002L, "CONFIRMED", null, OffsetDateTime.now()));

    mockMvc.perform(post(URL).with(TestAuth.admin(2002))).andExpect(status().isCreated());

    verify(enrollmentService).enroll(10L, 2002L);
  }

  @Test
  @DisplayName("없는 회차면 404 SESSION_NOT_FOUND")
  void enroll_sessionNotFound() throws Exception {
    given(enrollmentService.enroll(10L, 1001L)).willThrow(new SessionNotFoundException(10L));

    mockMvc
        .perform(post(URL).with(TestAuth.employee(1001)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
  }

  @Test
  @DisplayName("이미 진행 중 신청이 있으면 409 DUPLICATE_ENROLLMENT")
  void enroll_duplicate() throws Exception {
    given(enrollmentService.enroll(10L, 1001L)).willThrow(new DuplicateEnrollmentException(3L));

    mockMvc
        .perform(post(URL).with(TestAuth.employee(1001)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DUPLICATE_ENROLLMENT"));
  }

  @Test
  @DisplayName("회차 락을 얻지 못하면 503 SESSION_BUSY")
  void enroll_lockTimeout_serviceUnavailable() throws Exception {
    given(enrollmentService.enroll(10L, 1001L))
        .willThrow(new CannotAcquireLockException("lock timeout"));

    mockMvc
        .perform(post(URL).with(TestAuth.employee(1001)))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("SESSION_BUSY"));
  }

  @Test
  @DisplayName("대기로 등록되면 응답에 waitingNumber(숫자)가 있다")
  void enroll_waiting_hasWaitingNumber() throws Exception {
    given(enrollmentService.enroll(10L, 1001L))
        .willReturn(new EnrollmentResponse(6L, 10L, 3L, 1001L, "WAITING", 2, OffsetDateTime.now()));

    mockMvc
        .perform(post(URL).with(TestAuth.employee(1001)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("WAITING"))
        .andExpect(jsonPath("$.waitingNumber").value(2));
  }

  @Test
  @DisplayName("확정이면 waitingNumber 필드는 null이다")
  void enroll_confirmed_waitingNumberNull() throws Exception {
    given(enrollmentService.enroll(10L, 1001L))
        .willReturn(
            new EnrollmentResponse(5L, 10L, 3L, 1001L, "CONFIRMED", null, OffsetDateTime.now()));

    mockMvc
        .perform(post(URL).with(TestAuth.employee(1001)))
        .andExpect(jsonPath("$", hasKey("waitingNumber")))
        .andExpect(jsonPath("$.waitingNumber").value(nullValue()));
  }

  @Test
  @DisplayName("대기 인원이 가득 차면 409 WAITLIST_FULL")
  void enroll_waitlistFull() throws Exception {
    given(enrollmentService.enroll(10L, 1001L)).willThrow(new WaitlistFullException(10L));

    mockMvc
        .perform(post(URL).with(TestAuth.employee(1001)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("WAITLIST_FULL"));
  }

  @Test
  @DisplayName("취소하면 200과 CANCELED 상태의 신청을 돌려준다")
  void cancel_ok() throws Exception {
    given(enrollmentService.cancel(5L, 1001L))
        .willReturn(
            new EnrollmentResponse(5L, 10L, 3L, 1001L, "CANCELED", null, OffsetDateTime.now()));

    mockMvc
        .perform(post(CANCEL_URL).with(TestAuth.employee(1001)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(5))
        .andExpect(jsonPath("$.status").value("CANCELED"))
        .andExpect(jsonPath("$.waitingNumber").isEmpty());
  }

  @Test
  @DisplayName("취소 시 토큰이 없으면 401 UNAUTHORIZED")
  void cancel_noToken_unauthorized() throws Exception {
    mockMvc
        .perform(post(CANCEL_URL))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    verifyNoInteractions(enrollmentService);
  }

  @Test
  @DisplayName("취소자는 토큰의 사원이다")
  void cancel_usesTokenEmployee() throws Exception {
    given(enrollmentService.cancel(5L, 2002L))
        .willReturn(
            new EnrollmentResponse(5L, 10L, 3L, 2002L, "CANCELED", null, OffsetDateTime.now()));

    mockMvc.perform(post(CANCEL_URL).with(TestAuth.employee(2002))).andExpect(status().isOk());

    verify(enrollmentService).cancel(5L, 2002L);
  }

  @Test
  @DisplayName("없거나 본인 것이 아닌 신청이면 404 ENROLLMENT_NOT_FOUND")
  void cancel_notFound() throws Exception {
    given(enrollmentService.cancel(5L, 1001L)).willThrow(new EnrollmentNotFoundException(5L));

    mockMvc
        .perform(post(CANCEL_URL).with(TestAuth.employee(1001)))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ENROLLMENT_NOT_FOUND"));
  }

  @Test
  @DisplayName("이미 취소된 신청이면 409 ENROLLMENT_NOT_CANCELABLE")
  void cancel_notCancelable() throws Exception {
    given(enrollmentService.cancel(5L, 1001L)).willThrow(new EnrollmentNotCancelableException(5L));

    mockMvc
        .perform(post(CANCEL_URL).with(TestAuth.employee(1001)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ENROLLMENT_NOT_CANCELABLE"));
  }

  @Test
  @DisplayName("취소 기간이 지났으면 409 CANCEL_PERIOD_ENDED")
  void cancel_periodEnded() throws Exception {
    given(enrollmentService.cancel(5L, 1001L)).willThrow(new CancelPeriodEndedException(10L));

    mockMvc
        .perform(post(CANCEL_URL).with(TestAuth.employee(1001)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("CANCEL_PERIOD_ENDED"));
  }

  @Test
  @DisplayName("취소 시 회차 락을 얻지 못하면 503 SESSION_BUSY")
  void cancel_lockTimeout_serviceUnavailable() throws Exception {
    given(enrollmentService.cancel(5L, 1001L))
        .willThrow(new CannotAcquireLockException("lock timeout"));

    mockMvc
        .perform(post(CANCEL_URL).with(TestAuth.employee(1001)))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("SESSION_BUSY"));
  }
}
