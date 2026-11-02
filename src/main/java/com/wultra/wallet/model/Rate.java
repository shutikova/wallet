package com.wultra.wallet.model;

import java.math.BigDecimal;

/**
 * {@code units} of a currency cost {@code czkValue} CZK, as quoted by CNB (e.g. 100 JPY = 13.875 CZK).
 */
public record Rate(BigDecimal czkValue, int units) {

	public static final Rate CZK = new Rate(BigDecimal.ONE, 1);

	public Rate {
		if (czkValue.signum() <= 0) {
			throw new IllegalArgumentException("Rate value must be positive");
		}
		// Dividing by a power of 10 is always exact, so conversion never rounds early (see README decision 2).
		if (!String.valueOf(units).matches("10*")) {
			throw new IllegalArgumentException("Rate units must be a power of 10");
		}
		// Same scale for equal values, because the generated equals compares BigDecimal scale too.
		czkValue = czkValue.stripTrailingZeros();
	}

}
