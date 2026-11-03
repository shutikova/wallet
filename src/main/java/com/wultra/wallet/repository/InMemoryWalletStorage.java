package com.wultra.wallet.repository;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Currency;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

import org.springframework.stereotype.Repository;

// Takes no callbacks, so no outside code runs under the lock, and CNB cannot be called while it is held
// (see README decision 5).
@Repository
public class InMemoryWalletStorage {

	private final ReentrantLock lock = new ReentrantLock();

	private final SortedMap<Currency, BigDecimal> balances = new TreeMap<>(
			Comparator.comparing(Currency::getCurrencyCode));

	public BigDecimal add(Currency currency, BigDecimal amount) {
		return withLock(() -> this.balances.merge(currency, amount, BigDecimal::add));
	}

	// Checks and subtracts as one step, so two withdrawals cannot spend the same money.
	public Optional<BigDecimal> subtractIfAvailable(Currency currency, BigDecimal amount) {
		return withLock(() -> {
			BigDecimal current = this.balances.getOrDefault(currency, BigDecimal.ZERO);
			if (current.compareTo(amount) < 0) {
				return Optional.empty();
			}
			BigDecimal remaining = current.subtract(amount);
			if (remaining.signum() == 0) {
				this.balances.remove(currency);
			}
			else {
				this.balances.put(currency, remaining);
			}
			return Optional.of(remaining);
		});
	}

	public SortedMap<Currency, BigDecimal> snapshot() {
		return withLock(() -> new TreeMap<>(this.balances));
	}

	private <T> T withLock(Supplier<T> action) {
		this.lock.lock();
		try {
			return action.get();
		}
		finally {
			this.lock.unlock();
		}
	}

}
