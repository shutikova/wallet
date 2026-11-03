package com.wultra.wallet.service.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.concurrent.TimeUnit;

import com.wultra.wallet.model.error.InvalidAmountException;
import com.wultra.wallet.model.error.UnsupportedCurrencyException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AmountsTest {

	private static final Currency USD = Currency.getInstance("USD");

	@ParameterizedTest
	@CsvSource({ "10,USD,10.00", "10.5,USD,10.50", "10.50,USD,10.50", "0.01,USD,0.01", "007,USD,7.00", "100,JPY,100",
			"100,ISK,100" })
	void normalizesToTheScaleOfTheCurrency(String raw, String code, String expected) {
		BigDecimal amount = Amounts.parsePositive(raw, Currency.getInstance(code));

		assertThat(amount.toPlainString()).isEqualTo(expected);
	}

	@ParameterizedTest
	@CsvSource({ "1.001,USD", "10.000,USD", "0.000,USD", "10.0,JPY", "1.5,JPY", "1.5,ISK" })
	void rejectsMoreDecimalsThanTheCurrencyAllowsInsteadOfRounding(String raw, String code) {
		assertThatThrownBy(() -> Amounts.parsePositive(raw, Currency.getInstance(code)))
			.isInstanceOf(InvalidAmountException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = { "0", "000", "0.0", "0.00" })
	void rejectsZero(String raw) {
		assertThatThrownBy(() -> Amounts.parsePositive(raw, USD)).isInstanceOf(InvalidAmountException.class);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "-5", "+5", "1e5", "1E+3", ".5", "5.", "1.2.3", " 5", "5 ", "1,000", "1 000", "abc", "NaN",
			"0x10", "١٠" })
	void rejectsAnythingThatIsNotAPlainDecimal(String raw) {
		assertThatThrownBy(() -> Amounts.parsePositive(raw, USD)).isInstanceOf(InvalidAmountException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = { "999999999999999", "999999999999999.99", "0999999999999999", "000000000000000000001" })
	void allowsFifteenIntegerDigitsNotCountingLeadingZeros(String raw) {
		assertThat(Amounts.parsePositive(raw, USD).signum()).isPositive();
	}

	@ParameterizedTest
	@ValueSource(strings = { "1000000000000000", "1000000000000000.00", "9999999999999999" })
	void rejectsMoreThanFifteenIntegerDigits(String raw) {
		assertThatThrownBy(() -> Amounts.parsePositive(raw, USD)).isInstanceOf(InvalidAmountException.class);
	}

	@Test
	@Timeout(value = 5, unit = TimeUnit.SECONDS)
	void rejectsHugeInputWithoutParsingIt() {
		String millionDigits = "9".repeat(1_000_000);

		assertThatThrownBy(() -> Amounts.parsePositive(millionDigits, USD)).isInstanceOf(InvalidAmountException.class);
		assertThatThrownBy(() -> Amounts.parsePositive("1e999999999", USD)).isInstanceOf(InvalidAmountException.class);
	}

	@Test
	@Timeout(value = 5, unit = TimeUnit.SECONDS)
	void handlesAMillionLeadingZerosQuickly() {
		String paddedOne = "0".repeat(1_000_000) + "1";

		assertThat(Amounts.parsePositive(paddedOne, USD).toPlainString()).isEqualTo("1.00");
	}

	@ParameterizedTest
	@ValueSource(strings = { "XDR", "AED", "KWD", "DEM" })
	void rejectsCurrenciesOutsideTheSupportedList(String code) {
		assertThatThrownBy(() -> Amounts.parsePositive("1", Currency.getInstance(code)))
			.isInstanceOf(UnsupportedCurrencyException.class);
	}

}
