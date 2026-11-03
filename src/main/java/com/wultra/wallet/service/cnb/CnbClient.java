package com.wultra.wallet.service.cnb;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import com.wultra.wallet.model.RateTable;
import com.wultra.wallet.model.error.CnbFetchException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
@RequiredArgsConstructor
public class CnbClient {

	private static final String DAILY_TABLE_URL = "https://www.cnb.cz/en/financial-markets/foreign-exchange-market/"
			+ "central-bank-exchange-rate-fixing/central-bank-exchange-rate-fixing/daily.txt?date={date}";

	private static final DateTimeFormatter REQUEST_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

	private final RestClient restClient;

	private final CnbParser parser;

	public RateTable fetchDaily(LocalDate date) {
		try {
			String body = this.restClient.get().uri(DAILY_TABLE_URL, REQUEST_DATE.format(date)).retrieve().body(String.class);
			RateTable table = this.parser.parse(body);
			// CNB answers with the latest table on or before the date, so a later one is a damaged response.
			if (table.date().isAfter(date)) {
				throw new CnbFetchException("CNB rate table is dated after the requested date");
			}
			return table;
		}
		catch (RestClientException ex) {
			throw new CnbFetchException("Cannot get the rate table from CNB", ex);
		}
	}

}
