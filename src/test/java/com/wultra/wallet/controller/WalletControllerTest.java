package com.wultra.wallet.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import com.wultra.wallet.model.RateTable;
import com.wultra.wallet.model.RatesView;
import com.wultra.wallet.repository.InMemoryWalletStorage;
import com.wultra.wallet.service.cnb.CnbFiles;
import com.wultra.wallet.service.cnb.ExchangeRateProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
class WalletControllerTest {

	private static final RateTable TABLE = CnbFiles.table("daily-2026-10-30.txt");

	@Autowired
	private MockMvc mvc;

	@Autowired
	private InMemoryWalletStorage storage;

	@MockitoBean
	private ExchangeRateProvider rateProvider;

	@BeforeEach
	void emptyTheWallet() {
		this.storage.snapshot().forEach(this.storage::subtractIfAvailable);
	}

	@Test
	void amountsShowTheDecimalPlacesOfTheCurrency() throws Exception {
		expectBody(deposit("10", "USD"), "{\"currency\": \"USD\", \"balance\": \"10.00\"}");
		expectBody(deposit("0.5", "USD"), "{\"currency\": \"USD\", \"balance\": \"10.50\"}");
		expectBody(deposit("1000", "JPY"), "{\"currency\": \"JPY\", \"balance\": \"1000\"}");
	}

	@Test
	void largeBalancesAreNotShownWithAnExponent() throws Exception {
		deposit("999999999999999", "USD");

		expectBody(deposit("999999999999999", "USD"), "{\"currency\": \"USD\", \"balance\": \"1999999999999998.00\"}");
	}

	@Test
	void currencyCodesAreAcceptedInAnyLetterCase() throws Exception {
		expectBody(deposit("10", "usd"), "{\"currency\": \"USD\", \"balance\": \"10.00\"}");
		expectBody(withdraw("1", "uSd"), "{\"currency\": \"USD\", \"balance\": \"9.00\"}");
		expectBody(total("usd"), """
				{
				  "currency": "USD",
				  "total": "9.00",
				  "rates": null,
				  "breakdown": [{"currency": "USD", "amount": "9.00", "converted": "9.00"}]
				}
				""");
	}

	@Test
	void balancesAreSortedByCodeAndLeaveOutZero() throws Exception {
		deposit("5", "USD");
		deposit("100", "CZK");
		deposit("5", "EUR");
		expectBody(withdraw("5", "USD"), "{\"currency\": \"USD\", \"balance\": \"0.00\"}");

		expectBody(balances(), """
				{"balances": [{"currency": "CZK", "amount": "100.00"}, {"currency": "EUR", "amount": "5.00"}]}
				""");
	}

	@Test
	void anEmptyWalletHasNoBalances() throws Exception {
		expectBody(balances(), "{\"balances\": []}");
	}

	@Test
	void anEmptyWalletTotalsZeroWithoutRates() throws Exception {
		expectBody(total("USD"), "{\"currency\": \"USD\", \"total\": \"0.00\", \"rates\": null, \"breakdown\": []}");
		verifyNoInteractions(this.rateProvider);
	}

	@Test
	void aWalletHoldingOnlyTheTargetCurrencyNeedsNoRates() throws Exception {
		deposit("7", "USD");

		expectBody(total("USD"), """
				{
				  "currency": "USD",
				  "total": "7.00",
				  "rates": null,
				  "breakdown": [{"currency": "USD", "amount": "7.00", "converted": "7.00"}]
				}
				""");
		verifyNoInteractions(this.rateProvider);
	}

	@Test
	void depositsAndWithdrawalsNeverNeedRates() throws Exception {
		deposit("7", "USD");
		withdraw("1", "USD");
		balances();

		verifyNoInteractions(this.rateProvider);
	}

	@Test
	void theTotalShowsWhichTableWasUsedAndItsFlags() throws Exception {
		when(this.rateProvider.currentRates()).thenReturn(new RatesView(TABLE, LocalDate.of(2026, 11, 2), true, true));
		deposit("5", "EUR");

		expectBody(total("USD"), """
				{
				  "currency": "USD",
				  "total": "5.58",
				  "rates": {"valuationDate": "2026-11-02", "tableDate": "2026-10-30", "provisional": true, "stale": true},
				  "breakdown": [{"currency": "EUR", "amount": "5.00", "converted": "5.58"}]
				}
				""");
	}

	@Test
	void aTotalInJpyUsesItsRatePer100UnitsAndHasNoDecimals() throws Exception {
		when(this.rateProvider.currentRates()).thenReturn(new RatesView(TABLE, TABLE.date(), false, false));
		deposit("1", "USD");

		expectBody(total("JPY"), """
				{
				  "currency": "JPY",
				  "total": "157",
				  "rates": {"valuationDate": "2026-10-30", "tableDate": "2026-10-30", "provisional": false, "stale": false},
				  "breakdown": [{"currency": "USD", "amount": "1.00", "converted": "157"}]
				}
				""");
	}

	@Test
	void openApiMatchesTheResponses() throws Exception {
		String deposits = "$.paths['/api/v1/wallet/deposits'].post.responses";

		this.mvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.info.title").value("Multicurrency Wallet"))
			.andExpect(jsonPath(deposits + "['200'].content['application/json']").exists())
			.andExpect(jsonPath(deposits + "['422'].content['application/problem+json']").exists())
			.andExpect(jsonPath("$.components.schemas.RatesInfo.type[0]").value("object"))
			.andExpect(jsonPath("$.components.schemas.RatesInfo.type[1]").value("null"));
	}

	private ResultActions deposit(String amount, String currency) throws Exception {
		return moneyOperation("deposits", amount, currency);
	}

	private ResultActions withdraw(String amount, String currency) throws Exception {
		return moneyOperation("withdrawals", amount, currency);
	}

	private ResultActions moneyOperation(String operation, String amount, String currency) throws Exception {
		return this.mvc.perform(post("/api/v1/wallet/" + operation).contentType(MediaType.APPLICATION_JSON)
			.content("{\"amount\": \"%s\", \"currency\": \"%s\"}".formatted(amount, currency)));
	}

	private ResultActions balances() throws Exception {
		return this.mvc.perform(get("/api/v1/wallet/balances"));
	}

	private ResultActions total(String currency) throws Exception {
		return this.mvc.perform(get("/api/v1/wallet/balances/total").param("currency", currency));
	}

	private static void expectBody(ResultActions response, String expected) throws Exception {
		response.andExpect(status().isOk())
			.andExpect(content().contentType(MediaType.APPLICATION_JSON))
			.andExpect(content().json(expected, JsonCompareMode.STRICT));
	}

}
