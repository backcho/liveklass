package com.liveklass.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

import java.util.TimeZone;

@Configuration
public class TimeZoneConfig {

	// G-3: 전 구간 KST 고정. main()이 아닌 @PostConstruct라 테스트 컨텍스트에도 동일 적용
	@PostConstruct
	void setDefaultTimeZone() {
		TimeZone.setDefault(TimeZone.getTimeZone("Asia/Seoul"));
	}
}
