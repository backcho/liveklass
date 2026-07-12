package com.liveklass.notification;

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

/**
 * C-4: PROCESSING 고착 복구 — 타임아웃(3분) 경과 건을 복구 잡이 RETRY_WAIT 복귀 + retry_count 증가.
 */
@SpringBootTest
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("test")
class NotificationRecoveryTest {

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private NotificationProcessingService processingService;

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
		repository.deleteAll();
	}

	@Test
	void 타임아웃을_넘긴_PROCESSING은_복구_잡이_RETRY_WAIT로_회수한다() {
		String id = enqueue();
		processingService.claimBatch(); // PROCESSING 상태에서 워커가 죽었다고 가정
		assertThat(status(id)).isEqualTo(NotificationStatus.PROCESSING);

		// 타임아웃(3분) 전에는 회수하지 않는다
		clock.plus(Duration.ofMinutes(2));
		assertThat(processingService.recoverStuck()).isZero();

		clock.plus(Duration.ofMinutes(2)); // 총 4분 경과
		assertThat(processingService.recoverStuck()).isEqualTo(1);

		var recovered = repository.findById(id).orElseThrow();
		assertThat(recovered.getStatus()).isEqualTo(NotificationStatus.RETRY_WAIT);
		assertThat(recovered.getRetryCount()).isEqualTo(1); // C-4: 증가
		assertThat(recovered.getFailureReason()).contains("타임아웃");

		// 회수된 건은 재시도 간격 후 다시 발송된다
		clock.plus(Duration.ofMinutes(6));
		notificationDispatcher.dispatchBatch();
		assertThat(status(id)).isEqualTo(NotificationStatus.SENT);
	}

	@Test
	void 고착_복구도_임계_도달_시_DEAD_처리한다() {
		String id = enqueue();
		// 고착 3회 반복 (max-count 3)
		for (int i = 0; i < 3; i++) {
			processingService.claimBatch();
			clock.plus(Duration.ofMinutes(4)); // 타임아웃 경과
			processingService.recoverStuck();
			clock.plus(Duration.ofMinutes(6)); // 재시도 간격 경과
		}

		assertThat(status(id)).isEqualTo(NotificationStatus.DEAD);
		assertThat(repository.findById(id).orElseThrow().getRetryCount()).isEqualTo(3);
	}

	private String enqueue() {
		return notificationService.enqueue(new NotificationCreateRequest(
				"recovery-user", NotificationType.GENERAL, NotificationChannel.EMAIL,
				"recovery-" + UUID.randomUUID(), null)).notification().id();
	}

	private NotificationStatus status(String id) {
		return repository.findById(id).orElseThrow().getStatus();
	}
}
