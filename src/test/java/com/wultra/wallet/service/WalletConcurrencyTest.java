package com.wultra.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.wultra.wallet.model.Balance;
import com.wultra.wallet.model.error.InsufficientFundsException;
import com.wultra.wallet.repository.InMemoryWalletStorage;
import com.wultra.wallet.service.money.CurrencyConverter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.RepeatedTest;
import org.slf4j.LoggerFactory;

// Repeated because a race may not show on every run. See README decision 5.
class WalletConcurrencyTest {

	private static final Currency USD = Currency.getInstance("USD");

	private final WalletService service = new WalletService(new InMemoryWalletStorage(), new CurrencyConverter(), () -> {
		throw new AssertionError("CNB should not be needed");
	});

	// Thousands of money movements would flood the test output.
	@BeforeAll
	static void silenceWalletLogging() {
		((Logger) LoggerFactory.getLogger(WalletService.class)).setLevel(Level.OFF);
	}

	@AfterAll
	static void restoreWalletLogging() {
		((Logger) LoggerFactory.getLogger(WalletService.class)).setLevel(null);
	}

	@RepeatedTest(20)
	void hundredWithdrawalsOfOneUsdFromFiftySucceedExactlyFiftyTimes() throws Exception {
		this.service.deposit("50", "USD");
		AtomicInteger succeeded = new AtomicInteger();
		AtomicInteger rejected = new AtomicInteger();

		runTogether(Collections.nCopies(100, () -> {
			try {
				this.service.withdraw("1", "USD");
				succeeded.incrementAndGet();
			}
			catch (InsufficientFundsException ex) {
				rejected.incrementAndGet();
			}
		}));

		assertThat(succeeded).hasValue(50);
		assertThat(rejected).hasValue(50);
		assertThat(this.service.balances()).isEmpty();
	}

	@RepeatedTest(20)
	void hundredDepositsAreAllCounted() throws Exception {
		runTogether(Collections.nCopies(100, () -> this.service.deposit("1", "USD")));

		assertThat(this.service.balances()).containsExactly(new Balance(USD, new BigDecimal("100.00")));
	}

	@RepeatedTest(20)
	void depositsAndWithdrawalsTogetherLoseNothing() throws Exception {
		this.service.deposit("50", "USD");
		List<Runnable> tasks = new ArrayList<>();
		tasks.addAll(Collections.nCopies(50, () -> this.service.deposit("1", "USD")));
		tasks.addAll(Collections.nCopies(50, () -> this.service.withdraw("1", "USD")));

		runTogether(tasks);

		assertThat(this.service.balances()).containsExactly(new Balance(USD, new BigDecimal("50.00")));
	}

	@RepeatedTest(20)
	void balancesCanBeReadWhileTheyChange() throws Exception {
		List<Runnable> tasks = new ArrayList<>();
		for (String code : List.of("AUD", "CAD", "CHF", "EUR", "GBP", "JPY", "NOK", "SEK", "USD", "ZAR")) {
			tasks.add(() -> {
				for (int i = 0; i < 500; i++) {
					this.service.deposit("1", code);
					this.service.withdraw("1", code);
				}
			});
			tasks.add(() -> {
				for (int i = 0; i < 500; i++) {
					this.service.balances();
				}
			});
		}

		runTogether(tasks);

		assertThat(this.service.balances()).isEmpty();
	}

	private static void runTogether(List<? extends Runnable> tasks) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(tasks.size());
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<?>> running = new ArrayList<>();
			for (Runnable task : tasks) {
				running.add(executor.submit(() -> {
					start.await();
					task.run();
					return null;
				}));
			}
			start.countDown();
			for (Future<?> future : running) {
				future.get(30, TimeUnit.SECONDS);
			}
		}
		finally {
			executor.shutdownNow();
		}
	}

}
