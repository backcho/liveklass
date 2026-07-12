package com.liveklass.enrollment;

import com.liveklass.support.IntegrationTestConfiguration;
import com.liveklass.user.Role;
import com.liveklass.user.User;
import com.liveklass.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * API 규격·인가 스모크: 강의 등록(CREATOR) → 오픈 → 신청(STUDENT) → 확정 → 상세 인원 반영.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("test")
class EnrollmentApiTest {

	private static final String CREATOR_EMAIL = "api-creator@liveklass.local";
	private static final String STUDENT_EMAIL = "api-student@liveklass.local";
	private static final String PASSWORD = "test!";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private ObjectMapper objectMapper;

	@BeforeEach
	void setUp() {
		saveUserIfAbsent("api-creator", CREATOR_EMAIL, Role.CREATOR);
		saveUserIfAbsent("api-student", STUDENT_EMAIL, Role.STUDENT);
	}

	@Test
	void 강의_등록부터_결제_확정까지_API로_재현된다() throws Exception {
		String courseJson = mockMvc.perform(post("/api/courses")
						.with(httpBasic(CREATOR_EMAIL, PASSWORD))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"title":"API 스모크 강의","price":10000,"capacity":3}
								"""))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("DRAFT"))
				.andReturn().getResponse().getContentAsString();
		String courseId = objectMapper.readTree(courseJson).get("id").asText();

		mockMvc.perform(post("/api/courses/{id}/status", courseId)
						.with(httpBasic(CREATOR_EMAIL, PASSWORD))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"status\":\"OPEN\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("OPEN"));

		String enrollmentJson = mockMvc.perform(post("/api/courses/{id}/enrollments", courseId)
						.with(httpBasic(STUDENT_EMAIL, PASSWORD)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andReturn().getResponse().getContentAsString();
		String enrollmentId = objectMapper.readTree(enrollmentJson).get("id").asText();

		mockMvc.perform(post("/api/enrollments/{id}/confirm", enrollmentId)
						.with(httpBasic(STUDENT_EMAIL, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("CONFIRMED"));

		mockMvc.perform(get("/api/courses/{id}", courseId).with(httpBasic(STUDENT_EMAIL, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.confirmedCount").value(1))
				.andExpect(jsonPath("$.remainingSeats").value(2));

		mockMvc.perform(get("/api/enrollments/me").with(httpBasic(STUDENT_EMAIL, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].courseTitle").value("API 스모크 강의"));

		mockMvc.perform(get("/api/courses/{id}/enrollments", courseId)
						.with(httpBasic(CREATOR_EMAIL, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[0].studentId").value("api-student"))
				.andExpect(jsonPath("$.totalElements").value(1));
	}

	@Test
	void 역할이_없는_요청은_403_공통_규격으로_거부된다() throws Exception {
		// STUDENT가 강의 등록 시도 (@PreAuthorize)
		mockMvc.perform(post("/api/courses")
						.with(httpBasic(STUDENT_EMAIL, PASSWORD))
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"title":"권한 없음","price":10000,"capacity":3}
								"""))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));

		// CREATOR가 수강 신청 시도
		mockMvc.perform(post("/api/courses/any/enrollments").with(httpBasic(CREATOR_EMAIL, PASSWORD)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void 검증_실패는_400_필드_상세와_함께_응답한다() throws Exception {
		mockMvc.perform(post("/api/courses")
						.with(httpBasic(CREATOR_EMAIL, PASSWORD))
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"title\":\"\",\"price\":-1,\"capacity\":0}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.errors").isNotEmpty());
	}

	private void saveUserIfAbsent(String id, String email, Role role) {
		if (userRepository.existsById(id)) {
			return;
		}
		userRepository.save(User.builder()
				.id(id)
				.name("api-" + role.name())
				.email(email)
				.password(passwordEncoder.encode(PASSWORD))
				.role(role)
				.build());
	}
}
