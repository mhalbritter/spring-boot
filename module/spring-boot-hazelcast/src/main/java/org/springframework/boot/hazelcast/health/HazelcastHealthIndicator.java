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

package org.springframework.boot.hazelcast.health;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.transaction.TransactionOptions;
import com.hazelcast.transaction.TransactionTimedOutException;

import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.TimeoutSupport;
import org.springframework.util.Assert;

/**
 * {@link HealthIndicator} for Hazelcast.
 * <p>
 * When a health timeout is configured, it is applied as the Hazelcast transaction timeout
 * for the transactional health probe.
 *
 * @author Dmytro Nosan
 * @author Stephane Nicoll
 * @author Tommy Karlsson
 * @since 4.0.0
 */
public class HazelcastHealthIndicator extends AbstractHealthIndicator {

	private final HazelcastInstance hazelcast;

	public HazelcastHealthIndicator(HazelcastInstance hazelcast) {
		super("Hazelcast health check failed");
		Assert.notNull(hazelcast, "'hazelcast' must not be null");
		this.hazelcast = hazelcast;
	}

	@Override
	public TimeoutSupport getTimeoutSupport() {
		return TimeoutSupport.NATIVE;
	}

	@Override
	protected void doHealthCheck(Health.Builder builder) {
		if (!this.hazelcast.getLifecycleService().isRunning()) {
			builder.down();
			return;
		}
		this.hazelcast.executeTransaction((context) -> {
			addDetails(builder);
			return null;
		});
	}

	@Override
	protected void doHealthCheck(Health.Builder builder, Duration timeout) throws Exception {
		if (!this.hazelcast.getLifecycleService().isRunning()) {
			builder.down();
			return;
		}
		TransactionOptions options = new TransactionOptions();
		options.setTimeout(toTransactionTimeoutMillis(timeout), TimeUnit.MILLISECONDS);
		try {
			this.hazelcast.executeTransaction(options, (context) -> {
				addDetails(builder);
				return null;
			});
		}
		catch (TransactionTimedOutException ex) {
			TimeoutException timeoutException = new TimeoutException(ex.getMessage());
			timeoutException.initCause(ex);
			throw timeoutException;
		}
	}

	private void addDetails(Health.Builder builder) {
		String uuid = this.hazelcast.getLocalEndpoint().getUuid().toString();
		builder.up().withDetail("name", this.hazelcast.getName()).withDetail("uuid", uuid);
	}

	/**
	 * Converts a {@link Duration} to a positive transaction timeout in whole milliseconds
	 * (minimum {@code 1}).
	 * @param timeout the timeout
	 * @return timeout in milliseconds
	 */
	private long toTransactionTimeoutMillis(Duration timeout) {
		long millis = timeout.toMillis();
		return Math.max(1, millis);
	}

}
