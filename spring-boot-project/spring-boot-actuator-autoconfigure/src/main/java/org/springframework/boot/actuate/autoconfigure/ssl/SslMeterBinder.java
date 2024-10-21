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
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.TimeGauge;
import io.micrometer.core.instrument.binder.MeterBinder;

import org.springframework.boot.info.SslInfo;
import org.springframework.boot.info.SslInfo.BundleInfo;
import org.springframework.boot.info.SslInfo.CertificateChainInfo;
import org.springframework.boot.info.SslInfo.CertificateInfo;
import org.springframework.boot.info.SslInfo.CertificateValidityInfo;
import org.springframework.boot.info.SslInfo.CertificateValidityInfo.Status;
import org.springframework.boot.ssl.SslBundle;
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

	private final Map<String, BundleMetrics> bundleMetrics = new ConcurrentHashMap<>();

	private final List<MeterRegistry> meterRegistries = new CopyOnWriteArrayList<>();

	SslMeterBinder(SslInfo sslInfo, SslBundles sslBundles) {
		this(sslInfo, sslBundles, Clock.systemDefaultZone());
	}

	SslMeterBinder(SslInfo sslInfo, SslBundles sslBundles, Clock clock) {
		this.clock = clock;
		this.sslInfo = sslInfo;
		sslBundles.addBundleRegisterHandler(this::onRegisterBundle);
		for (String bundleName : sslBundles.getBundleNames()) {
			sslBundles.addBundleUpdateHandler(bundleName, (ignored) -> onUpdateBundle(bundleName));
		}
	}

	private void onRegisterBundle(String bundleName, SslBundle sslBundle) {
		BundleInfo bundle = this.sslInfo.getBundle(bundleName);
		for (MeterRegistry meterRegistry : this.meterRegistries) {
			createBundleMetrics(meterRegistry, bundle);
		}
	}

	private void onUpdateBundle(String bundleName) {
		BundleMetrics bundleMetrics = this.bundleMetrics.remove(bundleName);
		if (bundleMetrics == null) {
			return;
		}
		removeOldMeters(bundleMetrics);
		BundleInfo bundle = this.sslInfo.getBundle(bundleName);
		for (MeterRegistry meterRegistry : this.meterRegistries) {
			createBundleMetrics(meterRegistry, bundle);
		}
	}

	private void removeOldMeters(BundleMetrics bundleMetrics) {
		for (Meter meter : bundleMetrics.meters()) {
			for (MeterRegistry meterRegistry : this.meterRegistries) {
				meterRegistry.remove(meter);
			}
		}
	}

	@Override
	public void bindTo(MeterRegistry meterRegistry) {
		this.meterRegistries.add(meterRegistry);
		for (BundleInfo bundle : this.sslInfo.getBundles()) {
			createBundleMetrics(meterRegistry, bundle);
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

	private void createBundleMetrics(MeterRegistry meterRegistry, BundleInfo bundle) {
		if (bundle.getCertificateChains().isEmpty()) {
			return;
		}
		BundleMetrics bundleMetrics = this.bundleMetrics.computeIfAbsent(bundle.getName(),
				(ignored) -> BundleMetrics.emptyMeters(bundle));
		for (CertificateChainInfo chain : bundle.getCertificateChains()) {
			TimeGauge meter = registerChainExpiryGauge(meterRegistry, bundle, chain);
			bundleMetrics.addMeter(meter);
		}
	}

	private TimeGauge registerChainExpiryGauge(MeterRegistry meterRegistry, BundleInfo bundle,
			CertificateChainInfo chain) {
		CertificateInfo leastValidCertificate = chain.getCertificates()
			.stream()
			.min(Comparator.comparing(CertificateInfo::getValidityEnds))
			.orElse(null);
		if (leastValidCertificate == null) {
			return null;
		}
		return TimeGauge
			.builder("ssl.chain.expiry", () -> getChainExpiry(leastValidCertificate, TimeUnit.SECONDS),
					TimeUnit.SECONDS)
			.tag("chain", chain.getAlias())
			.tag("bundle", bundle.getName())
			.tag("certificate", leastValidCertificate.getSerialNumber())
			.description("SSL chain expiry")
			.register(meterRegistry);
	}

	private long countChainsByStatus(Status status) {
		long count = 0;
		for (BundleMetrics bundleMetrics : this.bundleMetrics.values()) {
			for (CertificateChainInfo chain : bundleMetrics.info().getCertificateChains()) {
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

	private long getChainExpiry(CertificateInfo certificate, TimeUnit unit) {
		Duration valid = Duration.between(Instant.now(this.clock), certificate.getValidityEnds());
		return valid.get(unit.toChronoUnit());
	}

	private record BundleMetrics(BundleInfo info, List<Meter> meters) {

		void addMeter(Meter meter) {
			if (meter != null) {
				this.meters.add(meter);
			}
		}

		static BundleMetrics emptyMeters(BundleInfo bundle) {
			return new BundleMetrics(bundle, new CopyOnWriteArrayList<>());
		}
	}

}
