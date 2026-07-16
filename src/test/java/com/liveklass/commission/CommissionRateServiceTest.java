package com.liveklass.commission;

import com.liveklass.commission.dto.CommissionRateCreateRequest;
import com.liveklass.common.error.BusinessException;
import com.liveklass.common.error.ErrorCode;
import com.liveklass.seed.SeedDataImporter;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * B-3: 전체 기본 요율(creatorId null) 재등록은 겹치는 이전 요율을 자동 마감 후 교체한다.
 * 개별 크리에이터 요율은 기존대로 기간 겹침을 거부한다.
 */
@SpringBootTest
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("test")
class CommissionRateServiceTest {

	@Autowired
	private CommissionRateService commissionRateService;

	@Autowired
	private CommissionRateRepository commissionRateRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private SeedDataImporter seedDataImporter;

	private String creatorId;

	@BeforeEach
	void setUp() {
		creatorId = "rate-c-" + UUID.randomUUID().toString().substring(0, 8);
		userRepository.save(User.builder()
				.id(creatorId).name("요율 테스트 강사").email(creatorId + "@liveklass.local")
				.password("x").role(Role.CREATOR).build());
		seedDataImporter.importAll(); // rate-default(2020-01-01~, 20%)가 존재함을 보장 (멱등)
	}

	@Test
	void 전체_기본_요율_재등록은_겹치는_이전_요율을_자동_마감한다() {
		commissionRateService.create("admin-1", new CommissionRateCreateRequest(
				null, new BigDecimal("15.00"), LocalDate.of(2026, 8, 1), null));

		CommissionRate previousDefault = commissionRateRepository.findById("rate-default").orElseThrow();
		assertThat(previousDefault.getEndedAt()).isEqualTo(LocalDate.of(2026, 7, 31)); // 신규 시작일 전날로 자동 마감

		assertThat(commissionRateService.resolveRate(creatorId, LocalDate.of(2026, 7, 31)))
				.isEqualByComparingTo("20.00");
		assertThat(commissionRateService.resolveRate(creatorId, LocalDate.of(2026, 8, 1)))
				.isEqualByComparingTo("15.00");
	}

	@Test
	void 개별_크리에이터_요율은_기간_겹침을_그대로_거부한다() {
		commissionRateService.create("admin-1", new CommissionRateCreateRequest(
				creatorId, new BigDecimal("10.00"), LocalDate.of(2026, 1, 1), null));

		assertThatThrownBy(() -> commissionRateService.create("admin-1", new CommissionRateCreateRequest(
				creatorId, new BigDecimal("12.00"), LocalDate.of(2026, 6, 1), null)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.COMMISSION_RATE_PERIOD_OVERLAP);
	}
}
