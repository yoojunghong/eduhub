package com.example.eduhub.course.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.eduhub.common.api.PageResponse;
import com.example.eduhub.course.application.CourseCommandService;
import com.example.eduhub.course.application.CourseListFilter;
import com.example.eduhub.course.application.CourseQueryService;
import com.example.eduhub.course.domain.CourseClosedException;
import com.example.eduhub.course.domain.CourseNotFoundException;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CourseController.class)
@ActiveProfiles("test")
class CourseControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private CourseQueryService courseQueryService;
  @MockitoBean private CourseCommandService courseCommandService;

  @Test
  @DisplayName("목록은 PageResponse 형식으로 돌려준다")
  void getCourses_returnsPage() throws Exception {
    given(courseQueryService.search(any(), any(), any(CourseListFilter.class), any()))
        .willReturn(
            new PageResponse<>(
                List.of(new CourseSummaryResponse(1L, "Spring Boot 입문", "BACKEND", "OPEN", 2)),
                0,
                10,
                1));

    mockMvc
        .perform(get("/api/v1/courses"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].title").value("Spring Boot 입문"))
        .andExpect(jsonPath("$.content[0].sessionCount").value(2))
        .andExpect(jsonPath("$.page").value(0))
        .andExpect(jsonPath("$.size").value(10))
        .andExpect(jsonPath("$.totalElements").value(1));
  }

  @Test
  @DisplayName("검색 조건과 페이지 번호를 바꾸지 않고 서비스에 넘긴다 (page는 0부터)")
  void getCourses_passesParamsAsIs() throws Exception {
    given(courseQueryService.search(any(), any(), any(CourseListFilter.class), any()))
        .willReturn(new PageResponse<>(List.of(), 2, 5, 0));

    mockMvc
        .perform(
            get("/api/v1/courses")
                .param("keyword", "spring")
                .param("category", "BACKEND")
                .param("page", "2")
                .param("size", "5"))
        .andExpect(status().isOk());

    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(courseQueryService)
        .search(eq("spring"), eq("BACKEND"), eq(CourseListFilter.OPEN), pageable.capture());
    assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
    assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
  }

  @Test
  @DisplayName("관리 화면은 status=ALL로 종료 과정까지 요청하고, 모르는 값은 400")
  void getCourses_statusFilter() throws Exception {
    given(courseQueryService.search(any(), any(), any(CourseListFilter.class), any()))
        .willReturn(new PageResponse<>(List.of(), 0, 10, 0));

    mockMvc.perform(get("/api/v1/courses").param("status", "ALL")).andExpect(status().isOk());
    verify(courseQueryService).search(any(), any(), eq(CourseListFilter.ALL), any());

    mockMvc
        .perform(get("/api/v1/courses").param("status", "DELETED"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
  }

  @Test
  @DisplayName("페이지 크기는 최대 50으로 제한한다")
  void getCourses_limitsPageSize() throws Exception {
    given(courseQueryService.search(any(), any(), any(CourseListFilter.class), any()))
        .willReturn(new PageResponse<>(List.of(), 0, 50, 0));

    mockMvc.perform(get("/api/v1/courses").param("size", "1000")).andExpect(status().isOk());

    ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
    verify(courseQueryService)
        .search(any(), any(), any(CourseListFilter.class), pageable.capture());
    assertThat(pageable.getValue().getPageSize()).isEqualTo(50);
  }

  @Test
  @DisplayName("상세는 회차 목록과 남은 자리를 함께 돌려준다")
  void getCourse_returnsDetailWithSessions() throws Exception {
    given(courseQueryService.get(1L))
        .willReturn(
            new CourseDetailResponse(
                1L,
                "Spring Boot 입문",
                "BACKEND",
                "스프링 부트 기초",
                "OPEN",
                List.of(
                    new SessionResponse(
                        10L, LocalDate.of(2026, 12, 1), "본사 3층 교육장", 20, 20, "OPEN"))));

    mockMvc
        .perform(get("/api/v1/courses/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("Spring Boot 입문"))
        .andExpect(jsonPath("$.sessions[0].startDate").value("2026-12-01"))
        .andExpect(jsonPath("$.sessions[0].remainingSeats").value(20));
  }

  @Test
  @DisplayName("없는 과정을 조회하면 404와 에러 코드를 돌려준다")
  void getCourse_notFound_returns404() throws Exception {
    given(courseQueryService.get(99L)).willThrow(new CourseNotFoundException(99L));

    mockMvc
        .perform(get("/api/v1/courses/99"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("COURSE_NOT_FOUND"));
  }

  @Test
  @DisplayName("과정을 등록하면 201과 Location 헤더를 돌려준다")
  void createCourse_returns201() throws Exception {
    given(courseCommandService.create(any()))
        .willReturn(new CourseDetailResponse(7L, "새 과정", "BACKEND", null, "OPEN", List.of()));

    mockMvc
        .perform(
            post("/api/v1/courses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title": "새 과정", "category": "BACKEND"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(header().string("Location", "/api/v1/courses/7"))
        .andExpect(jsonPath("$.id").value(7));
  }

  @Test
  @DisplayName("과정명이 비어 있으면 400 INVALID_REQUEST")
  void createCourse_blankTitle_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/courses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title": " ", "category": "BACKEND"}
                    """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.message").value(containsString("title")));
  }

  @Test
  @DisplayName("종료된 과정을 수정하면 409 COURSE_CLOSED")
  void updateCourse_closed_returns409() throws Exception {
    given(courseCommandService.update(eq(1L), any())).willThrow(new CourseClosedException(1L));

    mockMvc
        .perform(
            put("/api/v1/courses/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"title": "수정", "category": "BACKEND"}
                    """))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("COURSE_CLOSED"));
  }

  @Test
  @DisplayName("회차를 추가하면 201과 회차를 돌려준다")
  void addSession_returns201() throws Exception {
    LocalDate date = LocalDate.now().plusMonths(1);
    given(courseCommandService.addSession(eq(1L), any()))
        .willReturn(new SessionResponse(10L, date, "본사 3층", 20, 20, "OPEN"));

    mockMvc
        .perform(
            post("/api/v1/courses/1/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(sessionJson(date, 20)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(10));
  }

  @Test
  @DisplayName("정원이 0이면 400 INVALID_REQUEST")
  void addSession_zeroCapacity_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/courses/1/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(sessionJson(LocalDate.now().plusMonths(1), 0)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.message").value(containsString("capacity")));
  }

  @Test
  @DisplayName("과정을 종료하면 CLOSED 상태를 돌려준다")
  void closeCourse_returnsClosed() throws Exception {
    given(courseCommandService.close(1L))
        .willReturn(new CourseDetailResponse(1L, "과정", "BACKEND", null, "CLOSED", List.of()));

    mockMvc
        .perform(post("/api/v1/courses/1/close"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CLOSED"));
  }

  private static String sessionJson(LocalDate startDate, int capacity) {
    return """
    {"startDate": "%s", "place": "본사 3층", "capacity": %d}
    """
        .formatted(startDate, capacity);
  }
}
