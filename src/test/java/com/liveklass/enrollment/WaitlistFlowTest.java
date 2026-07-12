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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A-6: 대기열 편입 → 순번 조회(Redis 캐시 + DB 재구축) → 승격(결제 기한) → 기한 만료 자동 취소 → 다음 순번 승격.
 */
@SpringBootTest
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("test")
class WaitlistFlowTest {

	private static final String CREATOR = "wl-creator";

	@Autowired
	private EnrollmentService enrollmentService;

	@Autowired
	private EnrollmentRepository enrollmentRepository;

	@Autowired
	private CourseRepository courseRepository;

	@Autowired
	private CourseService courseService;

	@Autowired
	private WaitlistExpiryScheduler waitlistExpiryScheduler;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Autowired
	private MutableClock clock;

	@BeforeEach
	void setUp() {
		clock.set(LocalDateTime.of(2026, 7, 13, 12, 0, 0));
	}

	@Test
	void 만석_강의_신청은_대기열에_순서대로_편입된다() {
		String courseId = fullCourse("wl-s1");

		var w1 = enrollmentService.apply("wl-s2", courseId);
		clock.plus(Duration.ofSeconds(10));
		var w2 = enrollmentService.apply("wl-s3", courseId);

		assertThat(w1.status()).isEqualTo(EnrollmentStatus.WAITLISTED);
		assertThat(w2.status()).isEqualTo(EnrollmentStatus.WAITLISTED);

		assertThat(enrollmentService.waitlistPosition("wl-s2", w1.id()).position()).isEqualTo(1);
		assertThat(enrollmentService.waitlistPosition("wl-s3", w2.id()).position()).isEqualTo(2);
		assertThat(enrollmentService.waitlistPosition("wl-s3", w2.id()).waitingCount()).isEqualTo(2);
	}

	@Test
	void 캐시가_유실돼도_DB에서_재구축해_순번을_반환한다() {
		String courseId = fullCourse("wl-s4");
		var w1 = enrollmentService.apply("wl-s5", courseId);
		clock.plus(Duration.ofSeconds(10));
		var w2 = enrollmentService.apply("wl-s6", courseId);

		redisTemplate.delete("waitlist:course:" + courseId); // 캐시 유실 시뮬레이션

		assertThat(enrollmentService.waitlistPosition("wl-s5", w1.id()).position()).isEqualTo(1);
		assertThat(enrollmentService.waitlistPosition("wl-s6", w2.id()).position()).isEqualTo(2);
	}

	@Test
	void 대기_중이_아니면_순번_조회가_거부된다() {
		String courseId = openCourse(10);
		String pending = enrollmentService.apply("wl-s7", courseId).id();

		assertThatThrownBy(() -> enrollmentService.waitlistPosition("wl-s7", pending))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.NOT_WAITLISTED);
	}

	@Test
	void 확정_취소_시_대기_1순위가_결제_기한과_함께_승격된다() {
		String courseId = fullCourse("wl-s8");
		String w1 = enrollmentService.apply("wl-s9", courseId).id();
		clock.plus(Duration.ofSeconds(10));
		String w2 = enrollmentService.apply("wl-s10", courseId).id();

		LocalDateTime cancelTime = LocalDateTime.of(2026, 7, 13, 13, 0, 0);
		clock.set(cancelTime);
		String confirmedId = enrollmentRepository
				.findByCourseIdAndStatusOrderByAppliedAtAscIdAsc(courseId, EnrollmentStatus.CONFIRMED)
				.get(0).getId();
		enrollmentService.cancel("wl-s8", confirmedId);

		var promoted = enrollmentRepository.findById(w1).orElseThrow();
		assertThat(promoted.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
		assertThat(promoted.getPaymentDueAt()).isEqualTo(cancelTime.plusHours(24)); // 설정 기본값

		// 승격된 인원은 대기열에서 빠지고 다음 순번이 1위
		assertThat(enrollmentService.waitlistPosition("wl-s10", w2).position()).isEqualTo(1);

		// 승격자는 결제 확정으로 자리 점유 가능 (강의는 여전히 CLOSED — 재오픈은 수동, A-5)
		enrollmentService.confirm("wl-s9", w1);
		var course = courseRepository.findById(courseId).orElseThrow();
		assertThat(course.getConfirmedCount()).isEqualTo(1);
		assertThat(course.getStatus()).isEqualTo(CourseStatus.CLOSED);
	}

	@Test
	void 결제_기한이_지난_승격_건은_잡이_자동_취소하고_다음_순번을_승격한다() {
		String courseId = fullCourse("wl-s11");
		String w1 = enrollmentService.apply("wl-s12", courseId).id();
		clock.plus(Duration.ofSeconds(10));
		String w2 = enrollmentService.apply("wl-s13", courseId).id();

		String confirmedId = enrollmentRepository
				.findByCourseIdAndStatusOrderByAppliedAtAscIdAsc(courseId, EnrollmentStatus.CONFIRMED)
				.get(0).getId();
		enrollmentService.cancel("wl-s11", confirmedId); // w1 승격

		clock.plus(Duration.ofHours(25)); // 결제 기한(24h) 초과
		waitlistExpiryScheduler.expireOverduePromotions();

		assertThat(enrollmentRepository.findById(w1).orElseThrow().getStatus())
				.isEqualTo(EnrollmentStatus.CANCELLED);
		var next = enrollmentRepository.findById(w2).orElseThrow();
		assertThat(next.getStatus()).isEqualTo(EnrollmentStatus.PENDING);
		assertThat(next.getPaymentDueAt()).isNotNull();

		// 다음 승격자는 정상 결제 가능
		enrollmentService.confirm("wl-s13", w2);
		assertThat(courseRepository.findById(courseId).orElseThrow().getConfirmedCount()).isEqualTo(1);
	}

	@Test
	void 결제_기한이_지난_승격_건의_결제_시도는_거부된다() {
		String courseId = fullCourse("wl-s14");
		String w1 = enrollmentService.apply("wl-s15", courseId).id();

		String confirmedId = enrollmentRepository
				.findByCourseIdAndStatusOrderByAppliedAtAscIdAsc(courseId, EnrollmentStatus.CONFIRMED)
				.get(0).getId();
		enrollmentService.cancel("wl-s14", confirmedId); // w1 승격

		clock.plus(Duration.ofHours(25));

		assertThatThrownBy(() -> enrollmentService.confirm("wl-s15", w1))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.PAYMENT_DUE_EXPIRED);
	}

	/** capacity 1 강의를 만들어 confirmStudent로 만석(자동 CLOSED) 상태로 만든다 */
	private String fullCourse(String confirmStudent) {
		String courseId = openCourse(1);
		String enrollmentId = enrollmentService.apply(confirmStudent, courseId).id();
		enrollmentService.confirm(confirmStudent, enrollmentId);
		return courseId;
	}

	private String openCourse(int capacity) {
		String id = courseService.create(CREATOR,
				new CourseCreateRequest("대기열 테스트 강의", null, 10000, capacity, null, null)).id();
		courseService.changeStatus(CREATOR, id, CourseStatus.OPEN);
		return id;
	}
}
