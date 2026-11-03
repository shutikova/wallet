package com.wultra.wallet.service.cnb;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.wultra.wallet.model.Rate;
import com.wultra.wallet.model.RateTable;
import com.wultra.wallet.model.error.CnbFetchException;
import com.wultra.wallet.service.money.Currencies;
import org.springframework.stereotype.Component;

// One bad row of a supported currency rejects the whole table, so a damaged response is treated like CNB being
// down (see README decision 3). Values of other currencies are not read, so a new one cannot break the table
// (see README decision 7).
@Component
public class CnbParser {

	private static final Pattern FIRST_LINE = Pattern.compile("(\\d{2} [A-Z][a-z]{2} \\d{4}) #\\d+");

	// Strict, because the default resolver turns 31 Feb into 28 Feb. Strict needs uuuu, since yyyy also needs an era.
	private static final DateTimeFormatter TABLE_DATE = DateTimeFormatter.ofPattern("dd MMM uuuu", Locale.ENGLISH)
		.withResolverStyle(ResolverStyle.STRICT);

	private static final String COLUMNS = "Country|Currency|Amount|Code|Rate";

	private static final int COLUMN_COUNT = 5;

	private static final int AMOUNT_COLUMN = 2;

	private static final int CODE_COLUMN = 3;

	private static final int RATE_COLUMN = 4;

	public RateTable parse(String body) {
		List<String> lines = (body != null) ? body.lines().toList() : List.of();
		if (lines.size() < 2 || !COLUMNS.equals(lines.get(1))) {
			throw new CnbFetchException("CNB response is not a rate table");
		}
		try {
			Map<Currency, Rate> rates = new HashMap<>();
			for (String row : lines.subList(2, lines.size())) {
				String[] columns = row.split("\\|", -1);
				if (columns.length != COLUMN_COUNT) {
					throw new CnbFetchException("CNB rate table has a malformed row");
				}
				if (Currencies.isSupported(columns[CODE_COLUMN])) {
					rates.put(Currency.getInstance(columns[CODE_COLUMN]),
							new Rate(new BigDecimal(columns[RATE_COLUMN]), Integer.parseInt(columns[AMOUNT_COLUMN])));
				}
			}
			if (rates.isEmpty()) {
				throw new CnbFetchException("CNB rate table has no supported currency");
			}
			return new RateTable(parseDate(lines.get(0)), rates);
		}
		catch (IllegalArgumentException | DateTimeParseException ex) {
			throw new CnbFetchException("CNB rate table has an invalid value", ex);
		}
	}

	// The date of the table, which can be older than the date that was requested.
	private static LocalDate parseDate(String firstLine) {
		Matcher matcher = FIRST_LINE.matcher(firstLine);
		if (!matcher.matches()) {
			throw new CnbFetchException("CNB rate table has no valid date line");
		}
		return LocalDate.parse(matcher.group(1), TABLE_DATE);
	}

}
