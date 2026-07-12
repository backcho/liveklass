package com.liveklass.common;

import com.liveklass.TestcontainersConfiguration;
import com.liveklass.user.Role;
import com.liveklass.user.User;
import com.liveklass.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * G-2: 인증 실패/성공, 권한 부족이 공통 에러 규격(ErrorResponse)으로 응답하는지 검증.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class SecurityErrorFormatTest {

	private static final String EMAIL = "student-t@liveklass.local";
	private static final String PASSWORD = "student-t!";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@BeforeEach
	void setUp() {
		if (userRepository.findByEmail(EMAIL).isEmpty()) {
			userRepository.save(User.builder()
					.id("student-t")
					.name("테스트 수강생")
					.email(EMAIL)
					.password(passwordEncoder.encode(PASSWORD))
					.role(Role.STUDENT)
					.build());
		}
	}

	@Test
	void 미인증_요청은_401과_공통_에러_규격으로_응답한다() throws Exception {
		mockMvc.perform(get("/api/users/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").exists());
	}

	@Test
	void 인증_성공_시_내_정보를_반환한다() throws Exception {
		mockMvc.perform(get("/api/users/me").with(httpBasic(EMAIL, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value("student-t"))
				.andExpect(jsonPath("$.role").value("STUDENT"));
	}

	@Test
	void 권한_없는_경로는_403과_공통_에러_규격으로_응답한다() throws Exception {
		mockMvc.perform(get("/api/admin/ping").with(httpBasic(EMAIL, PASSWORD)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}
}
