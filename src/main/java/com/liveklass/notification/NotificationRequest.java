package com.liveklass.notification;

import com.liveklass.common.entity.BaseTimeEntity;
import com.liveklass.common.error.BusinessException;
import com.liveklass.common.error.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "notification_requests",
		uniqueConstraints = @jakarta.persistence.UniqueConstraint(name = "uk_notification_dedup",
				columnNames = {"event_id", "recipient_id", "channel"}), // C-6 중복 발송 방지 최종 방어선
		indexes = {
				@Index(name = "idx_notification_claim", columnList = "status, next_retry_at"), // C-5 폴링 클레임
				@Index(name = "idx_notification_recipient", columnList = "recipient_id, is_read")
		})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationRequest extends BaseTimeEntity {

	@Id
	@Column(length = 36)
	private String id;

	@Column(name = "recipient_id", nullable = false, length = 36)
	private String recipientId;

	// C-6: 요청자 부여 멱등키
	@Column(name = "event_id", nullable = false, length = 100)
	private String eventId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private NotificationType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private NotificationChannel channel;

	@Column(name = "reference_id", length = 100)
	private String referenceId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private NotificationStatus status;

	@Column(name = "is_read", nullable = false)
	private boolean isRead;

	// C-3: 실패 횟수. max-count 도달 시 DEAD (총 시도 = max-count회)
	@Column(name = "retry_count", nullable = false)
	private int retryCount;

	@Column(name = "next_retry_at")
	private LocalDateTime nextRetryAt;

	// C-4: 고착 감지 기준
	@Column(name = "processing_started_at")
	private LocalDateTime processingStartedAt;

	@Column(name = "failure_reason", length = 500)
	private String failureReason;

	@Builder
	private NotificationRequest(String recipientId, String eventId, NotificationType type,
			NotificationChannel channel, String referenceId) {
		this.id = UUID.randomUUID().toString();
		this.recipientId = recipientId;
		this.eventId = eventId;
		this.type = type;
		this.channel = channel;
		this.referenceId = referenceId;
		this.status = NotificationStatus.PENDING;
		this.isRead = false;
		this.retryCount = 0;
	}

	// 클레임: PENDING/RETRY_WAIT → PROCESSING (C-2)
	public void startProcessing(LocalDateTime now) {
		if (status != NotificationStatus.PENDING && status != NotificationStatus.RETRY_WAIT) {
			throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
					"발송 처리는 PENDING/RETRY_WAIT에서만 시작합니다: " + status);
		}
		this.status = NotificationStatus.PROCESSING;
		this.processingStartedAt = now;
	}

	public void markSent() {
		requireProcessing();
		this.status = NotificationStatus.SENT;
		this.failureReason = null;
	}

	// C-3: 실패 → retry_count 증가, 임계 미만이면 RETRY_WAIT(+interval), 도달하면 DEAD
	public void recordFailure(String reason, LocalDateTime now, int maxCount, Duration retryInterval) {
		requireProcessing();
		this.retryCount++;
		this.failureReason = reason;
		if (retryCount >= maxCount) {
			this.status = NotificationStatus.DEAD;
			this.nextRetryAt = null;
		} else {
			this.status = NotificationStatus.RETRY_WAIT;
			this.nextRetryAt = now.plus(retryInterval);
		}
	}

	// C-7: 수동 재시도 — 수동 개입은 새 시도로 간주해 retry_count 초기화
	public void retryManually() {
		if (status != NotificationStatus.DEAD) {
			throw new BusinessException(ErrorCode.NOTIFICATION_RETRY_NOT_ALLOWED);
		}
		this.status = NotificationStatus.PENDING;
		this.retryCount = 0;
		this.nextRetryAt = null;
		this.failureReason = null;
		this.processingStartedAt = null;
	}

	// 읽음 처리 — 멱등: 여러 기기의 동시 요청도 동일 결과로 수렴
	public void markRead() {
		this.isRead = true;
	}

	public boolean isStuck(LocalDateTime now, Duration processingTimeout) {
		return status == NotificationStatus.PROCESSING && processingStartedAt != null
				&& processingStartedAt.plus(processingTimeout).isBefore(now);
	}

	private void requireProcessing() {
		if (status != NotificationStatus.PROCESSING) {
			throw new BusinessException(ErrorCode.INVALID_STATE_TRANSITION,
					"PROCESSING 상태가 아닙니다: " + status);
		}
	}
}
