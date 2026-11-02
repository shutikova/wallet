package com.wultra.wallet.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RateTest {

	@ParameterizedTest
	@ValueSource(ints = { 1, 10, 100, 1000 })
	void acceptsUnitsThatArePowersOfTen(int units) {
		assertThat(new Rate(new BigDecimal("21.814"), units).units()).isEqualTo(units);
	}

	@ParameterizedTest
	@ValueSource(ints = { 0, -1, -100, 3, 20, 101, 1001 })
	void rejectsOtherUnits(int units) {
		assertThatThrownBy(() -> new Rate(new BigDecimal("21.814"), units)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsValuesThatCannotBeConverted() {
		assertThatThrownBy(() -> new Rate(new BigDecimal("0.000"), 1)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Rate(new BigDecimal("-1"), 1)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void comparesTheValueByValue() {
		Rate rate = new Rate(new BigDecimal("24.360"), 1);

		assertThat(rate).isEqualTo(new Rate(new BigDecimal("24.36"), 1))
			.hasSameHashCodeAs(new Rate(new BigDecimal("24.36"), 1));
		assertThat(rate).isNotEqualTo(new Rate(new BigDecimal("24.361"), 1));
		assertThat(rate).isNotEqualTo(new Rate(new BigDecimal("24.360"), 100));
	}

}
