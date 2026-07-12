package com.liveklass.support;

import com.liveklass.config.TimeZoneConfig;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/** 테스트용 조작 가능 시계 — 서비스는 Clock 빈을 경유하므로 이 시계로 시간 흐름을 재현한다 */
public class MutableClock extends Clock {

	private volatile Instant instant = Instant.now();

	public void reset() {
		instant = Instant.now();
	}

	public void set(LocalDateTime dateTime) {
		instant = dateTime.atZone(TimeZoneConfig.KST).toInstant();
	}

	public void plus(Duration duration) {
		instant = instant.plus(duration);
	}

	@Override
	public ZoneId getZone() {
		return TimeZoneConfig.KST;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		return Clock.fixed(instant, zone);
	}

	@Override
	public Instant instant() {
		return instant;
	}
}
