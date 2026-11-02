package com.wultra.wallet.model.error;

import java.util.Currency;

public class RateNotAvailableException extends RuntimeException {

	public RateNotAvailableException(Currency currency) {
		super("CNB has no rate for " + currency.getCurrencyCode());
	}

}
