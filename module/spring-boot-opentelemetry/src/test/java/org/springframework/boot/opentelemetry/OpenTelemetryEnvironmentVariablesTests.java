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

package org.springframework.boot.opentelemetry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;

/**
 * Tests for {@link OpenTelemetryEnvironmentVariables}.
 *
 * @author Moritz Halbritter
 */
@ExtendWith(OutputCaptureExtension.class)
class OpenTelemetryEnvironmentVariablesTests {

	private Map<String, String> environment;

	private OpenTelemetryEnvironmentVariables envVariables;

	@BeforeEach
	void setUp() {
		this.environment = new HashMap<>();
		this.envVariables = OpenTelemetryEnvironmentVariables.fromMap(this.environment);
	}

	@Test
	void shouldWorkWithInteger() {
		this.environment.put("a", "1");
		assertThat(this.envVariables.getInteger("a")).isEqualTo(1);
		assertThat(this.envVariables.getIntegerOrElse("b", 2)).isEqualTo(2);
		Consumer<Integer> consumer = mockConsumer();
		this.envVariables.applyInteger("a", consumer);
		then(consumer).should().accept(1);
		this.envVariables.applyInteger("b", consumer);
		then(consumer).shouldHaveNoMoreInteractions();
	}

	@Test
	void shouldWorkWithDouble() {
		this.environment.put("a", "1.5");
		assertThat(this.envVariables.getDouble("a")).isEqualTo(1.5);
		assertThat(this.envVariables.getDoubleOrElse("b", 2.5)).isEqualTo(2.5);
		Consumer<Double> consumer = mockConsumer();
		this.envVariables.applyDouble("a", consumer);
		then(consumer).should().accept(1.5);
		this.envVariables.applyDouble("b", consumer);
		then(consumer).shouldHaveNoMoreInteractions();
	}

	@Test
	void shouldWorkWithString() {
		this.environment.put("a", "value-a");
		assertThat(this.envVariables.getString("a")).isEqualTo("value-a");
		assertThat(this.envVariables.getStringOrElse("b", "value-b")).isEqualTo("value-b");
		Consumer<String> consumer = mockConsumer();
		this.envVariables.applyString("a", consumer);
		then(consumer).should().accept("value-a");
		this.envVariables.applyString("b", consumer);
		then(consumer).shouldHaveNoMoreInteractions();
	}

	@Test
	void shouldWorkWithFileContent(@TempDir Path tempDir) throws IOException {
		Path file = tempDir.resolve("content.txt");
		Files.write(file, new byte[] { 1, 2, 3 });
		this.environment.put("a", file.toString());
		assertThat(this.envVariables.getFileContent("a")).containsExactly(1, 2, 3);
		assertThat(this.envVariables.getFileContentOrElse("b", new byte[] { 4, 5 })).containsExactly(4, 5);
		Consumer<byte[]> consumer = mockConsumer();
		this.envVariables.applyFileContent("a", consumer);
		then(consumer).should().accept(new byte[] { 1, 2, 3 });
		this.envVariables.applyFileContent("b", consumer);
		then(consumer).shouldHaveNoMoreInteractions();
	}

	@Test
	void shouldWorkWithHeaders() {
		this.environment.put("a", "key1=value1,key2=value2");
		assertThat(this.envVariables.getHeaders("a")).containsExactly(Map.entry("key1", "value1"),
				Map.entry("key2", "value2"));
		assertThat(this.envVariables.getHeadersOrElse("b", Map.of("k", "v"))).containsExactly(Map.entry("k", "v"));
		Consumer<Map<String, String>> consumer = mockConsumer();
		this.envVariables.applyHeaders("a", consumer);
		then(consumer).should().accept(Map.of("key1", "value1", "key2", "value2"));
		this.envVariables.applyHeaders("b", consumer);
		then(consumer).shouldHaveNoMoreInteractions();
	}

	@Test
	void shouldWorkWithDuration() {
		this.environment.put("a", "1000");
		assertThat(this.envVariables.getDuration("a")).isEqualTo(Duration.ofSeconds(1));
		assertThat(this.envVariables.getDurationOrElse("b", Duration.ofMillis(2000))).isEqualTo(Duration.ofSeconds(2));
		Consumer<Duration> consumer = mockConsumer();
		this.envVariables.applyDuration("a", consumer);
		then(consumer).should().accept(Duration.ofSeconds(1));
		this.envVariables.applyDuration("b", consumer);
		then(consumer).shouldHaveNoMoreInteractions();
	}

	@Test
	void shouldWorkWithTimeout() {
		this.environment.put("a", "1000");
		assertThat(this.envVariables.getTimeout("a")).isEqualTo(Duration.ofSeconds(1));
		assertThat(this.envVariables.getTimeoutOrElse("b", Duration.ofMillis(2000))).isEqualTo(Duration.ofSeconds(2));
		Consumer<Duration> consumer = mockConsumer();
		this.envVariables.applyTimeout("a", consumer);
		then(consumer).should().accept(Duration.ofSeconds(1));
		this.envVariables.applyTimeout("b", consumer);
		then(consumer).shouldHaveNoMoreInteractions();
	}

	@Test
	void shouldTreatZeroTimeoutAsNoLimit() {
		this.environment.put("a", "0");
		assertThat(this.envVariables.getTimeout("a")).isEqualTo(Duration.ofMillis(Long.MAX_VALUE));
	}

