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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * C-5(다중 인스턴스 클레임)/C-6(동시 중복 요청) 동시성 보증.
 */
@SpringBootTest
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("test")
class NotificationConcurrencyTest {

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private NotificationProcessingService processingService;

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
	void 동일_멱등키의_동시_요청은_1건만_생성된다() throws Exception {
		String eventId = "dup-" + UUID.randomUUID();
		int threadCount = 5;
		ConcurrentLinkedQueue<String> resultIds = new ConcurrentLinkedQueue<>();
		ConcurrentLinkedQueue<Boolean> createdFlags = new ConcurrentLinkedQueue<>();

		runConcurrently(threadCount, () -> {
			var result = notificationService.enqueue(new NotificationCreateRequest(
					"dup-user", NotificationType.GENERAL, NotificationChannel.IN_APP, eventId, null));
			resultIds.add(result.notification().id());
			createdFlags.add(result.created());
		});

		// 전원 같은 요청을 돌려받고(에러 아님), 생성은 정확히 1번 (C-6)
		assertThat(new HashSet<>(resultIds)).hasSize(1);
		assertThat(createdFlags.stream().filter(c -> c).count()).isEqualTo(1);
		assertThat(repository.findByEventIdAndRecipientIdAndChannel(eventId, "dup-user",
				NotificationChannel.IN_APP)).isPresent();
		assertThat(repository.count()).isEqualTo(1);
	}

	@Test
	void 두_워커가_동시에_폴링해도_같은_알림을_중복_클레임하지_않는다() throws Exception {
		for (int i = 0; i < 10; i++) {
			notificationService.enqueue(new NotificationCreateRequest(
					"claim-user", NotificationType.GENERAL, NotificationChannel.IN_APP,
					"claim-" + UUID.randomUUID(), null));
		}

		List<List<String>> claims = new ArrayList<>(List.of(
				new ArrayList<>(), new ArrayList<>()));
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(2);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		for (int w = 0; w < 2; w++) {
			int worker = w;
			executor.submit(() -> {
				try {
					start.await();
					claims.get(worker).addAll(processingService.claimBatch()); // C-5 SKIP LOCKED
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} finally {
					done.countDown();
				}
			});
		}
		start.countDown();
		assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
		executor.shutdown();

		Set<String> intersection = new HashSet<>(claims.get(0));
		intersection.retainAll(claims.get(1));
		assertThat(intersection).isEmpty(); // 중복 클레임 없음
		assertThat(claims.get(0).size() + claims.get(1).size()).isEqualTo(10); // 유실도 없음
		assertThat(repository.findAll())
				.allMatch(n -> n.getStatus() == NotificationStatus.PROCESSING);
	}

	private void runConcurrently(int threadCount, Runnable task) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(threadCount);
		for (int i = 0; i < threadCount; i++) {
			executor.submit(() -> {
				try {
					start.await();
					task.run();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} finally {
					done.countDown();
				}
			});
		}
		start.countDown();
		assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
		executor.shutdown();
	}
}
