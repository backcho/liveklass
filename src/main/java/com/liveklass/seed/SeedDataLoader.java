package com.liveklass.seed;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * G-5: 앱 시작 시 샘플 데이터 시드 (local 프로파일 전용). 실제 주입은 SeedDataImporter.
 */
@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class SeedDataLoader implements ApplicationRunner {

	private final SeedDataImporter seedDataImporter;

	@Override
	public void run(ApplicationArguments args) {
		seedDataImporter.importAll();
		log.info("시드 완료 (seed/sample-data.json)");
	}
}
