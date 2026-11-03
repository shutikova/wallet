package com.wultra.wallet.model;

import java.math.BigDecimal;
import java.util.Currency;

public record Balance(Currency currency, BigDecimal amount) {

	// Every balance in a currency has the same scale, so the generated equals compares by value.
	public Balance {
		if (amount.scale() != currency.getDefaultFractionDigits()) {
			throw new IllegalArgumentException("Amount must use the decimal places of " + currency);
		}
	}

}
