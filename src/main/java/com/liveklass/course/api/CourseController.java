package com.liveklass.course.api;

import com.liveklass.auth.AuthUser;
import com.liveklass.common.dto.PageResponse;
import com.liveklass.course.CourseService;
import com.liveklass.course.CourseStatus;
import com.liveklass.course.dto.CourseCreateRequest;
import com.liveklass.course.dto.CourseDetailResponse;
import com.liveklass.course.dto.CourseResponse;
import com.liveklass.course.dto.CourseStatusChangeRequest;
import com.liveklass.course.dto.CourseUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "1-1. [과제A] Course", description = "강의 관리")
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
public class CourseController {

	private final CourseService courseService;

	@Operation(summary = "강의 등록", description = "CREATOR 전용. DRAFT 상태로 생성된다.")
	@PostMapping
	@PreAuthorize("hasRole('CREATOR')")
	@ResponseStatus(HttpStatus.CREATED)
	public CourseResponse create(@AuthenticationPrincipal AuthUser authUser,
			@Valid @RequestBody CourseCreateRequest request) {
		return courseService.create(authUser.getId(), request);
	}

	@Operation(summary = "[추가 기능] 강의 수정", description = "CREATOR 본인 강의 또는 ADMIN (A-5b). 정원은 확정 인원 미만으로 줄일 수 없다.")
	@PutMapping("/{courseId}")
	@PreAuthorize("hasAnyRole('CREATOR','ADMIN')")
	public CourseResponse update(@AuthenticationPrincipal AuthUser authUser, @PathVariable String courseId,
	                             @Valid @RequestBody CourseUpdateRequest request) {
		return courseService.update(authUser, courseId, request);
	}

	@Operation(summary = "강의 상태 변경", description = "ADMIN 전용 (A-5b) — 판매 개시·중단은 플랫폼 통제 영역. DRAFT→OPEN, OPEN→CLOSED, CLOSED→OPEN(재오픈)만 허용 (A-5)")
	@PostMapping("/{courseId}/status")
	@PreAuthorize("hasRole('ADMIN')")
	public CourseResponse changeStatus(@PathVariable String courseId,
			@Valid @RequestBody CourseStatusChangeRequest request) {
		return courseService.changeStatus(courseId, request.status());
	}

	@Operation(summary = "강의 목록 조회", description = "상태 필터 + 페이지네이션")
	@GetMapping
	public PageResponse<CourseResponse> list(@RequestParam(required = false) CourseStatus status,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return courseService.list(status, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
	}

	@Operation(summary = "강의 상세 조회", description = "현재 신청 인원(confirmedCount) 포함, 대기 수 병기 (A-1)")
	@GetMapping("/{courseId}")
	public CourseDetailResponse detail(@PathVariable String courseId) {
		return courseService.detail(courseId);
	}
}
