package com.liveklass.notification.dto;

import com.liveklass.notification.NotificationChannel;
import com.liveklass.notification.NotificationType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "알림 발송 요청 — 접수만 하고 즉시 발송하지 않음 (C-1)")
public record NotificationCreateRequest(
		@Schema(example = "student-1") @NotBlank String recipientId,
		@Schema(example = "GENERAL") @NotNull NotificationType type,
		@Schema(example = "IN_APP") @NotNull NotificationChannel channel,
		@Schema(description = "멱등키 — 동일 (eventId, recipientId, channel) 재요청은 기존 요청 반환 (C-6)",
				example = "manual-notice-1") @NotBlank String eventId,
		@Schema(description = "참조 데이터 (강의 ID 등)", example = "course-1") String referenceId
) {
}
