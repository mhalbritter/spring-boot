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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.mongodb.MongoExecutionTimeoutException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.client.ListDatabasesIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.jspecify.annotations.Nullable;

import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.TimeoutSupport;
import org.springframework.util.Assert;

/**
 * Simple implementation of a {@link HealthIndicator} returning status information for
 * MongoDB.
 *
 * @author Christian Dupuis
 * @author Moritz Halbritter
 * @since 4.0.0
 */
public class MongoHealthIndicator extends AbstractHealthIndicator {

	private static final Document HELLO_COMMAND = Document.parse("{ hello: 1 }");

	private final MongoClient mongoClient;

	public MongoHealthIndicator(MongoClient mongoClient) {
		super("MongoDB health check failed");
		Assert.notNull(mongoClient, "'mongoClient' must not be null");
		this.mongoClient = mongoClient;
	}

	@Override
	public TimeoutSupport getTimeoutSupport() {
		return TimeoutSupport.NATIVE;
	}

	@Override
	protected void doHealthCheck(Health.Builder builder) throws Exception {
		performHealthCheck(builder, null);
	}

	@Override
	protected void doHealthCheck(Health.Builder builder, Duration timeout) throws Exception {
		try {
			performHealthCheck(builder, timeout);
		}
		catch (MongoTimeoutException | MongoExecutionTimeoutException ex) {
			TimeoutException timeoutException = new TimeoutException(ex.getMessage());
			timeoutException.initCause(ex);
			throw timeoutException;
		}
	}

	private void performHealthCheck(Health.Builder builder, @Nullable Duration timeout) {
		Map<String, Object> details = new LinkedHashMap<>();
		List<String> databases = new ArrayList<>();
		details.put("databases", databases);
		ListDatabasesIterable<Document> listDatabases = this.mongoClient.listDatabases();
		if (timeout != null) {
			listDatabases = listDatabases.maxTime(toMaxTimeMillis(timeout), TimeUnit.MILLISECONDS);
		}
		listDatabases.forEach((database) -> {
			String name = database.getString("name");
			MongoDatabase mongoDatabase = this.mongoClient.getDatabase(name);
			if (timeout != null) {
				mongoDatabase = mongoDatabase.withTimeout(toMaxTimeMillis(timeout), TimeUnit.MILLISECONDS);
			}
			Document result = mongoDatabase.runCommand(HELLO_COMMAND);
			databases.add(name);
			details.putIfAbsent("maxWireVersion", result.getInteger("maxWireVersion"));
		});
		builder.up().withDetails(details);
	}

	/**
	 * Converts a {@link Duration} to a positive {@code maxTime} / client operation
	 * timeout in whole milliseconds (minimum {@code 1}).
	 * @param timeout the timeout
	 * @return timeout in milliseconds
	 */
	private long toMaxTimeMillis(Duration timeout) {
		long millis = timeout.toMillis();
		return Math.max(1, millis);
	}

}
