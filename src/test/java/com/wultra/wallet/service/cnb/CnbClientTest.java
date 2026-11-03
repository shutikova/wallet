package com.wultra.wallet.service.cnb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.SocketTimeoutException;
import java.time.LocalDate;

import com.wultra.wallet.model.error.CnbFetchException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CnbClientTest {

	private static final String SUNDAY_URL = "https://www.cnb.cz/en/financial-markets/foreign-exchange-market/"
			+ "central-bank-exchange-rate-fixing/central-bank-exchange-rate-fixing/daily.txt?date=01.11.2026";

	private static final LocalDate SUNDAY = LocalDate.of(2026, 11, 1);

	private final RestClient.Builder builder = RestClient.builder();

	private final MockRestServiceServer server = MockRestServiceServer.bindTo(this.builder).build();

	private final CnbClient client = new CnbClient(this.builder.build(), new CnbParser());

	@Test
	void requestsTheEnglishTableAndUsesTheDateInTheFile() {
		this.server.expect(requestTo(SUNDAY_URL))
			.andExpect(method(HttpMethod.GET))
			.andRespond(withSuccess(CnbFiles.read("daily-2026-10-30.txt"), MediaType.TEXT_PLAIN));

		assertThat(this.client.fetchDaily(SUNDAY).date()).isEqualTo(LocalDate.of(2026, 10, 30));
		this.server.verify();
	}

	@Test
	void acceptsATableDatedOnTheRequestedDate() {
		this.server.expect(requestTo(SUNDAY_URL.replace("01.11.2026", "30.10.2026")))
			.andRespond(withSuccess(CnbFiles.read("daily-2026-10-30.txt"), MediaType.TEXT_PLAIN));

		assertThat(this.client.fetchDaily(LocalDate.of(2026, 10, 30)).date()).isEqualTo(LocalDate.of(2026, 10, 30));
	}

	@Test
	void aTableDatedAfterTheRequestedDateIsAFetchFailure() {
		this.server.expect(requestTo(SUNDAY_URL.replace("01.11.2026", "29.10.2026")))
			.andRespond(withSuccess(CnbFiles.read("daily-2026-10-30.txt"), MediaType.TEXT_PLAIN));

		assertThatThrownBy(() -> this.client.fetchDaily(LocalDate.of(2026, 10, 29))).isInstanceOf(CnbFetchException.class);
	}

	@Test
	void serverErrorIsAFetchFailure() {
		this.server.expect(requestTo(SUNDAY_URL)).andRespond(withServerError());

		assertThatThrownBy(() -> this.client.fetchDaily(SUNDAY)).isInstanceOf(CnbFetchException.class);
	}

	@Test
	void timeoutIsAFetchFailure() {
		this.server.expect(requestTo(SUNDAY_URL)).andRespond(withException(new SocketTimeoutException("timed out")));

		assertThatThrownBy(() -> this.client.fetchDaily(SUNDAY)).isInstanceOf(CnbFetchException.class);
	}

	@Test
	void unreadableResponseIsAFetchFailure() {
		this.server.expect(requestTo(SUNDAY_URL)).andRespond(withSuccess("<html></html>", MediaType.TEXT_HTML));

		assertThatThrownBy(() -> this.client.fetchDaily(SUNDAY)).isInstanceOf(CnbFetchException.class);
	}

}
