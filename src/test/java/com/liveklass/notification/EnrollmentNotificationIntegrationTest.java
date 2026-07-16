package com.liveklass.notification;

import com.liveklass.course.CourseService;
import com.liveklass.course.CourseStatus;
import com.liveklass.course.dto.CourseCreateRequest;
import com.liveklass.enrollment.EnrollmentService;
import com.liveklass.support.IntegrationTestConfiguration;
import com.liveklass.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 이벤트 발행 지점 연동 검증 — 신청/확정/취소/승격이 알림 요청으로 적재된다 (멱등키 = 이벤트종류:enrollmentId).
 */
@SpringBootTest
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("test")
class EnrollmentNotificationIntegrationTest {

	private static final String CREATOR = "noti-creator";

	@Autowired
	private EnrollmentService enrollmentService;

	@Autowired
	private CourseService courseService;

	@Autowired
	private NotificationRequestRepository repository;

	@Autowired
	private MutableClock clock;

	@BeforeEach
	void setUp() {
		clock.reset();
	}

	@Test
	void 신청_확정_취소_승격_이벤트가_알림_요청으로_적재된다() {
		String courseId = courseService.create(CREATOR,
				new CourseCreateRequest("알림 연동 강의", null, 10000, 1, null, null)).id();
		courseService.changeStatus(courseId, CourseStatus.OPEN);

		// 신청 + 확정 (s1)
		String e1 = enrollmentService.apply("noti-s1", courseId).id();
		enrollmentService.confirm("noti-s1", e1);
		// 만석 → 대기열 편입 (s2)
		String e2 = enrollmentService.apply("noti-s2", courseId).id();
		// 확정 취소 → s2 승격
		enrollmentService.cancel("noti-s1", e1);

		assertThat(find("enrollment-applied:" + e1, "noti-s1")).isPresent();
		assertThat(find("enrollment-confirmed:" + e1, "noti-s1")).isPresent();
		assertThat(find("enrollment-cancelled:" + e1, "noti-s1")).isPresent();
		assertThat(find("enrollment-applied:" + e2, "noti-s2")).isPresent();
		assertThat(find("waitlist-promoted:" + e2, "noti-s2")).isPresent();

		// 적재 상태는 PENDING — 발송은 잡이 비동기로 (C-1)
		assertThat(find("enrollment-confirmed:" + e1, "noti-s1").orElseThrow().getStatus())
				.isEqualTo(NotificationStatus.PENDING);
	}

	private java.util.Optional<NotificationRequest> find(String eventId, String recipientId) {
		return repository.findByEventIdAndRecipientIdAndChannel(eventId, recipientId,
				NotificationChannel.IN_APP);
	}
}
