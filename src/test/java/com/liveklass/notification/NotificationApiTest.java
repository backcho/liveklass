package com.liveklass.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.liveklass.notification.batch.NotificationBatchConfig;
import com.liveklass.support.IntegrationTestConfiguration;
import com.liveklass.support.ToggleableNotificationSender;
import com.liveklass.user.Role;
import com.liveklass.user.User;
import com.liveklass.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 알림 API 규격·인가 + @Scheduled가 기동하는 실제 Spring Batch 잡 배선 스모크.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("test")
class NotificationApiTest {

	private static final String USER_EMAIL = "noti-api-user@liveklass.local";
	private static final String PASSWORD = "test!";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private NotificationRequestRepository repository;

	@Autowired
	private ToggleableNotificationSender sender;

	@Autowired
	private JobLauncher jobLauncher;

	@Autowired
	@Qualifier(NotificationBatchConfig.SEND_JOB)
	private Job notificationSendJob;

	@BeforeEach
	void setUp() {
		sender.reset();
		if (!userRepository.existsById("noti-api-user")) {
			userRepository.save(User.builder()
					.id("noti-api-user").name("알림 사용자").email(USER_EMAIL)
					.password(passwordEncoder.encode(PASSWORD)).role(Role.STUDENT).build());
		}
	}

	@Test
	void 발송_요청은_202로_접수되고_동일_멱등키_재요청은_기존_요청을_반환한다() throws Exception {
		String body = """
				{"recipientId":"noti-api-user","type":"GENERAL","channel":"IN_APP",
				 "eventId":"api-dedup-1","referenceId":"course-1"}
				""";

		String first = mockMvc.perform(post("/api/notifications")
						.with(httpBasic(USER_EMAIL, PASSWORD))
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isAccepted()) // 접수만, 즉시 발송 아님
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andReturn().getResponse().getContentAsString();
		String id = objectMapper.readTree(first).get("id").asText();

		// C-6: 재요청은 에러가 아니라 기존 요청 반환 (200)
		mockMvc.perform(post("/api/notifications")
						.with(httpBasic(USER_EMAIL, PASSWORD))
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id));
	}

	@Test
	void 상태_조회와_읽음_필터_목록_읽음_처리가_동작한다() throws Exception {
		String id = enqueueViaApi("api-flow-1");

		mockMvc.perform(get("/api/notifications/{id}", id).with(httpBasic(USER_EMAIL, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.isRead").value(false));

		mockMvc.perform(get("/api/notifications/me").param("isRead", "false")
						.with(httpBasic(USER_EMAIL, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.content[?(@.id == '%s')]".formatted(id)).exists());

		mockMvc.perform(post("/api/notifications/{id}/read", id).with(httpBasic(USER_EMAIL, PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.isRead").value(true));

		mockMvc.perform(get("/api/notifications/me").param("isRead", "false")
						.with(httpBasic(USER_EMAIL, PASSWORD)))
				.andExpect(jsonPath("$.content[?(@.id == '%s')]".formatted(id)).doesNotExist());
	}

	@Test
	void 수동_재시도는_운영자_전용이다() throws Exception {
		String id = enqueueViaApi("api-retry-1");

		mockMvc.perform(post("/api/admin/notifications/{id}/retry", id)
						.with(httpBasic(USER_EMAIL, PASSWORD)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void 배치_잡_기동으로_접수_건이_발송된다() throws Exception {
		String id = enqueueViaApi("api-batch-1");

		jobLauncher.run(notificationSendJob, new JobParametersBuilder()
				.addLong("timestamp", System.currentTimeMillis()).toJobParameters());

		assertThat(repository.findById(id).orElseThrow().getStatus()).isEqualTo(NotificationStatus.SENT);
		assertThat(sender.sentIds()).contains(id);
	}

	private String enqueueViaApi(String eventId) throws Exception {
		String body = """
				{"recipientId":"noti-api-user","type":"GENERAL","channel":"IN_APP","eventId":"%s"}
				""".formatted(eventId);
		String response = mockMvc.perform(post("/api/notifications")
						.with(httpBasic(USER_EMAIL, PASSWORD))
						.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isAccepted())
				.andReturn().getResponse().getContentAsString();
		return objectMapper.readTree(response).get("id").asText();
	}
}
