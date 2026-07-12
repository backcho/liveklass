package com.liveklass.notification.dto;

import com.liveklass.notification.NotificationChannel;
import com.liveklass.notification.NotificationRequest;
import com.liveklass.notification.NotificationStatus;
import com.liveklass.notification.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "알림 요청 상태")
public record NotificationResponse(
		String id,
		@Schema(example = "student-1") String recipientId,
		@Schema(example = "enrollment-confirmed:xxx") String eventId,
		NotificationType type,
		NotificationChannel channel,
		String referenceId,
		@Schema(example = "PENDING") NotificationStatus status,
		boolean isRead,
		@Schema(description = "실패 횟수 (C-3)") int retryCount,
		LocalDateTime nextRetryAt,
		@Schema(description = "실패 사유 — 과제 요구: 실패 사유 기록") String failureReason,
		LocalDateTime createdAt,
		LocalDateTime updatedAt
) {

	public static NotificationResponse from(NotificationRequest n) {
		return new NotificationResponse(n.getId(), n.getRecipientId(), n.getEventId(), n.getType(),
				n.getChannel(), n.getReferenceId(), n.getStatus(), n.isRead(), n.getRetryCount(),
				n.getNextRetryAt(), n.getFailureReason(), n.getCreatedAt(), n.getUpdatedAt());
	}
}
