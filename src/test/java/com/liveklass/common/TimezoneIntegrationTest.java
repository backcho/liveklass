package com.liveklass.common;

import com.liveklass.TestcontainersConfiguration;
import com.liveklass.user.Role;
import com.liveklass.user.User;
import com.liveklass.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * G-3: 전 구간 KST 고정 검증 — JVM 기본 시간대, DB 세션 시각, 저장/조회 왕복.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class TimezoneIntegrationTest {

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void JVM_기본_시간대는_KST다() {
		assertThat(TimeZone.getDefault().getID()).isEqualTo("Asia/Seoul");
	}

	@Test
	void DB_세션_시각과_JVM_시각이_일치한다() {
		LocalDateTime dbNow = jdbcTemplate.queryForObject("SELECT NOW()", LocalDateTime.class);
		assertThat(Duration.between(dbNow, LocalDateTime.now()).abs())
				.isLessThan(Duration.ofSeconds(60));
	}

	@Test
	void 저장_조회_왕복에서_시각이_보존된다() {
		User saved = userRepository.saveAndFlush(User.builder()
				.name("타임존 검증")
				.email("tz-check@liveklass.local")
				.password("n/a")
				.role(Role.STUDENT)
				.build());

		User reloaded = userRepository.findById(saved.getId()).orElseThrow();

		assertThat(reloaded.getCreatedAt())
				.isCloseTo(saved.getCreatedAt(), within(1, ChronoUnit.SECONDS));
		assertThat(reloaded.getCreatedAt())
				.isCloseTo(LocalDateTime.now(), within(5, ChronoUnit.SECONDS));
	}
}
