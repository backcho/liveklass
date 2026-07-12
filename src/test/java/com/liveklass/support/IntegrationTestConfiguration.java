package com.liveklass.support;

import com.liveklass.TestcontainersConfiguration;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

/** phase-1+ 통합 테스트 공통: Testcontainers + 조작 가능 Clock */
@TestConfiguration(proxyBeanMethods = false)
@Import(TestcontainersConfiguration.class)
public class IntegrationTestConfiguration {

	@Bean
	@Primary
	public MutableClock mutableClock() {
		return new MutableClock();
	}
}
