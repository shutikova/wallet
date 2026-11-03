package com.wultra.wallet.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class RatesConfigurationTest {

	@Test
	void clockUsesTheBusinessTimeZone() {
		Clock clock = new RatesConfiguration().clock();

		assertThat(clock.getZone()).isEqualTo(ZoneId.of("Europe/Prague"));
	}

}
