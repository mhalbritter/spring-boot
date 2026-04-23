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
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.mongodb.MongoException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.reactivestreams.client.ListDatabasesPublisher;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoDatabase;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.health.contributor.TimeoutSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link MongoReactiveHealthIndicator}.
 *
 * @author Yulin Qin
 * @author Stephane Nicoll
 */
class MongoReactiveHealthIndicatorTests {

	@Test
	void mongoIsUp() {
		MongoClient mongoClient = mock(MongoClient.class);
		Document databaseDoc = new Document("name", "db");
		ListDatabasesPublisher<Document> listPublisher = mockListDatabasesPublisher(Flux.just(databaseDoc));
		given(mongoClient.listDatabases(Document.class)).willReturn(listPublisher);
		MongoDatabase mongoDatabase = mock(MongoDatabase.class);
		given(mongoClient.getDatabase("db")).willReturn(mongoDatabase);
		Document commandResult = mock(Document.class);
		given(mongoDatabase.runCommand(Document.parse("{ hello: 1 }"))).willReturn(Mono.just(commandResult));
		given(commandResult.getInteger("maxWireVersion")).willReturn(10);
		MongoReactiveHealthIndicator mongoReactiveHealthIndicator = new MongoReactiveHealthIndicator(mongoClient);
		Mono<Health> health = mongoReactiveHealthIndicator.health();
		StepVerifier.create(health).consumeNextWith((h) -> {
			assertThat(h.getStatus()).isEqualTo(Status.UP);
			assertThat(h.getDetails()).containsOnlyKeys("maxWireVersion", "databases");
			assertThat(h.getDetails()).containsEntry("maxWireVersion", 10);
			assertThat(h.getDetails()).containsEntry("databases", List.of("db"));
		}).expectComplete().verify(Duration.ofSeconds(30));
	}

	@Test
	void mongoIsDown() {
		MongoClient mongoClient = mock(MongoClient.class);
		ListDatabasesPublisher<Document> listPublisher = mockListDatabasesPublisher(
				Flux.error(new MongoException("Connection failed")));
		given(mongoClient.listDatabases(Document.class)).willReturn(listPublisher);
		MongoReactiveHealthIndicator mongoReactiveHealthIndicator = new MongoReactiveHealthIndicator(mongoClient);
		Mono<Health> health = mongoReactiveHealthIndicator.health();
		StepVerifier.create(health).consumeNextWith((h) -> {
			assertThat(h.getStatus()).isEqualTo(Status.DOWN);
			assertThat(h.getDetails()).containsOnlyKeys("error");
			assertThat(h.getDetails()).containsEntry("error", MongoException.class.getName() + ": Connection failed");
		}).expectComplete().verify(Duration.ofSeconds(30));
	}

	@Test
	void getTimeoutSupportIsNative() {
		MongoClient mongoClient = mock(MongoClient.class);
		assertThat(new MongoReactiveHealthIndicator(mongoClient).getTimeoutSupport()).isEqualTo(TimeoutSupport.NATIVE);
	}

	@Test
	void mongoIsUpWithTimeoutUsesMaxTimeAndDatabaseTimeout() {
		MongoClient mongoClient = mock(MongoClient.class);
		Document databaseDoc = new Document("name", "db");
		@SuppressWarnings("unchecked")
		ListDatabasesPublisher<Document> listPublisher = mock(ListDatabasesPublisher.class);
		given(listPublisher.maxTime(anyLong(), any(TimeUnit.class))).willReturn(listPublisher);
		willAnswer((invocation) -> {
			Subscriber<? super Document> subscriber = invocation.getArgument(0);
			Flux.just(databaseDoc).subscribe(subscriber);
			return null;
		}).given(listPublisher).subscribe(any());
		given(mongoClient.listDatabases(Document.class)).willReturn(listPublisher);
		MongoDatabase mongoDatabase = mock(MongoDatabase.class);
		given(mongoClient.getDatabase("db")).willReturn(mongoDatabase);
		MongoDatabase timedDatabase = mock(MongoDatabase.class);
		given(mongoDatabase.withTimeout(anyLong(), any(TimeUnit.class))).willReturn(timedDatabase);
		Document commandResult = mock(Document.class);
		given(timedDatabase.runCommand(Document.parse("{ hello: 1 }"))).willReturn(Mono.just(commandResult));
		given(commandResult.getInteger("maxWireVersion")).willReturn(10);
		MongoReactiveHealthIndicator indicator = new MongoReactiveHealthIndicator(mongoClient);
		StepVerifier.create(indicator.health(Duration.ofSeconds(5)))
			.consumeNextWith((h) -> assertThat(h.getStatus()).isEqualTo(Status.UP))
			.expectComplete()
			.verify(Duration.ofSeconds(30));
		ArgumentCaptor<Long> maxTimeCaptor = ArgumentCaptor.forClass(Long.class);
		then(listPublisher).should().maxTime(maxTimeCaptor.capture(), eq(TimeUnit.MILLISECONDS));
		assertThat(maxTimeCaptor.getValue()).isEqualTo(5000L);
		ArgumentCaptor<Long> withTimeoutCaptor = ArgumentCaptor.forClass(Long.class);
		then(mongoDatabase).should().withTimeout(withTimeoutCaptor.capture(), eq(TimeUnit.MILLISECONDS));
		assertThat(withTimeoutCaptor.getValue()).isEqualTo(5000L);
	}

	@Test
	void mongoTimeoutIsPropagated() {
		MongoClient mongoClient = mock(MongoClient.class);
		@SuppressWarnings("unchecked")
		ListDatabasesPublisher<Document> listPublisher = mock(ListDatabasesPublisher.class);
		given(listPublisher.maxTime(anyLong(), any(TimeUnit.class))).willReturn(listPublisher);
		willAnswer((invocation) -> {
			Subscriber<? super Document> subscriber = invocation.getArgument(0);
			subscriber.onSubscribe(new Subscription() {
				@Override
				public void request(long n) {
					subscriber.onError(new MongoTimeoutException("timed out"));
				}

				@Override
				public void cancel() {
				}
			});
			return null;
		}).given(listPublisher).subscribe(any());
		given(mongoClient.listDatabases(Document.class)).willReturn(listPublisher);
		MongoReactiveHealthIndicator indicator = new MongoReactiveHealthIndicator(mongoClient);
		StepVerifier.create(indicator.health(Duration.ofMillis(1)))
			.expectError(TimeoutException.class)
			.verify(Duration.ofSeconds(30));
	}

	@SuppressWarnings("unchecked")
	private ListDatabasesPublisher<Document> mockListDatabasesPublisher(Flux<Document> flux) {
		ListDatabasesPublisher<Document> publisher = mock(ListDatabasesPublisher.class);
		given(publisher.maxTime(anyLong(), any(TimeUnit.class))).willReturn(publisher);
		willAnswer((invocation) -> {
			Subscriber<? super Document> subscriber = invocation.getArgument(0);
			flux.subscribe(subscriber);
			return null;
		}).given(publisher).subscribe(any());
		return publisher;
	}

}
