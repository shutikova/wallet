package com.wultra.wallet.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;

import org.junit.jupiter.api.Test;

class ConversionTest {

	private static final Currency EUR = Currency.getInstance("EUR");

	private static final Currency USD = Currency.getInstance("USD");

	@Test
	void comparesAmountsByValue() {
		Conversion conversion = conversion(USD, "7.0");

		assertThat(conversion).isEqualTo(conversion(USD, "7.00")).hasSameHashCodeAs(conversion(USD, "7.00"));
		assertThat(conversion.lines().get(0)).isEqualTo(conversion(USD, "7.00").lines().get(0))
			.hasSameHashCodeAs(conversion(USD, "7.00").lines().get(0));
	}

	@Test
	void differentValuesAreNotEqual() {
		assertThat(conversion(USD, "7.00")).isNotEqualTo(conversion(USD, "7.01"));
		assertThat(conversion(USD, "7.00")).isNotEqualTo(conversion(EUR, "7.00"));
	}

	private static Conversion conversion(Currency currency, String amount) {
		BigDecimal value = new BigDecimal(amount);
		return new Conversion(value, List.of(new ConvertedLine(currency, value, value)));
	}

}
