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
import java.io.UncheckedIOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.util.StringUtils;

/**
 * Support for OpenTelemetry environment variables.
 *
 * @author Moritz Halbritter
 * @since 4.1.0
 */
public final class OpenTelemetryEnvironmentVariables {

	private static final Log logger = LogFactory.getLog(OpenTelemetryEnvironmentVariables.class);

	private final Function<String, @Nullable String> envLookup;

	private OpenTelemetryEnvironmentVariables(Function<String, @Nullable String> envLookup) {
		this.envLookup = envLookup;
	}

	/**
	 * Checks if the specified environment variable exists.
	 * @param name the name of the environment variable to check
	 * @return whether the environment variable exists
	 */
	public boolean contains(String name) {
		return getString(name) != null;
	}

	/**
	 * Returns the value of a string environment variable or {@code null}.
	 * @param name the name of the environment variable
	 * @return the value or {@code null}
	 */
	public @Nullable String getString(String name) {
		String value = this.envLookup.apply(name);
		return (StringUtils.hasLength(value)) ? value : null;
	}

	/**
	 * Returns the value of a string environment variable or {@code defaultValue}.
	 * @param name the name of the environment variable
	 * @param defaultValue the default value to be used if the environment variable isn't
	 * set
	 * @return the value or {@code defaultValue}
	 */
	public String getStringOrElse(String name, String defaultValue) {
		String value = getString(name);
		return (value != null) ? value : defaultValue;
	}

	/**
	 * Calls the {@code consumer} with the value of a string environment variable if set.
	 * @param name the name of the environment variable
	 * @param consumer the consumer to call if the environment variable is set
	 */
	public void applyString(String name, Consumer<String> consumer) {
		String value = getString(name);
		if (value != null) {
			consumer.accept(value);
		}
	}

	/**
	 * Returns the value of a <a href=
	 * "https://opentelemetry.io/docs/specs/otel/configuration/common/#duration">duration
	 * environment variable</a> or {@code null}.
	 * @param name the name of the environment variable
	 * @return the value or {@code null}
	 */
	public @Nullable Duration getDuration(String name) {
		String string = getString(name);
		if (string == null) {
			return null;
		}
		try {
			int millis = Integer.parseInt(string);
			if (millis < 0) {
				logger.warn("Negative duration value for environment variable '%s': '%s'".formatted(name, string));
				return null;
			}
			return Duration.ofMillis(millis);
		}
		catch (NumberFormatException ex) {
			logger.warn("Invalid duration value for environment variable '%s': '%s'".formatted(name, string));
			return null;
		}
	}

	/**
	 * Returns the value of a duration environment variable or {@code defaultValue}.
	 * @param name the name of the environment variable
	 * @param defaultValue the default value to be used if the environment variable isn't
	 * set
	 * @return the value or {@code defaultValue}
	 */
	public Duration getDurationOrElse(String name, Duration defaultValue) {
		Duration duration = getDuration(name);
		return (duration != null) ? duration : defaultValue;
	}

	/**
	 * Calls the {@code consumer} with the value of an duration environment variable if
	 * set.
	 * @param name the name of the environment variable
	 * @param consumer the consumer to call if the environment variable is set
	 */
	public void applyDuration(String name, Consumer<Duration> consumer) {
		Duration value = getDuration(name);
		if (value != null) {
			consumer.accept(value);
		}
	}

	/**
	 * Returns the value of a <a href=
	 * "https://opentelemetry.io/docs/specs/otel/configuration/common/#timeout">timeout
	 * environment variable</a> or {@code null}. A timeout is a duration where a value of
	 * {@code 0} is treated as no limit, resulting in a maximum duration.
	 * @param name the name of the environment variable
	 * @return the value or {@code null}
	 */
	public @Nullable Duration getTimeout(String name) {
		Duration duration = getDuration(name);
		if (duration != null && duration.isZero()) {
			return Duration.ofMillis(Long.MAX_VALUE);
		}
		return duration;
	}

	/**
	 * Returns the value of a timeout environment variable or {@code defaultValue}.
	 * @param name the name of the environment variable
	 * @param defaultValue the default value to be used if the environment variable isn't
	 * set
	 * @return the value or {@code defaultValue}
	 * @see #getTimeout(String)
	 */
	public Duration getTimeoutOrElse(String name, Duration defaultValue) {
		Duration value = getTimeout(name);
		return (value != null) ? value : defaultValue;
	}

