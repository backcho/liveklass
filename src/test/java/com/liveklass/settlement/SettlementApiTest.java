package com.liveklass.settlement;

import com.liveklass.seed.SeedDataImporter;
import com.liveklass.support.IntegrationTestConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 정산 API 규격·인가 스모크 — 시드 사용자(비밀번호 {id}!)로 HTTP Basic 호출.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(IntegrationTestConfiguration.class)
@ActiveProfiles("test")
class SettlementApiTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private SeedDataImporter seedDataImporter;

	@BeforeEach
	void setUp() {
		seedDataImporter.importAll();
	}

	@Test
	void 크리에이터가_본인_월별_정산을_조회한다() throws Exception {
		mockMvc.perform(get("/api/settlements/monthly")
						.param("creatorId", "creator-1").param("month", "2025-03")
						.with(httpBasic("creator-1@liveklass.local", "creator-1!")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalSalesAmount").value(260000))
				.andExpect(jsonPath("$.payoutAmount").value(120000))
				.andExpect(jsonPath("$.salesCount").value(4))
				.andExpect(jsonPath("$.cancelCount").value(2));
	}

	@Test
	void 타인_정산_조회는_403이다() throws Exception {
		mockMvc.perform(get("/api/settlements/monthly")
						.param("creatorId", "creator-2").param("month", "2025-03")
						.with(httpBasic("creator-1@liveklass.local", "creator-1!")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
	}

	@Test
	void 잘못된_연월_형식은_400이다() throws Exception {
		mockMvc.perform(get("/api/settlements/monthly")
						.param("creatorId", "creator-1").param("month", "2025/03")
						.with(httpBasic("creator-1@liveklass.local", "creator-1!")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void 운영자_집계는_ADMIN_전용이다() throws Exception {
		mockMvc.perform(get("/api/admin/settlements/aggregate")
						.param("from", "2025-03-01").param("to", "2025-03-31")
						.with(httpBasic("creator-1@liveklass.local", "creator-1!")))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/admin/settlements/aggregate")
						.param("from", "2025-03-01").param("to", "2025-03-31")
						.with(httpBasic("admin-1@liveklass.local", "admin-1!")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalPayoutAmount").value(168000));
	}
}
