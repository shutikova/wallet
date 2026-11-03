package com.wultra.wallet.service.money;

import java.util.Currency;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.wultra.wallet.model.error.InvalidCurrencyException;
import com.wultra.wallet.model.error.UnsupportedCurrencyException;

public final class Currencies {

	public static final Currency CZK = Currency.getInstance("CZK");

	// CZK and CNB's daily table of 30 Oct 2026, without XDR, which has no ISO 4217 decimal places.
	// A fixed list means deposits and withdrawals never need CNB (see README decision 7).
	static final Set<Currency> SUPPORTED = Stream
		.of("AUD", "BRL", "CAD", "CHF", "CNY", "CZK", "DKK", "EUR", "GBP", "HKD", "HUF", "IDR", "ILS", "INR", "ISK",
				"JPY", "KRW", "MXN", "MYR", "NOK", "NZD", "PHP", "PLN", "RON", "SEK", "SGD", "THB", "TRY", "USD", "ZAR")
		.map(Currency::getInstance)
		.collect(Collectors.toUnmodifiableSet());

	// Checked before upper-casing, which would turn "ſ" into "S" and "ı" into "I" and accept them as valid codes.
	private static final Pattern CODE = Pattern.compile("[A-Za-z]{3}");

	private Currencies() {
	}

	public static Currency parse(String code) {
		if (code == null || !CODE.matcher(code).matches()) {
			throw new InvalidCurrencyException();
		}
		try {
			return Currency.getInstance(code.toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException ex) {
			throw new InvalidCurrencyException();
		}
	}

	public static Currency requireSupported(Currency currency) {
		if (!SUPPORTED.contains(currency)) {
			throw new UnsupportedCurrencyException(currency);
		}
		return currency;
	}

	// Takes a code as CNB wrote it, which does not have to be a valid ISO 4217 code.
	public static boolean isSupported(String code) {
		return SUPPORTED.stream().anyMatch(currency -> currency.getCurrencyCode().equals(code));
	}

}
