package com.liveklass.sale;

import com.liveklass.commission.CommissionRateService;
import com.liveklass.commission.dto.CommissionRateCreateRequest;
import com.liveklass.common.error.BusinessException;
import com.liveklass.common.error.ErrorCode;
import com.liveklass.course.Course;
import com.liveklass.course.CourseRepository;
import com.liveklass.course.CourseStatus;
import com.liveklass.sale.dto.CancelCreateRequest;
import com.liveklass.sale.dto.SaleCreateRequest;
import com.liveklass.support.IntegrationTestConfiguration;
import com.liveklass.user.Role;
import com.liveklass.user.User;
import com.liveklass.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * B-2(누적 환불 ≤ 원금) / B-3(요율 스냅샷, 개별>기본, 기간 겹침 거부) 규칙 검증.
 */
@SpringBootTest
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("test")
class SaleCommissionRuleTest {

	@Autowired
	private SaleService saleService;

	@Autowired
	private SaleRecordRepository saleRecordRepository;

	@Autowired
	private CommissionRateService commissionRateService;

	@Autowired
	private CourseRepository courseRepository;

	@Autowired
	private UserRepository userRepository;

	private String creatorId;
	private String courseId;

	@BeforeEach
	void setUp() {
		creatorId = "rate-c-" + UUID.randomUUID().toString().substring(0, 8);
		userRepository.save(User.builder()
				.id(creatorId).name("요율 테스트 강사").email(creatorId + "@liveklass.local")
				.password("n/a").role(Role.CREATOR).build());
		Course course = courseRepository.save(Course.builder()
				.creatorId(creatorId).title("요율 테스트 강의").price(100000).capacity(10)
				.status(CourseStatus.OPEN).build());
		courseId = course.getId();
	}

	@Test
	void 부분_환불은_다회_누적되고_원금_초과는_거부된다() {
		String saleId = createSale(80_000, LocalDateTime.of(2025, 5, 1, 10, 0));

		saleService.registerCancel(saleId, new CancelCreateRequest(null, 30_000,
				LocalDateTime.of(2025, 5, 2, 10, 0)));
		saleService.registerCancel(saleId, new CancelCreateRequest(null, 30_000,
				LocalDateTime.of(2025, 5, 3, 10, 0)));

		// 누적 60,000 + 30,000 > 80,000 → 거부 (B-2)
		assertThatThrownBy(() -> saleService.registerCancel(saleId, new CancelCreateRequest(null, 30_000,
				LocalDateTime.of(2025, 5, 4, 10, 0))))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.REFUND_EXCEEDS_SALE_AMOUNT);

		// 잔여 20,000 정확히는 허용
		saleService.registerCancel(saleId, new CancelCreateRequest(null, 20_000,
				LocalDateTime.of(2025, 5, 5, 10, 0)));
	}

	@Test
	void 판매_시점_요율이_스냅샷되고_개별_요율이_기본보다_우선한다() {
		// 개별 요율 10% — 2025-06-01 ~ 2025-06-30
		commissionRateService.create("admin-1", new CommissionRateCreateRequest(
				creatorId, new BigDecimal("10.00"), LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 30)));

		String before = createSale(100_000, LocalDateTime.of(2025, 5, 15, 10, 0)); // 기간 밖 → 기본 20%
		String inside = createSale(100_000, LocalDateTime.of(2025, 6, 15, 10, 0)); // 기간 안 → 개별 10%

		assertThat(saleRecordRepository.findById(before).orElseThrow().getCommissionRate())
				.isEqualByComparingTo("20.00");
		assertThat(saleRecordRepository.findById(inside).orElseThrow().getCommissionRate())
				.isEqualByComparingTo("10.00"); // B-3: 요율 변경 전후 스냅샷 차이
	}

	@Test
	void 동일_대상의_요율_기간_겹침은_거부된다() {
		commissionRateService.create("admin-1", new CommissionRateCreateRequest(
				creatorId, new BigDecimal("15.00"), LocalDate.of(2025, 7, 1), LocalDate.of(2025, 7, 31)));

		// 겹침 (7/15~8/15)
		assertThatThrownBy(() -> commissionRateService.create("admin-1", new CommissionRateCreateRequest(
				creatorId, new BigDecimal("12.00"), LocalDate.of(2025, 7, 15), LocalDate.of(2025, 8, 15))))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.COMMISSION_RATE_PERIOD_OVERLAP);

		// 무기한(endedAt null)은 이후 모든 기간과 겹침
		commissionRateService.create("admin-1", new CommissionRateCreateRequest(
				creatorId, new BigDecimal("11.00"), LocalDate.of(2025, 9, 1), null));
		assertThatThrownBy(() -> commissionRateService.create("admin-1", new CommissionRateCreateRequest(
				creatorId, new BigDecimal("13.00"), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.COMMISSION_RATE_PERIOD_OVERLAP);
	}

	private String createSale(int amount, LocalDateTime paidAt) {
		return saleService.create(new SaleCreateRequest(null, courseId, "student-x", amount, paidAt)).id();
	}
}
