package com.wultra.wallet.model.response;

import java.util.List;

import com.wultra.wallet.model.Balance;
import io.swagger.v3.oas.annotations.media.Schema;

public record BalancesResponse(List<BalanceLine> balances) {

	public static BalancesResponse from(List<Balance> balances) {
		return new BalancesResponse(balances.stream().map(BalanceLine::from).toList());
	}

	public record BalanceLine(@Schema(example = "EUR") String currency, @Schema(example = "5.00") String amount) {

		private static BalanceLine from(Balance balance) {
			return new BalanceLine(balance.currency().getCurrencyCode(), balance.amount().toPlainString());
		}

	}

}
