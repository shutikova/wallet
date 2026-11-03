package com.wultra.wallet.model;

import java.time.LocalDate;

public record RatesView(RateTable table, LocalDate valuationDate, boolean provisional, boolean stale) {
}
