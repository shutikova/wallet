package com.wultra.wallet.service.cnb;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

// Reports UTC, so a test fails if the code relies on the clock's zone instead of Europe/Prague.
final class MutableClock extends Clock {

	private Instant instant = Instant.EPOCH;

	void setPrague(String localDateTime) {
		this.instant = LocalDateTime.parse(localDateTime).atZone(CzechHolidayCalendar.PRAGUE).toInstant();
	}

	void setInstant(String instant) {
		this.instant = Instant.parse(instant);
	}

	@Override
	public ZoneId getZone() {
		return ZoneOffset.UTC;
	}

	@Override
	public Clock withZone(ZoneId zone) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Instant instant() {
		return this.instant;
	}

}
