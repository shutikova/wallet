package com.wultra.wallet.configuration;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(title = "Multicurrency Wallet", version = "v1",
		description = "Deposit and withdraw in different currencies, and get the total in one currency with CNB rates"))
public class OpenApiConfiguration {

	// A total that converts nothing has rates: null, which annotations cannot express for a referenced schema.
	@Bean
	public OpenApiCustomizer ratesCanBeNull() {
		return openApi -> openApi.getComponents().getSchemas().get("RatesInfo").addType("null");
	}

}
