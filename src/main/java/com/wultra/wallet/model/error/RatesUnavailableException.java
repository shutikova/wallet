package com.wultra.wallet.model.error;

public class RatesUnavailableException extends RuntimeException {

	public RatesUnavailableException() {
		super("No usable CNB exchange rates are available");
	}

}
