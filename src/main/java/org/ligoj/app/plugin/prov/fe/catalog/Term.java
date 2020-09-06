/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.fe.catalog;

import org.ligoj.app.plugin.prov.model.ProvInstancePriceTerm;
import org.ligoj.bootstrap.core.NamedBean;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import lombok.Getter;
import lombok.Setter;

/**
 * A defined term.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Term extends NamedBean<String> {

	/**
	 * Default SID
	 */
	private static final long serialVersionUID = 1L;

	/**
	 * Period in month.
	 */
	@Getter
	@Setter
	private int period;

	/**
	 * Resolved price term entity
	 */
	@Getter
	@Setter
	private ProvInstancePriceTerm entity;

}
