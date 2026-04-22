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
import java.util.Collections;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import com.mongodb.MongoException;
import com.mongodb.MongoExecutionTimeoutException;
import com.mongodb.MongoTimeoutException;
import com.mongodb.client.ListDatabasesIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.jupiter.api.Test;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.health.contributor.TimeoutSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link MongoHealthIndicator}.
 *
 * @author Christian Dupuis
 * @author Andy Wilkinson
 */
class MongoHealthIndicatorTests {

	@Test
	@SuppressWarnings("unchecked")
	void mongoIsUp() {
		Document commandResult = mock(Document.class);
		given(commandResult.getInteger("maxWireVersion")).willReturn(10);
		MongoClient mongoClient = mock(MongoClient.class);
		ListDatabasesIterable<Document> listDatabases = mock(ListDatabasesIterable.class);
		willAnswer((invocation) -> {
			((Consumer<Document>) invocation.getArgument(0)).accept(new Document("name", "db"));
			return null;
		}).given(listDatabases).forEach(any());
		given(mongoClient.listDatabases()).willReturn(listDatabases);
		MongoDatabase mongoDatabase = mock(MongoDatabase.class);
		given(mongoClient.getDatabase("db")).willReturn(mongoDatabase);
		given(mongoDatabase.runCommand(Document.parse("{ hello: 1 }"))).willReturn(commandResult);
		MongoHealthIndicator healthIndicator = new MongoHealthIndicator(mongoClient);
		Health health = healthIndicator.health();
		assertThat(health.getStatus()).isEqualTo(Status.UP);
		assertThat(health.getDetails()).containsEntry("maxWireVersion", 10);
		assertThat(health.getDetails()).containsEntry("databases", Collections.singletonList("db"));
		then(commandResult).should().getInteger("maxWireVersion");
	}

	@Test
	@SuppressWarnings("unchecked")
	void mongoIsUpWithTimeout() throws Exception {
		Document commandResult = mock(Document.class);
		given(commandResult.getInteger("maxWireVersion")).willReturn(10);
		MongoClient mongoClient = mock(MongoClient.class);
		ListDatabasesIterable<Document> listDatabases = mock(ListDatabasesIterable.class);
		given(listDatabases.maxTime(anyLong(), any(TimeUnit.class))).willReturn(listDatabases);
		willAnswer((invocation) -> {
			((Consumer<Document>) invocation.getArgument(0)).accept(new Document("name", "db"));
			return null;
		}).given(listDatabases).forEach(any());
		given(mongoClient.listDatabases()).willReturn(listDatabases);
		MongoDatabase mongoDatabase = mock(MongoDatabase.class);
		given(mongoDatabase.withTimeout(anyLong(), any(TimeUnit.class))).willReturn(mongoDatabase);
		given(mongoClient.getDatabase("db")).willReturn(mongoDatabase);
		given(mongoDatabase.runCommand(Document.parse("{ hello: 1 }"))).willReturn(commandResult);
		MongoHealthIndicator healthIndicator = new MongoHealthIndicator(mongoClient);
		Health health = healthIndicator.health(Duration.ofSeconds(5));
		assertThat(health.getStatus()).isEqualTo(Status.UP);
		assertThat(health.getDetails()).containsEntry("maxWireVersion", 10);
		then(listDatabases).should().maxTime(5000, TimeUnit.MILLISECONDS);
		then(mongoDatabase).should().withTimeout(5000, TimeUnit.MILLISECONDS);
	}

	@Test
	void mongoIsDown() {
		MongoClient mongoClient = mock(MongoClient.class);
		given(mongoClient.listDatabases()).willThrow(new MongoException("Connection failed"));
		MongoHealthIndicator healthIndicator = new MongoHealthIndicator(mongoClient);
		Health health = healthIndicator.health();
		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		assertThat((String) health.getDetails().get("error")).contains("Connection failed");
	}

	@Test
	void getTimeoutSupport() {
		MongoClient mongoClient = mock(MongoClient.class);
		MongoHealthIndicator healthIndicator = new MongoHealthIndicator(mongoClient);
		assertThat(healthIndicator.getTimeoutSupport()).isEqualTo(TimeoutSupport.NATIVE);
	}

	@Test
	@SuppressWarnings("unchecked")
	void mongoTimeoutIsPropagated() {
		MongoClient mongoClient = mock(MongoClient.class);
		ListDatabasesIterable<Document> listDatabases = mock(ListDatabasesIterable.class);
		given(listDatabases.maxTime(anyLong(), any(TimeUnit.class))).willReturn(listDatabases);
		willAnswer((invocation) -> {
			throw new MongoTimeoutException("timed out");
		}).given(listDatabases).forEach(any());
		given(mongoClient.listDatabases()).willReturn(listDatabases);
		MongoHealthIndicator healthIndicator = new MongoHealthIndicator(mongoClient);
		assertThatExceptionOfType(TimeoutException.class).isThrownBy(() -> healthIndicator.health(Duration.ofMillis(1)))
			.withMessageContaining("timed out");
	}

	@Test
	@SuppressWarnings("unchecked")
	void mongoExecutionTimeoutIsPropagated() {
		MongoClient mongoClient = mock(MongoClient.class);
		ListDatabasesIterable<Document> listDatabases = mock(ListDatabasesIterable.class);
		given(listDatabases.maxTime(anyLong(), any(TimeUnit.class))).willReturn(listDatabases);
		willAnswer((invocation) -> {
			throw new MongoExecutionTimeoutException("max time expired");
		}).given(listDatabases).forEach(any());
		given(mongoClient.listDatabases()).willReturn(listDatabases);
		MongoHealthIndicator healthIndicator = new MongoHealthIndicator(mongoClient);
		assertThatExceptionOfType(TimeoutException.class).isThrownBy(() -> healthIndicator.health(Duration.ofMillis(1)))
			.withMessageContaining("max time expired");
	}

}
