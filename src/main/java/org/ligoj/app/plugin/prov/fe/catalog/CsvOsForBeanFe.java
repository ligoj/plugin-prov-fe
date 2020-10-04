/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.fe.catalog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.ligoj.app.plugin.prov.model.VmOs;
import org.ligoj.bootstrap.core.csv.AbstractCsvManager;
import org.ligoj.bootstrap.core.csv.CsvBeanReader;
import org.ligoj.bootstrap.core.csv.CsvReader;

import lombok.extern.slf4j.Slf4j;

/**
 * Read AWS EC2 CSV input, skipping the AWS headers and non instance type rows.
 */
@Slf4j
public class CsvOsForBeanFe extends AbstractCsvManager {

	private final CsvBeanReader<CsvOsPrice> beanReader;

	/**
	 * OS mode.
	 */
	private VmOs os;

	/**
	 * Software mode.
	 */
	private String software;

	/**
	 * CSV Mapping to Java bean property
	 */
	protected static final Map<String, String> HEADERS_MAPPING = new HashMap<>();
	static {
		HEADERS_MAPPING.put("product", "product");
		HEADERS_MAPPING.put("cost_h", "cost1h");
		HEADERS_MAPPING.put("cost_m", "cost1m");
	}

	private static final Pattern PATTERN_LICENCE = Pattern.compile("Licence (.*)\\s+\\(.*");

	/**
	 * Build the reader parsing the CSV file from AWS to build {@link AwsEc2Price} instances. Non AWS instances data are
	 * skipped, and headers are ignored.
	 *
	 * @param reader The original AWS CSV input.
	 * @throws IOException When CSV content cannot be read.
	 */
	public CsvOsForBeanFe(final BufferedReader reader) throws IOException {

		// Complete the standard mappings
		final var mMapping = new HashMap<>(HEADERS_MAPPING);
		final var csvReader = new CsvReader(reader);

		// The real CSV header has be reached
		this.beanReader = newCsvReader(reader,
				csvReader.read().stream().map(v -> mMapping.getOrDefault(v, "drop")).toArray(String[]::new));
	}

	protected CsvBeanReader<CsvOsPrice> newCsvReader(final Reader reader, final String[] headers) {
		return new AbstractFeCsvReader<>(reader, headers, CsvOsPrice.class) {

			@Override
			protected boolean isValidRaw(final List<String> rawValues) {
				return CsvOsForBeanFe.this.isValidRaw(rawValues);
			}
		};
	}

	private boolean isValidRaw(final List<String> rawValues) {
		if (!rawValues.isEmpty()) {
			// Check the convertible switch mode
			final var col0 = rawValues.get(0);
			final var matcher = PATTERN_LICENCE.matcher(col0);

			if (matcher.find()) {
				final var licPart = matcher.group(1).toUpperCase(Locale.ENGLISH);
				// New block
				if (licPart.contains("WINDOWS")) {
					os = VmOs.WINDOWS;
					software = null;// No supported software for Windows
				} else if (licPart.contains("REDHAT")) {
					os = VmOs.RHEL;
					software = null;// No supported software for RHEL
				} else if (licPart.contains("SUSE")) {
					os = VmOs.SUSE;
					if ((licPart.contains("SAP APPLICATIONS"))) {
						software = "SAP APPLICATIONS";
					} else if ((licPart.contains("SAP"))) {
						software = "SAP";
					} else {
						software = null;
					}
				} else {
					log.warn("Unsupported licence model {}", licPart);
				}
				return false;
			}
			if (StringUtils.equalsIgnoreCase(col0, "Produit")) {
				// Ignore original CSV headers
				return false;
			}
		}
		return rawValues.size() >= 7 && !rawValues.get(0).isBlank();
	}

	/**
	 * Return a list of JPA bean re ad from the given CSV input. Headers are expected.
	 *
	 * @return The bean read from the next CSV record.
	 * @throws IOException When the CSV record cannot be read.
	 */
	public CsvOsPrice read() throws IOException {
		final var entry = beanReader.read();

		// Forward the block data
		entry.setOs(os);
		entry.setSoftware(software);
		return entry;
	}
}
