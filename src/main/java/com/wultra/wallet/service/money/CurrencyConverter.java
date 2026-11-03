package com.wultra.wallet.service.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import com.wultra.wallet.model.Conversion;
import com.wultra.wallet.model.ConvertedLine;
import com.wultra.wallet.model.Rate;
import com.wultra.wallet.model.error.RateNotAvailableException;
import org.springframework.stereotype.Service;

@Service
public final class CurrencyConverter {

	// With nothing to convert, a total needs no rates and works while CNB is down (see README decision 3).
	public boolean needsRates(Map<Currency, BigDecimal> holdings, Currency target) {
		return !holdings.keySet().stream().allMatch(target::equals);
	}

	public Conversion convert(Map<Currency, BigDecimal> holdings, Currency target,
			Function<Currency, Optional<Rate>> rates) {
		int decimals = Currencies.requireSupported(target).getDefaultFractionDigits();
		if (!needsRates(holdings, target)) {
			BigDecimal total = holdings.getOrDefault(target, BigDecimal.ZERO).setScale(decimals);
			return new Conversion(total, holdings.keySet().stream()
				.map(currency -> new ConvertedLine(currency, total, total))
				.toList());
		}
		Rate targetRate = rateOf(target, rates);

		BigDecimal totalCzk = BigDecimal.ZERO;
		List<ConvertedLine> lines = new ArrayList<>();
		for (Map.Entry<Currency, BigDecimal> holding : holdings.entrySet()) {
			Currency currency = holding.getKey();
			Rate rate = rateOf(currency, rates);
			BigDecimal czk = toCzk(holding.getValue(), rate);
			totalCzk = totalCzk.add(czk);
			// Rounded for display only and never added up, so the total is rounded once (see README decision 4).
			lines.add(new ConvertedLine(currency, holding.getValue(), fromCzk(czk, targetRate, decimals)));
		}
		return new Conversion(fromCzk(totalCzk, targetRate, decimals), lines);
	}

	// CZK is always 1 and is never looked up, so it works without CNB (see README decision 7).
	private static Rate rateOf(Currency currency, Function<Currency, Optional<Rate>> rates) {
		if (Currencies.CZK.equals(currency)) {
			return Rate.CZK;
		}
		return rates.apply(currency).orElseThrow(() -> new RateNotAvailableException(currency));
	}

	// Rate only allows powers of 10 as units, so this division is always exact (see README decision 2).
	private static BigDecimal toCzk(BigDecimal amount, Rate rate) {
		return amount.multiply(rate.czkValue()).divide(BigDecimal.valueOf(rate.units()));
	}

	private static BigDecimal fromCzk(BigDecimal czk, Rate rate, int scale) {
		return czk.multiply(BigDecimal.valueOf(rate.units())).divide(rate.czkValue(), scale, RoundingMode.HALF_EVEN);
	}

}