	/**
	 * Calls the {@code consumer} with the value of a timeout environment variable if set.
	 * @param name the name of the environment variable
	 * @param consumer the consumer to call if the environment variable is set
	 * @see #getTimeout(String)
	 */
	public void applyTimeout(String name, Consumer<Duration> consumer) {
		Duration value = getTimeout(name);
		if (value != null) {
			consumer.accept(value);
		}
	}

	/**
	 * Returns the contents of the file referenced in an environment variable or
	 * {@code null}.
	 * @param name the name of the environment variable
	 * @return the file contents or {@code null}
	 */
	public byte @Nullable [] getFileContent(String name) {
		String path = getString(name);
		if (path == null) {
			return null;
		}
		try {
			return Files.readAllBytes(Paths.get(path));
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Failed to read file '%s' referenced by '%s'".formatted(path, name), ex);
		}
	}

	/**
	 * Returns the contents of the file referenced in an environment variable or
	 * {@code defaultValue}.
	 * @param name the name of the environment variable
	 * @param defaultValue the default value to be used if the environment variable isn't
	 * set
	 * @return the file contents or {@code defaultValue}
	 */
	public byte[] getFileContentOrElse(String name, byte[] defaultValue) {
		byte[] value = getFileContent(name);
		return (value != null) ? value : defaultValue;
	}

	/**
	 * Calls the {@code consumer} with the contents of the file referenced in an
	 * environment variable if set.
	 * @param name the name of the environment variable
	 * @param consumer the consumer to call if the environment variable is set
	 */
	public void applyFileContent(String name, Consumer<byte[]> consumer) {
		byte[] value = getFileContent(name);
		if (value != null) {
			consumer.accept(value);
		}
	}

	/**
	 * Returns the value of a string environment variable parsed as http headers or
	 * {@code null}.
	 * @param name the name of the environment variable
	 * @return the value parsed as http headers or {@code null}
	 */
	public @Nullable Map<String, String> getHeaders(String name) {
		String value = getString(name);
		return (value != null) ? W3CHeaderParser.parse(value) : null;
	}

	/**
	 * Returns the value of a string environment variable parsed as http headers or
	 * {@code defaultValue}.
	 * @param name the name of the environment variable
	 * @param defaultValue the default value to be used if the environment variable isn't
	 * set
	 * @return the value parsed as http headers or {@code defaultValue}
	 */
	public Map<String, String> getHeadersOrElse(String name, Map<String, String> defaultValue) {
		Map<String, String> value = getHeaders(name);
		return (value != null) ? value : defaultValue;
	}

	/**
	 * Calls the {@code consumer} with the value of a string environment variable parsed
	 * as http headers if set.
	 * @param name the name of the environment variable
	 * @param consumer the consumer to call if the environment variable is set
	 */
	public void applyHeaders(String name, Consumer<Map<String, String>> consumer) {
		Map<String, String> value = getHeaders(name);
		if (value != null) {
			consumer.accept(value);
		}
	}

	/**
	 * Returns the value of a <a href=
	 * "https://opentelemetry.io/docs/specs/otel/configuration/sdk-environment-variables/#boolean">boolean
	 * environment variable</a>. Only the case-insensitive string {@code "true"} is
	 * treated as {@code true}. Unset, empty, and the case-insensitive string
	 * {@code "false"} are treated as {@code false}. Any other value is treated as
	 * {@code false} with a warning logged.
	 * @param name the name of the environment variable
	 * @return the boolean value
	 */
	public boolean getBoolean(String name) {
		String value = getString(name);
		if (value == null) {
			return false;
		}
		if ("true".equalsIgnoreCase(value)) {
			return true;
		}
		if ("false".equalsIgnoreCase(value)) {
			return false;
		}
		logger.warn("Invalid boolean value for environment variable '%s': '%s', falling back to false".formatted(name,
				value));
		return false;
	}

	/**
	 * Returns the value of an <a href=
	 * "https://opentelemetry.io/docs/specs/otel/configuration/common/#integer">integer
	 * environment variable</a> or {@code null}.
	 * @param name the name of the environment variable
	 * @return the value or {@code null}
	 */
	public @Nullable Integer getInteger(String name) {
		String string = getString(name);
		if (string == null) {
			return null;
		}
		try {
			return Integer.parseInt(string);
		}
		catch (NumberFormatException ex) {
			logger.warn("Invalid integer value for environment variable '%s': '%s'".formatted(name, string));
			return null;
		}
	}

