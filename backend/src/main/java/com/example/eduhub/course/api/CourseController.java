package com.example.eduhub.course.api;

import com.example.eduhub.common.api.ErrorResponse;
import com.example.eduhub.common.api.PageResponse;
import com.example.eduhub.course.application.CourseQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Course", description = "교육과정 조회·관리")
@RestController
@RequestMapping("/api/v1/courses")
public class CourseController {

  private final CourseQueryService courseQueryService;

  public CourseController(CourseQueryService courseQueryService) {
    this.courseQueryService = courseQueryService;
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
}
