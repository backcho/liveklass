package com.liveklass.course;

import com.liveklass.common.error.BusinessException;
import com.liveklass.common.error.ErrorCode;
import com.liveklass.course.dto.CourseCreateRequest;
import com.liveklass.course.dto.CourseUpdateRequest;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A-5: DRAFT→OPEN(수동) / OPEN→CLOSED(수동+만석 자동) / CLOSED→OPEN(재오픈)
 */
@SpringBootTest
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("test")
class CourseStatusTransitionTest {

	private static final String CREATOR = "transition-creator";

	@Autowired
	private CourseService courseService;

	@Autowired
	private CourseRepository courseRepository;

	@Autowired
	private EnrollmentService enrollmentService;

	@Autowired
	private MutableClock clock;

	@BeforeEach
	void setUp() {
		clock.reset();
	}

	@Test
	void 허용된_전이는_성공한다() {
		String courseId = createCourse().id();

		courseService.changeStatus(CREATOR, courseId, CourseStatus.OPEN);
		assertThat(status(courseId)).isEqualTo(CourseStatus.OPEN);

		courseService.changeStatus(CREATOR, courseId, CourseStatus.CLOSED);
		assertThat(status(courseId)).isEqualTo(CourseStatus.CLOSED);

		// 재오픈 (A-5)
		courseService.changeStatus(CREATOR, courseId, CourseStatus.OPEN);
		assertThat(status(courseId)).isEqualTo(CourseStatus.OPEN);
	}

	@Test
	void 허용되지_않은_전이는_거부된다() {
		String courseId = createCourse().id();

		assertThatThrownBy(() -> courseService.changeStatus(CREATOR, courseId, CourseStatus.CLOSED))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);

		courseService.changeStatus(CREATOR, courseId, CourseStatus.OPEN);
		assertThatThrownBy(() -> courseService.changeStatus(CREATOR, courseId, CourseStatus.DRAFT))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
	}

	@Test
	void 타인_강의_관리는_거부된다() {
		String courseId = createCourse().id();

		assertThatThrownBy(() -> courseService.changeStatus("someone-else", courseId, CourseStatus.OPEN))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.FORBIDDEN);
	}

	@Test
	void 마지막_자리_결제_확정_시_자동_마감된다() {
		String courseId = courseService.create(CREATOR,
				new CourseCreateRequest("정원1 강의", null, 10000, 1, null, null)).id();
		courseService.changeStatus(CREATOR, courseId, CourseStatus.OPEN);

		String enrollmentId = enrollmentService.apply("transition-s1", courseId).id();
		enrollmentService.confirm("transition-s1", enrollmentId);

		Course course = courseRepository.findById(courseId).orElseThrow();
		assertThat(course.getStatus()).isEqualTo(CourseStatus.CLOSED); // A-5 만석 자동 마감
		assertThat(course.getConfirmedCount()).isEqualTo(1);
	}

	@Test
	void 정원은_확정_인원보다_작게_줄일_수_없다() {
		String courseId = courseService.create(CREATOR,
				new CourseCreateRequest("정원 축소 강의", null, 10000, 2, null, null)).id();
		courseService.changeStatus(CREATOR, courseId, CourseStatus.OPEN);
		String enrollmentId = enrollmentService.apply("transition-s2", courseId).id();
		enrollmentService.confirm("transition-s2", enrollmentId);

		assertThatThrownBy(() -> courseService.update(CREATOR, courseId,
				new CourseUpdateRequest("정원 축소 강의", null, 10000, 0, null, null)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.CAPACITY_LESS_THAN_CONFIRMED);
	}

	private com.liveklass.course.dto.CourseResponse createCourse() {
		return courseService.create(CREATOR, new CourseCreateRequest("전이 테스트 강의", null, 10000, 10, null, null));
	}

	private CourseStatus status(String courseId) {
		return courseRepository.findById(courseId).orElseThrow().getStatus();
	}
}
