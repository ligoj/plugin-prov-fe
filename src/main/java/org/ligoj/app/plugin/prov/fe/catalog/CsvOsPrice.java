/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.fe.catalog;

import org.ligoj.app.plugin.prov.model.VmOs;
import org.ligoj.bootstrap.core.DescribedBean;

import lombok.Getter;
import lombok.Setter;

/**
 * Catalog entry for OS with block reading.
 */
public class CsvOsPrice extends DescribedBean<String> {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;
	@Getter
	@Setter
	private String product;

	@Getter
	@Setter
	private VmOs os;

	/**
	 * Extracted location code name.
	 */
	@Getter
	@Setter
	private String location;

	/**
	 * Extracted instance type code name.
	 */
	@Getter
	@Setter
	private String type;

	@Getter
	@Setter
	private String software;

	@Getter
	@Setter
	private Double cost1h;

	@Getter
	@Setter
	private Double cost1m;

	/**
	 * Ignored property
	 */
	@Setter
	private String drop;
}
