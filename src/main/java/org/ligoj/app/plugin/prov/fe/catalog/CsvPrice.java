/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.fe.catalog;

import org.ligoj.app.plugin.prov.model.ProvInstanceType;
import org.ligoj.app.plugin.prov.model.ProvLocation;
import org.ligoj.bootstrap.core.DescribedBean;

import lombok.Getter;
import lombok.Setter;

/**
 * Catalog entry for compute price with block reading.
 */
public class CsvPrice extends DescribedBean<String> {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;
	@Getter
	@Setter
	private String product;

	/**
	 * Resolved location.
	 */
	@Getter
	@Setter
	private ProvLocation location;

	@Getter
	@Setter
	private int cpu;
	@Getter
	@Setter
	private int ram;

	/**
	 * Resolved instance type.
	 */
	@Getter
	@Setter
	private ProvInstanceType type;

	@Getter
	@Setter
	private Double cost1h;

	@Getter
	@Setter
	private Double cost1m;

	@Getter
	@Setter
	private Double cost1yPerMonth;

	@Getter
	@Setter
	private Double cost1yUFFee;

	@Getter
	@Setter
	private Double cost1yUFPerMonth;

	@Getter
	@Setter
	private Double cost2yUFFee;

	@Getter
	@Setter
	private Double cost2yUFPerMonth;

	@Getter
	@Setter
	private Double cost3yPerMonth;

	@Getter
	@Setter
	private Double cost3yUFFee;

	@Getter
	@Setter
	private Double cost3yUFPerMonth;

	@Getter
	@Setter
	private Double cost5yPerMonth;

	@Getter
	@Setter
	private Double cost3yPerMonthConvertible;

	/**
	 * Used to retrieve the right term.
	 */
	@Getter
	@Setter
	private boolean convertible;

	/**
	 * Ignored property
	 */
	@Setter
	private String drop;
}