	/**
	 * Returns the value of an integer environment variable or {@code defaultValue}.
	 * @param name the name of the environment variable
	 * @param defaultValue the default value to be used if the environment variable isn't
	 * set
	 * @return the value or {@code defaultValue}
	 */
	public int getIntegerOrElse(String name, int defaultValue) {
		Integer value = getInteger(name);
		return (value != null) ? value : defaultValue;
	}

	/**
	 * Calls the {@code consumer} with the value of an integer environment variable if
	 * set.
	 * @param name the name of the environment variable
	 * @param consumer the consumer to call if the environment variable is set
	 */
	public void applyInteger(String name, Consumer<Integer> consumer) {
		Integer value = getInteger(name);
		if (value != null) {
			consumer.accept(value);
		}
	}

	/**
	 * Returns the value of a double environment variable or {@code null}.
	 * @param name the name of the environment variable
	 * @return the value or {@code null}
	 */
	public @Nullable Double getDouble(String name) {
		String string = getString(name);
		if (string == null) {
			return null;
		}
		try {
			return Double.parseDouble(string);
		}
		catch (NumberFormatException ex) {
			logger.warn("Invalid double value for environment variable '%s': '%s'".formatted(name, string));
			return null;
		}
	}

	/**
	 * Returns the value of a double environment variable or {@code defaultValue}.
	 * @param name the name of the environment variable
	 * @param defaultValue the default value to be used if the environment variable isn't
	 * set
	 * @return the value or {@code defaultValue}
	 */
	public double getDoubleOrElse(String name, double defaultValue) {
		Double value = getDouble(name);
		return (value != null) ? value : defaultValue;
	}

	/**
	 * Calls the {@code consumer} with the value of a double environment variable if set.
	 * @param name the name of the environment variable
	 * @param consumer the consumer to call if the environment variable is set
	 */
	public void applyDouble(String name, Consumer<Double> consumer) {
		Double value = getDouble(name);
		if (value != null) {
			consumer.accept(value);
		}
	}

	/**
	 * Returns an {@link OpenTelemetryEnvironmentVariables} instance which reads from the
	 * system environment.
	 * @return the {@link OpenTelemetryEnvironmentVariables} instance
	 */
	public static OpenTelemetryEnvironmentVariables fromSystemEnv() {
		return new OpenTelemetryEnvironmentVariables(System::getenv);
	}

	/**
	 * Returns an {@link OpenTelemetryEnvironmentVariables} instance which reads from a
	 * map.
	 * @param map the map to read from
	 * @return the {@link OpenTelemetryEnvironmentVariables} instance
	 */
	public static OpenTelemetryEnvironmentVariables fromMap(Map<String, String> map) {
		return new OpenTelemetryEnvironmentVariables(map::get);
	}

	/**
	 * Parser for the W3C Baggage HTTP header as defined in the
	 * <a href="https://www.w3.org/TR/baggage/#header-content">W3C Baggage
	 * specification</a>.
	 */
	static final class W3CHeaderParser {

		private W3CHeaderParser() {
		}

		/**
		 * Parses a W3C Baggage header value into a map of key-value pairs. Properties
		 * attached to list members are ignored. Values are percent-decoded.
		 * @param header the baggage header string to parse
		 * @return the baggage entries
		 */
		static Map<String, String> parse(String header) {
			if (header.isEmpty()) {
				return Collections.emptyMap();
			}
			Map<String, String> result = new LinkedHashMap<>();
			for (String part : header.split(",", -1)) {
				part = part.strip();
				if (part.isEmpty()) {
					continue;
				}
				int semicolon = part.indexOf(';');
				String keyValue = (semicolon != -1) ? part.substring(0, semicolon) : part;
				int equals = keyValue.indexOf('=');
				if (equals == -1) {
					continue;
				}
				String key = keyValue.substring(0, equals).strip();
				String value = keyValue.substring(equals + 1).strip();
				if (key.isEmpty()) {
					continue;
				}
				result.put(key, percentDecode(value));
			}
			return result;
		}

		private static String percentDecode(String value) {
			if (value.indexOf('%') == -1) {
				return value;
			}
			return URLDecoder.decode(value, StandardCharsets.UTF_8);
		}

	}

}
