package com.wultra.wallet.configuration;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

import com.wultra.wallet.service.cnb.CzechHolidayCalendar;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class RatesConfiguration {

	@Bean
	public Clock clock() {
		return Clock.system(CzechHolidayCalendar.PRAGUE);
	}

	@Bean
	public RestClient cnbRestClient(@Value("${wallet.cnb.connect-timeout}") Duration connectTimeout,
			@Value("${wallet.cnb.read-timeout}") Duration readTimeout) {
		HttpClient httpClient = HttpClient.newBuilder()
			.connectTimeout(connectTimeout)
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(readTimeout);
		return RestClient.builder().requestFactory(requestFactory).build();
	}

}
