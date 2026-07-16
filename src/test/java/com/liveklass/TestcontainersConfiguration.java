package com.liveklass;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	MariaDBContainer<?> mariaDbContainer() {
		// C-5: SKIP LOCKED 요건으로 10.6+ — 운영 docker-compose와 동일 버전 고정. TZ는 G-3(KST)
		return new MariaDBContainer<>(DockerImageName.parse("mariadb:10.11")).withEnv("TZ", "Asia/Seoul");
	}

}
