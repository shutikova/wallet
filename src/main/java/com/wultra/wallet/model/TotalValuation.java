package com.wultra.wallet.model;

import java.util.Currency;

/**
 * {@code rates} is {@code null} when the total converts nothing and so uses no CNB rates.
 */
public record TotalValuation(Currency currency, Conversion conversion, RatesView rates) {
}
