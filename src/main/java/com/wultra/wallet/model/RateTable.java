package com.wultra.wallet.model;

import java.time.LocalDate;
import java.util.Currency;
import java.util.Map;

public record RateTable(LocalDate date, Map<Currency, Rate> rates) {

	public RateTable {
		rates = Map.copyOf(rates);
	}

}
