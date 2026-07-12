package com.liveklass.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * C-3/C-4 수치 외부화. processing-timeout(3m)은 재시도 간격(5m)보다 짧게 유지 —
 * 복구와 재시도 폴링의 중첩 방지.
 */
@ConfigurationProperties(prefix = "liveklass.notification")
public record NotificationProperties(
		@DefaultValue Retry retry,
		@DefaultValue("3m") Duration processingTimeout,
		@DefaultValue("100") int batchSize
) {

	public record Retry(
			@DefaultValue("3") int maxCount,
			@DefaultValue("5m") Duration interval
	) {
	}
}
