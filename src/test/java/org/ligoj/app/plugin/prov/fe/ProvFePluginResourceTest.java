/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.fe;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import javax.transaction.Transactional;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.ligoj.app.AbstractServerTest;
import org.ligoj.app.iam.model.CacheCompany;
import org.ligoj.app.iam.model.CacheUser;
import org.ligoj.app.model.DelegateNode;
import org.ligoj.app.model.Node;
import org.ligoj.app.model.Parameter;
import org.ligoj.app.model.ParameterValue;
import org.ligoj.app.model.Project;
import org.ligoj.app.model.Subscription;
import org.ligoj.app.plugin.prov.fe.catalog.FePriceImport;
import org.ligoj.app.plugin.prov.model.ProvLocation;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.resource.subscription.SubscriptionResource;
import org.ligoj.bootstrap.core.resource.BusinessException;
import org.ligoj.bootstrap.resource.system.configuration.ConfigurationResource;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Test class of {@link ProvFePluginResource}
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
class ProvFePluginResourceTest extends AbstractServerTest {

	protected int subscription;

	@Autowired
	private ProvFePluginResource resource;

	@Autowired
	private SubscriptionResource subscriptionResource;

	@Autowired
	private ConfigurationResource configuration;

	@BeforeEach
	void prepareData() throws IOException {
		persistSystemEntities();
		persistEntities("csv",
				new Class[] { Node.class, Project.class, CacheCompany.class, CacheUser.class, DelegateNode.class,
						Subscription.class, ProvLocation.class, ProvQuote.class, Parameter.class,
						ParameterValue.class },
				StandardCharsets.UTF_8.name());
		configuration.put("service:prov:fe:api", "http://localhost:" + MOCK_PORT + "/");
		this.subscription = getSubscription("gStack");

		// Invalidate fe cache
		cacheManager.getCache("curl-tokens").clear();
	}

	@Test
	void getKey() {
		Assertions.assertEquals("service:prov:fe", resource.getKey());
	}

	@Test
	void install() throws Exception {
		final ProvFePluginResource resource2 = new ProvFePluginResource();
		resource2.priceImport = Mockito.mock(FePriceImport.class);
		resource2.install();
	}

	@Test
	void updateCatalog() throws Exception {
		// Re-Install a new configuration
		final var resource2 = new ProvFePluginResource();
		super.applicationContext.getAutowireCapableBeanFactory().autowireBean(resource2);
		resource2.priceImport = Mockito.mock(FePriceImport.class);
		resource2.updateCatalog("service:prov:fe:test", false);
	}

	@Test
	void updateCatalogNoRight() {
		initSpringSecurityContext("any");

		// Re-Install a new configuration
		Assertions.assertEquals("read-only-node", Assertions.assertThrows(BusinessException.class, () -> {
			resource.updateCatalog("service:prov:fe:test", false);
		}).getMessage());
	}

	@Test
	void create() throws Exception {
		resource.create(subscription);
	}

	/**
	 * Return the subscription identifier of the given project. Assumes there is only one subscription for a service.
	 */
	private int getSubscription(final String project) {
		return getSubscription(project, ProvFePluginResource.KEY);
	}

	@Test
	void checkStatus() throws Exception {
		Assertions.assertTrue(resource.checkStatus(subscriptionResource.getParametersNoCheck(subscription)));
	}

	@Test
	void getVersion() throws Exception {
		Assertions.assertEquals("2", resource.getVersion(subscription));
	}

}
