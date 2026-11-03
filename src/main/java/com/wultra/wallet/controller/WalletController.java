package com.wultra.wallet.controller;

import com.wultra.wallet.model.request.MoneyOperationRequest;
import com.wultra.wallet.model.response.BalanceResponse;
import com.wultra.wallet.model.response.BalancesResponse;
import com.wultra.wallet.model.response.TotalResponse;
import com.wultra.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// With produces, Spring refuses a client that cannot read JSON before a method runs, so no money moves on a 406.
@RestController
@RequestMapping(path = "/api/v1/wallet", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Wallet", description = "Deposits, withdrawals and balances of the one wallet")
public class WalletController {

	private static final String PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON_VALUE;

	private final WalletService walletService;

	@PostMapping("/deposits")
	@Operation(summary = "Deposit money", description = "Adds the amount to the balance of that currency. Never needs CNB.")
	@ApiResponse(responseCode = "200", description = "New balance of that currency")
	@ApiResponse(responseCode = "400", description = "Malformed request, invalid amount or invalid currency code",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "422", description = "Currency is not supported by the wallet",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	public BalanceResponse deposit(@Valid @RequestBody MoneyOperationRequest request) {
		return BalanceResponse.from(this.walletService.deposit(request.amount(), request.currency()));
	}

	@PostMapping("/withdrawals")
	@Operation(summary = "Withdraw money",
			description = "Subtracts the amount from the balance of that currency. Never needs CNB.")
	@ApiResponse(responseCode = "200", description = "New balance of that currency")
	@ApiResponse(responseCode = "400", description = "Malformed request, invalid amount or invalid currency code",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "422", description = "Currency is not supported, or the balance is too low",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	public BalanceResponse withdraw(@Valid @RequestBody MoneyOperationRequest request) {
		return BalanceResponse.from(this.walletService.withdraw(request.amount(), request.currency()));
	}

	@GetMapping("/balances")
	@Operation(summary = "List balances", description = "All non-zero balances, sorted by currency code.")
	@ApiResponse(responseCode = "200", description = "All non-zero balances")
	public BalancesResponse balances() {
		return BalancesResponse.from(this.walletService.balances());
	}

	@GetMapping("/balances/total")
	@Operation(summary = "Get the total in one currency",
			description = "Converts all balances with CNB's exchange rates. A total that converts nothing needs no CNB.")
	@ApiResponse(responseCode = "200", description = "Total in the selected currency")
	@ApiResponse(responseCode = "400", description = "Missing or invalid currency code",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "422", description = "Currency is not supported, or CNB does not quote a needed rate",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	@ApiResponse(responseCode = "503", description = "No usable CNB rates",
			content = @Content(mediaType = PROBLEM_JSON, schema = @Schema(implementation = ProblemDetail.class)))
	public TotalResponse total(@Parameter(description = "ISO 4217 currency code in any letter case",
			example = "EUR") @RequestParam String currency) {
		return TotalResponse.from(this.walletService.total(currency));
	}

}
