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
import com.mongodb.reactivestreams.client.ListDatabasesPublisher;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoDatabase;
import org.bson.Document;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.boot.health.contributor.AbstractReactiveHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.TimeoutSupport;
import org.springframework.util.Assert;

/**
 * A {@link org.springframework.boot.health.contributor.ReactiveHealthIndicator} for
 * Mongo.
 * <p>
 * This indicator uses {@link TimeoutSupport#NATIVE}: when a health timeout is configured,
 * it is applied as {@code maxTime} for {@link MongoClient#listDatabases(Class)} and as a
 * client operation timeout on each
 * {@link MongoDatabase#runCommand(org.bson.conversions.Bson) runCommand}.
 *
 * @author Yulin Qin
 * @author Stephane Nicoll
 * @author Moritz Halbritter
 * @since 4.0.0
 */
public class MongoReactiveHealthIndicator extends AbstractReactiveHealthIndicator {

	private static final Document HELLO_COMMAND = Document.parse("{ hello: 1 }");

	private final MongoClient mongoClient;

	public MongoReactiveHealthIndicator(MongoClient mongoClient) {
		super("Mongo health check failed");
		Assert.notNull(mongoClient, "'mongoClient' must not be null");
		this.mongoClient = mongoClient;
	}

	@Override
	public TimeoutSupport getTimeoutSupport() {
		return TimeoutSupport.NATIVE;
	}

	@Override
	protected Mono<Health> doHealthCheck(Health.Builder builder) {
		return collectHealthDetails(null).map((details) -> builder.up().withDetails(details).build());
	}

	@Override
	protected Mono<Health> doHealthCheck(Health.Builder builder, Duration timeout) {
		return collectHealthDetails(timeout).map((details) -> builder.up().withDetails(details).build())
			.onErrorMap(MongoTimeoutException.class, this::asTimeoutException)
			.onErrorMap(MongoExecutionTimeoutException.class, this::asTimeoutException);
	}

	private Mono<Map<String, Object>> collectHealthDetails(@Nullable Duration timeout) {
		ListDatabasesPublisher<Document> listDatabases = this.mongoClient.listDatabases(Document.class);
		if (timeout != null) {
			listDatabases = listDatabases.maxTime(toMaxTimeMillis(timeout), TimeUnit.MILLISECONDS);
		}
		return Flux.from(listDatabases)
			.flatMap((databaseDoc) -> helloForDatabase(databaseDoc.getString("name"), timeout))
			.collectList()
			.map(MongoReactiveHealthIndicator::toDetails);
	}

	private Mono<HelloResponse> helloForDatabase(String name, @Nullable Duration timeout) {
		MongoDatabase mongoDatabase = this.mongoClient.getDatabase(name);
		if (timeout != null) {
			mongoDatabase = mongoDatabase.withTimeout(toMaxTimeMillis(timeout), TimeUnit.MILLISECONDS);
		}
		return Mono.from(mongoDatabase.runCommand(HELLO_COMMAND)).map((document) -> new HelloResponse(name, document));
	}

	private static Map<String, Object> toDetails(List<HelloResponse> responses) {
		Map<String, Object> databaseDetails = new LinkedHashMap<>();
		List<String> databases = new ArrayList<>();
		databaseDetails.put("databases", databases);
		for (HelloResponse response : responses) {
			databases.add(response.database());
			databaseDetails.putIfAbsent("maxWireVersion", response.document().getInteger("maxWireVersion"));
		}
		return databaseDetails;
	}

	private TimeoutException asTimeoutException(Exception ex) {
		TimeoutException timeoutException = new TimeoutException(ex.getMessage());
		timeoutException.initCause(ex);
		return timeoutException;
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

	private record HelloResponse(String database, Document document) {
	}

}
