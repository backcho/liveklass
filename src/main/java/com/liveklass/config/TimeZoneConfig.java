package com.liveklass.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;
import java.util.TimeZone;

@Configuration
public class TimeZoneConfig {

	public static final ZoneId KST = ZoneId.of("Asia/Seoul");

	// G-3: 전 구간 KST 고정. main()이 아닌 @PostConstruct라 테스트 컨텍스트에도 동일 적용
	@PostConstruct
	void setDefaultTimeZone() {
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
	}

	// 현재 시각은 항상 이 빈을 경유 — 테스트에서 시각 조작(@Primary 교체) 가능
	@Bean
	public Clock clock() {
		return Clock.system(KST);
	}
}
