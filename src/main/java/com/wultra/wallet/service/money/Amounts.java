package com.wultra.wallet.service.money;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.regex.Pattern;

import com.wultra.wallet.model.error.InvalidAmountException;

public final class Amounts {

	private static final int MAX_INTEGER_DIGITS = 15;

	private static final Pattern PLAIN_DECIMAL = Pattern.compile("[0-9]++(?:\\.[0-9]++)?");

	private Amounts() {
	}

	// All limits are checked on the text as typed, before any BigDecimal is created (see README decision 4).
	public static BigDecimal parsePositive(String raw, Currency currency) {
		int allowedDecimals = Currencies.requireSupported(currency).getDefaultFractionDigits();
		if (!isPlainDecimal(raw)) {
			throw new InvalidAmountException("Amount must be a plain decimal number such as 10 or 10.50");
		}
		int dot = raw.indexOf('.');
		int integerDigits = (dot < 0) ? raw.length() : dot;
		int decimals = (dot < 0) ? 0 : raw.length() - dot - 1;
		if (integerDigits > MAX_INTEGER_DIGITS) {
			throw new InvalidAmountException("Amount must have at most " + MAX_INTEGER_DIGITS + " digits before the decimal point");
		}
		if (decimals > allowedDecimals) {
			throw new InvalidAmountException(currency.getCurrencyCode() + " allows at most " + allowedDecimals + " decimal places");
		}
		BigDecimal amount = new BigDecimal(raw);
		if (amount.signum() <= 0) {
			throw new InvalidAmountException("Amount must be greater than 0");
		}
		return amount.setScale(allowedDecimals);
	}

	// Also the format of CNB's rates (see README decision 2).
	public static boolean isPlainDecimal(String text) {
		return text != null && PLAIN_DECIMAL.matcher(text).matches();
	}

}
