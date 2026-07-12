package com.liveklass.notification.batch;

import com.liveklass.notification.NotificationDispatcher;
import com.liveklass.notification.NotificationProcessingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.batch.support.transaction.ResourcelessTransactionManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * C-1: @Scheduled가 기동하는 Spring Batch 잡 2종 — 발송(dispatch), 고착 복구(recovery).
 * 트랜잭션 경계는 서비스 내부(REQUIRES_NEW)에서 관리하므로 스텝 자체는
 * ResourcelessTransactionManager(무트랜잭션)로 감싼다 — 외부 IO(발송)가 DB 트랜잭션을 점유하지 않게.
 */
@Slf4j
@Configuration
public class NotificationBatchConfig {

	public static final String SEND_JOB = "notificationSendJob";
	public static final String RECOVERY_JOB = "notificationRecoveryJob";

	@Bean
	public Job notificationSendJob(JobRepository jobRepository, Step notificationSendStep) {
		return new JobBuilder(SEND_JOB, jobRepository)
				.start(notificationSendStep)
				.build();
	}

	@Bean
	public Step notificationSendStep(JobRepository jobRepository, NotificationDispatcher dispatcher) {
		return new StepBuilder("notificationSendStep", jobRepository)
				.tasklet((contribution, chunkContext) -> {
					int processed = dispatcher.dispatchBatch();
					if (processed > 0) {
						log.info("알림 발송 잡: {}건 처리", processed);
					}
					return RepeatStatus.FINISHED;
				}, new ResourcelessTransactionManager())
				.build();
	}

	@Bean
	public Job notificationRecoveryJob(JobRepository jobRepository, Step notificationRecoveryStep) {
		return new JobBuilder(RECOVERY_JOB, jobRepository)
				.start(notificationRecoveryStep)
				.build();
	}

	@Bean
	public Step notificationRecoveryStep(JobRepository jobRepository,
			NotificationProcessingService processingService) {
		return new StepBuilder("notificationRecoveryStep", jobRepository)
				.tasklet((contribution, chunkContext) -> {
					int recovered = processingService.recoverStuck();
					if (recovered > 0) {
						log.warn("알림 고착 복구 잡: {}건 회수 (C-4)", recovered);
					}
					return RepeatStatus.FINISHED;
				}, new ResourcelessTransactionManager())
				.build();
	}
}
