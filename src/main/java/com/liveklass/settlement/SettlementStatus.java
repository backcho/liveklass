package com.liveklass.settlement;

public enum SettlementStatus {
	PENDING, CONFIRMED, PAID;

	// B-5: PENDING → CONFIRMED → PAID 단방향
	public boolean canTransitionTo(SettlementStatus target) {
		return switch (this) {
			case PENDING -> target == CONFIRMED;
			case CONFIRMED -> target == PAID;
			case PAID -> false;
		};
	}
}
