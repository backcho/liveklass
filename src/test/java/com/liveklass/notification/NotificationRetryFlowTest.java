package com.liveklass.notification;

import com.liveklass.common.error.BusinessException;
import com.liveklass.common.error.ErrorCode;
import com.liveklass.notification.dto.NotificationCreateRequest;
import com.liveklass.support.IntegrationTestConfiguration;
import com.liveklass.support.MutableClock;
import com.liveklass.support.ToggleableNotificationSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * C-2/C-3/C-7: 실패 → RETRY_WAIT(간격) → 재시도 → 임계 도달 DEAD → 수동 재시도(카운트 초기화) → SENT.
 */
@SpringBootTest
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("test")
class NotificationRetryFlowTest {

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private NotificationDispatcher notificationDispatcher;

	@Autowired
	private NotificationRequestRepository repository;

	@Autowired
	private ToggleableNotificationSender sender;

	@Autowired
	private MutableClock clock;

	@BeforeEach
	void setUp() {
		clock.reset();
		sender.reset();
		repository.deleteAll(); // 다른 테스트의 잔존 알림이 배치에 섞이지 않게
	}

	@Test
	void 성공_발송은_SENT로_전이된다() {
		String id = enqueue();

		notificationDispatcher.dispatchBatch();

		assertThat(status(id)).isEqualTo(NotificationStatus.SENT);
		assertThat(sender.sentIds()).contains(id);
	}

	@Test
	void 실패는_재시도_간격을_두고_임계_도달_시_DEAD가_된다() {
		String id = enqueue();
		sender.failWith(true);

		// 1차 시도 실패 → RETRY_WAIT + next_retry_at = now + 5m
		notificationDispatcher.dispatchBatch();
		var n1 = repository.findById(id).orElseThrow();
		assertThat(n1.getStatus()).isEqualTo(NotificationStatus.RETRY_WAIT);
		assertThat(n1.getRetryCount()).isEqualTo(1);
		assertThat(n1.getFailureReason()).contains("SMTP");
		assertThat(n1.getNextRetryAt()).isNotNull();

		// 간격 경과 전에는 클레임되지 않는다 (C-3)
		notificationDispatcher.dispatchBatch();
		assertThat(repository.findById(id).orElseThrow().getRetryCount()).isEqualTo(1);

		// 2차 (5분 경과 후)
		clock.plus(Duration.ofMinutes(6));
		notificationDispatcher.dispatchBatch();
		assertThat(repository.findById(id).orElseThrow().getRetryCount()).isEqualTo(2);

		// 3차 = 임계(max-count 3) 도달 → DEAD (C-2/C-3)
		clock.plus(Duration.ofMinutes(6));
		notificationDispatcher.dispatchBatch();
		var dead = repository.findById(id).orElseThrow();
		assertThat(dead.getStatus()).isEqualTo(NotificationStatus.DEAD);
		assertThat(dead.getRetryCount()).isEqualTo(3);

		// DEAD는 더 이상 클레임되지 않는다
		clock.plus(Duration.ofMinutes(6));
		notificationDispatcher.dispatchBatch();
		assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo(NotificationStatus.DEAD);
	}

	@Test
	void 수동_재시도는_카운트를_초기화하고_재발송된다() {
		String id = enqueue();
		sender.failWith(true);
		for (int i = 0; i < 3; i++) {
			notificationDispatcher.dispatchBatch();
			clock.plus(Duration.ofMinutes(6));
		}
		assertThat(status(id)).isEqualTo(NotificationStatus.DEAD);

		// C-7: DEAD → PENDING, retry_count 초기화
		var retried = notificationService.retryDead(id);
		assertThat(retried.status()).isEqualTo(NotificationStatus.PENDING);
		assertThat(retried.retryCount()).isZero();
		assertThat(retried.failureReason()).isNull();

		sender.failWith(false);
		notificationDispatcher.dispatchBatch();
		assertThat(status(id)).isEqualTo(NotificationStatus.SENT);
	}

	@Test
	void DEAD가_아닌_알림의_수동_재시도는_거부된다() {
		String id = enqueue();

		assertThatThrownBy(() -> notificationService.retryDead(id))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.NOTIFICATION_RETRY_NOT_ALLOWED);
	}

	@Test
	void 재기동_시나리오_잔존_PENDING과_RETRY_WAIT는_다음_폴링이_그대로_처리한다() {
		// "서버 재시작 후에도 미처리 알림이 유실 없이 재처리" — 상태가 DB에 있으므로
		// 재기동 후 첫 폴링과 동일한 dispatchBatch 호출로 잔존 건이 처리됨을 증명 (C-1)
		String pendingId = enqueue();
		String retryWaitId = enqueue();
		sender.failWith(true);
		notificationDispatcher.dispatchBatch(); // 둘 다 1차 실패 → RETRY_WAIT
		sender.failWith(false);
		clock.plus(Duration.ofMinutes(6));

		String newPendingId = enqueue(); // 재기동 직후 새 요청과 섞여도
		notificationDispatcher.dispatchBatch();

		assertThat(status(pendingId)).isEqualTo(NotificationStatus.SENT);
		assertThat(status(retryWaitId)).isEqualTo(NotificationStatus.SENT);
		assertThat(status(newPendingId)).isEqualTo(NotificationStatus.SENT);
	}

	private String enqueue() {
		return notificationService.enqueue(new NotificationCreateRequest(
				"retry-user", NotificationType.GENERAL, NotificationChannel.EMAIL,
				"retry-" + UUID.randomUUID(), null)).notification().id();
	}

	private NotificationStatus status(String id) {
		return repository.findById(id).orElseThrow().getStatus();
	}
}
