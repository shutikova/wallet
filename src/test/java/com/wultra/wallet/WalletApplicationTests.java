package com.wultra.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class WalletApplicationTests {

	@Autowired
	private MockMvc mvc;

	@Test
	void contextLoads() {
	}

	@Test
	void logsAreStructuredJson(CapturedOutput output) {
		LoggerFactory.getLogger(WalletApplicationTests.class).info("structured log check");

		assertThat(output).contains("\"message\":\"structured log check\"", "\"ecs\":{\"version\"");
	}

	@Test
	void healthIsUp() throws Exception {
		this.mvc.perform(get("/actuator/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "env", "beans", "configprops", "loggers", "heapdump" })
	void otherActuatorEndpointsAreNotExposed(String endpoint) throws Exception {
		this.mvc.perform(get("/actuator/" + endpoint)).andExpect(status().isNotFound());
	}

	@Test
	void swaggerUiIsAvailable() throws Exception {
		this.mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
		this.mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
	}

	@ParameterizedTest
	@ValueSource(strings = { "deposits", "withdrawals", "balances", "balances/total" })
	void openApiDescribesEveryEndpoint(String path) throws Exception {
		this.mvc.perform(get("/v3/api-docs"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.paths['/api/v1/wallet/" + path + "']").exists());
	}

}
