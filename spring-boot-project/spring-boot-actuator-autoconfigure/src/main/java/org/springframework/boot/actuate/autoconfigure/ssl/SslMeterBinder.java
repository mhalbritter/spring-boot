/*
 * Copyright 2012-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.boot.actuate.autoconfigure.ssl;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.MultiGauge;
import io.micrometer.core.instrument.MultiGauge.Row;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.binder.MeterBinder;

import org.springframework.boot.info.SslInfo;
import org.springframework.boot.info.SslInfo.BundleInfo;
import org.springframework.boot.info.SslInfo.CertificateChainInfo;
import org.springframework.boot.info.SslInfo.CertificateInfo;
import org.springframework.boot.info.SslInfo.CertificateValidityInfo;
import org.springframework.boot.info.SslInfo.CertificateValidityInfo.Status;
import org.springframework.boot.ssl.SslBundles;

/**
 * {@link MeterBinder} which registers the SSL chain validity (soonest to expire
 * certificate in the chain) as a {@link TimeGauge}. Also contributes two {@link Gauge
 * gauges} to count the valid and invalid chains.
 *
 * @author Moritz Halbritter
 */
class SslMeterBinder implements MeterBinder {

	private final Clock clock;

	private final SslInfo sslInfo;

	private final Bundles bundles = new Bundles();

	SslMeterBinder(SslInfo sslInfo, SslBundles sslBundles) {
		this(sslInfo, sslBundles, Clock.systemDefaultZone());
	}

	SslMeterBinder(SslInfo sslInfo, SslBundles sslBundles, Clock clock) {
		this.clock = clock;
		this.sslInfo = sslInfo;
		sslBundles.addBundleRegisterHandler((bundleName, ignored) -> onBundleChange(bundleName));
		for (String bundleName : sslBundles.getBundleNames()) {
			sslBundles.addBundleUpdateHandler(bundleName, (ignored) -> onBundleChange(bundleName));
		}
	}

	private void onBundleChange(String bundleName) {
		BundleInfo bundle = this.sslInfo.getBundle(bundleName);
		this.bundles.updateBundle(bundle);
		for (MeterRegistry meterRegistry : this.bundles.getMeterRegistries()) {
			createOrUpdateBundleMetrics(meterRegistry, bundle);
		}
	}

	@Override
	public void bindTo(MeterRegistry meterRegistry) {
		for (BundleInfo bundle : this.sslInfo.getBundles()) {
			createOrUpdateBundleMetrics(meterRegistry, bundle);
		}
		Gauge.builder("ssl.chains", () -> countChainsByStatus(Status.VALID))
			.tag("status", "valid")
			.register(meterRegistry);
		Gauge.builder("ssl.chains", () -> countChainsByStatus(Status.EXPIRED))
			.tag("status", "expired")
			.register(meterRegistry);
		Gauge.builder("ssl.chains", () -> countChainsByStatus(Status.NOT_YET_VALID))
			.tag("status", "not-yet-valid")
			.register(meterRegistry);
		Gauge.builder("ssl.chains", () -> countChainsByStatus(Status.WILL_EXPIRE_SOON))
			.tag("status", "will-expire-soon")
			.register(meterRegistry);
	}

	private void createOrUpdateBundleMetrics(MeterRegistry meterRegistry, BundleInfo bundle) {
		MultiGauge multiGauge = this.bundles.getGauge(bundle, meterRegistry);
		List<Row<CertificateInfo>> rows = new ArrayList<>();
		for (CertificateChainInfo chain : bundle.getCertificateChains()) {
			Row<CertificateInfo> row = createRowForChain(bundle, chain);
			if (row != null) {
				rows.add(row);
			}
		}
		multiGauge.register(rows, true);
	}

	private Row<CertificateInfo> createRowForChain(BundleInfo bundle, CertificateChainInfo chain) {
		CertificateInfo leastValidCertificate = chain.getCertificates()
			.stream()
			.min(Comparator.comparing(CertificateInfo::getValidityEnds))
			.orElse(null);
		if (leastValidCertificate == null) {
			return null;
		}
		Tags tags = Tags.of("chain", chain.getAlias(), "bundle", bundle.getName(), "certificate",
				leastValidCertificate.getSerialNumber());
		return Row.of(tags, leastValidCertificate, this::getChainExpiry);
	}

	private long countChainsByStatus(Status status) {
		long count = 0;
		for (BundleInfo bundle : this.bundles.getBundles()) {
			for (CertificateChainInfo chain : bundle.getCertificateChains()) {
				if (getChainStatus(chain) == status) {
					count++;
				}
			}
		}
		return count;
	}

