package com.liveklass.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 'make migrate' 전용: ddl-auto=update로 스키마만 맞추고(컨텍스트 기동 시 Hibernate가 이미 반영) 서버로 남지 않고 즉시 종료.
 */
@Slf4j
@Component
@Profile("local")
@ConditionalOnProperty(prefix = "liveklass", name = "migrate-only", havingValue = "true")
@RequiredArgsConstructor
public class MigrateOnlyExitRunner implements ApplicationRunner {

	private final ApplicationContext context;

	@Override
	public void run(ApplicationArguments args) {
		log.info("스키마 마이그레이션 완료 (ddl-auto=update) — migrate-only 모드 종료");
		System.exit(SpringApplication.exit(context, () -> 0));
	}
}
