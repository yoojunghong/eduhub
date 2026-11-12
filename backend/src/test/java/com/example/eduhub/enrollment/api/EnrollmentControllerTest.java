package com.example.eduhub.enrollment.api;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.eduhub.enrollment.application.EnrollmentService;
import com.example.eduhub.enrollment.domain.DuplicateEnrollmentException;
import com.example.eduhub.enrollment.domain.SessionNotFoundException;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(EnrollmentController.class)
@ActiveProfiles("test")
class EnrollmentControllerTest {

  private static final String URL = "/api/v1/sessions/10/enrollments";
  private static final String HEADER = "X-Employee-Id";

  @Autowired private MockMvc mockMvc;
  @MockitoBean private EnrollmentService enrollmentService;

  @Test
  @DisplayName("신청하면 201과 Location 헤더, 신청 결과를 돌려준다")
  void enroll_created() throws Exception {
    given(enrollmentService.enroll(10L, 1001L))
        .willReturn(new EnrollmentResponse(5L, 10L, 3L, 1001L, "CONFIRMED", OffsetDateTime.now()));

    mockMvc
        .perform(post(URL).header(HEADER, "1001"))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/enrollments/5"))
        .andExpect(jsonPath("$.id").value(5))
        .andExpect(jsonPath("$.sessionId").value(10))
        .andExpect(jsonPath("$.status").value("CONFIRMED"));
  }

  @Test
  @DisplayName("X-Employee-Id 헤더가 없으면 400 INVALID_REQUEST")
  void enroll_missingHeader_badRequest() throws Exception {
    mockMvc
        .perform(post(URL))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test
  @DisplayName("X-Employee-Id가 0이거나 음수면 400 INVALID_REQUEST")
  void enroll_nonPositiveHeader_badRequest() throws Exception {
    mockMvc
        .perform(post(URL).header(HEADER, "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    mockMvc
        .perform(post(URL).header(HEADER, "-5"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test
  @DisplayName("X-Employee-Id가 숫자가 아니면 400 INVALID_REQUEST")
  void enroll_nonNumericHeader_badRequest() throws Exception {
    mockMvc
        .perform(post(URL).header(HEADER, "abc"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test
  @DisplayName("없는 회차면 404 SESSION_NOT_FOUND")
  void enroll_sessionNotFound() throws Exception {
    given(enrollmentService.enroll(10L, 1001L)).willThrow(new SessionNotFoundException(10L));

    mockMvc
        .perform(post(URL).header(HEADER, "1001"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));
  }

  @Test
  @DisplayName("이미 진행 중 신청이 있으면 409 DUPLICATE_ENROLLMENT")
  void enroll_duplicate() throws Exception {
    given(enrollmentService.enroll(10L, 1001L)).willThrow(new DuplicateEnrollmentException(3L));

    mockMvc
        .perform(post(URL).header(HEADER, "1001"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DUPLICATE_ENROLLMENT"));
  }

  @Test
  @DisplayName("회차 락을 얻지 못하면 503 SESSION_BUSY")
  void enroll_lockTimeout_serviceUnavailable() throws Exception {
    given(enrollmentService.enroll(10L, 1001L))
        .willThrow(new CannotAcquireLockException("lock timeout"));

    mockMvc
        .perform(post(URL).header(HEADER, "1001"))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("SESSION_BUSY"));
  }
}
