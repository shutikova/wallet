package com.wultra.wallet.service.cnb;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.locks.ReentrantLock;

import com.wultra.wallet.model.RateTable;
import com.wultra.wallet.model.RatesView;
import com.wultra.wallet.model.error.CnbFetchException;
import com.wultra.wallet.model.error.RatesUnavailableException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

// Applies the fetching and freshness rules (see README decision 3).
@Service
@Slf4j
@RequiredArgsConstructor
public class CachingExchangeRateProvider implements ExchangeRateProvider {

	private static final Duration WAIT_WITH_NOTHING_CACHED = Duration.ofMinutes(1);

	private static final Duration WAIT_WITH_CACHED_TABLE = Duration.ofMinutes(10);

	private static final int MAX_TABLE_AGE_DAYS = 7;

	private final CnbClient client;

	private final CzechHolidayCalendar calendar;

	private final Clock clock;

	// Held during the CNB call, so at most one runs at a time. This is not the wallet lock.
	private final ReentrantLock lock = new ReentrantLock();

	private RateTable cached;

	private Instant nextAttempt = Instant.MIN;

	private boolean lastAttemptFailed;

	@Override
	public RatesView currentRates() {
		this.lock.lock();
		try {
			Instant instant = this.clock.instant();
			LocalDateTime now = LocalDateTime.ofInstant(instant, CzechHolidayCalendar.PRAGUE);
			LocalDate today = now.toLocalDate();
			if (isOlderThan(this.calendar.newestPossibleTable(now)) && !instant.isBefore(this.nextAttempt)) {
				fetch(today, instant);
			}
			return view(today);
		}
		finally {
			this.lock.unlock();
		}
	}

	private boolean isOlderThan(LocalDate date) {
		return this.cached == null || this.cached.date().isBefore(date);
	}

	private void fetch(LocalDate today, Instant instant) {
		try {
			RateTable fetched = this.client.fetchDaily(today);
			if (this.cached == null || fetched.date().isAfter(this.cached.date())) {
				this.cached = fetched;
				log.info("Loaded CNB rate table of {}", fetched.date());
			}
			this.lastAttemptFailed = false;
		}
		catch (CnbFetchException ex) {
			log.warn("CNB rate table could not be loaded: {}", ex.getMessage());
			this.lastAttemptFailed = true;
		}
		this.nextAttempt = instant.plus((this.cached != null) ? WAIT_WITH_CACHED_TABLE : WAIT_WITH_NOTHING_CACHED);
	}

	private RatesView view(LocalDate today) {
		if (this.cached == null || today.isAfter(this.cached.date().plusDays(MAX_TABLE_AGE_DAYS))) {
			throw new RatesUnavailableException();
		}
		boolean provisional = this.calendar.isWorkingDay(today) && this.cached.date().isBefore(today);
		// An attempt only runs when the cache is behind, and it stays behind until one succeeds.
		return new RatesView(this.cached, today, provisional, this.lastAttemptFailed);
	}

}
