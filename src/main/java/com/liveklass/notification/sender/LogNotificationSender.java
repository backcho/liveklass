package com.liveklass.notification.sender;

import com.liveklass.notification.NotificationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 과제 제약: 실제 이메일 발송 불필요 — Mock/로그 출력으로 대체.
 */
@Slf4j
@Component
public class LogNotificationSender implements NotificationSender {

	@Override
	public void send(NotificationRequest request) {
		log.info("[알림 발송/{}] id={}, recipient={}, type={}, ref={}",
				request.getChannel(), request.getId(), request.getRecipientId(),
				request.getType(), request.getReferenceId());
	}
}
