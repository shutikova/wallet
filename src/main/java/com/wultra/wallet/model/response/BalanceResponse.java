package com.wultra.wallet.model.response;

import com.wultra.wallet.model.Balance;
import io.swagger.v3.oas.annotations.media.Schema;

public record BalanceResponse(@Schema(example = "USD") String currency, @Schema(example = "10.50") String balance) {

	public static BalanceResponse from(Balance balance) {
		return new BalanceResponse(balance.currency().getCurrencyCode(), balance.amount().toPlainString());
	}

}
