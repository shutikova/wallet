package com.wultra.wallet.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BalanceTest {

	private static final Currency USD = Currency.getInstance("USD");

	@ParameterizedTest
	@CsvSource({ "USD,5.00", "USD,0.00", "JPY,5", "JPY,0" })
	void acceptsTheDecimalPlacesOfTheCurrency(String code, String amount) {
		Balance balance = new Balance(Currency.getInstance(code), new BigDecimal(amount));

		assertThat(balance.amount().toPlainString()).isEqualTo(amount);
	}

	@ParameterizedTest
	@CsvSource({ "USD,5", "USD,5.0", "USD,5.001", "JPY,5.0", "JPY,1E+3" })
	void rejectsOtherDecimalPlacesInsteadOfChangingThem(String code, String amount) {
		assertThatThrownBy(() -> new Balance(Currency.getInstance(code), new BigDecimal(amount)))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void equalBalancesHaveTheSameValue() {
		Balance balance = new Balance(USD, new BigDecimal("5.00"));

		assertThat(balance).isEqualTo(new Balance(USD, new BigDecimal("5.00")))
			.hasSameHashCodeAs(new Balance(USD, new BigDecimal("5.00")));
		assertThat(balance).isNotEqualTo(new Balance(USD, new BigDecimal("5.01")));
	}

}
