package com.liveklass.notification;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRequestRepository extends JpaRepository<NotificationRequest, String> {

	Optional<NotificationRequest> findByEventIdAndRecipientIdAndChannel(String eventId, String recipientId,
			NotificationChannel channel);

	/**
	 * C-5: 다중 인스턴스 클레임 — FOR UPDATE SKIP LOCKED (lock timeout -2 = SKIP_LOCKED).
	 * 대상: PENDING 전부 + next_retry_at이 경과한 RETRY_WAIT (C-3).
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
	@Query("""
			select n from NotificationRequest n
			where n.status = com.liveklass.notification.NotificationStatus.PENDING
			or (n.status = com.liveklass.notification.NotificationStatus.RETRY_WAIT and n.nextRetryAt <= :now)
			order by n.createdAt asc
			""")
	List<NotificationRequest> claimBatch(@Param("now") LocalDateTime now, Pageable pageable);

	// C-4: 고착 복구 대상 — 타임아웃 경과한 PROCESSING. 복구 잡끼리도 SKIP LOCKED로 경합 회피
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "-2"))
	@Query("""
			select n from NotificationRequest n
			where n.status = com.liveklass.notification.NotificationStatus.PROCESSING
			and n.processingStartedAt <= :cutoff
			order by n.processingStartedAt asc
			""")
	List<NotificationRequest> findStuck(@Param("cutoff") LocalDateTime cutoff, Pageable pageable);

	// 운영자 알림센터 목록 — 상태 필터
	Page<NotificationRequest> findByStatus(NotificationStatus status, Pageable pageable);

	Page<NotificationRequest> findByRecipientId(String recipientId, Pageable pageable);

	Page<NotificationRequest> findByRecipientIdAndIsRead(String recipientId, boolean isRead, Pageable pageable);

	long countByStatus(NotificationStatus status);
}