	@Test
	void shouldDiscardInvalidTimeoutAndLogWarning(CapturedOutput output) {
		this.environment.put("timeoutvar", "not-a-number");
		assertThat(this.envVariables.getTimeout("timeoutvar")).isNull();
		assertThat(this.envVariables.getTimeoutOrElse("timeoutvar", Duration.ofSeconds(5)))
			.isEqualTo(Duration.ofSeconds(5));
		Consumer<Duration> consumer = mockConsumer();
		this.envVariables.applyTimeout("timeoutvar", consumer);
		then(consumer).shouldHaveNoInteractions();
		assertThat(output).contains("Invalid duration value for environment variable 'timeoutvar': 'not-a-number'");
	}

	@Test
	void shouldDiscardNegativeTimeoutAndLogWarning(CapturedOutput output) {
		this.environment.put("timeoutvar", "-1000");
		assertThat(this.envVariables.getTimeout("timeoutvar")).isNull();
		assertThat(this.envVariables.getTimeoutOrElse("timeoutvar", Duration.ofSeconds(5)))
			.isEqualTo(Duration.ofSeconds(5));
		Consumer<Duration> consumer = mockConsumer();
		this.envVariables.applyTimeout("timeoutvar", consumer);
		then(consumer).shouldHaveNoInteractions();
		assertThat(output).contains("Negative duration value for environment variable 'timeoutvar': '-1000'");
	}

	@Test
	void shouldWorkWithBoolean() {
		this.environment.put("a", "true");
		assertThat(this.envVariables.getBoolean("a")).isTrue();
	}

	@Test
	void booleanShouldBeCaseInsensitive() {
		this.environment.put("a", "True");
		this.environment.put("b", "TRUE");
		this.environment.put("c", "tRuE");
		assertThat(this.envVariables.getBoolean("a")).isTrue();
		assertThat(this.envVariables.getBoolean("b")).isTrue();
		assertThat(this.envVariables.getBoolean("c")).isTrue();
	}

	@Test
	void booleanShouldReturnFalseForExplicitFalse() {
		this.environment.put("a", "false");
		this.environment.put("b", "False");
		this.environment.put("c", "FALSE");
		assertThat(this.envVariables.getBoolean("a")).isFalse();
		assertThat(this.envVariables.getBoolean("b")).isFalse();
		assertThat(this.envVariables.getBoolean("c")).isFalse();
	}

	@Test
	void booleanShouldReturnFalseWhenUnset() {
		assertThat(this.envVariables.getBoolean("missing")).isFalse();
	}

	@Test
	void booleanShouldReturnFalseForEmptyValue() {
		this.environment.put("a", "");
		assertThat(this.envVariables.getBoolean("a")).isFalse();
	}

	@Test
	void booleanShouldReturnFalseAndLogWarningForInvalidValue(CapturedOutput output) {
		this.environment.put("boolvar", "yes");
		assertThat(this.envVariables.getBoolean("boolvar")).isFalse();
		assertThat(output)
			.contains("Invalid boolean value for environment variable 'boolvar': 'yes', falling back to false");
	}

	@Test
	void booleanShouldNotLogWarningForExplicitFalse(CapturedOutput output) {
		this.environment.put("a", "false");
		this.envVariables.getBoolean("a");
		assertThat(output).doesNotContain("Invalid boolean value");
	}

	@Test
	void shouldDiscardInvalidIntegerAndLogWarning(CapturedOutput output) {
		this.environment.put("intvar", "not-a-number");
		assertThat(this.envVariables.getInteger("intvar")).isNull();
		assertThat(this.envVariables.getIntegerOrElse("intvar", 42)).isEqualTo(42);
		Consumer<Integer> consumer = mockConsumer();
		this.envVariables.applyInteger("intvar", consumer);
		then(consumer).shouldHaveNoInteractions();
		assertThat(output).contains("Invalid integer value for environment variable 'intvar': 'not-a-number'");
	}

	@Test
	void shouldDiscardInvalidDoubleAndLogWarning(CapturedOutput output) {
		this.environment.put("doublevar", "not-a-number");
		assertThat(this.envVariables.getDouble("doublevar")).isNull();
		assertThat(this.envVariables.getDoubleOrElse("doublevar", 42.5)).isEqualTo(42.5);
		Consumer<Double> consumer = mockConsumer();
		this.envVariables.applyDouble("doublevar", consumer);
		then(consumer).shouldHaveNoInteractions();
		assertThat(output).contains("Invalid double value for environment variable 'doublevar': 'not-a-number'");
	}

	@Test
	void shouldDiscardInvalidDurationAndLogWarning(CapturedOutput output) {
		this.environment.put("durationvar", "not-a-number");
		assertThat(this.envVariables.getDuration("durationvar")).isNull();
		assertThat(this.envVariables.getDurationOrElse("durationvar", Duration.ofSeconds(5)))
			.isEqualTo(Duration.ofSeconds(5));
		Consumer<Duration> consumer = mockConsumer();
		this.envVariables.applyDuration("durationvar", consumer);
		then(consumer).shouldHaveNoInteractions();
		assertThat(output).contains("Invalid duration value for environment variable 'durationvar': 'not-a-number'");
	}

	@Test
	void shouldDiscardNegativeDurationAndLogWarning(CapturedOutput output) {
		this.environment.put("durationvar", "-1000");
		assertThat(this.envVariables.getDuration("durationvar")).isNull();
		assertThat(this.envVariables.getDurationOrElse("durationvar", Duration.ofSeconds(5)))
			.isEqualTo(Duration.ofSeconds(5));
		Consumer<Duration> consumer = mockConsumer();
		this.envVariables.applyDuration("durationvar", consumer);
		then(consumer).shouldHaveNoInteractions();
		assertThat(output).contains("Negative duration value for environment variable 'durationvar': '-1000'");
	}

	@SuppressWarnings("unchecked")
	private <T> Consumer<T> mockConsumer() {
		return mock(Consumer.class);
	}

}
