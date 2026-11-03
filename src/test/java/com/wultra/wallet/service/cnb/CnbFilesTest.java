package com.wultra.wallet.service.cnb;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CnbFilesTest {

	@Test
	void aMissingFileIsNamedInTheError() {
		assertThatThrownBy(() -> CnbFiles.read("missing.txt")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("missing.txt");
	}

}
