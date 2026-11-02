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

	// All limits are checked on the text, before any BigDecimal is created (see README decision 4).
	public static BigDecimal parsePositive(String raw, Currency currency) {
		int allowedDecimals = Currencies.requireSupported(currency).getDefaultFractionDigits();
		if (raw == null || !PLAIN_DECIMAL.matcher(raw).matches()) {
			throw new InvalidAmountException("Amount must be a plain decimal number such as 10 or 10.50");
		}
		int dot = raw.indexOf('.');
		String integerPart = (dot < 0) ? raw : raw.substring(0, dot);
		int decimals = (dot < 0) ? 0 : raw.length() - dot - 1;
		if (significantDigits(integerPart) > MAX_INTEGER_DIGITS) {
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

	public static String format(BigDecimal amount) {
		return amount.toPlainString();
	}

	private static int significantDigits(String integerPart) {
		int firstNonZero = 0;
		while (firstNonZero < integerPart.length() && integerPart.charAt(firstNonZero) == '0') {
			firstNonZero++;
		}
		return integerPart.length() - firstNonZero;
	}

}