	private Status getChainStatus(CertificateChainInfo chain) {
		EnumSet<Status> statuses = EnumSet.noneOf(Status.class);
		for (CertificateInfo certificate : chain.getCertificates()) {
			CertificateValidityInfo validity = certificate.getValidity();
			statuses.add(validity.getStatus());
		}
		if (statuses.contains(Status.EXPIRED)) {
			return Status.EXPIRED;
		}
		if (statuses.contains(Status.NOT_YET_VALID)) {
			return Status.NOT_YET_VALID;
		}
		if (statuses.contains(Status.WILL_EXPIRE_SOON)) {
			return Status.WILL_EXPIRE_SOON;
		}
		return statuses.isEmpty() ? null : Status.VALID;
	}

	private long getChainExpiry(CertificateInfo certificate) {
		Duration valid = Duration.between(Instant.now(this.clock), certificate.getValidityEnds());
		return valid.get(ChronoUnit.SECONDS);
	}

	/**
	 * Manages bundles and their gauges.
	 */
	private static final class Bundles {

		private final Map<String, Bundle> bundles = new ConcurrentHashMap<>();

		/**
		 * Gets (or creates) a {@link MultiGauge} for the given bundle and meter registry.
		 * @param bundleInfo the bundle
		 * @param meterRegistry the meter registry
		 * @return the {@link MultiGauge}
		 */
		MultiGauge getGauge(BundleInfo bundleInfo, MeterRegistry meterRegistry) {
			Bundle bundle = this.bundles.computeIfAbsent(bundleInfo.getName(),
					(ignored) -> Bundle.emptyGauges(bundleInfo));
			return bundle.getGauge(meterRegistry);
		}

		/**
		 * Returns all bundles.
		 * @return all bundles
		 */
		Collection<BundleInfo> getBundles() {
			List<BundleInfo> result = new ArrayList<>();
			for (Bundle metrics : this.bundles.values()) {
				result.add(metrics.bundle());
			}
			return result;
		}

		/**
		 * Returns all meter registries.
		 * @return all meter registries
		 */
		Collection<MeterRegistry> getMeterRegistries() {
			Set<MeterRegistry> result = new HashSet<>();
			for (Bundle metrics : this.bundles.values()) {
				result.addAll(metrics.getMeterRegistries());
			}
			return result;
		}

		/**
		 * Updates the given bundle.
		 * @param bundle the updated bundle
		 */
		void updateBundle(BundleInfo bundle) {
			this.bundles.computeIfPresent(bundle.getName(), (key, oldValue) -> oldValue.withBundle(bundle));
		}

		/**
		 * Manages a bundle and associated {@link MultiGauge MultiGauges}.
		 *
		 * @param bundle the bundle
		 * @param multiGauges mapping from meter registry to {@link MultiGauge}
		 */
		private record Bundle(BundleInfo bundle, Map<MeterRegistry, MultiGauge> multiGauges) {

			/**
			 * Gets (or creates) the {@link MultiGauge} for the given meter registry.
			 * @param meterRegistry the meter registry
			 * @return the {@link MultiGauge}
			 */
			MultiGauge getGauge(MeterRegistry meterRegistry) {
				return this.multiGauges.computeIfAbsent(meterRegistry, (ignored) -> createGauge(meterRegistry));
			}

			/**
			 * Returns a copy of this bundle with an updated {@link BundleInfo}.
			 * @param bundle the updated {@link BundleInfo}
			 * @return the copy of this bundle with an updated {@link BundleInfo}
			 */
			Bundle withBundle(BundleInfo bundle) {
				return new Bundle(bundle, this.multiGauges);
			}

			/**
			 * Returns all meter registries.
			 * @return all meter registries
			 */
			Set<MeterRegistry> getMeterRegistries() {
				return this.multiGauges.keySet();
			}

			private MultiGauge createGauge(MeterRegistry meterRegistry) {
				return MultiGauge.builder("ssl.chain.expiry")
					.baseUnit("seconds")
					.description("SSL chain expiry")
					.register(meterRegistry);
			}

			/**
			 * Creates an instance with an empty gauge mapping.
			 * @param bundle the {@link BundleInfo} associated with the new instance
			 * @return the new instance
			 */
			static Bundle emptyGauges(BundleInfo bundle) {
				return new Bundle(bundle, new ConcurrentHashMap<>());
			}
		}

	}

}
