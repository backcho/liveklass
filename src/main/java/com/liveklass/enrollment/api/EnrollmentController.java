package com.liveklass.enrollment.api;

import com.liveklass.auth.AuthUser;
import com.liveklass.common.dto.PageResponse;
import com.liveklass.enrollment.EnrollmentService;
import com.liveklass.enrollment.EnrollmentStatus;
import com.liveklass.enrollment.dto.EnrollmentResponse;
import com.liveklass.enrollment.dto.WaitlistPositionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Enrollment", description = "수강 신청")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class EnrollmentController {

	private final EnrollmentService enrollmentService;

	@Operation(summary = "수강 신청", description = "STUDENT 전용. 만석이면 대기열(WAITLISTED) 편입 (A-1/A-6)")
	@PostMapping("/courses/{courseId}/enrollments")
	@PreAuthorize("hasRole('STUDENT')")
	@ResponseStatus(HttpStatus.CREATED)
	public EnrollmentResponse apply(@AuthenticationPrincipal AuthUser authUser, @PathVariable String courseId) {
		return enrollmentService.apply(authUser.getId(), courseId);
	}

	@Operation(summary = "결제 확정", description = "PENDING → CONFIRMED. 정원 최종 검증 지점 (A-1/A-3)")
	@PostMapping("/enrollments/{enrollmentId}/confirm")
	public EnrollmentResponse confirm(@AuthenticationPrincipal AuthUser authUser,
			@PathVariable String enrollmentId) {
		return enrollmentService.confirm(authUser.getId(), enrollmentId);
	}

	@Operation(summary = "수강 신청 취소",
			description = "PENDING/WAITLISTED 무제한, CONFIRMED는 확정 후 7일 이내 (A-4). 취소 시 대기열 승격 (A-6)")
	@PostMapping("/enrollments/{enrollmentId}/cancel")
	public EnrollmentResponse cancel(@AuthenticationPrincipal AuthUser authUser,
			@PathVariable String enrollmentId) {
		return enrollmentService.cancel(authUser.getId(), enrollmentId);
	}

	@Operation(summary = "대기 순번 조회", description = "WAITLISTED 전용. Redis ZSET 캐시, 미스 시 DB 재구축 (A-6)")
	@GetMapping("/enrollments/{enrollmentId}/waitlist-position")
	public WaitlistPositionResponse waitlistPosition(@AuthenticationPrincipal AuthUser authUser,
			@PathVariable String enrollmentId) {
		return enrollmentService.waitlistPosition(authUser.getId(), enrollmentId);
	}

	@Operation(summary = "내 수강 신청 목록", description = "상태 필터 + 페이지네이션")
	@GetMapping("/enrollments/me")
	public PageResponse<EnrollmentResponse> myEnrollments(@AuthenticationPrincipal AuthUser authUser,
			@RequestParam(required = false) EnrollmentStatus status,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return enrollmentService.myEnrollments(authUser.getId(), status,
				PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "appliedAt")));
	}

	@Operation(summary = "내 강의 수강 신청 목록", description = "CREATOR 전용 — 본인 강의만")
	@GetMapping("/courses/{courseId}/enrollments")
	@PreAuthorize("hasRole('CREATOR')")
	public PageResponse<EnrollmentResponse> courseEnrollments(@AuthenticationPrincipal AuthUser authUser,
			@PathVariable String courseId, @RequestParam(required = false) EnrollmentStatus status,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return enrollmentService.courseEnrollments(authUser.getId(), courseId, status,
				PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "appliedAt")));
	}
}
