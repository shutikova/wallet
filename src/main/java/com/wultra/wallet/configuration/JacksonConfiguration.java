package com.wultra.wallet.configuration;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.StreamReadFeature;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

@Configuration
public class JacksonConfiguration {

	// By default Jackson would silently read the number 10 as the string "10", and let the last of two amount keys win
	// (see README, API).
	@Bean
	public JsonMapperBuilderCustomizer strictJsonRequests() {
		return builder -> builder.enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
			.withCoercionConfig(LogicalType.Textual, config -> config
				.setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
				.setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
				.setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail));
	}

}
