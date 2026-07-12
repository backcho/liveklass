package com.liveklass.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 발송 파이프라인의 트랜잭션 단위들. 각 단계는 독립 트랜잭션(REQUIRES_NEW) —
 * 클레임 커밋으로 행 락을 빨리 풀고, 발송(외부 IO)은 트랜잭션 밖에서 수행한다(NotificationDispatcher).
 */
@Service
@RequiredArgsConstructor
public class NotificationProcessingService {

	private final NotificationRequestRepository repository;
	private final NotificationProperties properties;
	private final Clock clock;

	/** C-5: SKIP LOCKED 클레임 → PROCESSING 전이 후 커밋. 반환된 id들만 이 워커의 소유 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public List<String> claimBatch() {
		LocalDateTime now = LocalDateTime.now(clock);
		List<NotificationRequest> claimed = repository.claimBatch(now, PageRequest.of(0, properties.batchSize()));
		claimed.forEach(n -> n.startProcessing(now));
		return claimed.stream().map(NotificationRequest::getId).toList();
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markSent(String id) {
		repository.findById(id).ifPresent(NotificationRequest::markSent);
	}

	/** C-3: 실패 기록 — RETRY_WAIT(+interval) 또는 임계 도달 시 DEAD */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void markFailure(String id, String reason) {
		repository.findById(id).ifPresent(n -> n.recordFailure(
				truncate(reason), LocalDateTime.now(clock),
				properties.retry().maxCount(), properties.retry().interval()));
	}

	/**
	 * C-4: PROCESSING 고착 복구 — 타임아웃 경과 건을 RETRY_WAIT 복귀 + retry_count 증가.
	 * 고착 시점엔 발송 성공 여부를 알 수 없으므로 시스템은 at-least-once.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public int recoverStuck() {
		LocalDateTime now = LocalDateTime.now(clock);
		LocalDateTime cutoff = now.minus(properties.processingTimeout());
		List<NotificationRequest> stuck = repository.findStuck(cutoff, PageRequest.of(0, properties.batchSize()));
		stuck.forEach(n -> n.recordFailure("처리 타임아웃(고착) 복구", now,
				properties.retry().maxCount(), properties.retry().interval()));
		return stuck.size();
	}

	private String truncate(String reason) {
		if (reason == null) {
			return "알 수 없는 오류";
		}
		return reason.length() > 500 ? reason.substring(0, 500) : reason;
	}
}
