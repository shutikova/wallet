package com.wultra.wallet.service.money;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Currency;
import java.util.HashMap;
import java.util.Map;

import com.wultra.wallet.model.Rate;

final class CnbFixture {

	private CnbFixture() {
	}

	// Reads a real CNB file: a date line, a header line, then Country|Currency|Amount|Code|Rate rows.
	static Map<Currency, Rate> rates(String fileName) {
		try (InputStream file = CnbFixture.class.getResourceAsStream("/cnb/" + fileName)) {
			Map<Currency, Rate> rates = new HashMap<>();
			new String(file.readAllBytes(), StandardCharsets.UTF_8).lines().skip(2).map(line -> line.split("\\|"))
				.forEach(row -> rates.put(Currency.getInstance(row[3]),
						new Rate(new BigDecimal(row[4]), Integer.parseInt(row[2]))));
			return rates;
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
