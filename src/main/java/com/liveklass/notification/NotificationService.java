package com.liveklass.notification;

import com.liveklass.auth.AuthUser;
import com.liveklass.common.dto.PageResponse;
import com.liveklass.common.error.BusinessException;
import com.liveklass.common.error.ErrorCode;
import com.liveklass.notification.dto.NotificationCreateRequest;
import com.liveklass.notification.dto.NotificationResponse;
import com.liveklass.user.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationService {

	private final NotificationRequestRepository repository;

	public record EnqueueResult(NotificationResponse notification, boolean created) {
	}

	/**
	 * C-6: 멱등 접수 — 동일 (event_id, recipient_id, channel) 재요청은 기존 요청 반환(에러 아님).
	 * 동시 중복 요청은 UNIQUE 제약이 최종 방어선: 충돌 시 기존 행을 다시 읽어 반환.
	 */
	@Transactional
	public EnqueueResult enqueue(NotificationCreateRequest request) {
		var existing = repository.findByEventIdAndRecipientIdAndChannel(
				request.eventId(), request.recipientId(), request.channel());
		if (existing.isPresent()) {
			return new EnqueueResult(NotificationResponse.from(existing.get()), false);
		}
		try {
			NotificationRequest saved = repository.saveAndFlush(NotificationRequest.builder()
					.recipientId(request.recipientId())
					.eventId(request.eventId())
					.type(request.type())
					.channel(request.channel())
					.referenceId(request.referenceId())
					.build());
			return new EnqueueResult(NotificationResponse.from(saved), true);
		} catch (DataIntegrityViolationException e) {
			// 동시 요청이 먼저 커밋됨 — 기존 요청 반환 (C-6)
			NotificationRequest winner = repository.findByEventIdAndRecipientIdAndChannel(
							request.eventId(), request.recipientId(), request.channel())
					.orElseThrow(() -> new BusinessException(ErrorCode.INTERNAL_ERROR));
			return new EnqueueResult(NotificationResponse.from(winner), false);
		}
	}

	/** 알림 상태 조회 — 수신자 본인 또는 ADMIN */
	@Transactional(readOnly = true)
	public NotificationResponse get(AuthUser requester, String id) {
		NotificationRequest n = getNotification(id);
		if (requester.getRole() != Role.ADMIN && !requester.getId().equals(n.getRecipientId())) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "본인 알림만 조회할 수 있습니다.");
		}
		return NotificationResponse.from(n);
	}

	@Transactional(readOnly = true)
	public PageResponse<NotificationResponse> myNotifications(String recipientId, Boolean isRead,
			Pageable pageable) {
		Page<NotificationRequest> page = isRead != null
				? repository.findByRecipientIdAndIsRead(recipientId, isRead, pageable)
				: repository.findByRecipientId(recipientId, pageable);
		return PageResponse.of(page, NotificationResponse::from);
	}

	/** 운영자 알림센터 — 전체 알림 목록 (상태 필터) */
	@Transactional(readOnly = true)
	public PageResponse<NotificationResponse> adminList(NotificationStatus status, Pageable pageable) {
		Page<NotificationRequest> page = status != null
				? repository.findByStatus(status, pageable)
				: repository.findAll(pageable);
		return PageResponse.of(page, NotificationResponse::from);
	}

	/** 읽음 처리 — 멱등 (여러 기기 동시 요청도 동일 결과) */
	@Transactional
	public NotificationResponse markRead(String recipientId, String id) {
		NotificationRequest n = getNotification(id);
		if (!n.getRecipientId().equals(recipientId)) {
			throw new BusinessException(ErrorCode.FORBIDDEN, "본인 알림만 읽음 처리할 수 있습니다.");
		}
		n.markRead();
		return NotificationResponse.from(n);
	}

	/** C-7: 수동 재시도 — DEAD 전용, retry_count 초기화 */
	@Transactional
	public NotificationResponse retryDead(String id) {
		NotificationRequest n = getNotification(id);
		n.retryManually();
		return NotificationResponse.from(n);
	}

	private NotificationRequest getNotification(String id) {
		return repository.findById(id)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "알림을 찾을 수 없습니다: " + id));
	}
}
