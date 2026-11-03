package com.wultra.wallet.mcp;

import java.util.function.Supplier;

import com.wultra.wallet.model.error.InsufficientFundsException;
import com.wultra.wallet.model.error.InvalidAmountException;
import com.wultra.wallet.model.error.InvalidCurrencyException;
import com.wultra.wallet.model.error.RateNotAvailableException;
import com.wultra.wallet.model.error.RatesUnavailableException;
import com.wultra.wallet.model.error.UnsupportedCurrencyException;
import com.wultra.wallet.model.response.BalanceResponse;
import com.wultra.wallet.model.response.BalancesResponse;
import com.wultra.wallet.model.response.TotalResponse;
import com.wultra.wallet.service.WalletService;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

// Calls the same service as the REST API, so agents get the same rules. Errors are built here because the message
// of any thrown exception would otherwise reach the agent, including internal details.
@Component
@Slf4j
@RequiredArgsConstructor
public class WalletMcpTools {

	private static final String AMOUNT = "Plain decimal number as a string, such as \"10.50\", with at most the decimal places of the currency";

	private static final String CURRENCY = "ISO 4217 currency code, such as USD";

	private final WalletService walletService;

	private final JsonMapper jsonMapper;

	@McpTool(name = "deposit", description = "Adds money to the wallet and returns the new balance of that currency.")
	public CallToolResult deposit(@McpToolParam(description = AMOUNT) String amount,
			@McpToolParam(description = CURRENCY) String currency) {
		return run(() -> BalanceResponse.from(this.walletService.deposit(amount, currency)));
	}

	@McpTool(name = "withdraw", description = "Takes money from the wallet and returns the new balance of that currency.")
	public CallToolResult withdraw(@McpToolParam(description = AMOUNT) String amount,
			@McpToolParam(description = CURRENCY) String currency) {
		return run(() -> BalanceResponse.from(this.walletService.withdraw(amount, currency)));
	}

	@McpTool(name = "balances", description = "Lists all non-zero balances, sorted by currency code.",
			annotations = @McpTool.McpAnnotations(readOnlyHint = true))
	public CallToolResult balances() {
		return run(() -> BalancesResponse.from(this.walletService.balances()));
	}

	@McpTool(name = "total", description = "Converts all balances to one currency with Czech National Bank rates.",
			annotations = @McpTool.McpAnnotations(readOnlyHint = true))
	public CallToolResult total(@McpToolParam(description = CURRENCY) String currency) {
		return run(() -> TotalResponse.from(this.walletService.total(currency)));
	}

	private CallToolResult run(Supplier<Object> tool) {
		try {
			return CallToolResult.builder().addTextContent(this.jsonMapper.writeValueAsString(tool.get())).build();
		}
		catch (InvalidAmountException | InvalidCurrencyException | UnsupportedCurrencyException | InsufficientFundsException
				| RateNotAvailableException | RatesUnavailableException ex) {
			log.info("Tool call rejected: {}", ex.getMessage());
			return failure(ex.getMessage());
		}
		catch (RuntimeException ex) {
			log.error("Unexpected error", ex);
			return failure("An unexpected error occurred");
		}
	}

	private static CallToolResult failure(String message) {
		return CallToolResult.builder().isError(true).addTextContent(message).build();
	}

}
