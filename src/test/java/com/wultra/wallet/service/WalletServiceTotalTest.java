package com.wultra.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.Currency;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import com.wultra.wallet.model.ConvertedLine;
import com.wultra.wallet.model.Rate;
import com.wultra.wallet.model.RateTable;
import com.wultra.wallet.model.RatesView;
import com.wultra.wallet.model.TotalValuation;
import com.wultra.wallet.model.error.InvalidCurrencyException;
import com.wultra.wallet.model.error.RateNotAvailableException;
import com.wultra.wallet.model.error.RatesUnavailableException;
import com.wultra.wallet.model.error.UnsupportedCurrencyException;
import com.wultra.wallet.repository.InMemoryWalletStorage;
import com.wultra.wallet.service.cnb.CnbFiles;
import com.wultra.wallet.service.cnb.ExchangeRateProvider;
import com.wultra.wallet.service.money.CurrencyConverter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WalletServiceTotalTest {

	private static final RateTable TABLE = CnbFiles.table("daily-2026-10-30.txt");

	private static final RatesView RATES = new RatesView(TABLE, LocalDate.of(2026, 11, 1), false, false);

	private static final ExchangeRateProvider CNB_NOT_ALLOWED = () -> {
		throw new AssertionError("CNB should not be needed");
	};

	private final InMemoryWalletStorage storage = new InMemoryWalletStorage();

	@Test
	void assignmentWorkflowWithRealRates() {
		WalletService service = serviceUsing(() -> RATES);
		service.deposit("10", "USD");
		service.deposit("5", "EUR");
		service.withdraw("3", "USD");

		TotalValuation inUsd = service.total("USD");
		TotalValuation inEur = service.total("EUR");

		assertThat(inUsd.currency()).isEqualTo(Currency.getInstance("USD"));
		assertThat(inUsd.conversion().total().toPlainString()).isEqualTo("12.58");
		assertThat(inUsd.conversion().lines()).extracting(line -> line.converted().toPlainString())
			.containsExactly("5.58", "7.00");
		assertThat(inUsd.rates()).isEqualTo(RATES);
		assertThat(inEur.conversion().total().toPlainString()).isEqualTo("11.27");
	}

	@Test
	void breakdownIsSortedByCurrencyCode() {
		WalletService service = serviceUsing(() -> RATES);
		service.deposit("7", "USD");
		service.deposit("5", "EUR");
		service.deposit("100", "CZK");

		assertThat(service.total("USD").conversion().lines()).extracting(ConvertedLine::currency)
			.extracting(Currency::getCurrencyCode)
			.containsExactly("CZK", "EUR", "USD");
	}

	@Test
	void targetCodeIsAcceptedInAnyLetterCase() {
		assertThat(serviceUsing(() -> RATES).total("usd").currency()).isEqualTo(Currency.getInstance("USD"));
	}

	@Test
	void anEmptyWalletTotalsZeroWithoutCnb() {
		TotalValuation total = serviceUsing(CNB_NOT_ALLOWED).total("USD");

		assertThat(total.conversion().total().toPlainString()).isEqualTo("0.00");
		assertThat(total.conversion().lines()).isEmpty();
		assertThat(total.rates()).isNull();
	}

	@ParameterizedTest
	@ValueSource(strings = { "CZK", "USD", "JPY" })
	void aWalletHoldingOnlyTheTargetCurrencyNeedsNoCnb(String code) {
		WalletService service = serviceUsing(CNB_NOT_ALLOWED);
		service.deposit("100", code);

		TotalValuation total = service.total(code);

		assertThat(total.conversion().total()).isEqualByComparingTo("100");
		assertThat(total.rates()).isNull();
	}

	@Test
	void aCzkWalletInAnotherCurrencyUsesTheRate() {
		WalletService service = serviceUsing(() -> RATES);
		service.deposit("121.80", "CZK");

		TotalValuation total = service.total("USD");

		assertThat(total.conversion().total().toPlainString()).isEqualTo("5.58");
		assertThat(total.rates()).isEqualTo(RATES);
	}

	@Test
	void missingRatesAreUnavailableWhenTheyAreNeeded() {
		WalletService service = serviceUsing(() -> {
			throw new RatesUnavailableException();
		});
		service.deposit("5", "EUR");

		assertThatThrownBy(() -> service.total("USD")).isInstanceOf(RatesUnavailableException.class);
	}

	@Test
	void aSupportedCurrencyMissingFromTheTableIsRateNotAvailable() {
		Map<Currency, Rate> withoutEur = new HashMap<>(TABLE.rates());
		withoutEur.remove(Currency.getInstance("EUR"));
		RatesView rates = new RatesView(new RateTable(TABLE.date(), withoutEur), TABLE.date(), false, false);
		WalletService service = serviceUsing(() -> rates);
		service.deposit("5", "EUR");

		assertThatThrownBy(() -> service.total("USD")).isInstanceOf(RateNotAvailableException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = { "AED", "XDR", "DEM" })
	void aTargetOutsideTheListIsUnsupportedWithoutCnb(String code) {
		assertThatThrownBy(() -> serviceUsing(CNB_NOT_ALLOWED).total(code))
			.isInstanceOf(UnsupportedCurrencyException.class);
	}

	@Test
	void anInvalidTargetIsRejectedWithoutCnb() {
		assertThatThrownBy(() -> serviceUsing(CNB_NOT_ALLOWED).total("ABC")).isInstanceOf(InvalidCurrencyException.class);
	}

	@Test
	void cnbIsNeverCalledWhileTheWalletLockIsHeld() {
		WalletService service = serviceUsing(() -> {
			// Another thread would wait here, and time out, if this thread held the wallet lock.
			CompletableFuture.supplyAsync(this.storage::snapshot).orTimeout(5, TimeUnit.SECONDS).join();
			return RATES;
		});
		service.deposit("5", "EUR");

		assertThat(service.total("USD").rates()).isEqualTo(RATES);
	}

	private WalletService serviceUsing(ExchangeRateProvider provider) {
		return new WalletService(this.storage, new CurrencyConverter(), provider);
	}

}
