package com.wultra.wallet.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class InMemoryWalletStorageTest {

	private static final Currency EUR = Currency.getInstance("EUR");

	private static final Currency USD = Currency.getInstance("USD");

	private final InMemoryWalletStorage storage = new InMemoryWalletStorage();

	@Test
	void addStartsAndGrowsABalance() {
		assertThat(this.storage.add(USD, new BigDecimal("10.00"))).isEqualByComparingTo("10.00");
		assertThat(this.storage.add(USD, new BigDecimal("2.50"))).isEqualByComparingTo("12.50");
		assertThat(this.storage.snapshot().get(USD)).isEqualByComparingTo("12.50");
	}

	@Test
	void subtractReturnsWhatRemains() {
		this.storage.add(USD, new BigDecimal("10.00"));

		assertThat(this.storage.subtractIfAvailable(USD, new BigDecimal("3.00"))).hasValueSatisfying(
				remaining -> assertThat(remaining).isEqualByComparingTo("7.00"));
		assertThat(this.storage.snapshot().get(USD)).isEqualByComparingTo("7.00");
	}

	@Test
	void subtractingEverythingReturnsZeroAndRemovesTheBalance() {
		this.storage.add(USD, new BigDecimal("10.00"));

		assertThat(this.storage.subtractIfAvailable(USD, new BigDecimal("10"))).hasValueSatisfying(
				remaining -> assertThat(remaining).isEqualByComparingTo("0"));
		assertThat(this.storage.snapshot()).isEmpty();
	}

	@Test
	void subtractingTooMuchReturnsNothingAndChangesNothing() {
		this.storage.add(USD, new BigDecimal("10.00"));

		assertThat(this.storage.subtractIfAvailable(USD, new BigDecimal("10.01"))).isEmpty();
		assertThat(this.storage.snapshot().get(USD)).isEqualByComparingTo("10.00");
	}

	@Test
	void subtractingACurrencyNotHeldReturnsNothing() {
		assertThat(this.storage.subtractIfAvailable(USD, new BigDecimal("1.00"))).isEmpty();
		assertThat(this.storage.snapshot()).isEmpty();
	}

	@Test
	void snapshotIsSortedByCurrencyCode() {
		this.storage.add(USD, BigDecimal.ONE);
		this.storage.add(EUR, BigDecimal.ONE);

		assertThat(this.storage.snapshot().keySet()).containsExactly(EUR, USD);
	}

	@Test
	void changingASnapshotDoesNotChangeTheBalances() {
		this.storage.snapshot().put(USD, BigDecimal.ONE);

		assertThat(this.storage.snapshot()).isEmpty();
	}

	@Test
	void failedOperationReleasesTheLock() throws Exception {
		assertThatThrownBy(() -> this.storage.add(USD, null)).isInstanceOf(NullPointerException.class);

		assertThat(CompletableFuture.supplyAsync(this.storage::snapshot).get(5, TimeUnit.SECONDS)).isEmpty();
	}

}
