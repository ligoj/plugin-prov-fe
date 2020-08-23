/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.fe;

import java.util.Map;

import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.ligoj.app.plugin.prov.AbstractProvResource;
import org.ligoj.app.plugin.prov.ProvResource;
import org.ligoj.app.plugin.prov.catalog.ImportCatalogService;
import org.ligoj.app.plugin.prov.fe.catalog.FePriceImport;
import org.ligoj.bootstrap.resource.system.configuration.ConfigurationResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The provisioning service for Digital Ocean. There is complete quote configuration along the subscription.
 */
@Service
@Path(ProvFePluginResource.SERVICE_URL)
@Produces(MediaType.APPLICATION_JSON)
public class ProvFePluginResource extends AbstractProvResource implements ImportCatalogService {

	/**
	 * Plug-in Key shortcut
	 */
	public static final String PLUGIN_KEY = "service:prov:fe";

	/**
	 * Plug-in key.
	 */
	public static final String SERVICE_URL = ProvResource.SERVICE_URL + "/fe";

	/**
	 * Plug-in key.
	 */
	public static final String KEY = SERVICE_URL.replace('/', ':').substring(1);

	@Autowired
	protected FePriceImport priceImport;

	@Autowired
	protected ConfigurationResource configuration;

	@Override
	public String getKey() {
		return KEY;
	}

	@Override
	public String getVersion(final Map<String, String> parameters) {
		// Use API version as product version
		return "2";
	}

	@Override
	public boolean checkStatus(final Map<String, String> parameters) {
		// Status is UP <=> Administration access is UP (if defined)
		return true;
	}

	/**
	 * Fetch the prices from the DigitalOcean server. Install or update the prices
	 */
	@Override
	public void install() throws Exception {
		priceImport.install(false);
	}

	@Override
	public void updateCatalog(final String node, final boolean force) throws Exception {
		// Digital Ocean catalog is shared with all instances, require tool level access
		nodeResource.checkWritableNode(KEY);
		priceImport.install(force);
	}

	@Override
	public void create(final int subscription) {
		// Authenticate only for the check
	}
}
