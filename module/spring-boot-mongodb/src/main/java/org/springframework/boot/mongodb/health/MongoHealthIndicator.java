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

package org.springframework.boot.mongodb.health;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import com.mongodb.MongoOperationTimeoutException;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCluster;
import org.bson.Document;
import org.jspecify.annotations.Nullable;

import org.springframework.boot.health.contributor.AbstractTimeoutAwareHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.TimeoutEnforcement;
import org.springframework.util.Assert;

/**
 * Simple implementation of a {@link HealthIndicator} returning status information for
 * MongoDB.
 *
 * @author Christian Dupuis
 * @author Seonwoo Jung
 * @author Moritz Halbritter
 * @since 4.0.0
 */
public class MongoHealthIndicator extends AbstractTimeoutAwareHealthIndicator {

	private static final String ADMIN_DATABASE = "admin";

	private static final Document HELLO_COMMAND = Document.parse("{ hello: 1 }");

	private final MongoClient mongoClient;

	public MongoHealthIndicator(MongoClient mongoClient) {
		super(TimeoutEnforcement.INDICATOR, "MongoDB health check failed");
		Assert.notNull(mongoClient, "'mongoClient' must not be null");
		this.mongoClient = mongoClient;
	}

	@Override
	protected void doHealthCheck(Health.Builder builder, @Nullable Duration timeout) throws Exception {
		if (timeout == null) {
			performHealthCheck(builder, () -> this.mongoClient);
			return;
		}
		long deadline = System.nanoTime() + timeout.toNanos();
		try {
			performHealthCheck(builder, () -> withRemainingTimeout(deadline));
		}
		catch (MongoOperationTimeoutException ex) {
			TimeoutException timeoutException = new TimeoutException(ex.getMessage());
			timeoutException.initCause(ex);
			throw timeoutException;
		}
	}

	private void performHealthCheck(Health.Builder builder, Supplier<MongoCluster> cluster) {
		List<String> databases = new ArrayList<>();
		cluster.get().listDatabaseNames().forEach(databases::add);
		Document result = cluster.get().getDatabase(getDatabaseName(databases)).runCommand(HELLO_COMMAND);
		builder.up()
			.withDetail("databases", databases)
			.withDetail("maxWireVersion", result.getInteger("maxWireVersion"));
	}

	private static String getDatabaseName(List<String> databases) {
		if (databases.contains(ADMIN_DATABASE)) {
			return ADMIN_DATABASE;
		}
		return (!databases.isEmpty()) ? databases.get(0) : ADMIN_DATABASE;
	}

	private MongoCluster withRemainingTimeout(long deadline) {
		long remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
		return this.mongoClient.withTimeout(Math.max(1, remaining), TimeUnit.MILLISECONDS);
	}

}
