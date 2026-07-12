package com.liveklass.enrollment;

import com.liveklass.common.error.BusinessException;
import com.liveklass.common.error.ErrorCode;
import com.liveklass.course.CourseRepository;
import com.liveklass.course.CourseService;
import com.liveklass.course.CourseStatus;
import com.liveklass.course.dto.CourseCreateRequest;
import com.liveklass.support.IntegrationTestConfiguration;
import com.liveklass.support.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A-1(확정 시점 점유) / A-2(취소 후 재신청) / A-4(취소 기한) / A-5a(만석 CLOSED 대기열 편입) 흐름 검증.
 */
@SpringBootTest
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("test")
class EnrollmentFlowTest {

	private static final String CREATOR = "flow-creator";

	@Autowired
	private EnrollmentService enrollmentService;

	@Autowired
	private EnrollmentRepository enrollmentRepository;

	@Autowired
	private CourseService courseService;

	@Autowired
	private CourseRepository courseRepository;

	@Autowired
	private MutableClock clock;

	@BeforeEach
	void setUp() {
		clock.reset();
	}

	@Test
	void 신청은_PENDING으로_생성되고_정원을_점유하지_않는다() {
		String courseId = openCourse(10);

		var enrollment = enrollmentService.apply("flow-s1", courseId);

		assertThat(enrollment.status()).isEqualTo(EnrollmentStatus.PENDING);
		assertThat(courseRepository.findById(courseId).orElseThrow().getConfirmedCount()).isZero(); // A-1
	}

	@Test
	void OPEN이_아닌_강의는_신청할_수_없다() {
		String draftId = courseService.create(CREATOR,
				new CourseCreateRequest("드래프트", null, 10000, 10, null, null)).id();
		assertErrorCode(() -> enrollmentService.apply("flow-s2", draftId), ErrorCode.COURSE_NOT_OPEN);

		// 여석 있는 수동 CLOSED도 거부 (A-5a)
		String closedId = openCourse(10);
		courseService.changeStatus(CREATOR, closedId, CourseStatus.CLOSED);
		assertErrorCode(() -> enrollmentService.apply("flow-s2", closedId), ErrorCode.COURSE_NOT_OPEN);
	}

	@Test
	void 활성_신청이_있으면_중복_신청이_거부된다() {
		String courseId = openCourse(10);
		enrollmentService.apply("flow-s3", courseId);

		assertErrorCode(() -> enrollmentService.apply("flow-s3", courseId), ErrorCode.DUPLICATE_ACTIVE_ENROLLMENT);
	}

	@Test
	void 취소_후_재신청은_허용된다() {
		String courseId = openCourse(10);
		String first = enrollmentService.apply("flow-s4", courseId).id();
		enrollmentService.cancel("flow-s4", first);

		var second = enrollmentService.apply("flow-s4", courseId); // A-2

		assertThat(second.status()).isEqualTo(EnrollmentStatus.PENDING);
		assertThat(second.id()).isNotEqualTo(first);
	}

	@Test
	void 결제_확정으로_정원을_점유하고_만석이면_대기열로_편입된다() {
		String courseId = openCourse(1);
		String e1 = enrollmentService.apply("flow-s5", courseId).id();
		enrollmentService.confirm("flow-s5", e1);

		assertThat(courseRepository.findById(courseId).orElseThrow().getStatus()).isEqualTo(CourseStatus.CLOSED);

		// 만석 자동 CLOSED 상태에서도 대기열 편입 가능 (A-5a/A-6)
		var waitlisted = enrollmentService.apply("flow-s6", courseId);
		assertThat(waitlisted.status()).isEqualTo(EnrollmentStatus.WAITLISTED);
	}

	@Test
	void 만석이면_결제_확정이_거부된다() {
		String courseId = openCourse(1);
		String e1 = enrollmentService.apply("flow-s7", courseId).id();
		String e2 = enrollmentService.apply("flow-s8", courseId).id();
		enrollmentService.confirm("flow-s7", e1);

		assertErrorCode(() -> enrollmentService.confirm("flow-s8", e2), ErrorCode.COURSE_CAPACITY_EXCEEDED);
		assertThat(enrollmentRepository.findById(e2).orElseThrow().getStatus()).isEqualTo(EnrollmentStatus.PENDING);
	}

	@Test
	void PENDING이_아니면_결제_확정이_거부된다() {
		String courseId = openCourse(10);
		String e1 = enrollmentService.apply("flow-s9", courseId).id();
		enrollmentService.confirm("flow-s9", e1);

		assertErrorCode(() -> enrollmentService.confirm("flow-s9", e1), ErrorCode.INVALID_STATE_TRANSITION);
	}

	@Test
	void 확정_취소는_7일_이내만_가능하고_정원을_반환한다() {
		String courseId = openCourse(10);
		String e1 = enrollmentService.apply("flow-s10", courseId).id();
		enrollmentService.confirm("flow-s10", e1);

		clock.plus(Duration.ofDays(6));
		enrollmentService.cancel("flow-s10", e1); // A-4: 기한 내

		var course = courseRepository.findById(courseId).orElseThrow();
		assertThat(course.getConfirmedCount()).isZero();
	}

	@Test
	void 확정_후_7일이_지나면_취소가_거부된다() {
		String courseId = openCourse(10);
		String e1 = enrollmentService.apply("flow-s11", courseId).id();
		enrollmentService.confirm("flow-s11", e1);

		clock.plus(Duration.ofDays(8));

		assertErrorCode(() -> enrollmentService.cancel("flow-s11", e1), ErrorCode.CANCEL_PERIOD_EXPIRED);
	}

	@Test
	void 이미_취소된_신청은_다시_취소할_수_없다() {
		String courseId = openCourse(10);
		String e1 = enrollmentService.apply("flow-s12", courseId).id();
		enrollmentService.cancel("flow-s12", e1);

		assertErrorCode(() -> enrollmentService.cancel("flow-s12", e1), ErrorCode.INVALID_STATE_TRANSITION);
	}

	@Test
	void 본인_신청만_처리할_수_있다() {
		String courseId = openCourse(10);
		String e1 = enrollmentService.apply("flow-s13", courseId).id();

		assertErrorCode(() -> enrollmentService.confirm("flow-other", e1), ErrorCode.FORBIDDEN);
		assertErrorCode(() -> enrollmentService.cancel("flow-other", e1), ErrorCode.FORBIDDEN);
	}

	private String openCourse(int capacity) {
		String id = courseService.create(CREATOR,
				new CourseCreateRequest("흐름 테스트 강의", null, 10000, capacity, null, null)).id();
		courseService.changeStatus(CREATOR, id, CourseStatus.OPEN);
		return id;
	}

	private void assertErrorCode(Runnable action, ErrorCode expected) {
		assertThatThrownBy(action::run)
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(expected);
	}
}
