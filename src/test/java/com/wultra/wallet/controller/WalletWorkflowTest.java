package com.wultra.wallet.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;

import com.wultra.wallet.service.cnb.CnbClient;
import com.wultra.wallet.service.cnb.CnbFiles;
import com.wultra.wallet.service.cnb.CzechHolidayCalendar;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

// The whole application except the HTTP call to CNB, which answers with a real CNB file.
@SpringBootTest
@AutoConfigureMockMvc
class WalletWorkflowTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private CnbClient cnbClient;

	@TestBean
	private Clock clock;

	// Sunday 1 Nov 2026, so Friday's table is the official one and the total is not provisional.
	static Clock clock() {
		return Clock.fixed(Instant.parse("2026-11-01T11:00:00Z"), CzechHolidayCalendar.PRAGUE);
	}

	@Test
	void assignmentWorkflowWithRealRates() throws Exception {
		when(this.cnbClient.fetchDaily(any())).thenReturn(CnbFiles.table("daily-2026-10-30.txt"));

		expectBody(moneyOperation("deposits", "10", "USD"), """
				{"currency": "USD", "balance": "10.00"}
				""");
		expectBody(moneyOperation("deposits", "5", "EUR"), """
				{"currency": "EUR", "balance": "5.00"}
				""");
		expectBody(moneyOperation("withdrawals", "3", "USD"), """
				{"currency": "USD", "balance": "7.00"}
				""");
		expectBody(get("/api/v1/wallet/balances"), """
				{"balances": [{"currency": "EUR", "amount": "5.00"}, {"currency": "USD", "amount": "7.00"}]}
				""");
		verifyNoInteractions(this.cnbClient);

		expectBody(get("/api/v1/wallet/balances/total").param("currency", "USD"), """
				{
				  "currency": "USD",
				  "total": "12.58",
				  "rates": {"valuationDate": "2026-11-01", "tableDate": "2026-10-30", "provisional": false, "stale": false},
				  "breakdown": [
				    {"currency": "EUR", "amount": "5.00", "converted": "5.58"},
				    {"currency": "USD", "amount": "7.00", "converted": "7.00"}
				  ]
				}
				""");
		expectBody(get("/api/v1/wallet/balances/total").param("currency", "EUR"), """
				{
				  "currency": "EUR",
				  "total": "11.27",
				  "rates": {"valuationDate": "2026-11-01", "tableDate": "2026-10-30", "provisional": false, "stale": false},
				  "breakdown": [
				    {"currency": "EUR", "amount": "5.00", "converted": "5.00"},
				    {"currency": "USD", "amount": "7.00", "converted": "6.27"}
				  ]
				}
				""");

		verify(this.cnbClient, times(1)).fetchDaily(any());
	}

	private static RequestBuilder moneyOperation(String operation, String amount, String currency) {
		return post("/api/v1/wallet/" + operation).contentType(MediaType.APPLICATION_JSON)
			.content("{\"amount\": \"%s\", \"currency\": \"%s\"}".formatted(amount, currency));
	}

	private void expectBody(RequestBuilder request, String expected) throws Exception {
		this.mvc.perform(request)
			.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andExpect(content().json(expected, JsonCompareMode.STRICT));
	}

}
