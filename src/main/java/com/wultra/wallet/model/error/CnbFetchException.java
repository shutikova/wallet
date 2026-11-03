package com.wultra.wallet.model.error;

public class CnbFetchException extends RuntimeException {

	public CnbFetchException(String message) {
		super(message);
	}

	public CnbFetchException(String message, Throwable cause) {
		super(message, cause);
	}

}
