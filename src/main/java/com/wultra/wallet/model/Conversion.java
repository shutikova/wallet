package com.wultra.wallet.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

public record Conversion(BigDecimal total, List<ConvertedLine> lines) {

	public Conversion {
		lines = List.copyOf(lines);
	}

	// Compares by value, because the generated equals would treat 7.0 and 7.00 as different.
	@Override
	public boolean equals(Object other) {
		return other instanceof Conversion conversion && this.total.compareTo(conversion.total) == 0
				&& this.lines.equals(conversion.lines);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.total.stripTrailingZeros(), this.lines);
	}

}
