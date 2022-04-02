/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.fe.catalog;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.ligoj.app.plugin.prov.quote.instance.QuoteInstanceQuery.builder;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;

import javax.annotation.PostConstruct;
import javax.transaction.Transactional;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.apache.http.HttpStatus;
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
import org.ligoj.app.plugin.prov.AbstractLookup;
import org.ligoj.app.plugin.prov.ProvResource;
import org.ligoj.app.plugin.prov.QuoteVo;
import org.ligoj.app.plugin.prov.catalog.AbstractImportCatalogResource;
import org.ligoj.app.plugin.prov.catalog.ImportCatalogResource;
import org.ligoj.app.plugin.prov.dao.ProvQuoteRepository;
import org.ligoj.app.plugin.prov.fe.ProvFePluginResource;
import org.ligoj.app.plugin.prov.model.ProvLocation;
import org.ligoj.app.plugin.prov.model.ProvQuote;
import org.ligoj.app.plugin.prov.model.ProvQuoteInstance;
import org.ligoj.app.plugin.prov.model.ProvQuoteStorage;
import org.ligoj.app.plugin.prov.model.ProvStorageOptimized;
import org.ligoj.app.plugin.prov.model.ProvTenancy;
import org.ligoj.app.plugin.prov.model.ProvUsage;
import org.ligoj.app.plugin.prov.model.Rate;
import org.ligoj.app.plugin.prov.model.SupportType;
import org.ligoj.app.plugin.prov.model.VmOs;
import org.ligoj.app.plugin.prov.quote.instance.ProvQuoteInstanceResource;
import org.ligoj.app.plugin.prov.quote.instance.QuoteInstanceEditionVo;
import org.ligoj.app.plugin.prov.quote.support.ProvQuoteSupportResource;
import org.ligoj.bootstrap.resource.system.configuration.ConfigurationResource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

