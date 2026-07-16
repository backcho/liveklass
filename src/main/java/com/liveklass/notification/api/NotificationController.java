package com.liveklass.notification.api;

import com.liveklass.auth.AuthUser;
import com.liveklass.common.dto.PageResponse;
import com.liveklass.notification.NotificationService;
import com.liveklass.notification.NotificationStatus;
import com.liveklass.notification.dto.NotificationCreateRequest;
import com.liveklass.notification.dto.NotificationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "3-1. [과제C] Notification", description = "알림 발송")
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class NotificationController {

	private final NotificationService notificationService;

	@Operation(summary = "알림 발송 요청",
			description = "접수만 하고 즉시 발송하지 않음(202). 동일 멱등키 재요청은 기존 요청 반환(200, 에러 아님 — C-6)")
	@PostMapping("/notifications")
	public ResponseEntity<NotificationResponse> enqueue(@Valid @RequestBody NotificationCreateRequest request) {
		var result = notificationService.enqueue(request);
		return ResponseEntity.status(result.created() ? HttpStatus.ACCEPTED : HttpStatus.OK)
				.body(result.notification());
	}

	@Operation(summary = "알림 상태 조회", description = "수신자 본인 또는 ADMIN")
	@GetMapping("/notifications/{notificationId}")
	public NotificationResponse get(@AuthenticationPrincipal AuthUser authUser,
			@PathVariable String notificationId) {
		return notificationService.get(authUser, notificationId);
	}

	@Operation(summary = "내 알림 목록", description = "읽음/안읽음 필터 + 페이지네이션")
	@GetMapping("/notifications/me")
	public PageResponse<NotificationResponse> myNotifications(@AuthenticationPrincipal AuthUser authUser,
			@RequestParam(required = false) Boolean isRead,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return notificationService.myNotifications(authUser.getId(), isRead,
				PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
	}

	@Operation(summary = "알림 읽음 처리", description = "멱등 — 여러 기기의 동시 요청도 동일 결과")
	@PostMapping("/notifications/{notificationId}/read")
	public NotificationResponse markRead(@AuthenticationPrincipal AuthUser authUser,
			@PathVariable String notificationId) {
		return notificationService.markRead(authUser.getId(), notificationId);
	}

	@Operation(summary = "알림센터 목록 (운영자)", description = "전체 알림 요청, 상태 필터 (DEAD 조회 등)")
	@GetMapping("/admin/notifications")
	public PageResponse<NotificationResponse> adminList(
			@RequestParam(required = false) NotificationStatus status,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
		return notificationService.adminList(status,
				PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
	}

	@Operation(summary = "알림 수동 재시도", description = "운영자 전용 — DEAD 대상, retry_count 초기화 (C-7)")
	@PostMapping("/admin/notifications/{notificationId}/retry")
	public NotificationResponse retryDead(@PathVariable String notificationId) {
		return notificationService.retryDead(notificationId);
	}
}
