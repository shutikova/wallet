package com.wultra.wallet.service.cnb;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

import com.wultra.wallet.model.RateTable;

/**
 * Reads the real CNB files in {@code src/test/resources/cnb}.
 */
public final class CnbFiles {

	private CnbFiles() {
	}

	public static String read(String fileName) {
		try (InputStream file = CnbFiles.class.getResourceAsStream("/cnb/" + fileName)) {
			if (file == null) {
				throw new IllegalArgumentException("No CNB test file named " + fileName);
			}
			return new String(file.readAllBytes(), StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	public static RateTable table(String fileName) {
		return new CnbParser().parse(read(fileName));
	}

}
