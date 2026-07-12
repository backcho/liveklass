package com.liveklass.sale;

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
 * B-1: 결제 확정 → SaleRecord, CONFIRMED 취소 → CancelRecord 자동 생성 (phase-1 연동).
 */
@SpringBootTest
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("test")
class EnrollmentSaleIntegrationTest {

	private static final String CREATOR = "sale-int-creator";

	@Autowired
	private EnrollmentService enrollmentService;

	@Autowired
	private CourseService courseService;

	@Autowired
	private SaleRecordRepository saleRecordRepository;

	@Autowired
	private CancelRecordRepository cancelRecordRepository;

	@Autowired
	private MutableClock clock;

	private String courseId;

	@BeforeEach
	void setUp() {
		clock.reset();
		courseId = courseService.create(CREATOR,
				new CourseCreateRequest("자동 판매 강의", null, 45000, 10, null, null)).id();
		courseService.changeStatus(CREATOR, courseId, CourseStatus.OPEN);
	}

	@Test
	void 결제_확정_시_요율_스냅샷과_함께_판매가_자동_생성된다() {
		String enrollmentId = enrollmentService.apply("sale-int-s1", courseId).id();
		enrollmentService.confirm("sale-int-s1", enrollmentId);

		SaleRecord sale = saleRecordRepository.findByEnrollmentId(enrollmentId).orElseThrow();
		assertThat(sale.getAmount()).isEqualTo(45000); // 강의 가격
		assertThat(sale.getCourseId()).isEqualTo(courseId);
		assertThat(sale.getStudentId()).isEqualTo("sale-int-s1");
		assertThat(sale.getCommissionRate()).isEqualByComparingTo("20.00"); // 기본 요율 폴백
	}

	@Test
	void 확정_수강의_취소는_잔여_전액_환불을_자동_생성한다() {
		String enrollmentId = enrollmentService.apply("sale-int-s2", courseId).id();
		enrollmentService.confirm("sale-int-s2", enrollmentId);
		enrollmentService.cancel("sale-int-s2", enrollmentId);

		SaleRecord sale = saleRecordRepository.findByEnrollmentId(enrollmentId).orElseThrow();
		assertThat(cancelRecordRepository.sumRefundBySaleRecordId(sale.getId())).isEqualTo(45000); // B-2a
	}

	@Test
	void 미확정_취소는_판매_환불_기록을_남기지_않는다() {
		String enrollmentId = enrollmentService.apply("sale-int-s3", courseId).id();
		enrollmentService.cancel("sale-int-s3", enrollmentId); // PENDING 취소

		assertThat(saleRecordRepository.findByEnrollmentId(enrollmentId)).isEmpty();
	}
}
