/*
 * Licensed under MIT (https://github.com/ligoj/ligoj/blob/master/LICENSE)
 */
package org.ligoj.app.plugin.prov.fe.catalog;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.commons.io.input.BOMInputStream;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.ligoj.app.plugin.prov.catalog.AbstractImportCatalogResource;
import org.ligoj.app.plugin.prov.fe.ProvFePluginResource;
import org.ligoj.app.plugin.prov.model.ImportCatalogStatus;
import org.ligoj.app.plugin.prov.model.ProvInstancePrice;
import org.ligoj.app.plugin.prov.model.ProvInstancePriceTerm;
import org.ligoj.app.plugin.prov.model.ProvInstanceType;
import org.ligoj.app.plugin.prov.model.ProvLocation;
import org.ligoj.app.plugin.prov.model.ProvStoragePrice;
import org.ligoj.app.plugin.prov.model.ProvStorageType;
import org.ligoj.app.plugin.prov.model.ProvSupportPrice;
import org.ligoj.app.plugin.prov.model.ProvSupportType;
import org.ligoj.app.plugin.prov.model.ProvTenancy;
import org.ligoj.app.plugin.prov.model.Rate;
import org.ligoj.app.plugin.prov.model.VmOs;
import org.ligoj.bootstrap.core.INamableBean;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * The provisioning price service for Digital Ocean. Manage install or update of prices.<br>
 * Note about RI: Subscribing to a Reserved Instance is a pricing option and does not guarantee resource availability.
 * If your target flavor is not already in use, it is recommended to check the availability on the Console.
 * 
 * 
 * 
 * @see <a href=
 *      "https://cloud.orange-business.com/offres/infrastructure-iaas/flexible-engine/assistance-flexible-engine/comprendre-sa-facture">VM
 *      Billing</a>
 * @see <a href=
 *      "https://cloud.orange-business.com/wp-content/uploads/2020/06/Quote-Flexible-Engine-External.xlsx">Pricing
 *      sheet</a>
 * @see <a href="https://cloud.orange-business.com/nos-tarifs/elastic-cloud-server/">Pricing</a>
 * 
 * @see <a href=
 *      "https://cloud.orange-business.com/offres/infrastructure-iaas/flexible-engine/fonctionnalites/elastic-cloud-server/">Instance
 *      types</a>
 * 
 * @see <a href=
 *      "https://cloud.orange-business.com/wp-content/uploads/2019/03/Flexible-Engine-Service-Description_280619.pdf">Service
 *      Description</a>
 * @see <a href=
 *      "https://cloud.orange-business.com/wp-content/uploads/2019/11/flexible_engine-service_description.pdf">Service
 *      Description (new)</a>
 * 
 */
@Component
@Setter
@Slf4j
public class FePriceImport extends AbstractImportCatalogResource {

	private static final String NO_SOFTWARE = "DEF";

	/**
	 * Orange FE term option AKA "convertible" for AWS.
	 */
	private static final String FLEXIBLE_TERM = "flexible";

	/**
	 * Configuration key used for URL prices.
	 */
	protected static final String CONF_API_PRICES = ProvFePluginResource.KEY + ":prices-url";

	/**
	 * Configuration key used for enabled regions pattern names. When value is <code>null</code>, no restriction.
	 */
	protected static final String CONF_REGIONS = ProvFePluginResource.KEY + ":regions";

	/**
	 * Pattern of the production for compute and OS. Sample <code>Paris - t2.micro (1 vCPU, 1GB RAM)</code>
	 */
	private static final Pattern PRODUCT_PATTERN = Pattern.compile("^\\s*([^\\s]+)\\s*-\\s*([^\\s]+)\\s*\\(.*$");
	/**
	 * Default pricing URL.
	 */
	protected static final String DEFAULT_API_PRICES = "https://fe.ligoj.io";

	/**
	 * Name space for local configuration files
	 */
	protected static final String PREFIX = "fe";

	/**
	 * Configuration key used for enabled instance type pattern names. When value is <code>null</code>, no restriction.
	 */
	public static final String CONF_ITYPE = ProvFePluginResource.KEY + ":instance-type";

