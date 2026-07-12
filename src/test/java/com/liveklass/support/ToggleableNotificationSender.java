package com.liveklass.support;

import com.liveklass.notification.NotificationRequest;
import com.liveklass.notification.sender.NotificationSender;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/** 테스트용 발송기 — 실패 주입 + 발송 이력 기록. 기본은 성공 */
public class ToggleableNotificationSender implements NotificationSender {

	private final AtomicBoolean failing = new AtomicBoolean(false);
	private final List<String> sentIds = new CopyOnWriteArrayList<>();

	@Override
	public void send(NotificationRequest request) {
		if (failing.get()) {
			throw new IllegalStateException("테스트 주입 발송 실패 (SMTP 불능 시뮬레이션)");
		}
		sentIds.add(request.getId());
	}

	public void failWith(boolean fail) {
		failing.set(fail);
	}

	public List<String> sentIds() {
		return sentIds;
	}

	public void reset() {
		failing.set(false);
		sentIds.clear();
	}
}
