package com.wultra.wallet.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;

import org.junit.jupiter.api.Test;

class ConversionTest {

	private static final Currency USD = Currency.getInstance("USD");

	private static final Conversion CONVERSION = conversion("7.00", "7.00");

	@Test
	void comparesAmountsByValue() {
		Conversion sameValues = conversion("7.0", "7.0");

		assertThat(CONVERSION).isEqualTo(sameValues).hasSameHashCodeAs(sameValues);
	}

	@Test
	void differentTotalIsNotEqual() {
		assertThat(CONVERSION).isNotEqualTo(conversion("7.01", "7.00"));
	}

	@Test
	void differentLinesAreNotEqual() {
		assertThat(CONVERSION).isNotEqualTo(conversion("7.00", "7.01"));
	}

	@Test
	void otherTypeIsNotEqual() {
		assertThat(CONVERSION).isNotEqualTo("7.00");
	}

	@Test
	void linesCannotBeChangedAfterwards() {
		assertThat(CONVERSION.lines()).isUnmodifiable();
	}

	private static Conversion conversion(String total, String lineAmount) {
		BigDecimal amount = new BigDecimal(lineAmount);
		return new Conversion(new BigDecimal(total), List.of(new ConvertedLine(USD, amount, amount)));
	}

}