	/**
	 * Configuration key used for enabled OS pattern names. When value is <code>null</code>, no restriction.
	 */
	public static final String CONF_OS = ProvFePluginResource.KEY + ":os";

	protected static final TypeReference<Map<String, Term>> MAP_TERMS = new TypeReference<>() {
		// Nothing to extend
	};

	private String getPricesApi() {
		return configuration.get(CONF_API_PRICES, DEFAULT_API_PRICES);
	}

	@Override
	protected int getWorkload(final ImportCatalogStatus status) {
		return 5; // init + get catalog + vm + support+storage
	}

	/**
	 * Install or update prices.
	 *
	 * @param force When <code>true</code>, all cost attributes are update.
	 * @throws IOException When CSV or XML files cannot be read.
	 */
	public void install(final boolean force) throws IOException, URISyntaxException {
		final UpdateContext context = initContext(new UpdateContext(), ProvFePluginResource.KEY, force);
		final var node = context.getNode();

		// Get previous data
		nextStep(node, "initialize");
		context.setValidOs(Pattern.compile(configuration.get(CONF_OS, ".*"), Pattern.CASE_INSENSITIVE));
		context.setValidInstanceType(Pattern.compile(configuration.get(CONF_ITYPE, ".*"), Pattern.CASE_INSENSITIVE));
		context.setValidRegion(Pattern.compile(configuration.get(CONF_REGIONS, ".*")));
		context.getMapRegionToName().putAll(toMap("fe/regions.json", MAP_LOCATION));
		context.setInstanceTypes(itRepository.findAllBy(BY_NODE, node).stream()
				.collect(Collectors.toMap(ProvInstanceType::getCode, Function.identity())));
		context.setPriceTerms(iptRepository.findAllBy(BY_NODE, node).stream()
				.collect(Collectors.toMap(ProvInstancePriceTerm::getCode, Function.identity())));
		context.setStorageTypes(stRepository.findAllBy(BY_NODE, node).stream()
				.collect(Collectors.toMap(ProvStorageType::getCode, Function.identity())));
		context.setPreviousStorage(spRepository.findAllBy("type.node", node).stream()
				.collect(Collectors.toMap(ProvStoragePrice::getCode, Function.identity())));
		context.setSupportTypes(st2Repository.findAllBy(BY_NODE, node).stream()
				.collect(Collectors.toMap(ProvSupportType::getName, Function.identity())));
		context.setPreviousSupport(sp2Repository.findAllBy("type.node", node).stream()
				.collect(Collectors.toMap(ProvSupportPrice::getCode, Function.identity())));
		context.setRegions(locationRepository.findAllBy(BY_NODE, context.getNode()).stream()
				.filter(r -> isEnabledRegion(context, r))
				.collect(Collectors.toMap(INamableBean::getName, Function.identity())));
		context.setPrevious(ipRepository.findAllBy("term.node", node).stream()
				.collect(Collectors.toMap(ProvInstancePrice::getCode, Function.identity())));

		// Term definitions
		final var terms = toMap("fe/terms.json", MAP_TERMS);
		terms.entrySet().forEach(e -> {
			final var term = e.getValue();
			term.setId(e.getKey());
			term.setEntity(installPriceTerm(context, term));
		});
		context.setCsvTerms(terms);

		// Complete location description from "subRegion"
		context.getMapRegionToName().values().forEach(r -> r.setDescription(r.getSubRegion()));

		// Fetch the remote prices stream and build the price objects
		// Instances
		nextStep(node, "install-instances");
		// Install the specific prices

		// Read OS prices
		fetchOSPrices(context, StringUtils.removeEnd(getPricesApi(), "/") + "/prices/pricing-os.csv");

		// Read and install instance prices
		installInstancesPrices(context, StringUtils.removeEnd(getPricesApi(), "/") + "/prices/pricing-compute.csv");

		// Storages
		nextStep(node, "install-storages");
		// installStorage(context);

		// Support
		nextStep(node, "install-support");
		csvForBean.toBean(ProvSupportType.class, PREFIX + "/prov-support-type.csv").forEach(t -> {
			installSupportType(context, t.getCode(), t);
		});
		csvForBean.toBean(ProvSupportPrice.class, PREFIX + "/prov-support-price.csv").forEach(t -> {
			installSupportPrice(context, t.getCode(), t);
		});
	}

