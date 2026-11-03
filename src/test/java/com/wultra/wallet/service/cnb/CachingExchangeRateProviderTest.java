package com.wultra.wallet.service.cnb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.wultra.wallet.model.RateTable;
import com.wultra.wallet.model.RatesView;
import com.wultra.wallet.model.error.CnbFetchException;
import com.wultra.wallet.model.error.RatesUnavailableException;
import org.junit.jupiter.api.Test;

// 27 Oct 2026 is a Tuesday, 28 Oct a holiday, 30 Oct a Friday and 2 Nov a Monday. Tables are real CNB files.
class CachingExchangeRateProviderTest {

	private static final RateTable TABLE_27_OCT = CnbFiles.table("daily-2026-10-27.txt");

	private static final RateTable TABLE_30_OCT = CnbFiles.table("daily-2026-10-30.txt");

	private static final RateTable TABLE_2_NOV = CnbFiles.table("daily-2026-11-02.txt");

	private static final CnbFetchException CNB_DOWN = new CnbFetchException("CNB is down");

	private final MutableClock clock = new MutableClock();

	private final CnbClient client = mock(CnbClient.class);

	private final CachingExchangeRateProvider provider = new CachingExchangeRateProvider(this.client,
			new CzechHolidayCalendar(), this.clock);

	@Test
	void firstRequestLoadsTheTable() {
		RatesView rates = ratesAt("2026-10-31T12:00", TABLE_30_OCT);

		assertThat(rates.table()).isEqualTo(TABLE_30_OCT);
		assertThat(rates.valuationDate()).isEqualTo(LocalDate.of(2026, 10, 31));
		assertThat(rates.provisional()).isFalse();
		assertThat(rates.stale()).isFalse();
		verify(this.client).fetchDaily(LocalDate.of(2026, 10, 31));
	}

	@Test
	void noRequestOnAWeekendWhenTheCacheHoldsFridaysTable() {
		ratesAt("2026-10-31T12:00", TABLE_30_OCT);

		RatesView rates = ratesAt("2026-11-01T23:59");

		assertThat(rates.table()).isEqualTo(TABLE_30_OCT);
		assertThat(rates.provisional()).isFalse();
		verify(this.client, times(1)).fetchDaily(any());
	}

	@Test
	void noRequestOnAHolidayWhenTheCacheHoldsTheLastWorkingDaysTable() {
		ratesAt("2026-10-27T15:00", TABLE_27_OCT);

		RatesView rates = ratesAt("2026-10-28T15:00");

		assertThat(rates.table()).isEqualTo(TABLE_27_OCT);
		assertThat(rates.provisional()).isFalse();
		verify(this.client, times(1)).fetchDaily(any());
	}

	@Test
	void noRequestBefore1330AndTheTotalIsProvisional() {
		ratesAt("2026-10-31T12:00", TABLE_30_OCT);

		RatesView rates = ratesAt("2026-11-02T13:29");

		assertThat(rates.table()).isEqualTo(TABLE_30_OCT);
		assertThat(rates.provisional()).isTrue();
		assertThat(rates.stale()).isFalse();
		verify(this.client, times(1)).fetchDaily(any());
	}

	@Test
	void morningAfterAHolidayIsProvisionalWithoutARequest() {
		ratesAt("2026-10-27T15:00", TABLE_27_OCT);

		RatesView rates = ratesAt("2026-10-29T09:00");

		assertThat(rates.provisional()).isTrue();
		verify(this.client, times(1)).fetchDaily(any());
	}

