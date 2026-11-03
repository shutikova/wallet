package com.wultra.wallet.model.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record MoneyOperationRequest(
		@Schema(description = "Plain decimal number as a string, with at most the decimal places of the currency",
				example = "10.50") @NotNull String amount,
		@Schema(description = "ISO 4217 currency code in any letter case", example = "USD") @NotNull String currency) {
}