	/**
	 * Install or update a storage type.
	 */
	private ProvStorageType installStorageType(final UpdateContext context, final String code,
			final Consumer<ProvStorageType> aType) {
		final var type = context.getStorageTypes().computeIfAbsent(code, c -> {
			final var newType = new ProvStorageType();
			newType.setNode(context.getNode());
			newType.setCode(c);
			return newType;
		});

		return copyAsNeeded(context, type, t -> {
			t.setName(code /* human readable name */);
			t.setIncrement(null);
			t.setAvailability(99d);
			t.setMaximal(14901d);
			t.setMinimal(1d);
			aType.accept(t);
		}, stRepository);
	}

	/**
	 * Install or update a storage price.
	 */
	private void installStoragePrice(final UpdateContext context, final String region, final ProvStorageType type,
			final double cost) {
		final var price = context.getPreviousStorage().computeIfAbsent(region + "/" + type.getCode(), c -> {
			final var newPrice = new ProvStoragePrice();
			newPrice.setType(type);
			newPrice.setCode(c);
			return newPrice;
		});

		copyAsNeeded(context, price, p -> {
			p.setLocation(installRegion(context, region));
			p.setType(type);
		});

		// Update the cost
		saveAsNeeded(context, price, cost, spRepository);
	}

	/**
	 * Install a new instance price as needed.
	 */
	private void fetchOSPrices(final UpdateContext context, final String endpoint)
			throws MalformedURLException, IOException, URISyntaxException {
		// Track the created instance to cache partial costs
		log.info("FE OS import started@{} ...", endpoint);

		final var result = new HashMap<String, Map<String, Map<VmOs, Map<String, CsvOsPrice>>>>();
		context.setOsPrices(result);

		// Get the remote prices stream
		try (var reader = new BufferedReader(
				new InputStreamReader(new BOMInputStream(new URI(endpoint).toURL().openStream())))) {
			// Pipe to the CSV reader
			final var csvReader = new CsvOsForBeanFe(reader);

			// Build the AWS instance prices from the CSV
			var csv = csvReader.read();
			while (csv != null) {
				// Extract the instance type from the product
				// Sample : Paris - t2.micro (1 vCPU, 1GB RAM)
				final var matcher = PRODUCT_PATTERN.matcher(csv.getProduct());
				if (!matcher.find()) {
					// Ignore this line, maybe a CSV header
					return;
				}
				csv.setLocation(matcher.group(1));
				csv.setType(matcher.group(2));

				// Install the location name as needed
				// Install the type name as needed
				// Install the OS as needed
				// Install the Software as needed
				result.computeIfAbsent(csv.getLocation(), o -> new HashMap<>())
						.computeIfAbsent(csv.getType(), o -> new HashMap<>())
						.computeIfAbsent(csv.getOs(), o -> new HashMap<>())
						.put(StringUtils.defaultString(csv.getSoftware(), NO_SOFTWARE), csv);

				// Read the next one
				csv = csvReader.read();
			}
		} finally {
			// Report
			log.info("FE OS import finished: {} prices ({})", context.getPrices().size(),
					String.format("%+d", context.getPrices().size()));
		}
	}

	/**
	 * Install a new instance price as needed.
	 */
	private void installInstancesPrices(final UpdateContext context, final String endpoint)
			throws MalformedURLException, IOException, URISyntaxException {
		// Track the created instance to cache partial costs
		log.info("FE OnDemand/Reserved import started@{} ...", endpoint);

		// Get the remote prices stream
		try (var reader = new BufferedReader(
				new InputStreamReader(new BOMInputStream(new URI(endpoint).toURL().openStream())))) {
			// Pipe to the CSV reader
			final var csvReader = new CsvForBeanFe(reader);

			// Build the AWS instance prices from the CSV
			var csv = csvReader.read();
			while (csv != null) {
				installInstancePrices(context, csv);

				// Read the next one
				csv = csvReader.read();
			}
		} finally {
			// Report
			log.info("FE OnDemand/Reserved import finished: {} prices ({})", context.getPrices().size(),
					String.format("%+d", context.getPrices().size()));
		}
	}

