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

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A-3: 핵심 평가 항목 — 정원 경합과 중복 신청의 동시성 보증.
 */
@SpringBootTest
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("test")
class EnrollmentConcurrencyTest {

	private static final String CREATOR = "cc-creator";

	@Autowired
	private EnrollmentService enrollmentService;

	@Autowired
	private EnrollmentRepository enrollmentRepository;

	@Autowired
	private CourseRepository courseRepository;

	@Autowired
	private CourseService courseService;

	@Autowired
	private MutableClock clock;

	@BeforeEach
	void setUp() {
		clock.reset();
	}

	@Test
	void 잔여_1석에_동시_결제_확정이_몰려도_정확히_1명만_확정된다() throws Exception {
		String courseId = openCourse(1);
		int threadCount = 10;
		List<String> enrollmentIds = new ArrayList<>();
		for (int i = 0; i < threadCount; i++) {
			enrollmentIds.add(enrollmentService.apply("cc-race-s" + i, courseId).id());
		}

		AtomicInteger successCount = new AtomicInteger();
		Queue<ErrorCode> failures = new ConcurrentLinkedQueue<>();
		runConcurrently(threadCount, i -> {
			try {
				enrollmentService.confirm("cc-race-s" + i, enrollmentIds.get(i));
				successCount.incrementAndGet();
			} catch (BusinessException e) {
				failures.add(e.getErrorCode());
			}
		});

		assertThat(successCount.get()).isEqualTo(1);
		assertThat(failures).hasSize(threadCount - 1)
				.allMatch(code -> code == ErrorCode.COURSE_CAPACITY_EXCEEDED);

		// confirmed_count == CONFIRMED 행 수 정합성 (A-3)
		var course = courseRepository.findById(courseId).orElseThrow();
		assertThat(course.getConfirmedCount()).isEqualTo(1);
		assertThat(enrollmentRepository.countByCourseIdAndStatus(courseId, EnrollmentStatus.CONFIRMED))
				.isEqualTo(1);
		assertThat(course.getStatus()).isEqualTo(CourseStatus.CLOSED); // 만석 자동 마감
	}

	@Test
	void 동일_사용자의_동시_중복_신청은_유니크_제약으로_1건만_성공한다() throws Exception {
		String courseId = openCourse(10);
		int threadCount = 5;

		AtomicInteger successCount = new AtomicInteger();
		Queue<ErrorCode> failures = new ConcurrentLinkedQueue<>();
		runConcurrently(threadCount, i -> {
			try {
				enrollmentService.apply("cc-dup-student", courseId);
				successCount.incrementAndGet();
			} catch (BusinessException e) {
				failures.add(e.getErrorCode());
			}
		});

		assertThat(successCount.get()).isEqualTo(1);
		assertThat(failures).hasSize(threadCount - 1)
				.allMatch(code -> code == ErrorCode.DUPLICATE_ACTIVE_ENROLLMENT);
		assertThat(enrollmentRepository.existsByCourseIdAndStudentIdAndStatusIn(
				courseId, "cc-dup-student", EnrollmentStatus.ACTIVE_STATUSES)).isTrue();
	}

	@Test
	void 동시_확정과_취소가_섞여도_카운터_정합성이_유지된다() throws Exception {
		String courseId = openCourse(5);
		int threadCount = 10; // 5석에 10명 경쟁
		List<String> enrollmentIds = new ArrayList<>();
		for (int i = 0; i < threadCount; i++) {
			enrollmentIds.add(enrollmentService.apply("cc-mix-s" + i, courseId).id());
		}

		runConcurrently(threadCount, i -> {
			try {
				enrollmentService.confirm("cc-mix-s" + i, enrollmentIds.get(i));
				if (i % 2 == 0) {
					enrollmentService.cancel("cc-mix-s" + i, enrollmentIds.get(i));
				}
			} catch (BusinessException ignored) {
				// 정원 초과 실패는 정상 경로
			}
		});

		var course = courseRepository.findById(courseId).orElseThrow();
		long confirmedRows = enrollmentRepository.countByCourseIdAndStatus(courseId, EnrollmentStatus.CONFIRMED);
		assertThat(course.getConfirmedCount()).isEqualTo(confirmedRows); // A-3 정합성
		assertThat(course.getConfirmedCount()).isLessThanOrEqualTo(5);
	}

	private String openCourse(int capacity) {
		String id = courseService.create(CREATOR,
				new CourseCreateRequest("동시성 테스트 강의", null, 10000, capacity, null, null)).id();
		courseService.changeStatus(id, CourseStatus.OPEN);
		return id;
	}

	private void runConcurrently(int threadCount, java.util.function.IntConsumer task) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		CountDownLatch ready = new CountDownLatch(threadCount);
		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(threadCount);
		for (int i = 0; i < threadCount; i++) {
			int index = i;
			executor.submit(() -> {
				ready.countDown();
				try {
					start.await();
					task.accept(index);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} finally {
					done.countDown();
				}
			});
		}
		assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
		start.countDown(); // 동시 출발
		assertThat(done.await(60, TimeUnit.SECONDS)).isTrue();
		executor.shutdown();
	}
}
