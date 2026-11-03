package com.wultra.wallet.service.cnb;

import com.wultra.wallet.model.RatesView;

public interface ExchangeRateProvider {

	/**
	 * Returns the rates to use right now.
	 * @throws com.wultra.wallet.model.error.RatesUnavailableException if no usable table exists
	 */
	RatesView currentRates();

}