	@Test
	void checksEveryTenMinutesFrom1330UntilTodaysTableArrives() {
		ratesAt("2026-10-31T12:00", TABLE_30_OCT);

		assertThat(ratesAt("2026-11-02T13:30", TABLE_30_OCT).provisional()).isTrue();
		verify(this.client, times(2)).fetchDaily(any());

		ratesAt("2026-11-02T13:39:59");
		verify(this.client, times(2)).fetchDaily(any());

		RatesView arrived = ratesAt("2026-11-02T13:40", TABLE_2_NOV);
		assertThat(arrived.table()).isEqualTo(TABLE_2_NOV);
		assertThat(arrived.provisional()).isFalse();
		assertThat(arrived.stale()).isFalse();
		verify(this.client, times(3)).fetchDaily(any());

		ratesAt("2026-11-02T14:30");
		ratesAt("2026-11-02T23:00");
		verify(this.client, times(3)).fetchDaily(any());
	}

	@Test
	void aTableMissedOnAQuietFridayIsFetchedOnTheWeekend() {
		ratesAt("2026-10-27T15:00", TABLE_27_OCT);

		RatesView rates = ratesAt("2026-10-31T10:00", TABLE_30_OCT);

		assertThat(rates.table()).isEqualTo(TABLE_30_OCT);
		assertThat(rates.provisional()).isFalse();
		verify(this.client, times(2)).fetchDaily(any());
	}

	@Test
	void aTableMissedOnAQuietAfternoonIsFetchedTheNextMorning() {
		ratesAt("2026-10-31T12:00", TABLE_30_OCT);

		RatesView rates = ratesAt("2026-11-03T09:00", TABLE_2_NOV);

		assertThat(rates.table()).isEqualTo(TABLE_2_NOV);
		assertThat(rates.provisional()).isTrue();
		assertThat(rates.stale()).isFalse();
	}

	@Test
	void cnbDownAtStartupIsUnavailableAndRetriedAfterOneMinute() {
		when(this.client.fetchDaily(any())).thenThrow(CNB_DOWN);

		this.clock.setPrague("2026-11-02T14:00");
		assertThatThrownBy(this.provider::currentRates).isInstanceOf(RatesUnavailableException.class);
		this.clock.setPrague("2026-11-02T14:00:59");
		assertThatThrownBy(this.provider::currentRates).isInstanceOf(RatesUnavailableException.class);
		verify(this.client, times(1)).fetchDaily(any());

		RatesView rates = ratesAt("2026-11-02T14:01", TABLE_2_NOV);

		assertThat(rates.table()).isEqualTo(TABLE_2_NOV);
		assertThat(rates.stale()).isFalse();
	}

	@Test
	void cnbDownWithACachedTableIsStaleAndProvisionalAndWaitsTenMinutes() {
		ratesAt("2026-10-31T12:00", TABLE_30_OCT);
		when(this.client.fetchDaily(any())).thenThrow(CNB_DOWN);

		this.clock.setPrague("2026-11-02T14:00");
		RatesView rates = this.provider.currentRates();
		assertThat(rates.table()).isEqualTo(TABLE_30_OCT);
		assertThat(rates.stale()).isTrue();
		assertThat(rates.provisional()).isTrue();

		this.clock.setPrague("2026-11-02T14:09:59");
		assertThat(this.provider.currentRates().stale()).isTrue();
		verify(this.client, times(2)).fetchDaily(any());

		this.clock.setPrague("2026-11-02T14:10");
		assertThat(this.provider.currentRates().stale()).isTrue();
		verify(this.client, times(3)).fetchDaily(any());
	}

	@Test
	void cnbDownOnAWeekendWithAnOlderTableIsStaleButNotProvisional() {
		ratesAt("2026-10-27T15:00", TABLE_27_OCT);
		when(this.client.fetchDaily(any())).thenThrow(CNB_DOWN);

		this.clock.setPrague("2026-10-31T10:00");
		RatesView rates = this.provider.currentRates();

		assertThat(rates.stale()).isTrue();
		assertThat(rates.provisional()).isFalse();
	}

