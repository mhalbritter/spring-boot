/*
 * Copyright 2012-present the original author or authors.
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

package org.springframework.boot.couchbase.health;

import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.ReactiveCluster;
import reactor.core.publisher.Mono;

import org.springframework.boot.health.contributor.AbstractReactiveHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.ReactiveHealthIndicator;
import org.springframework.boot.health.contributor.TimeoutSupport;

/**
 * A {@link ReactiveHealthIndicator} for Couchbase.
 * <p>
 * This indicator uses {@link TimeoutSupport#NONE}: configured health timeouts do not
 * apply. The check uses {@link ReactiveCluster#diagnostics() passive diagnostics} only.
 *
 * @author Mikalai Lushchytski
 * @author Stephane Nicoll
 * @since 4.0.0
 */
public class CouchbaseReactiveHealthIndicator extends AbstractReactiveHealthIndicator {

	private final Cluster cluster;

	/**
	 * Create a new {@link CouchbaseReactiveHealthIndicator} instance.
	 * @param cluster the Couchbase cluster
	 */
	public CouchbaseReactiveHealthIndicator(Cluster cluster) {
		super("Couchbase health check failed");
		this.cluster = cluster;
	}

	@Override
	public TimeoutSupport getTimeoutSupport() {
		return TimeoutSupport.NONE;
	}

	@Override
	protected Mono<Health> doHealthCheck(Health.Builder builder) {
		return this.cluster.reactive().diagnostics().map((diagnostics) -> {
			new CouchbaseHealth(diagnostics).applyTo(builder);
			return builder.build();
		});
	}

}
