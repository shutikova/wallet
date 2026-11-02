package com.wultra.wallet.model.error;

import java.util.Currency;

public class UnsupportedCurrencyException extends RuntimeException {

	public UnsupportedCurrencyException(Currency currency) {
		super("Currency " + currency.getCurrencyCode() + " is not supported by the wallet");
	}

}
