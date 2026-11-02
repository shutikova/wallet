package com.wultra.wallet.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Currency;

import org.junit.jupiter.api.Test;

class ConvertedLineTest {

	private static final Currency EUR = Currency.getInstance("EUR");

	private static final Currency USD = Currency.getInstance("USD");

	private static final ConvertedLine LINE = line(USD, "7.00", "5.50");

	@Test
	void comparesAmountsByValue() {
		ConvertedLine sameValues = line(USD, "7.0", "5.5");

		assertThat(LINE).isEqualTo(sameValues).hasSameHashCodeAs(sameValues);
	}

	@Test
	void differentCurrencyIsNotEqual() {
		assertThat(LINE).isNotEqualTo(line(EUR, "7.00", "5.50"));
	}

	@Test
	void differentAmountIsNotEqual() {
		assertThat(LINE).isNotEqualTo(line(USD, "7.01", "5.50"));
	}

	@Test
	void differentConvertedValueIsNotEqual() {
		assertThat(LINE).isNotEqualTo(line(USD, "7.00", "5.51"));
	}

	@Test
	void otherTypeIsNotEqual() {
		assertThat(LINE).isNotEqualTo("7.00");
	}

	private static ConvertedLine line(Currency currency, String amount, String converted) {
		return new ConvertedLine(currency, new BigDecimal(amount), new BigDecimal(converted));
	}

}
