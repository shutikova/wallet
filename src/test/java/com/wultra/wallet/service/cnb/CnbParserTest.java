package com.wultra.wallet.service.cnb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Currency;

import com.wultra.wallet.model.Rate;
import com.wultra.wallet.model.RateTable;
import com.wultra.wallet.model.error.CnbFetchException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class CnbParserTest {

	private static final String REAL_FILE = CnbFiles.read("daily-2026-10-30.txt");

	private final CnbParser parser = new CnbParser();

	@Test
	void readsTheDateFromTheFirstLine() {
		assertThat(this.parser.parse(REAL_FILE).date()).isEqualTo(LocalDate.of(2026, 10, 30));
	}

	@Test
	void readsAMonthWhoseAbbreviationVariesBetweenLocales() {
		assertThat(CnbFiles.table("daily-2026-09-30.txt").date()).isEqualTo(LocalDate.of(2026, 9, 30));
	}

	@Test
	void readsEverySupportedCurrency() {
		RateTable table = this.parser.parse(REAL_FILE);

		assertThat(table.rates()).hasSize(29)
			.doesNotContainKey(Currency.getInstance("XDR"))
			.doesNotContainKey(Currency.getInstance("CZK"));
	}

	@ParameterizedTest
	@CsvSource({ "USD,21.814,1", "EUR,24.360,1", "JPY,13.875,100", "HUF,6.649,100", "IDR,1.204,1000" })
	void readsTheRateWithItsAmountColumn(String code, String rate, int units) {
		Rate parsed = this.parser.parse(REAL_FILE).rates().get(Currency.getInstance(code));

		assertThat(parsed.czkValue()).isEqualByComparingTo(rate);
		assertThat(parsed.units()).isEqualTo(units);
	}

	@Test
	void acceptsWindowsLineEndings() {
		RateTable table = this.parser.parse(REAL_FILE.replace("\n", "\r\n"));

		assertThat(table.date()).isEqualTo(LocalDate.of(2026, 10, 30));
		assertThat(table.rates()).hasSize(29);
	}

	// Each case stands for a currency CNB could add, which must not cost the wallet the whole table.
	@ParameterizedTest
	@CsvSource(delimiterString = " -> ", value = { "|XDR| -> |ZZZ|", "|1|XDR| -> |3|XDR|", "|1|XDR| -> |x|XDR|",
			"|XDR|29.505 -> |XDR|0.000" })
	void skipsARowOutsideTheSupportedListWithoutReadingItsValues(String original, String changed) {
		String body = REAL_FILE.replace(original, changed);

		assertThat(body).isNotEqualTo(REAL_FILE);
		assertThat(this.parser.parse(body).rates()).hasSize(29);
	}

	@ParameterizedTest
	@NullAndEmptySource
	void rejectsAMissingBody(String body) {
		assertThatThrownBy(() -> this.parser.parse(body)).isInstanceOf(CnbFetchException.class);
	}

	@Test
	void rejectsATableWithoutRows() {
		String headerOnly = REAL_FILE.substring(0, REAL_FILE.indexOf("Australia"));

		assertThatThrownBy(() -> this.parser.parse(headerOnly)).isInstanceOf(CnbFetchException.class);
	}

	@Test
	void rejectsATableWithNoSupportedCurrency() {
		String headerOnly = REAL_FILE.substring(0, REAL_FILE.indexOf("Australia"));

		assertThatThrownBy(() -> this.parser.parse(headerOnly + "IMF|SDR|1|XDR|29.505")).isInstanceOf(CnbFetchException.class);
	}

	// Each case damages one part of the real file.
	@ParameterizedTest
	@CsvSource(delimiterString = " -> ", value = { "30 Oct 2026 #209 -> 30.10.2026 #209", "30 Oct 2026 #209 -> 32 Oct 2026 #209",
			"30 Oct 2026 #209 -> 31 Feb 2026 #209", "30 Oct 2026 #209 -> 31 Apr 2026 #209", "30 Oct 2026 #209 -> 30 Oct 2026",
			"Amount|Code -> Code|Amount", "USA|dollar|1|USD|21.814 -> USA|dollar|1|USD",
			"USA|dollar|1|USD|21.814 -> USA|dollar|1|USD|21.814|x", "IMF|SDR|1|XDR|29.505 -> IMF|SDR|1|XDR",
			"|USD|21.814 -> |USD|21,814", "|USD|21.814 -> |USD|0.000", "|1|USD| -> |x|USD|", "|1|USD| -> |3|USD|" })
	void rejectsADamagedTable(String original, String damaged) {
		String body = REAL_FILE.replace(original, damaged);

		assertThat(body).isNotEqualTo(REAL_FILE);
		assertThatThrownBy(() -> this.parser.parse(body)).isInstanceOf(CnbFetchException.class);
	}

}
