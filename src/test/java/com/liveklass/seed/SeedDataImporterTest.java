package com.liveklass.seed;

import com.liveklass.course.CourseRepository;
import com.liveklass.course.CourseStatus;
import com.liveklass.enrollment.EnrollmentRepository;
import com.liveklass.enrollment.EnrollmentStatus;
import com.liveklass.support.IntegrationTestConfiguration;
import com.liveklass.user.Role;
import com.liveklass.user.User;
import com.liveklass.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 시드 데이터(sample-data.json) 규모·대기열 시나리오 검증. 실제 기동 시 SeedDataLoader가 호출하는
 * 경로와 동일(SeedDataImporter#importAll, 멱등).
 */
@SpringBootTest
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("test")
class SeedDataImporterTest {

	@Autowired
	private SeedDataImporter seedDataImporter;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CourseRepository courseRepository;

	@Autowired
	private EnrollmentRepository enrollmentRepository;

	@Test
	void 크리에이터_10명과_수강생_20명이_시드된다() {
		// 다른 테스트가 같은 DB에 임의의 CREATOR/STUDENT를 추가로 만들 수 있어 전체 카운트 대신
		// 시드 전용 id(creator-4~10, student-8~20)의 존재와 role만 확인한다.
		seedDataImporter.importAll();

		for (int i = 4; i <= 10; i++) {
			assertThat(userRepository.findById("creator-" + i)).get()
					.extracting(User::getRole).isEqualTo(Role.CREATOR);
		}
		for (int i = 8; i <= 20; i++) {
			assertThat(userRepository.findById("student-" + i)).get()
					.extracting(User::getRole).isEqualTo(Role.STUDENT);
		}
	}

	@Test
	void course4는_정원_도달로_10명_확정_5명_대기열이다() {
		seedDataImporter.importAll();

		var course = courseRepository.findById("course-4").orElseThrow();
		assertThat(course.getConfirmedCount()).isEqualTo(10);
		assertThat(course.getCapacity()).isEqualTo(10);
		assertThat(course.getStatus()).isEqualTo(CourseStatus.CLOSED); // A-5: 만석 자동 마감

		assertThat(enrollmentRepository.countByCourseIdAndStatus("course-4", EnrollmentStatus.CONFIRMED))
				.isEqualTo(10);
		assertThat(enrollmentRepository.countByCourseIdAndStatus("course-4", EnrollmentStatus.WAITLISTED))
				.isEqualTo(5);
	}

	@Test
	void course5는_정원_초과_신청에도_확정_인원이_정원_미만이라_OPEN을_유지한다() {
		seedDataImporter.importAll();

		var course = courseRepository.findById("course-5").orElseThrow();
		assertThat(course.getCapacity()).isEqualTo(10);
		assertThat(course.getConfirmedCount()).isEqualTo(6);
		assertThat(course.getStatus()).isEqualTo(CourseStatus.OPEN); // 정원 미달 — 자동 마감 안 됨

		long confirmed = enrollmentRepository.countByCourseIdAndStatus("course-5", EnrollmentStatus.CONFIRMED);
		long pending = enrollmentRepository.countByCourseIdAndStatus("course-5", EnrollmentStatus.PENDING);
		assertThat(confirmed).isEqualTo(6);
		assertThat(pending).isEqualTo(6);
		assertThat(confirmed + pending).isGreaterThan(course.getCapacity()); // 신청 총원(12) > 정원(10)
	}
}
