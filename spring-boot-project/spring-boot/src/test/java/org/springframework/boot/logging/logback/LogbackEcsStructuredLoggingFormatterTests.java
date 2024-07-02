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

package org.springframework.boot.logging.logback;

import java.util.Collections;
import java.util.Map;

import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.logging.structured.ApplicationMetadata;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link LogbackEcsStructuredLoggingFormatter}.
 *
 * @author Moritz Halbritter
 */
class LogbackEcsStructuredLoggingFormatterTests extends AbstractStructuredLoggingTests {

	private LogbackEcsStructuredLoggingFormatter formatter;

	@Override
	@BeforeEach
	void setUp() {
		super.setUp();
		this.formatter = new LogbackEcsStructuredLoggingFormatter(getThrowableProxyConverter(),
				new ApplicationMetadata(1L, "name", "1.0.0", "test", "node-1"));
	}

	@Test
	void shouldFormat() {
		LoggingEvent event = createEvent();
		event.setMDCPropertyMap(Map.of("mdc-1", "mdc-v-1"));
		event.setKeyValuePairs(keyValuePairs("kv-1", "kv-v-1"));
		String json = this.formatter.format(event);
		assertThat(json).endsWith("\n");
		Map<String, Object> deserialized = deserialize(json);
		assertThat(deserialized).containsExactlyInAnyOrderEntriesOf(map("@timestamp", "2024-07-02T08:49:53Z",
				"log.level", "INFO", "process.pid", 1, "process.thread.name", "main", "service.name", "name",
				"service.version", "1.0.0", "service.environment", "test", "service.node.name", "node-1", "log.logger",
				"org.example.Test", "message", "message", "mdc-1", "mdc-v-1", "kv-1", "kv-v-1", "ecs.version", "8.11"));
	}

	@Test
	void shouldFormatException() {
		LoggingEvent event = createEvent();
		event.setMDCPropertyMap(Collections.emptyMap());
		event.setThrowableProxy(new ThrowableProxy(new RuntimeException("Boom")));
		String json = this.formatter.format(event);
		Map<String, Object> deserialized = deserialize(json);
		assertThat(deserialized)
			.containsAllEntriesOf(map("error.type", "java.lang.RuntimeException", "error.message", "Boom"));
		String stackTrace = (String) deserialized.get("error.stack_trace");
		assertThat(stackTrace).startsWith(
				"""
						java.lang.RuntimeException: Boom
						\tat org.springframework.boot.logging.logback.LogbackEcsStructuredLoggingFormatterTests.shouldFormatException
						"""
					.trim());
		assertThat(json).contains(
				"""
						java.lang.RuntimeException: Boom\\n\\tat org.springframework.boot.logging.logback.LogbackEcsStructuredLoggingFormatterTests.shouldFormatException
						"""
					.trim());
	}

}
