package com.wultra.wallet.model;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

public record ConvertedLine(Currency currency, BigDecimal amount, BigDecimal converted) {

	// Compares by value, because the generated equals would treat 7.0 and 7.00 as different.
	@Override
	public boolean equals(Object other) {
		return other instanceof ConvertedLine line && this.currency.equals(line.currency)
				&& this.amount.compareTo(line.amount) == 0 && this.converted.compareTo(line.converted) == 0;
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.currency, this.amount.stripTrailingZeros(), this.converted.stripTrailingZeros());
	}

}
