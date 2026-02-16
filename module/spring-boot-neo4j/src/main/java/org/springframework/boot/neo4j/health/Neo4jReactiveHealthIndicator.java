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

package org.springframework.boot.neo4j.health;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.TransactionConfig;
import org.neo4j.driver.exceptions.SessionExpiredException;
import org.neo4j.driver.reactivestreams.ReactiveResult;
import org.neo4j.driver.reactivestreams.ReactiveSession;
import org.neo4j.driver.summary.ResultSummary;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import org.springframework.boot.health.contributor.AbstractTimeoutAwareReactiveHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.ReactiveHealthIndicator;
import org.springframework.boot.health.contributor.TimeoutEnforcement;
import org.springframework.util.Assert;

/**
 * {@link ReactiveHealthIndicator} that tests the status of a Neo4j by executing a Cypher
 * statement and extracting server and database information.
 *
 * @author Michael J. Simons
 * @author Stephane Nicoll
 * @author Phillip Webb
 * @author Moritz Halbritter
 * @since 4.0.0
 */
public final class Neo4jReactiveHealthIndicator extends AbstractTimeoutAwareReactiveHealthIndicator {

	private static final Log logger = LogFactory.getLog(Neo4jReactiveHealthIndicator.class);

	private final Driver driver;

	private final Neo4jHealthDetailsHandler healthDetailsHandler;

	public Neo4jReactiveHealthIndicator(Driver driver) {
		super(TimeoutEnforcement.FRAMEWORK);
		this.driver = driver;
		this.healthDetailsHandler = new Neo4jHealthDetailsHandler();
	}

	@Override
	protected Mono<Health> doHealthCheck(Health.Builder builder, @Nullable Duration timeout) {
		return runHealthCheckQuery(createTransactionConfig(timeout))
			.doOnError(SessionExpiredException.class, (ex) -> logger.warn(Neo4jHealthIndicator.MESSAGE_SESSION_EXPIRED))
			.retryWhen(Retry.max(1).filter(SessionExpiredException.class::isInstance))
			.map((healthDetails) -> {
				this.healthDetailsHandler.addHealthDetails(builder, healthDetails);
				return builder.build();
			});
	}

	Mono<Neo4jHealthDetails> runHealthCheckQuery(TransactionConfig transactionConfig) {
		return Mono.usingWhen(Mono.fromSupplier(this::session), (session) -> healthDetails(session, transactionConfig),
				(session) -> Mono.from(session.close()));
	}

	private ReactiveSession session() {
		return this.driver.session(ReactiveSession.class, Neo4jHealthIndicator.DEFAULT_SESSION_CONFIG);
	}

	private Mono<Neo4jHealthDetails> healthDetails(ReactiveSession session, TransactionConfig transactionConfig) {
		return Mono.from(session.run(Neo4jHealthIndicator.CYPHER, transactionConfig)).flatMap(this::healthDetails);
	}

	private Mono<? extends Neo4jHealthDetails> healthDetails(ReactiveResult result) {
		Flux<Record> records = Flux.from(result.records());
		Mono<ResultSummary> summary = Mono.from(result.consume());
		Neo4jHealthDetailsBuilder builder = new Neo4jHealthDetailsBuilder();
		return records.single().doOnNext(builder::record).then(summary).map(builder::build);
	}

	private TransactionConfig createTransactionConfig(@Nullable Duration timeout) {
		if (timeout == null) {
			return TransactionConfig.empty();
		}
		Duration truncated = timeout.truncatedTo(ChronoUnit.MILLIS);
		Duration rounded = (truncated.equals(timeout)) ? timeout : truncated.plusMillis(1);
		return TransactionConfig.builder().withTimeout(rounded).build();
	}

	/**
	 * Builder used to create a {@link Neo4jHealthDetails} from a {@link Record} and a
	 * {@link ResultSummary}.
	 */
	private static final class Neo4jHealthDetailsBuilder {

		private @Nullable Record record;

		void record(Record record) {
			this.record = record;
		}

		private Neo4jHealthDetails build(ResultSummary summary) {
			Assert.state(this.record != null, "'record' must not be null");
			return new Neo4jHealthDetails(this.record, summary);
		}

	}

}