	/**
	 * Return the location from its human readable name like <code>eu-west-0</code/>.
	 */
	private String getLocationFromName(final UpdateContext context, final String humanName) {
		return context.getMapRegionToName().entrySet().stream()
				.filter(r -> humanName.equalsIgnoreCase(r.getValue().getSubRegion())).map(r -> r.getKey()).findFirst()
				.orElse(humanName);
	}

	/**
	 * Install all instance price as needed. Each CSV entry contains several term prices.
	 */
	private void installInstancePrices(final UpdateContext context, final CsvPrice price) {
		final var matcher = PRODUCT_PATTERN.matcher(price.getProduct());
		if (!matcher.find()) {
			// Ignore this line, maybe a CSV header
			return;
		}

		// Install location
		final var humanName = matcher.group(1);
		final var location = installRegion(context, getLocationFromName(context, humanName));
		if (location == null) {
			// Unsupported region, or invalid row -> ignore
			return;
		}

		final var typeName = matcher.group(2);
		final var type = installInstanceType(context, typeName, price);
		if (type == null) {
			// Unsupported type, or invalid row -> ignore
			return;
		}

		if (price.isConvertible()) {
			// Convertible mode
			installInstancePrice(context, location, "ri-1y-" + FLEXIBLE_TERM, type, 1, price.getCost1yPerMonth(), 0d);
			installInstancePrice(context, location, "ri-1y-upfront-" + FLEXIBLE_TERM, type, 1,
					price.getCost1yUFPerMonth(), price.getCost1yUFFee());
			installInstancePrice(context, location, "ri-2y-upfront-" + FLEXIBLE_TERM, type, 1,
					price.getCost2yUFPerMonth(), price.getCost2yUFFee());
			installInstancePrice(context, location, "ri-3y-" + FLEXIBLE_TERM, type, 1, price.getCost3yPerMonth(), 0d);
			installInstancePrice(context, location, "ri-3y-upfront-" + FLEXIBLE_TERM, type, 1,
					price.getCost3yUFPerMonth(), price.getCost3yUFFee());
		} else {
			// Standard, non convertible price entry
			installInstancePrice(context, location, "on-demand", type, context.getHoursMonth(), price.getCost1h(), 0d);
			installInstancePrice(context, location, "on-demand-1m", type, 1, price.getCost1m(), 0d);
			installInstancePrice(context, location, "ri-1y", type, 1, price.getCost1yPerMonth(), 0d);
			installInstancePrice(context, location, "ri-3y", type, 1, price.getCost3yPerMonth(), 0d);
			installInstancePrice(context, location, "ri-5y", type, 1, price.getCost5yPerMonth(), 0d);
			installInstancePrice(context, location, "ri-1y-upfront", type, context.getHoursMonth(),
					price.getCost1yUFPerMonth(), price.getCost1yUFPerMonth());
			installInstancePrice(context, location, "ri-2y-upfront", type, context.getHoursMonth(), price.getCost1h(),
					price.getCost2yUFPerMonth());
			installInstancePrice(context, location, "ri-3y-upfront", type, context.getHoursMonth(), price.getCost1h(),
					price.getCost3yUFPerMonth());
		}

		// Handle extra CSV column for convertible (flexible) 3y
		if (price.getCost3yPerMonthConvertible() != null) {
			installInstancePrice(context, location, "ri-3y-" + FLEXIBLE_TERM, type, 1,
					price.getCost3yPerMonthConvertible(), 0d);
		}
	}

