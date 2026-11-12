package com.example.eduhub.course.api;

import com.example.eduhub.common.api.ErrorResponse;
import com.example.eduhub.common.api.PageResponse;
import com.example.eduhub.course.application.CourseCommandService;
import com.example.eduhub.course.application.CourseQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Course", description = "교육과정 조회·관리")
@RestController
@RequestMapping("/api/v1/courses")
public class CourseController {

  private final CourseQueryService courseQueryService;
  private final CourseCommandService courseCommandService;

  public CourseController(
      CourseQueryService courseQueryService, CourseCommandService courseCommandService) {
    this.courseQueryService = courseQueryService;
    this.courseCommandService = courseCommandService;
  }

  @Operation(
      summary = "과정 목록 조회",
      description = "운영 중인 과정을 제목·분류로 검색하고 페이지 단위로 조회한다 (page는 0부터, size 최대 50)")
  @GetMapping
  public PageResponse<CourseSummaryResponse> getCourses(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String category,
      @ParameterObject @PageableDefault(size = 10, sort = "id") Pageable pageable) {
    return courseQueryService.search(keyword, category, pageable);
  }

  @Operation(summary = "과정 상세 조회", description = "과정 정보와 회차 목록(일정, 장소, 정원, 남은 자리)을 조회한다")
  @ApiResponse(responseCode = "200", description = "조회 성공")
  @ApiResponse(
      responseCode = "404",
      description = "과정이 없음",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @GetMapping("/{id}")
  public CourseDetailResponse getCourse(@PathVariable Long id) {
    return courseQueryService.get(id);
  }

  @Operation(summary = "과정 등록", description = "새 과정을 운영 중(OPEN) 상태로 등록한다 (FR-03)")
  @ApiResponse(responseCode = "201", description = "등록 성공")
  @ApiResponse(
      responseCode = "400",
      description = "입력값 오류",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @PostMapping
  public ResponseEntity<CourseDetailResponse> createCourse(
      @Valid @RequestBody CourseCreateRequest request) {
    CourseDetailResponse created = courseCommandService.create(request);
    return ResponseEntity.created(URI.create("/api/v1/courses/" + created.id())).body(created);
  }

  @Operation(summary = "과정 수정", description = "과정명·분류·설명을 수정한다. 종료된 과정은 수정할 수 없다 (FR-03)")
  @ApiResponse(responseCode = "200", description = "수정 성공")
  @ApiResponse(
      responseCode = "400",
      description = "입력값 오류",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "404",
      description = "과정이 없음",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "409",
      description = "종료된 과정",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @PutMapping("/{id}")
  public CourseDetailResponse updateCourse(
      @PathVariable Long id, @Valid @RequestBody CourseUpdateRequest request) {
    return courseCommandService.update(id, request);
  }

  @Operation(summary = "회차 추가", description = "과정에 회차를 추가한다. 정원은 1 이상, 종료된 과정에는 추가할 수 없다 (FR-03)")
  @ApiResponse(responseCode = "201", description = "추가 성공")
  @ApiResponse(
      responseCode = "400",
      description = "입력값 오류 (정원 0 이하, 지난 날짜 등)",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "404",
      description = "과정이 없음",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(
      responseCode = "409",
      description = "종료된 과정",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @PostMapping("/{id}/sessions")
  public ResponseEntity<SessionResponse> addSession(
      @PathVariable Long id, @Valid @RequestBody SessionCreateRequest request) {
    SessionResponse created = courseCommandService.addSession(id, request);
    return ResponseEntity.status(201).body(created);
  }

  @Operation(summary = "과정 종료", description = "과정과 모든 회차를 종료한다. 이미 종료된 과정은 그대로 돌려준다 (FR-03)")
  @ApiResponse(responseCode = "200", description = "종료 성공")
  @ApiResponse(
      responseCode = "404",
      description = "과정이 없음",
      content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  @PostMapping("/{id}/close")
  public CourseDetailResponse closeCourse(@PathVariable Long id) {
    return courseCommandService.close(id);
  }
}
