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
import java.util.concurrent.TimeoutException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.TransactionConfig;
import org.neo4j.driver.exceptions.ConnectionReadTimeoutException;
import org.neo4j.driver.exceptions.SessionExpiredException;
import org.neo4j.driver.exceptions.TransactionTerminatedException;
import org.neo4j.driver.reactivestreams.ReactiveResult;
import org.neo4j.driver.reactivestreams.ReactiveSession;
import org.neo4j.driver.summary.ResultSummary;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import org.springframework.boot.health.contributor.AbstractReactiveHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.TimeoutSupport;
import org.springframework.util.Assert;

/**
 * {@link org.springframework.boot.health.contributor.ReactiveHealthIndicator} that tests
 * the status of a Neo4j by executing a Cypher statement and extracting server and
 * database information.
 * <p>
 * This indicator uses {@link TimeoutSupport#NATIVE}: when a health timeout is configured,
 * it is applied to the Cypher health check with {@link TransactionConfig#timeout()}.
 *
 * @author Michael J. Simons
 * @author Stephane Nicoll
 * @author Phillip Webb
 * @since 4.0.0
 */
public final class Neo4jReactiveHealthIndicator extends AbstractReactiveHealthIndicator {

	private static final Log logger = LogFactory.getLog(Neo4jReactiveHealthIndicator.class);

	private final Driver driver;

	private final Neo4jHealthDetailsHandler healthDetailsHandler;

	public Neo4jReactiveHealthIndicator(Driver driver) {
		super("Neo4j health check failed");
		this.driver = driver;
		this.healthDetailsHandler = new Neo4jHealthDetailsHandler();
	}

	@Override
	public TimeoutSupport getTimeoutSupport() {
		return TimeoutSupport.NATIVE;
	}

	@Override
	protected Mono<Health> doHealthCheck(Health.Builder builder) {
		return runHealthCheckQuery(null)
			.doOnError(SessionExpiredException.class, (ex) -> logger.warn(Neo4jHealthIndicator.MESSAGE_SESSION_EXPIRED))
			.retryWhen(Retry.max(1).filter(SessionExpiredException.class::isInstance))
			.map((healthDetails) -> {
				this.healthDetailsHandler.addHealthDetails(builder, healthDetails);
				return builder.build();
			});
	}

	@Override
	protected Mono<Health> doHealthCheck(Health.Builder builder, Duration timeout) {
		return runHealthCheckQuery(timeout)
			.doOnError(SessionExpiredException.class, (ex) -> logger.warn(Neo4jHealthIndicator.MESSAGE_SESSION_EXPIRED))
			.retryWhen(Retry.max(1).filter(SessionExpiredException.class::isInstance))
			.onErrorMap(this::mapClientTimeoutFailure)
			.map((healthDetails) -> {
				this.healthDetailsHandler.addHealthDetails(builder, healthDetails);
				return builder.build();
			});
	}

	private Throwable mapClientTimeoutFailure(Throwable ex) {
		if (ex instanceof TimeoutException) {
			return ex;
		}
		if (ex instanceof TransactionTerminatedException || ex instanceof ConnectionReadTimeoutException) {
			TimeoutException timeoutException = new TimeoutException(ex.getMessage());
			timeoutException.initCause(ex);
			return timeoutException;
		}
		return ex;
	}

	Mono<Neo4jHealthDetails> runHealthCheckQuery(@Nullable Duration timeout) {
		TransactionConfig transactionConfig = transactionConfigForHealth(timeout);
		return Mono.using(this::session, (session) -> healthDetails(session, transactionConfig),
				ReactiveSession::close);
	}

	private TransactionConfig transactionConfigForHealth(@Nullable Duration timeout) {
		if (timeout == null) {
			return TransactionConfig.empty();
		}
		return TransactionConfig.builder().withTimeout(minimumTransactionTimeout(timeout)).build();
	}

	private Duration minimumTransactionTimeout(Duration timeout) {
		Duration minimum = Duration.ofMillis(1);
		return (timeout.compareTo(minimum) < 0) ? minimum : timeout;
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