	private void installInstancePrice(final UpdateContext context, final ProvLocation region, final String termCode,
			final ProvInstanceType type, final double monthlyCoeff, final Double monthlyCostNoCoeff,
			final Double initialCost) {

		if (monthlyCostNoCoeff == null) {
			// Ignore this null price (not 0)
			return;
		}
		final var monthlyCost = monthlyCoeff * monthlyCostNoCoeff;
		final var term = installPriceTerm(context, context.getCsvTerms().get(termCode));

		// Get the OS/Software price from : location , type, OS, software
		final var localOsPrices = context.getOsPrices().getOrDefault(region.getName(), Collections.emptyMap())
				.getOrDefault(type.getCode(), Collections.emptyMap());
		localOsPrices.entrySet().forEach(localOsPrice -> {
			final VmOs os = localOsPrice.getKey();
			localOsPrice.getValue().entrySet().forEach(csvEntry -> {
				final String software;
				if (csvEntry.getKey().equals(NO_SOFTWARE)) {
					// only OS, no software for this price
					software = null;
				} else {
					software = csvEntry.getKey();
				}

				// Compute the total cost
				final var csvOsPrice = csvEntry.getValue();
				if (termCode.equals("on-demand")) {
					// On demand term is the most suitable for hourly cost
					installInstancePrice(context, region, term, os, software, type,
							monthlyCost + csvOsPrice.getCost1h() * context.getHoursMonth(), initialCost);
				} else {
					// Combine the monthly costs
					installInstancePrice(context, region, term, os, software, type,
							monthlyCost + csvOsPrice.getCost1m() * term.getPeriod(), initialCost);
				}
			});
		});

		// Basic Linux price without license
		installInstancePrice(context, region, term, VmOs.LINUX, null, type, monthlyCost, initialCost);

	}

	private void installInstancePrice(final UpdateContext context, final ProvLocation region,
			final ProvInstancePriceTerm term, final VmOs os, final String software, final ProvInstanceType type,
			final Double monthlyCost, final Double initialCost) {
		// Build the code string

		final var price = context.getPrevious().computeIfAbsent(
				String.join("/", region.getName(), term.getCode(), type.getCode(), os.name()).toLowerCase(), code -> {
					// New instance price (not update mode)
					final var newPrice = new ProvInstancePrice();
					newPrice.setCode(code);
					return newPrice;
				});

		// Save the price as needed
		copyAsNeeded(context, price, p -> {
			p.setLocation(region);
			p.setOs(os);
			p.setSoftware(software);
			p.setTerm(term);
			p.setTenancy(ProvTenancy.SHARED);
			p.setType(type);
			p.setPeriod(term.getPeriod());
		});

		// Update the cost
		context.getPrices().add(price.getCode());
		saveAsNeeded(context, price, price.getCost(), monthlyCost, (cR, c) -> {
			price.setInitialCost(initialCost);
			price.setCost(cR);
			price.setCostPeriod(round3Decimals(
					ObjectUtils.defaultIfNull(price.getInitialCost(), 0d) + c * price.getTerm().getPeriod()));
		}, ipRepository::save);

	}

	/**
	 * Install a new instance type as needed.
	 */
	private ProvInstanceType installInstanceType(final UpdateContext context, final String code, final CsvPrice price) {
		// Only enabled types
		if (!isEnabledType(context, code)) {
			return null;
		}

		final var type = context.getInstanceTypes().computeIfAbsent(code, c -> {
			// New instance type (not update mode)
			final var newType = new ProvInstanceType();
			newType.setNode(context.getNode());
			newType.setCode(c);
			return newType;
		});

		// Merge as needed
		return copyAsNeeded(context, type, t -> {
			final var instanceFamily = StringUtils.split(code, ".")[0];
			t.setName(code);
			t.setCpu(price.getCpu());
			t.setRam(price.getRam() * 1024);
			t.setConstant(!instanceFamily.startsWith("t"));
			t.setAutoScale(true);

			// See (out of date)
			// https://cloud.orange-business.com/offres/infrastructure-iaas/flexible-engine/fonctionnalites/elastic-cloud-server/
			t.setProcessor("Intel Xeon");

			// Rating CPU
			t.setCpuRate(Rate.MEDIUM);
			switch (instanceFamily.substring(0, 1)) {
			case "t" -> t.setCpuRate(Rate.WORST);
			}

			// Rating RAM
			t.setRamRate(Rate.MEDIUM);

			// Rating
			t.setNetworkRate(Rate.MEDIUM);
			switch (instanceFamily.substring(0, 1)) {
			case "t" -> t.setCpuRate(Rate.WORST);
			}
			t.setStorageRate(Rate.MEDIUM);
		}, itRepository);
	}

