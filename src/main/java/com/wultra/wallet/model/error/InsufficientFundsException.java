package com.wultra.wallet.model.error;

import java.util.Currency;

public class InsufficientFundsException extends RuntimeException {

	public InsufficientFundsException(Currency currency) {
		super("The " + currency.getCurrencyCode() + " balance is too low for this withdrawal");
	}

}
