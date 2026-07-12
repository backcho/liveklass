package com.liveklass.notification.batch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * C-1: DB 폴링 트리거. 서버 재시작 복구는 "상태가 DB에 있음"으로 자연 충족 —
 * 재기동 후 첫 폴링이 잔존 PENDING/RETRY_WAIT를 그대로 집어간다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationScheduler {

	private final JobLauncher jobLauncher;

	@Qualifier(NotificationBatchConfig.SEND_JOB)
	private final Job notificationSendJob;

	@Qualifier(NotificationBatchConfig.RECOVERY_JOB)
	private final Job notificationRecoveryJob;

	@Scheduled(fixedDelayString = "${liveklass.notification.send-poll-delay:10s}")
	public void runSendJob() {
		launch(notificationSendJob);
	}

	@Scheduled(fixedDelayString = "${liveklass.notification.recovery-poll-delay:60s}")
	public void runRecoveryJob() {
		launch(notificationRecoveryJob);
	}

	private void launch(Job job) {
		try {
			jobLauncher.run(job, new JobParametersBuilder()
					.addLong("timestamp", System.currentTimeMillis())
					.toJobParameters());
		} catch (Exception e) {
			log.error("배치 잡 기동 실패: {}", job.getName(), e);
		}
	}
}
