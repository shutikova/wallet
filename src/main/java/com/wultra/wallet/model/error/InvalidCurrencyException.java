package com.wultra.wallet.model.error;

public class InvalidCurrencyException extends RuntimeException {

	public InvalidCurrencyException() {
		super("Not a valid ISO 4217 currency code");
	}

}
