package com.wultra.wallet.service.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import com.wultra.wallet.model.Conversion;
import com.wultra.wallet.model.ConvertedLine;
import com.wultra.wallet.model.Rate;
import com.wultra.wallet.model.error.RateNotAvailableException;
import com.wultra.wallet.model.error.UnsupportedCurrencyException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CurrencyConverterTest {

	private static final Currency AUD = Currency.getInstance("AUD");

	private static final Currency CZK = Currency.getInstance("CZK");

	private static final Currency EUR = Currency.getInstance("EUR");

	private static final Currency IDR = Currency.getInstance("IDR");

	private static final Currency JPY = Currency.getInstance("JPY");

	private static final Currency KWD = Currency.getInstance("KWD");

	private static final Currency USD = Currency.getInstance("USD");

	private static final Currency XDR = Currency.getInstance("XDR");

	private static final Map<Currency, Rate> RATES = CnbFixture.rates("daily-2026-10-30.txt");

	private static final Function<Currency, Optional<Rate>> RATE_LOOKUP = currency -> Optional
		.ofNullable(RATES.get(currency));

	private static final Function<Currency, Optional<Rate>> NO_LOOKUP_ALLOWED = currency -> {
		throw new AssertionError("No rate should be needed for " + currency);
	};

	private final CurrencyConverter converter = new CurrencyConverter();

	@Test
	void assignmentWorkflowInUsd() {
		Conversion conversion = convert(USD, Map.of(EUR, "5.00"), Map.of(USD, "7.00"));

		assertThat(conversion.total().toPlainString()).isEqualTo("12.58");
		assertThat(convertedValues(conversion)).containsExactly("5.58", "7.00");
	}

	@Test
	void assignmentWorkflowInEur() {
		Conversion conversion = convert(EUR, Map.of(EUR, "5.00"), Map.of(USD, "7.00"));

		assertThat(conversion.total().toPlainString()).isEqualTo("11.27");
		assertThat(convertedValues(conversion)).containsExactly("5.00", "6.27");
	}

	@Test
	void linesKeepTheOriginalAmountsInHoldingOrder() {
		Conversion conversion = convert(USD, Map.of(EUR, "5.00"), Map.of(USD, "7.00"));

		assertThat(conversion.lines()).extracting(ConvertedLine::currency).containsExactly(EUR, USD);
		assertThat(conversion.lines()).extracting(line -> line.amount().toPlainString()).containsExactly("5.00", "7.00");
	}

	@ParameterizedTest
	@CsvSource({ "7.50,163.60", "2.50,54.54" })
	void roundsTiesHalfEven(String usd, String expectedCzk) {
		// 7.50 USD is exactly 163.605 CZK and 2.50 USD is exactly 54.535 CZK.
		Conversion conversion = convert(CZK, Map.of(USD, usd));

		assertThat(conversion.total().toPlainString()).isEqualTo(expectedCzk);
	}

	@Test
	void totalIsRoundedFromTheExactSumNotFromTheRoundedLines() {
		// 21.814 + 15.323 = 37.137 CZK, while the rounded lines would add up to 37.13.
		Conversion conversion = convert(CZK, Map.of(USD, "1.00"), Map.of(AUD, "1.00"));

		assertThat(conversion.total().toPlainString()).isEqualTo("37.14");
		assertThat(convertedValues(conversion)).containsExactly("21.81", "15.32");
	}

	@Test
	void doesNotRoundTheIntermediateCzkValue() {
		// Rounding to 21.81 CZK on the way would give 18114.62 IDR.
		Conversion conversion = convert(IDR, Map.of(USD, "1.00"));

		assertThat(conversion.total().toPlainString()).isEqualTo("18117.94");
	}

	@Test
	void sameCurrencyStaysExact() {
		assertThat(convert(USD, Map.of(USD, "7.00")).total().toPlainString()).isEqualTo("7.00");
		assertThat(convert(USD, Map.of(USD, "999999999999999.99")).total().toPlainString())
			.isEqualTo("999999999999999.99");
		assertThat(convert(JPY, Map.of(JPY, "1000")).total().toPlainString()).isEqualTo("1000");
	}

	@Test
	void largestAmountLosesNoPrecision() {
		assertThat(convert(CZK, Map.of(USD, "999999999999999.99")).total().toPlainString())
			.isEqualTo("21813999999999999.78");
		assertThat(convert(CZK, Map.of(JPY, "999999999999999")).total().toPlainString())
			.isEqualTo("138749999999999.86");
	}

	@Test
	void balanceBeyond34DigitsStaysExact() {
		String balance = "1234567890123456789012345678901234567890";

		assertThat(convert(JPY, Map.of(JPY, balance)).total().toPlainString()).isEqualTo(balance);
	}

	@Test
	void usesTheQuotedUnitsForJpyPer100AndIdrPer1000() {
		assertThat(convert(CZK, Map.of(JPY, "1000")).total().toPlainString()).isEqualTo("138.75");
		assertThat(convert(USD, Map.of(JPY, "1000")).total().toPlainString()).isEqualTo("6.36");
		assertThat(convert(CZK, Map.of(IDR, "1000.00")).total().toPlainString()).isEqualTo("1.20");
		assertThat(convert(CZK, Map.of(JPY, "1")).total().toPlainString()).isEqualTo("0.14");
	}

	@Test
	void usesTheQuotedUnitsWhenConvertingFromCzk() {
		assertThat(convert(JPY, Map.of(CZK, "138.75")).total().toPlainString()).isEqualTo("1000");
		assertThat(convert(USD, Map.of(CZK, "121.80")).total().toPlainString()).isEqualTo("5.58");
	}

	@Test
	void czkIsOneToOne() {
		assertThat(convert(CZK, Map.of(CZK, "12.34")).total().toPlainString()).isEqualTo("12.34");
	}

	@Test
	void czkNeverNeedsARate() {
		Map<Currency, BigDecimal> holdings = Map.of(CZK, new BigDecimal("12.34"));

		Conversion conversion = this.converter.convert(holdings, CZK, NO_LOOKUP_ALLOWED);

		assertThat(conversion.total().toPlainString()).isEqualTo("12.34");
	}

	@Test
	void jpyTargetHasNoDecimals() {
		Conversion conversion = convert(JPY, Map.of(USD, "1.00"));

		assertThat(conversion.total().toPlainString()).isEqualTo("157");
		assertThat(convertedValues(conversion)).containsExactly("157");
	}

	@Test
	void emptyHoldingsTotalZeroInTheScaleOfTheTarget() {
		assertThat(convert(USD).total().toPlainString()).isEqualTo("0.00");
		assertThat(convert(JPY).total().toPlainString()).isEqualTo("0");
		assertThat(convert(USD).lines()).isEmpty();
	}

	@Test
	void emptyHoldingsNeedNoRates() {
		Conversion conversion = this.converter.convert(Map.of(), USD, NO_LOOKUP_ALLOWED);

		assertThat(conversion.total().toPlainString()).isEqualTo("0.00");
		assertThat(conversion.lines()).isEmpty();
	}

	@Test
	void emptyHoldingsStillRejectATargetOutsideTheList() {
		assertThatThrownBy(() -> this.converter.convert(Map.of(), KWD, NO_LOOKUP_ALLOWED))
			.isInstanceOf(UnsupportedCurrencyException.class);
	}

	@Test
	void heldCurrencyCnbStoppedQuotingIsRateNotAvailable() {
		Map<Currency, BigDecimal> holdings = Map.of(JPY, new BigDecimal("1000"));

		assertThatThrownBy(() -> this.converter.convert(holdings, USD, ratesWithout(JPY)))
			.isInstanceOf(RateNotAvailableException.class);
	}

	@Test
	void targetCnbStoppedQuotingIsRateNotAvailable() {
		Map<Currency, BigDecimal> holdings = Map.of(USD, new BigDecimal("1.00"));

		assertThatThrownBy(() -> this.converter.convert(holdings, JPY, ratesWithout(JPY)))
			.isInstanceOf(RateNotAvailableException.class);
	}

	@Test
	void targetOutsideTheListIsUnsupported() {
		assertThatThrownBy(() -> convert(KWD, Map.of(USD, "1.00"))).isInstanceOf(UnsupportedCurrencyException.class);
	}

	@Test
	void xdrTargetIsUnsupportedEvenThoughCnbQuotesIt() {
		assertThat(RATES).containsKey(XDR);
		assertThatThrownBy(() -> convert(XDR, Map.of(USD, "1.00"))).isInstanceOf(UnsupportedCurrencyException.class);
	}

	@SafeVarargs
	private Conversion convert(Currency target, Map<Currency, String>... holdings) {
		Map<Currency, BigDecimal> ordered = new LinkedHashMap<>();
		for (Map<Currency, String> holding : holdings) {
			holding.forEach((currency, amount) -> ordered.put(currency, new BigDecimal(amount)));
		}
		return this.converter.convert(ordered, target, RATE_LOOKUP);
	}

	private static Function<Currency, Optional<Rate>> ratesWithout(Currency missing) {
		return currency -> currency.equals(missing) ? Optional.empty() : RATE_LOOKUP.apply(currency);
	}

	private static List<String> convertedValues(Conversion conversion) {
		return conversion.lines().stream().map(line -> line.converted().toPlainString()).toList();
	}

}
