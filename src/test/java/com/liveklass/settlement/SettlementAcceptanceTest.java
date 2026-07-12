package com.liveklass.settlement;

import com.liveklass.auth.AuthUser;
import com.liveklass.common.error.BusinessException;
import com.liveklass.common.error.ErrorCode;
import com.liveklass.seed.SeedDataImporter;
import com.liveklass.settlement.dto.MonthlySettlementResponse;
import com.liveklass.support.IntegrationTestConfiguration;
import com.liveklass.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 과제 B 원문 "샘플 데이터로 검증해야 할 시나리오" 4건 — 시드와 동일한 SeedDataImporter로 주입해 검증.
 */
@SpringBootTest
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("test")
class SettlementAcceptanceTest {

	@Autowired
	private SettlementService settlementService;

	@Autowired
	private SeedDataImporter seedDataImporter;

	@Autowired
	private UserRepository userRepository;

	@BeforeEach
	void setUp() {
		seedDataImporter.importAll(); // 멱등
	}

	@Test
	void 시나리오1_creator1의_2025_03_정산() {
		MonthlySettlementResponse r = monthlyAsAdmin("creator-1", "2025-03");

		assertThat(r.totalSalesAmount()).isEqualTo(260_000); // sale-1,2,3,4
		assertThat(r.refundAmount()).isEqualTo(110_000);     // cancel-1(80k), cancel-2(30k)
		assertThat(r.netSalesAmount()).isEqualTo(150_000);
		assertThat(r.commissionAmount()).isEqualTo(30_000);  // 20%
		assertThat(r.payoutAmount()).isEqualTo(120_000);
		assertThat(r.salesCount()).isEqualTo(4);
		assertThat(r.cancelCount()).isEqualTo(2);
	}

	@Test
	void 시나리오2_부분_환불이_순_판매에_반영된다() {
		// cancel-2(30,000) < sale-4(80,000) — 부분 환불만큼만 차감되는지는 시나리오1 합계에 포함되지만,
		// 단독으로도 확인: creator-1 3월 환불 총액이 전액(160,000)이 아니라 110,000
		MonthlySettlementResponse r = monthlyAsAdmin("creator-1", "2025-03");
		assertThat(r.refundAmount()).isEqualTo(110_000);
		assertThat(r.refundAmount()).isNotEqualTo(160_000);
	}

	@Test
	void 시나리오3_월_경계_판매와_취소는_각자의_월에_귀속된다() {
		// sale-5: 1월 판매 → 1월 정산에 +
		MonthlySettlementResponse jan = monthlyAsAdmin("creator-2", "2025-01");
		assertThat(jan.totalSalesAmount()).isEqualTo(60_000);
		assertThat(jan.refundAmount()).isZero();
		assertThat(jan.payoutAmount()).isEqualTo(48_000);

		// cancel-3: 2월 취소 → 2월 정산에 − (음수 허용, B-3a)
		MonthlySettlementResponse feb = monthlyAsAdmin("creator-2", "2025-02");
		assertThat(feb.totalSalesAmount()).isZero();
		assertThat(feb.refundAmount()).isEqualTo(60_000);
		assertThat(feb.netSalesAmount()).isEqualTo(-60_000);
		assertThat(feb.commissionAmount()).isEqualTo(-12_000);
		assertThat(feb.payoutAmount()).isEqualTo(-48_000);

		// 3월엔 sale-6만
		MonthlySettlementResponse mar = monthlyAsAdmin("creator-2", "2025-03");
		assertThat(mar.totalSalesAmount()).isEqualTo(60_000);
		assertThat(mar.payoutAmount()).isEqualTo(48_000);
	}

	@Test
	void 시나리오4_판매_없는_월은_0원_정상_응답이다() {
		MonthlySettlementResponse r = monthlyAsAdmin("creator-3", "2025-03"); // B-4

		assertThat(r.totalSalesAmount()).isZero();
		assertThat(r.refundAmount()).isZero();
		assertThat(r.netSalesAmount()).isZero();
		assertThat(r.commissionAmount()).isZero();
		assertThat(r.payoutAmount()).isZero();
		assertThat(r.salesCount()).isZero();
		assertThat(r.cancelCount()).isZero();
	}

	@Test
	void 운영자_집계는_기간_내_크리에이터별_정산과_합계를_반환한다() {
		var r = settlementService.aggregate(
				java.time.LocalDate.of(2025, 3, 1), java.time.LocalDate.of(2025, 3, 31));

		// 3월: creator-1 120,000 + creator-2 48,000 (creator-3은 3월 기록 없음)
		assertThat(r.creators()).extracting("creatorId").containsExactly("creator-1", "creator-2");
		assertThat(r.totalPayoutAmount()).isEqualTo(168_000);
	}

	@Test
	void 크리에이터는_본인_정산만_조회할_수_있다() {
		AuthUser creator1 = authUser("creator-1");

		assertThat(settlementService.monthly(creator1, "creator-1", "2025-03").payoutAmount())
				.isEqualTo(120_000);
		assertThatThrownBy(() -> settlementService.monthly(creator1, "creator-2", "2025-03"))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.FORBIDDEN);
	}

	@Test
	void 잘못된_연월_형식은_거부되고_미래_연월은_0원으로_허용된다() {
		AuthUser admin = authUser("admin-1");

		assertThatThrownBy(() -> settlementService.monthly(admin, "creator-1", "2025-3"))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_REQUEST);

		assertThat(settlementService.monthly(admin, "creator-1", "2099-01").payoutAmount()).isZero(); // B-4a
	}

	private MonthlySettlementResponse monthlyAsAdmin(String creatorId, String month) {
		return settlementService.monthly(authUser("admin-1"), creatorId, month);
	}

	private AuthUser authUser(String userId) {
		return new AuthUser(userRepository.findById(userId).orElseThrow());
	}
}