/**
 * Test class of {@link FePriceImport}
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = "classpath:/META-INF/spring/application-context-test.xml")
@Rollback
@Transactional
class ProvFePriceImportTest extends AbstractServerTest {

	private static final double DELTA = 0.001;

	private FePriceImport resource;

	@Autowired
	private ProvResource provResource;

	@Autowired
	private ProvQuoteInstanceResource qiResource;

	@Autowired
	private ProvQuoteSupportResource qs2Resource;

	@Autowired
	private ProvQuoteRepository repository;

	@Autowired
	private ConfigurationResource configuration;

	protected int subscription;

	@BeforeEach
	void prepareData() throws IOException {
		persistSystemEntities();
		persistEntities("csv",
				new Class[] { Node.class, Project.class, CacheCompany.class, CacheUser.class, DelegateNode.class,
						Parameter.class, ProvLocation.class, Subscription.class, ParameterValue.class,
						ProvQuote.class },
				StandardCharsets.UTF_8.name());
		this.subscription = getSubscription("gStack");

		// Mock catalog import helper
		final var helper = new ImportCatalogResource();
		applicationContext.getAutowireCapableBeanFactory().autowireBean(helper);
		this.resource = initCatalog(helper, new FePriceImport());

		clearAllCache();
		initSpringSecurityContext(DEFAULT_USER);
		resetImportTask();

		final var usage12 = new ProvUsage();
		usage12.setName("12month");
		usage12.setRate(100);
		usage12.setDuration(12);
		usage12.setConfiguration(repository.findBy("subscription.id", subscription));
		em.persist(usage12);

		final var usage36 = new ProvUsage();
		usage36.setName("36month");
		usage36.setRate(100);
		usage36.setDuration(36);
		usage36.setConfiguration(repository.findBy("subscription.id", subscription));
		em.persist(usage36);

		final var usage36Convertible = new ProvUsage();
		usage36Convertible.setName("36month-convertible");
		usage36Convertible.setRate(100);
		usage36Convertible.setDuration(36);
		usage36Convertible.setConvertibleFamily(true);
		usage36Convertible.setConfiguration(repository.findBy("subscription.id", subscription));
		em.persist(usage36Convertible);

		final var usageDev = new ProvUsage();
		usageDev.setName("dev");
		usageDev.setRate(30);
		usageDev.setDuration(1);
		usageDev.setConfiguration(repository.findBy("subscription.id", subscription));
		em.persist(usageDev);
		em.flush();
		em.clear();
	}

	private <T extends AbstractImportCatalogResource> T initCatalog(ImportCatalogResource importHelper, T catalog) {
		applicationContext.getAutowireCapableBeanFactory().autowireBean(catalog);
		catalog.setImportCatalogResource(importHelper);
		MethodUtils.getMethodsListWithAnnotation(catalog.getClass(), PostConstruct.class).forEach(m -> {
			try {
				m.invoke(catalog);
			} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
				// Ignore;
			}
		});
		return catalog;
	}

	private void resetImportTask() {
		this.resource.getImportCatalogResource().endTask("service:prov:fe", false);
		this.resource.getImportCatalogResource().startTask("service:prov:fe", t -> {
			t.setLocation(null);
			t.setNbInstancePrices(null);
			t.setNbInstanceTypes(null);
			t.setNbStorageTypes(null);
			t.setWorkload(0);
			t.setDone(0);
			t.setPhase(null);
		});
	}

	@Test
	void installOffLineKoPrices() throws Exception {
		configuration.put(FePriceImport.CONF_API_PRICES, "http://localhost:" + MOCK_PORT);
		httpServer.start();
		Assertions.assertThrows(FileNotFoundException.class, () -> resource.install(false));
	}

	private void assertLookup(final String code, AbstractLookup<?> lookup, double cost) {
		Assertions.assertEquals(code, lookup.getPrice().getCode());
		Assertions.assertEquals(cost, lookup.getCost(), DELTA);
	}

	@Test
	void installOffLine() throws Exception {
		// Install a new configuration
		final var quote = install();

		// Check the whole quote
		check(quote, 1238.27d, 2476.54d, 1238.27d);
		checkImportStatus();

		// Check the 3 years term
		var lookup = qiResource.lookup(subscription,
				builder().cpu(7).ram(1741).constant(true).usage("36month").build());
		assertLookup("eu-west-2/ri-3y/linux/tinav1.cxry.high", lookup, 29.2);
		Assertions.assertEquals(0, lookup.getPrice().getCost()); // Dynamic, per vCpu price
		Assertions.assertEquals(0, lookup.getPrice().getCostPeriod()); // Dynamic
		Assertions.assertEquals(36.0, lookup.getPrice().getPeriod(), DELTA);

		// SQL Server
		lookup = qiResource.lookup(subscription,
				builder().cpu(3).ram(8000).os(VmOs.WINDOWS).software("sql server web").build());
		assertLookup("eu-west-2/ri-1m/windows/tinav1.cxry.medium/sql server web", lookup, 205.296d);

		// Oracle Linux
		lookup = qiResource.lookup(subscription, builder().cpu(3).ram(8000).os(VmOs.ORACLE).build());
		assertLookup("eu-west-2/ri-1m/oracle/tinav1.cxry.medium", lookup, 190.968d);
		checkImportStatus();

		// Check the support
		Assertions.assertEquals(0, qs2Resource
				.lookup(subscription, 0, SupportType.ALL, SupportType.ALL, SupportType.ALL, SupportType.ALL, Rate.BEST)
				.size());

		final var lookupSu = qs2Resource
				.lookup(subscription, 0, null, SupportType.ALL, SupportType.ALL, SupportType.ALL, Rate.BEST).get(0);
		assertLookup("fe-excellence", lookupSu, 5000.0d);

		// Install again to check the update without change
		resetImportTask();
		resource.install(false);
		provResource.updateCost(subscription);
		check(provResource.getConfiguration(subscription), 222.428d, 444.856d, 211.428d);
		checkImportStatus();

		// Now, change a price within the remote catalog

		// Point to another catalog with different prices
		configuration.put(FePriceImport.CONF_API_PRICES, "http://localhost:" + MOCK_PORT + "/v2");

		// Install the new catalog, update occurs
		resetImportTask();
		resource.install(false);
		provResource.updateCost(subscription);
		checkImportStatus();

		// Check the new prices
		lookup = qiResource.lookup(subscription, builder().cpu(7).ram(1741).constant(true).usage("36month").build());
		assertLookup("eu-west-2/ri-3y/linux/tinav1.cxry.high", lookup, 30.514);
		lookup = qiResource.lookup(subscription,
				builder().cpu(3).ram(8000).os(VmOs.WINDOWS).software("sql server web").build());
		assertLookup("eu-west-2/ri-1m/windows/tinav1.cxry.medium/sql server web", lookup, 208.376);
		lookup = qiResource.lookup(subscription, builder().cpu(3).ram(8000).os(VmOs.ORACLE).build());
		assertLookup("eu-west-2/ri-1m/oracle/tinav2.cxry.medium", lookup, 194.034d);
	}

	private void checkImportStatus() {
		final var status = this.resource.getImportCatalogResource().getTask("service:prov:fe");
		Assertions.assertEquals(4, status.getDone());
		Assertions.assertEquals(4, status.getWorkload());
		Assertions.assertEquals("install-support", status.getPhase());
		Assertions.assertEquals(DEFAULT_USER, status.getAuthor());
		Assertions.assertTrue(status.getNbInstancePrices().intValue() >= 100);
		Assertions.assertTrue(status.getNbInstanceTypes().intValue() >= 15);
		Assertions.assertTrue(status.getNbLocations() >= 1);
		Assertions.assertTrue(status.getNbStorageTypes().intValue() >= 3);
	}

	private void mockServer() throws IOException {
		configuration.put(FePriceImport.CONF_API_PRICES, "http://localhost:" + MOCK_PORT);
		httpServer.stubFor(get(urlEqualTo("/prices/pricing-compute.csv"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils.toString(
						new ClassPathResource("mock-server/fe/pricing-compute.csv").getInputStream(), "UTF-8"))));
		httpServer.stubFor(get(urlEqualTo("/prices/pricing-os.csv"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils
						.toString(new ClassPathResource("mock-server/fe/pricing-os.csv").getInputStream(), "UTF-8"))));
		httpServer.stubFor(get(urlEqualTo("/v2/prices/pricing-compute.csv"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils.toString(
						new ClassPathResource("mock-server/fe/v2/pricing-compute.csv").getInputStream(), "UTF-8"))));
		httpServer.stubFor(get(urlEqualTo("/v2/prices/pricing-os.csv"))
				.willReturn(aResponse().withStatus(HttpStatus.SC_OK).withBody(IOUtils.toString(
						new ClassPathResource("mock-server/fe/v2/pricing-os.csv").getInputStream(), "UTF-8"))));
		httpServer.start();
	}

	private ProvQuoteInstance check(final QuoteVo quote, final double minCost, final double maxCost,
			final double instanceCost) {
		Assertions.assertEquals(minCost, quote.getCost().getMin(), DELTA);
		Assertions.assertEquals(maxCost, quote.getCost().getMax(), DELTA);
		// TODO checkStorage(quote.getStorages().get(0));
		return checkInstance(quote.getInstances().get(0), instanceCost);
	}

	private ProvQuoteInstance checkInstance(final ProvQuoteInstance instance, final double cost) {
		Assertions.assertEquals(cost, instance.getCost(), DELTA);
		final var price = instance.getPrice();
		Assertions.assertEquals("eu-west-0/ri-3y-flexible/p2.2xlarge.8/linux", price.getCode());
		Assertions.assertEquals(0, price.getInitialCost());
		Assertions.assertEquals(VmOs.LINUX, price.getOs());
		Assertions.assertEquals(ProvTenancy.SHARED, price.getTenancy());
		Assertions.assertEquals(36, price.getPeriod());
		Assertions.assertEquals(1238.27d, price.getCost());
		Assertions.assertEquals(44577.72d, price.getCostPeriod());
		Assertions.assertNull(price.getSoftware());
		final var term = price.getTerm();
		Assertions.assertEquals("ri-3y-flexible", term.getCode());
		Assertions.assertEquals("Reserved, 3yr, flexible", term.getName());
		Assertions.assertFalse(term.isEphemeral());
		Assertions.assertEquals(36, term.getPeriod());
		Assertions.assertEquals("p2.2xlarge.8", price.getType().getCode());
		Assertions.assertEquals("p2.2xlarge.8", price.getType().getName());
		Assertions.assertNull(price.getType().getDescription());
		Assertions.assertEquals("Intel Xeon", price.getType().getProcessor());
		Assertions.assertTrue(price.getType().isAutoScale());
		return instance;
	}

	private ProvQuoteStorage checkStorage(final ProvQuoteStorage storage) {
		Assertions.assertEquals(11d, storage.getCost(), DELTA);
		Assertions.assertEquals(100, storage.getSize(), DELTA);
		Assertions.assertNotNull(storage.getQuoteInstance());
		final var type = storage.getPrice().getType();
		Assertions.assertEquals("bsu-gp2", type.getCode());
		Assertions.assertEquals("Performance", type.getName());
		Assertions.assertEquals(10000, type.getIops());
		Assertions.assertEquals(160, type.getThroughput());
		Assertions.assertEquals(0d, storage.getPrice().getCostTransaction(), DELTA);
		Assertions.assertEquals(1, type.getMinimal());
		Assertions.assertEquals(14901, type.getMaximal().intValue());
		Assertions.assertEquals(Rate.BEST, type.getLatency());
		Assertions.assertEquals(ProvStorageOptimized.IOPS, type.getOptimized());
		return storage;
	}

	/**
	 * Common offline inystall and configuring an instance
	 *
	 * @return The new quote from the installed
	 */
	private QuoteVo install() throws Exception {
		mockServer();

		// Check the basic quote
		return installAndConfigure(false);
	}

	@Test
	void installOnLine() throws Exception {
		configuration.delete(FePriceImport.CONF_API_PRICES);
		configuration.put(FePriceImport.CONF_ITYPE, "(t2|g1|p2).*");
		configuration.put(FePriceImport.CONF_OS, "(WINDOWS|LINUX)");
		configuration.put(FePriceImport.CONF_REGIONS, "(eu-west-0|na-east-0)");
		installAndConfigure(true);
	}

	/**
	 * Install and check
	 */
	private QuoteVo installAndConfigure(final boolean online) throws IOException, Exception {
		resource.install(false);
		em.flush();
		em.clear();
		Assertions.assertEquals(0, provResource.getConfiguration(subscription).getCost().getMin(), DELTA);
		// em.createQuery("FROM ProvInstancePriceTerm").getResultList();
		// em.createQuery("FROM ProvLocation").getResultList();

		// Request an instance for a specific OS
		var lookup = qiResource.lookup(subscription,
				builder().cpu(8).ram(12000).os(VmOs.LINUX).location("eu-west-0").usage("36month").build());
		if (online) {
			Assertions.assertEquals("eu-west-0/ri-3y/p2.2xlarge.8/linux", lookup.getPrice().getCode());
		} else {
			Assertions.assertEquals("eu-west-0/ri-3y/p2.2xlarge.8/linux", lookup.getPrice().getCode());
		}
		Assertions.assertTrue(lookup.getPrice().getType().getConstant());

		// Request convertible
		lookup = qiResource.lookup(subscription,
				builder().cpu(8).ram(12000).os(VmOs.LINUX).location("eu-west-0").usage("36month-convertible").build());
		if (online) {
			Assertions.assertEquals("eu-west-0/ri-3y-flexible/p2.2xlarge.8/linux", lookup.getPrice().getCode());
		} else {
			Assertions.assertEquals("eu-west-0/ri-3y-flexible/p2.2xlarge.8/linux", lookup.getPrice().getCode());
		}

		// New instance LINUX
		var ivo = new QuoteInstanceEditionVo();
		ivo.setCpu(8d);
		ivo.setRam(12000);
		ivo.setLocation("eu-west-0");
		ivo.setPrice(lookup.getPrice().getId());
		ivo.setName("server1");
		ivo.setMaxQuantity(2);
		ivo.setSubscription(subscription);
		var createInstance = qiResource.create(ivo);
		Assertions.assertTrue(createInstance.getTotal().getMin() > 1);
		Assertions.assertTrue(createInstance.getId() > 0);

		em.flush();
		em.clear();
		return provResource.getConfiguration(subscription);
	}

	/**
	 * Return the subscription identifier of the given project. Assumes there is only one subscription for a service.
	 */
	private int getSubscription(final String project) {
		return getSubscription(project, ProvFePluginResource.KEY);
	}
}