	@Test
	void staleEndsWhenCnbAnswersEvenWithoutANewTable() {
		ratesAt("2026-10-31T12:00", TABLE_30_OCT);
		when(this.client.fetchDaily(any())).thenThrow(CNB_DOWN);
		this.clock.setPrague("2026-11-02T14:00");
		assertThat(this.provider.currentRates().stale()).isTrue();

		RatesView rates = ratesAt("2026-11-02T14:10", TABLE_30_OCT);

		assertThat(rates.stale()).isFalse();
		assertThat(rates.provisional()).isTrue();
	}

	@Test
	void aDamagedResponseCountsAsCnbBeingDown() {
		ratesAt("2026-10-31T12:00", TABLE_30_OCT);
		when(this.client.fetchDaily(any())).thenThrow(new CnbFetchException("CNB response is not a rate table"));

		this.clock.setPrague("2026-11-02T14:00");

		assertThat(this.provider.currentRates().stale()).isTrue();
	}

	@Test
	void aTableIsUsableForSevenDaysAfterItsDateAndNotLonger() {
		ratesAt("2026-10-31T12:00", TABLE_30_OCT);
		when(this.client.fetchDaily(any())).thenThrow(CNB_DOWN);

		this.clock.setPrague("2026-11-06T23:59");
		assertThat(this.provider.currentRates().table()).isEqualTo(TABLE_30_OCT);

		this.clock.setPrague("2026-11-07T00:00");
		assertThatThrownBy(this.provider::currentRates).isInstanceOf(RatesUnavailableException.class);
	}

	@Test
	void theAgeLimitAppliesEvenWhenCnbAnswersWithAnOldTable() {
		this.clock.setPrague("2026-11-07T12:00");
		when(this.client.fetchDaily(any())).thenReturn(TABLE_30_OCT);

		assertThatThrownBy(this.provider::currentRates).isInstanceOf(RatesUnavailableException.class);
	}

	@Test
	void anOlderResponseDoesNotReplaceTheCachedTable() {
		ratesAt("2026-11-02T14:00", TABLE_2_NOV);

		RatesView rates = ratesAt("2026-11-03T14:00", TABLE_30_OCT);

		assertThat(rates.table()).isEqualTo(TABLE_2_NOV);
	}

	@Test
	void usesThePragueDateNotTheClocksZone() {
		this.clock.setInstant("2026-11-01T23:30:00Z");
		when(this.client.fetchDaily(any())).thenReturn(TABLE_30_OCT);

		RatesView rates = this.provider.currentRates();

		assertThat(rates.valuationDate()).isEqualTo(LocalDate.of(2026, 11, 2));
		assertThat(rates.provisional()).isTrue();
		verify(this.client).fetchDaily(LocalDate.of(2026, 11, 2));
	}

	@Test
	void atMostOneRequestRunsAtATime() throws Exception {
		this.clock.setPrague("2026-11-02T14:00");
		AtomicInteger running = new AtomicInteger();
		AtomicInteger mostRunning = new AtomicInteger();
		when(this.client.fetchDaily(any())).thenAnswer(invocation -> {
			mostRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
			Thread.sleep(100);
			running.decrementAndGet();
			return TABLE_2_NOV;
		});

		ExecutorService executor = Executors.newFixedThreadPool(20);
		try {
			List<Future<RatesView>> requests = new ArrayList<>();
			for (int i = 0; i < 20; i++) {
				requests.add(executor.submit(this.provider::currentRates));
			}
			for (Future<RatesView> request : requests) {
				assertThat(request.get(30, TimeUnit.SECONDS).table()).isEqualTo(TABLE_2_NOV);
			}
		}
		finally {
			executor.shutdownNow();
		}

		assertThat(mostRunning).hasValue(1);
		verify(this.client, times(1)).fetchDaily(any());
	}

	private RatesView ratesAt(String pragueTime, RateTable cnbAnswer) {
		doReturn(cnbAnswer).when(this.client).fetchDaily(any());
		return ratesAt(pragueTime);
	}

	private RatesView ratesAt(String pragueTime) {
		this.clock.setPrague(pragueTime);
		return this.provider.currentRates();
	}

}
