package com.liveklass.settlement;

import com.liveklass.common.error.BusinessException;
import com.liveklass.common.error.ErrorCode;
import com.liveklass.seed.SeedDataImporter;
import com.liveklass.settlement.dto.SettlementCreateRequest;
import com.liveklass.settlement.dto.SettlementWithDetailsResponse;
import com.liveklass.support.IntegrationTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * B-5(확정 생성·기간 겹침 거부·상태 전이) / B-6(± 상세 라인, 합계 재현성) 검증.
 */
@SpringBootTest
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("test")
class SettlementConfirmFlowTest {

	@Autowired
	private SettlementService settlementService;

	@Autowired
	private SettlementRepository settlementRepository;

	@Autowired
	private SeedDataImporter seedDataImporter;

	@BeforeEach
	void setUp() {
		seedDataImporter.importAll();
		settlementRepository.deleteAll(); // 정산 이력은 테스트마다 초기화 (겹침 거부 검증 간섭 방지)
	}

	@Test
	void 정산_확정은_집계와_상세_라인을_함께_생성한다() {
		SettlementWithDetailsResponse r = confirmMarchForCreator1();

		assertThat(r.settlement().totalSalesAmount()).isEqualTo(260_000);
		assertThat(r.settlement().refundAmount()).isEqualTo(110_000);
		assertThat(r.settlement().netSalesAmount()).isEqualTo(150_000);
		assertThat(r.settlement().commissionAmount()).isEqualTo(30_000);
		assertThat(r.settlement().payoutAmount()).isEqualTo(120_000);
		assertThat(r.settlement().status()).isEqualTo(SettlementStatus.PENDING);

		// B-6: 판매 4건(+) + 환불 2건(−), 라인 합계 = 순 판매 재현
		assertThat(r.details()).hasSize(6);
		long saleSum = r.details().stream()
				.filter(d -> d.recordType() == SettlementDetail.RecordType.SALE)
				.mapToLong(SettlementWithDetailsResponse.DetailLine::amount).sum();
		long cancelSum = r.details().stream()
				.filter(d -> d.recordType() == SettlementDetail.RecordType.CANCEL)
				.mapToLong(SettlementWithDetailsResponse.DetailLine::amount).sum();
		assertThat(saleSum).isEqualTo(260_000);
		assertThat(cancelSum).isEqualTo(-110_000);
		assertThat(saleSum + cancelSum).isEqualTo(r.settlement().netSalesAmount());
	}

	@Test
	void 동일_크리에이터의_기간_겹침_정산은_거부된다() {
		confirmMarchForCreator1();

		assertThatThrownBy(() -> settlementService.confirmSettlement("admin-1", new SettlementCreateRequest(
				"creator-1", LocalDate.of(2025, 3, 15), LocalDate.of(2025, 4, 15))))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.SETTLEMENT_PERIOD_OVERLAP);

		// 다른 크리에이터는 같은 기간 가능
		settlementService.confirmSettlement("admin-1", new SettlementCreateRequest(
				"creator-2", LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 31)));
	}

	@Test
	void 정산_상태는_PENDING_CONFIRMED_PAID_순서만_허용된다() {
		String id = confirmMarchForCreator1().settlement().id();

		// 건너뛰기 거부
		assertThatThrownBy(() -> settlementService.changeStatus(id, SettlementStatus.PAID))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);

		var confirmed = settlementService.changeStatus(id, SettlementStatus.CONFIRMED);
		assertThat(confirmed.confirmedAt()).isNotNull();

		var paid = settlementService.changeStatus(id, SettlementStatus.PAID);
		assertThat(paid.paidAt()).isNotNull();

		// 역행 거부
		assertThatThrownBy(() -> settlementService.changeStatus(id, SettlementStatus.CONFIRMED))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).getErrorCode())
				.isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
	}

	private SettlementWithDetailsResponse confirmMarchForCreator1() {
		return settlementService.confirmSettlement("admin-1", new SettlementCreateRequest(
				"creator-1", LocalDate.of(2025, 3, 1), LocalDate.of(2025, 3, 31)));
	}
}
