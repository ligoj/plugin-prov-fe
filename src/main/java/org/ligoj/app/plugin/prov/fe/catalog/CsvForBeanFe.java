/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.fe.catalog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.ligoj.bootstrap.core.csv.AbstractCsvManager;
import org.ligoj.bootstrap.core.csv.CsvBeanReader;
import org.ligoj.bootstrap.core.csv.CsvReader;

/**
 * Read AWS EC2 CSV input, skipping the AWS headers and non instance type rows.
 */
public class CsvForBeanFe extends AbstractCsvManager {

	private final CsvBeanReader<CsvPrice> beanReader;

	/**
	 * Convertible mode.
	 */
	private boolean convertible = false;

	/**
	 * CSV Mapping to Java bean property
	 */
	protected static final Map<String, String> HEADERS_MAPPING = new HashMap<>();
	static {
		HEADERS_MAPPING.put("product", "product");
		HEADERS_MAPPING.put("cpu", "cpu");
		HEADERS_MAPPING.put("ram (GB)", "ram");
		HEADERS_MAPPING.put("cost_h", "cost1h");
		HEADERS_MAPPING.put("cost_m", "cost1m");
		HEADERS_MAPPING.put("cost_m_1y_no_upfront", "cost1yPerMonth");
		HEADERS_MAPPING.put("cost_1y_upfront_fees", "cost1yUFFee");
		HEADERS_MAPPING.put("cost_m_1y_upfront", "cost1yUFPerMonth");
		HEADERS_MAPPING.put("cost_2y_upfront_fees", "cost2yUFFee");
		HEADERS_MAPPING.put("cost_m_2y_upfront", "cost2yUFPerMonth");
		HEADERS_MAPPING.put("cost_m_3y_no_upfront", "cost3yPerMonth");
		HEADERS_MAPPING.put("cost_3y_upfront_fees", "cost3yUFFee");
		HEADERS_MAPPING.put("cost_m_3y_upfront", "cost3yUFPerMonth");
		HEADERS_MAPPING.put("cost_m_5y_no_upfront", "cost5yPerMonth");
		HEADERS_MAPPING.put("cost_m_3y_convertible", "cost3yPerMonthConvertible");

	}

	/**
	 * Build the reader parsing the CSV file from AWS to build {@link AwsEc2Price} instances. Non AWS instances data are
	 * skipped, and headers are ignored.
	 *
	 * @param reader The original AWS CSV input.
	 * @throws IOException When CSV content cannot be read.
	 */
	public CsvForBeanFe(final BufferedReader reader) throws IOException {

		// Complete the standard mappings
		final var mMapping = new HashMap<>(HEADERS_MAPPING);
		final var csvReader = new CsvReader(reader);

		// The real CSV header has be reached
		this.beanReader = newCsvReader(reader,
				csvReader.read().stream().map(v -> mMapping.getOrDefault(v, "drop")).toArray(String[]::new));
	}

	protected CsvBeanReader<CsvPrice> newCsvReader(final Reader reader, final String[] headers) {
		return new AbstractFeCsvReader<>(reader, headers, CsvPrice.class) {

			@Override
			protected boolean isValidRaw(final List<String> rawValues) {
				return CsvForBeanFe.this.isValidRaw(rawValues);
			}

		};
	}

	private boolean isValidRaw(final List<String> rawValues) {
		if (!rawValues.isEmpty()) {
			// Check the convertible switch mode
			final var col0 = rawValues.get(0);
			if (StringUtils.containsIgnoreCase(col0, "Flexible Elastic Cloud Serve")) {
				// Encounter the convertible "ECS" switch
				convertible = true;
			} else if (StringUtils.containsIgnoreCase(col0, "ECS - Orange Business Services Compute")) {
				// Encounter the not convertible "ECS" switch
				convertible = false;
			}
			if (StringUtils.equalsIgnoreCase(col0, "Produit")) {
				// Ignore original CSV headers
				return false;
			}
		}
		if (rawValues.size() < 19 || !NumberUtils.isDigits(rawValues.get(1))) {
			return false;
		}

		// Sanitize amounts
		rawValues.set(1, rawValues.get(1).replaceAll("[^\\d,]", ""));
		rawValues.set(2, rawValues.get(2).replaceAll("[^\\d,]", ""));
		return true;
	}

	/**
	 * Return a list of JPA bean re ad from the given CSV input. Headers are expected.
	 *
	 * @return The bean read from the next CSV record. Return <code>null</code> when the EOF is reached.
	 * @throws IOException When the CSV record cannot be read.
	 */
	public CsvPrice read() throws IOException {
		final var entry = beanReader.read();

		// Forward the convertible mode to this new CSV entry
		if (entry != null) {
			entry.setConvertible(convertible);
		}
		return entry;
	}
}
