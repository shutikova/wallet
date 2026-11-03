package com.wultra.wallet.errorhandling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.wultra.wallet.model.Rate;
import com.wultra.wallet.model.RateTable;
import com.wultra.wallet.model.RatesView;
import com.wultra.wallet.model.error.RatesUnavailableException;
import com.wultra.wallet.repository.InMemoryWalletStorage;
import com.wultra.wallet.service.cnb.CnbFiles;
import com.wultra.wallet.service.cnb.ExchangeRateProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;

// Sends real requests through the real service, so each error comes from the code that raises it in production.
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

	private static final String DEPOSITS = "/api/v1/wallet/deposits";

	private static final String WITHDRAWALS = "/api/v1/wallet/withdrawals";

	private static final List<String> MONEY_OPERATIONS = List.of(DEPOSITS, WITHDRAWALS);

	private static final String TOTAL = "/api/v1/wallet/balances/total";

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

	@ParameterizedTest
	@ValueSource(strings = { "", "{not json", "null", "[]", "\"10\"", "{}", "{\"currency\": \"USD\"}", "{\"amount\": \"10\"}",
			"{\"amount\": null, \"currency\": \"USD\"}", "{\"amount\": \"10\", \"currency\": null}",
			"{\"amount\": \"1\", \"amount\": \"1000\", \"currency\": \"USD\"}" })
	void malformedOrIncompleteBodyIsInvalidRequest(String body) throws Exception {
		for (String path : MONEY_OPERATIONS) {
			send(path, body).andExpect(problem(400, "/problems/invalid-request"));
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "{\"amount\": 10, \"currency\": \"USD\"}", "{\"amount\": 10.5, \"currency\": \"USD\"}",
			"{\"amount\": true, \"currency\": \"USD\"}", "{\"amount\": [\"10\"], \"currency\": \"USD\"}",
			"{\"amount\": {\"value\": \"10\"}, \"currency\": \"USD\"}", "{\"amount\": \"10\", \"currency\": 978}" })
	void aFieldThatIsNotAJsonStringIsInvalidRequest(String body) throws Exception {
		for (String path : MONEY_OPERATIONS) {
			send(path, body).andExpect(problem(400, "/problems/invalid-request"));
		}
	}

	@ParameterizedTest
	@CsvSource({ "0,USD", "0.00,USD", "-5,USD", "+5,USD", "1e5,USD", ".5,USD", "5.,USD", "'1,000',USD", "'',USD", "' 10',USD",
			"abc,USD", "10.001,USD", "10.000,USD", "10.5,JPY", "10.0,JPY", "1000000000000000,USD", "0000000000000001,USD" })
	void invalidAmountIsInvalidRequest(String amount, String currency) throws Exception {
		for (String path : MONEY_OPERATIONS) {
			sendMoney(path, amount, currency).andExpect(problem(400, "/problems/invalid-request"));
		}
	}

	@Test
	void missingTargetCurrencyIsInvalidRequest() throws Exception {
		this.mvc.perform(get(TOTAL)).andExpect(problem(400, "/problems/invalid-request"));
	}

	@Test
	void invalidRequestDoesNotShowParserDetails() throws Exception {
		send(DEPOSITS, "{not json").andExpect(
				jsonPath("$.detail").value("Request body must be JSON with amount and currency as strings"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "US", "USDD", "U1D", "ABC", " usd" })
	void aCodeThatIsNotIsoIsInvalidCurrency(String code) throws Exception {
		for (String path : MONEY_OPERATIONS) {
			sendMoney(path, "10", code).andExpect(problem(400, "/problems/invalid-currency"));
		}
		total(code).andExpect(problem(400, "/problems/invalid-currency"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "AED", "XDR", "DEM" })
	void aValidCodeOutsideTheListIsUnsupportedCurrency(String code) throws Exception {
		for (String path : MONEY_OPERATIONS) {
			sendMoney(path, "10", code).andExpect(problem(422, "/problems/unsupported-currency"));
		}
		total(code).andExpect(problem(422, "/problems/unsupported-currency"));
	}

	@Test
	void withdrawingMoreThanTheBalanceIsInsufficientFundsAndChangesNothing() throws Exception {
		sendMoney(DEPOSITS, "5", "USD").andExpect(status().isOk());

		sendMoney(WITHDRAWALS, "5.01", "USD").andExpect(problem(422, "/problems/insufficient-funds"));

		this.mvc.perform(get("/api/v1/wallet/balances")).andExpect(jsonPath("$.balances[0].amount").value("5.00"));
	}

	@Test
	void withdrawingACurrencyThatIsNotHeldIsInsufficientFunds() throws Exception {
		sendMoney(WITHDRAWALS, "1", "USD").andExpect(problem(422, "/problems/insufficient-funds"));
	}

	@Test
	void aProblemHasAllItsFields() throws Exception {
		sendMoney(WITHDRAWALS, "1", "USD").andExpect(content().json("""
				{
				  "type": "/problems/insufficient-funds",
				  "title": "Insufficient funds",
				  "status": 422,
				  "detail": "The USD balance is too low for this withdrawal",
				  "instance": "/api/v1/wallet/withdrawals"
				}
				""", JsonCompareMode.STRICT));
	}

	@Test
	void aHeldCurrencyMissingFromTheTableIsRateNotAvailable() throws Exception {
		Map<Currency, Rate> withoutEur = new HashMap<>(TABLE.rates());
		withoutEur.remove(Currency.getInstance("EUR"));
		when(this.rateProvider.currentRates())
			.thenReturn(new RatesView(new RateTable(TABLE.date(), withoutEur), TABLE.date(), false, false));
		sendMoney(DEPOSITS, "5", "EUR").andExpect(status().isOk());

		total("USD").andExpect(problem(422, "/problems/rate-not-available"));
	}

	@Test
	void noUsableRatesIsRatesUnavailable() throws Exception {
		when(this.rateProvider.currentRates()).thenThrow(new RatesUnavailableException());
		sendMoney(DEPOSITS, "5", "EUR").andExpect(status().isOk());

		total("USD").andExpect(problem(503, "/problems/rates-unavailable"));
	}

	@Test
	void unknownPathIsNotFoundWithTheDefaultType() throws Exception {
		this.mvc.perform(get("/api/v1/wallet/unknown")).andExpect(defaultProblem(404));
	}

	@Test
	void wrongMethodIsNotAllowedWithTheDefaultType() throws Exception {
		this.mvc.perform(get(DEPOSITS)).andExpect(defaultProblem(405));
	}

	@Test
	void wrongContentTypeIsUnsupportedWithTheDefaultType() throws Exception {
		this.mvc.perform(post(DEPOSITS).contentType(MediaType.TEXT_PLAIN).content("10 USD"))
			.andExpect(defaultProblem(415));
	}

	@Test
	void aClientThatCannotReadJsonIsRefusedBeforeMoneyMoves() throws Exception {
		sendMoney(DEPOSITS, "5", "USD").andExpect(status().isOk());

		for (String path : MONEY_OPERATIONS) {
			this.mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.TEXT_PLAIN)
				.content("{\"amount\": \"1\", \"currency\": \"USD\"}")).andExpect(defaultProblem(406));
		}

		this.mvc.perform(get("/api/v1/wallet/balances")).andExpect(jsonPath("$.balances[0].amount").value("5.00"));
	}

	@Test
	void rejectedRequestsAreLogged(CapturedOutput output) throws Exception {
		sendMoney(WITHDRAWALS, "1", "USD");

		assertThat(output).contains("Request rejected: insufficient-funds: The USD balance is too low for this withdrawal");
	}

	@Test
	void unexpectedErrorGoesToTheLogAndShowsNoInternalDetails(CapturedOutput output) throws Exception {
		when(this.rateProvider.currentRates()).thenThrow(new IllegalStateException("secret internal detail"));
		sendMoney(DEPOSITS, "5", "EUR").andExpect(status().isOk());

		String body = total("USD").andExpect(defaultProblem(500))
			.andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(body).doesNotContain("secret", "IllegalStateException");
		assertThat(output).contains("secret internal detail");
	}

	@Test
	void problemsStayProblemJsonWhenTheClientAsksForJson() throws Exception {
		this.mvc.perform(get(TOTAL).param("currency", "ABC").accept(MediaType.APPLICATION_JSON))
			.andExpect(problem(400, "/problems/invalid-currency"));
	}

	private ResultActions sendMoney(String path, String amount, String currency) throws Exception {
		return send(path, "{\"amount\": \"%s\", \"currency\": \"%s\"}".formatted(amount, currency));
	}

	private ResultActions send(String path, String body) throws Exception {
		return this.mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body));
	}

	private ResultActions total(String currency) throws Exception {
		return this.mvc.perform(get(TOTAL).param("currency", currency));
	}

	private static ResultMatcher problem(int status, String type) {
		return result -> {
			problemWithStatus(status).match(result);
			jsonPath("$.type").value(type).match(result);
		};
	}

	// Spring leaves out about:blank, which is what RFC 9457 says a missing type means.
	private static ResultMatcher defaultProblem(int status) {
		return result -> {
			problemWithStatus(status).match(result);
			jsonPath("$.type").doesNotExist().match(result);
		};
	}

	private static ResultMatcher problemWithStatus(int status) {
		return result -> {
			status().is(status).match(result);
			content().contentType(MediaType.APPLICATION_PROBLEM_JSON).match(result);
			jsonPath("$.status").value(status).match(result);
		};
	}

}
