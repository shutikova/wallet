package com.wultra.wallet.model.response;

import java.time.LocalDate;
import java.util.List;

import com.wultra.wallet.model.ConvertedLine;
import com.wultra.wallet.model.RatesView;
import com.wultra.wallet.model.TotalValuation;
import io.swagger.v3.oas.annotations.media.Schema;

public record TotalResponse(@Schema(example = "USD") String currency, @Schema(example = "12.58") String total,
		@Schema(description = "Null when the total converts nothing and so uses no CNB rates") RatesInfo rates,
		List<BreakdownLine> breakdown) {

	public static TotalResponse from(TotalValuation valuation) {
		return new TotalResponse(valuation.currency().getCurrencyCode(), valuation.conversion().total().toPlainString(),
				(valuation.rates() != null) ? RatesInfo.from(valuation.rates()) : null,
				valuation.conversion().lines().stream().map(BreakdownLine::from).toList());
	}

	public record RatesInfo(@Schema(description = "Today in Europe/Prague") LocalDate valuationDate,
			@Schema(description = "Date of the CNB table that was used") LocalDate tableDate,
			@Schema(description = "Today is a working day and today's table is not in use yet") boolean provisional,
			@Schema(description = "The last attempt to get a newer table from CNB failed") boolean stale) {

		private static RatesInfo from(RatesView rates) {
			return new RatesInfo(rates.valuationDate(), rates.table().date(), rates.provisional(), rates.stale());
		}

	}

	public record BreakdownLine(@Schema(example = "EUR") String currency, @Schema(example = "5.00") String amount,
			@Schema(description = "Rounded for display, so the lines can differ from the total by a cent",
					example = "5.58") String converted) {

		private static BreakdownLine from(ConvertedLine line) {
			return new BreakdownLine(line.currency().getCurrencyCode(), line.amount().toPlainString(),
					line.converted().toPlainString());
		}

	}

}
