package com.wultra.wallet.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
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
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.InitializeResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import io.modelcontextprotocol.spec.McpSchema.Tool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

// A real MCP client talks to the real server over HTTP, as an agent would.
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class WalletMcpToolsTest {

	private static final RateTable TABLE = CnbFiles.table("daily-2026-10-30.txt");

	private static final RatesView RATES = new RatesView(TABLE, LocalDate.of(2026, 11, 1), false, false);

	@LocalServerPort
	private int port;

	@Autowired
	private InMemoryWalletStorage storage;

	@MockitoBean
	private ExchangeRateProvider rateProvider;

	private McpSyncClient client;

	private InitializeResult server;

	@BeforeEach
	void emptyTheWalletAndConnect() {
		this.storage.snapshot().forEach(this.storage::subtractIfAvailable);
		this.client = McpClient.sync(HttpClientStreamableHttpTransport.builder("http://localhost:" + this.port).build()).build();
		this.server = this.client.initialize();
	}

	@AfterEach
	void disconnect() {
		this.client.closeGracefully();
	}

	@Test
	void offersTheFourTools() {
		assertThat(this.client.listTools().tools()).extracting(Tool::name)
			.containsExactlyInAnyOrder("deposit", "withdraw", "balances", "total");
	}

	@Test
	void offersToolsOnly() {
		assertThat(this.server.serverInfo().name()).isEqualTo("wallet");
		assertThat(this.server.capabilities().tools()).isNotNull();
		assertThat(this.server.capabilities().prompts()).isNull();
		assertThat(this.server.capabilities().resources()).isNull();
		assertThat(this.server.capabilities().completions()).isNull();
	}

	@Test
	void marksOnlyTheReadsAsReadOnly() {
		Map<String, Boolean> readOnly = new HashMap<>();
		this.client.listTools().tools().forEach(tool -> readOnly.put(tool.name(), tool.annotations().readOnlyHint()));

		assertThat(readOnly).containsOnly(Map.entry("deposit", false), Map.entry("withdraw", false),
				Map.entry("balances", true), Map.entry("total", true));
	}

	@Test
	void assignmentWorkflowWithRealRates() {
		when(this.rateProvider.currentRates()).thenReturn(RATES);

		assertThat(textOf(moneyCall("deposit", "10", "USD"))).isEqualTo("{\"currency\":\"USD\",\"balance\":\"10.00\"}");
		assertThat(textOf(moneyCall("deposit", "5", "eur"))).isEqualTo("{\"currency\":\"EUR\",\"balance\":\"5.00\"}");
		assertThat(textOf(moneyCall("withdraw", "3", "USD"))).isEqualTo("{\"currency\":\"USD\",\"balance\":\"7.00\"}");
		assertThat(textOf(call("balances", Map.of()))).isEqualTo(
				"{\"balances\":[{\"currency\":\"EUR\",\"amount\":\"5.00\"},{\"currency\":\"USD\",\"amount\":\"7.00\"}]}");
		assertThat(textOf(call("total", Map.of("currency", "USD")))).isEqualTo(
				"{\"currency\":\"USD\",\"total\":\"12.58\",\"rates\":{\"valuationDate\":\"2026-11-01\",\"tableDate\":\"2026-10-30\","
						+ "\"provisional\":false,\"stale\":false},\"breakdown\":[{\"currency\":\"EUR\",\"amount\":\"5.00\",\"converted\":\"5.58\"},"
						+ "{\"currency\":\"USD\",\"amount\":\"7.00\",\"converted\":\"7.00\"}]}");
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { "withdraw|1000|USD|The USD balance is too low for this withdrawal",
			"deposit|1.001|USD|USD allows at most 2 decimal places", "deposit|1|ABC|Not a valid ISO 4217 currency code",
			"deposit|1|XDR|Currency XDR is not supported by the wallet" })
	void aRejectedCallReturnsTheReasonOnce(String tool, String amount, String currency, String reason, CapturedOutput output) {
		CallToolResult result = moneyCall(tool, amount, currency);

		assertThat(result.isError()).isTrue();
		assertThat(textOf(result)).isEqualTo(reason);
		assertThat(output).contains("Tool call rejected: " + reason);
	}

	@Test
	void noUsableRatesIsAnError() {
		when(this.rateProvider.currentRates()).thenThrow(new RatesUnavailableException());
		moneyCall("deposit", "5", "EUR");

		CallToolResult result = call("total", Map.of("currency", "USD"));

		assertThat(result.isError()).isTrue();
		assertThat(textOf(result)).isEqualTo("No usable CNB exchange rates are available");
	}

	@Test
	void aHeldCurrencyMissingFromTheTableIsAnError() {
		Map<Currency, Rate> withoutEur = new HashMap<>(TABLE.rates());
		withoutEur.remove(Currency.getInstance("EUR"));
		when(this.rateProvider.currentRates())
			.thenReturn(new RatesView(new RateTable(TABLE.date(), withoutEur), TABLE.date(), false, false));
		moneyCall("deposit", "5", "EUR");

		CallToolResult result = call("total", Map.of("currency", "USD"));

		assertThat(result.isError()).isTrue();
		assertThat(textOf(result)).isEqualTo("CNB has no rate for EUR");
	}

	@Test
	void anUnexpectedErrorShowsNoInternalDetailsAndGoesToTheLog(CapturedOutput output) {
		when(this.rateProvider.currentRates()).thenThrow(new IllegalStateException("secret internal detail"));
		moneyCall("deposit", "5", "EUR");

		CallToolResult result = call("total", Map.of("currency", "USD"));

		assertThat(result.isError()).isTrue();
		assertThat(textOf(result)).isEqualTo("An unexpected error occurred");
		assertThat(output).contains("secret internal detail");
	}

	@Test
	void anAmountThatIsNotAStringMovesNoMoney() {
		for (Object amount : List.of(10, 10.5, true, List.of("10"))) {
			assertThat(call("deposit", Map.of("amount", amount, "currency", "USD")).isError()).isTrue();
		}

		assertThat(this.storage.snapshot()).isEmpty();
	}

	@Test
	void aMissingArgumentMovesNoMoney() {
		assertThat(call("deposit", Map.of("currency", "USD")).isError()).isTrue();
		assertThat(call("deposit", Map.of("amount", "1")).isError()).isTrue();

		assertThat(this.storage.snapshot()).isEmpty();
	}

	@Test
	void aMalformedRequestShowsNoStackTrace() throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + this.port + "/mcp"))
			.header("Content-Type", "application/json")
			.header("Accept", "application/json, text/event-stream")
			.POST(HttpRequest.BodyPublishers.ofString("{not json"))
			.build();

		HttpResponse<String> response;
		try (HttpClient http = HttpClient.newHttpClient()) {
			response = http.send(request, HttpResponse.BodyHandlers.ofString());
		}

		assertThat(response.statusCode()).isEqualTo(400);
		assertThat(response.body()).contains("Invalid message format").doesNotContain("stackTrace", "McpError");
	}

	@Test
	void moneyMovementsAreLogged(CapturedOutput output) {
		moneyCall("deposit", "10", "USD");

		assertThat(output).contains("Deposited 10.00 USD, new balance 10.00");
	}

	private CallToolResult moneyCall(String tool, String amount, String currency) {
		return call(tool, Map.of("amount", amount, "currency", currency));
	}

	private CallToolResult call(String tool, Map<String, Object> arguments) {
		return this.client.callTool(new CallToolRequest(tool, arguments));
	}

	private static String textOf(CallToolResult result) {
		return ((TextContent) result.content().getFirst()).text();
	}

}
