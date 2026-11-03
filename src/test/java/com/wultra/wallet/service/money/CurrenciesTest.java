package com.wultra.wallet.service.money;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Currency;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import com.wultra.wallet.model.error.InvalidCurrencyException;
import com.wultra.wallet.model.error.UnsupportedCurrencyException;
import com.wultra.wallet.service.cnb.CnbFiles;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CurrenciesTest {

	@ParameterizedTest
	@ValueSource(strings = { "usd", "USD", "uSd" })
	void acceptsAnyLetterCase(String code) {
		assertThat(Currencies.parse(code)).isEqualTo(Currency.getInstance("USD"));
	}

	@Test
	void upperCasesIndependentlyOfTheDefaultLocale() {
		Locale original = Locale.getDefault();
		Locale.setDefault(Locale.forLanguageTag("tr"));
		try {
			assertThat(Currencies.parse("idr")).isEqualTo(Currency.getInstance("IDR"));
		}
		finally {
			Locale.setDefault(original);
		}
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "US", "USDD", "U1D", " usd", "usd ", "ABC", "uſd", "ıdr" })
	void rejectsAnythingThatIsNotAnIsoCode(String code) {
		assertThatThrownBy(() -> Currencies.parse(code)).isInstanceOf(InvalidCurrencyException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = { "XDR", "XAU", "XXX", "AED", "KWD", "RUB", "BGN", "DEM" })
	void validCodesOutsideTheListAreNotSupported(String code) {
		Currency currency = Currencies.parse(code);

		assertThatThrownBy(() -> Currencies.requireSupported(currency)).isInstanceOf(UnsupportedCurrencyException.class);
	}

	// Reads the file directly, because the parser already filters by this list.
	@Test
	void listIsCnbsDailyTableWithoutXdrPlusCzk() {
		Set<Currency> expected = CnbFiles.read("daily-2026-10-30.txt").lines()
			.skip(2)
			.map(row -> row.split("\\|")[3])
			.filter(code -> !code.equals("XDR"))
			.map(Currency::getInstance)
			.collect(Collectors.toCollection(HashSet::new));
		expected.add(Currency.getInstance("CZK"));

		assertThat(Currencies.SUPPORTED).isEqualTo(expected);
	}

	@ParameterizedTest
	@CsvSource({ "USD,true", "CZK,true", "XDR,false", "ZZZ,false", "usd,false" })
	void checksACodeAsCnbWroteIt(String code, boolean supported) {
		assertThat(Currencies.isSupported(code)).isEqualTo(supported);
	}

	// Decimal places from the ISO 4217 list published on 2026-09-17, for every supported currency.
	// Fails if a JDK update disagrees.
	@ParameterizedTest
	@CsvSource({ "CZK,2", "AUD,2", "BRL,2", "CAD,2", "CNY,2", "DKK,2", "EUR,2", "HKD,2", "HUF,2", "ISK,0", "INR,2",
			"IDR,2", "ILS,2", "JPY,0", "MYR,2", "MXN,2", "NZD,2", "NOK,2", "PHP,2", "PLN,2", "RON,2", "SGD,2", "ZAR,2",
			"KRW,0", "SEK,2", "CHF,2", "THB,2", "TRY,2", "GBP,2", "USD,2" })
	void decimalPlacesMatchIso4217(String code, int decimals) {
		assertThat(Currencies.requireSupported(Currencies.parse(code)).getDefaultFractionDigits()).isEqualTo(decimals);
	}

}
