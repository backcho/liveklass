package com.liveklass.notification;

import com.liveklass.notification.sender.NotificationSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * C-1 발송 오케스트레이션: 클레임(tx 커밋) → 발송(트랜잭션 밖 IO) → 결과 기록(tx).
 * 발송 실패는 예외를 "무시"하지 않고 상태(RETRY_WAIT/DEAD)와 failure_reason으로 기록된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDispatcher {

	private final NotificationProcessingService processingService;
	private final NotificationRequestRepository repository;
	private final NotificationSender notificationSender;

	/** 한 번의 폴링 사이클: 클레임된 건들을 발송하고 결과를 기록. 처리 건수 반환 */
	public int dispatchBatch() {
		List<String> claimedIds = processingService.claimBatch();
		for (String id : claimedIds) {
			dispatchOne(id);
		}
		return claimedIds.size();
	}

	private void dispatchOne(String id) {
		NotificationRequest request = repository.findById(id).orElse(null);
		if (request == null || request.getStatus() != NotificationStatus.PROCESSING) {
			return; // 복구 잡이 먼저 회수한 경우 등 — 소유권 상실
		}
		try {
			notificationSender.send(request);
			processingService.markSent(id);
		} catch (Exception e) {
			log.warn("알림 발송 실패: id={}, reason={}", id, e.getMessage());
			processingService.markFailure(id, e.getMessage());
		}
	}
}
