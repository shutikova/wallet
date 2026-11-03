package com.wultra.wallet.service;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.wultra.wallet.model.Balance;
import com.wultra.wallet.model.Rate;
import com.wultra.wallet.model.RatesView;
import com.wultra.wallet.model.TotalValuation;
import com.wultra.wallet.model.error.InsufficientFundsException;
import com.wultra.wallet.repository.InMemoryWalletStorage;
import com.wultra.wallet.service.cnb.ExchangeRateProvider;
import com.wultra.wallet.service.money.Amounts;
import com.wultra.wallet.service.money.Currencies;
import com.wultra.wallet.service.money.CurrencyConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class WalletService {

	private final InMemoryWalletStorage storage;

	private final CurrencyConverter converter;

	private final ExchangeRateProvider rateProvider;

	public Balance deposit(String amount, String currencyCode) {
		Currency currency = Currencies.parse(currencyCode);
		BigDecimal deposited = Amounts.parsePositive(amount, currency);
		BigDecimal balance = this.storage.add(currency, deposited);
		log.info("Deposited {} {}, new balance {}", deposited, currency, balance);
		return new Balance(currency, balance);
	}

	public Balance withdraw(String amount, String currencyCode) {
		Currency currency = Currencies.parse(currencyCode);
		BigDecimal withdrawn = Amounts.parsePositive(amount, currency);
		BigDecimal balance = this.storage.subtractIfAvailable(currency, withdrawn)
			.orElseThrow(() -> new InsufficientFundsException(currency));
		log.info("Withdrew {} {}, new balance {}", withdrawn, currency, balance);
		return new Balance(currency, balance);
	}

	public List<Balance> balances() {
		return this.storage.snapshot().entrySet().stream()
			.map(entry -> new Balance(entry.getKey(), entry.getValue()))
			.toList();
	}

	public TotalValuation total(String currencyCode) {
		Currency target = Currencies.requireSupported(Currencies.parse(currencyCode));
		// The copy is complete before CNB is called, so the wallet lock is not held (see README decision 5).
		Map<Currency, BigDecimal> holdings = this.storage.snapshot();
		RatesView rates = this.converter.needsRates(holdings, target) ? this.rateProvider.currentRates() : null;
		Map<Currency, Rate> table = (rates != null) ? rates.table().rates() : Map.of();
		return new TotalValuation(target,
				this.converter.convert(holdings, target, currency -> Optional.ofNullable(table.get(currency))), rates);
	}

}
