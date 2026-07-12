package com.liveklass.seed;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.liveklass.user.Role;
import com.liveklass.user.User;
import com.liveklass.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.TreeSet;

/**
 * G-5: 과제 샘플 데이터 시드 (local 프로파일 전용).
 * 시드 사용자 비밀번호 규칙: {id}! (예: creator-1!)
 */
@Slf4j
@Component
@Profile("local")
@RequiredArgsConstructor
public class SeedDataLoader implements ApplicationRunner {

	private final UserRepository userRepository;
	private final org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;
	private final ObjectMapper objectMapper;

	@Override
	@Transactional
	public void run(ApplicationArguments args) throws Exception {
		JsonNode root = objectMapper.readTree(new ClassPathResource("seed/sample-data.json").getInputStream());

		saveUserIfAbsent("admin-1", "운영자", Role.ADMIN);

		for (JsonNode creator : root.get("creators")) {
			saveUserIfAbsent(creator.get("id").asText(), creator.get("name").asText(), Role.CREATOR);
		}

		TreeSet<String> studentIds = new TreeSet<>();
		for (JsonNode sale : root.get("saleRecords")) {
			studentIds.add(sale.get("studentId").asText());
		}
		studentIds.forEach(id -> saveUserIfAbsent(id, "수강생-" + id, Role.STUDENT));

		// TODO phase-1: courses 시드 (COURSE 엔티티 생성 후)
		// TODO phase-2: saleRecords 시드 (SALE_RECORD 엔티티 생성 후)

		log.info("시드 완료: users={}", userRepository.count());
	}

	private void saveUserIfAbsent(String id, String name, Role role) {
		if (userRepository.existsById(id)) {
			return;
		}
		userRepository.save(User.builder()
				.id(id)
				.name(name)
				.email(id + "@liveklass.local")
				.password(passwordEncoder.encode(id + "!"))
				.role(role)
				.build());
	}
}