	/**
	 * Return the most precise rate from a base rate and a generation.
	 *
	 * @param rate The base rate.
	 * @param gen  The generation.
	 * @return The adjusted rate. Previous generations types are downgraded.
	 */
	protected Rate getRate(final Rate rate, final int gen, final Rate min) {
		// Downgrade the rate for a previous generation
		return Rate.values()[Math.max(min.ordinal(), rate.ordinal() - (5 - gen))];
	}

	/**
	 * Install a new price term as needed and complete the specifications.
	 */
	protected ProvInstancePriceTerm installPriceTerm(final UpdateContext context, final Term jsonTerm) {
		final var code = jsonTerm.getId().toLowerCase(Locale.ENGLISH);
		final var term = context.getPriceTerms().computeIfAbsent(code, t -> {
			final var newTerm = new ProvInstancePriceTerm();
			newTerm.setNode(context.getNode());
			newTerm.setCode(t);
			return newTerm;
		});

		// Complete the specifications
		return copyAsNeeded(context, term, t -> {
			t.setName(jsonTerm.getName());
			t.setPeriod(jsonTerm.getPeriod());
			t.setReservation(false);
			t.setConvertibleFamily(code.contains(FLEXIBLE_TERM));
			t.setConvertibleType(code.contains(FLEXIBLE_TERM));
			t.setConvertibleLocation(false);
			t.setConvertibleOs(true);
			t.setEphemeral(false);
		});
	}

	public void installSupportPrice(final UpdateContext context, final String code, final ProvSupportPrice aPrice) {
		final var price = context.getPreviousSupport().computeIfAbsent(code, c -> {
			// New instance price
			final ProvSupportPrice newPrice = new ProvSupportPrice();
			newPrice.setCode(c);
			return newPrice;
		});

		// Merge the support type details
		copyAsNeeded(context, price, p -> {
			p.setLimit(aPrice.getLimit());
			p.setMin(aPrice.getMin());
			p.setRate(aPrice.getRate());
			p.setType(aPrice.getType());
		});

		// Update the cost
		saveAsNeeded(context, price, price.getCost(), aPrice.getCost(), (cR, c) -> price.setCost(cR),
				sp2Repository::save);
	}

	private ProvSupportType installSupportType(final UpdateContext context, final String code,
			final ProvSupportType aType) {
		final var type = context.getSupportTypes().computeIfAbsent(code, c -> {
			var newType = new ProvSupportType();
			newType.setName(c);
			newType.setCode(c);
			newType.setNode(context.getNode());
			return newType;
		});

		// Merge the support type details
		type.setAccessApi(aType.getAccessApi());
		type.setAccessChat(aType.getAccessChat());
		type.setAccessEmail(aType.getAccessEmail());
		type.setAccessPhone(aType.getAccessPhone());
		type.setSlaStartTime(aType.getSlaStartTime());
		type.setSlaEndTime(aType.getSlaEndTime());
		type.setDescription(aType.getDescription());

		type.setSlaBusinessCriticalSystemDown(aType.getSlaBusinessCriticalSystemDown());
		type.setSlaGeneralGuidance(aType.getSlaGeneralGuidance());
		type.setSlaProductionSystemDown(aType.getSlaProductionSystemDown());
		type.setSlaProductionSystemImpaired(aType.getSlaProductionSystemImpaired());
		type.setSlaSystemImpaired(aType.getSlaSystemImpaired());
		type.setSlaWeekEnd(aType.isSlaWeekEnd());

		type.setCommitment(aType.getCommitment());
		type.setSeats(aType.getSeats());
		type.setLevel(aType.getLevel());
		st2Repository.save(type);
		return type;
	}

}
