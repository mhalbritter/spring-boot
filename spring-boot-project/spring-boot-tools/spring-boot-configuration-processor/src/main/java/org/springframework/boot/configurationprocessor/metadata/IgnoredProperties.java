/*
 * Copyright 2012-2025 the original author or authors.
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

package org.springframework.boot.configurationprocessor.metadata;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;

/**
 * Ignored configuration properties.
 *
 * @author Moritz Halbritter
 * @since 3.5.0
 */
public final class IgnoredProperties {

	private final Set<String> properties;

	private IgnoredProperties(Set<String> properties) {
		this.properties = properties;
	}

	/**
	 * Creates an empty instance.
	 * @return the empty instance.
	 */
	public static IgnoredProperties empty() {
		return new IgnoredProperties(Collections.emptySet());
	}

	/**
	 * Creates an instance from the given collection of ignored properties.
	 * @param ignoredProperties the ignored properties
	 * @return the instance
	 */
	public static IgnoredProperties of(Collection<String> ignoredProperties) {
		return new IgnoredProperties(Set.copyOf(ignoredProperties));
	}

	/**
	 * Returns whether the given property is ignored.
	 * @param property the property to check
	 * @return whether the given property is ignored
	 */
	public boolean isIgnored(String property) {
		return this.properties.contains(property);
	}

	/**
	 * Loads an instance from the given stream containing JSON.
	 * @param stream the stream to load from
	 * @return the instance
	 * @throws IOException if something went wrong while doing I/O
	 * @throws JSONException if something went wrong while deserializing the JSON
	 */
	public static IgnoredProperties loadFromJson(InputStream stream) throws IOException, JSONException {
		JSONObject object = new JSONObject(toString(stream));
		checkAllowedKeys(object, "properties");
		JSONArray properties = assertType(object.opt("properties"), JSONArray.class, "properties");
		if (properties == null) {
			return IgnoredProperties.empty();
		}
		Set<String> ignoredProperties = new HashSet<>();
		for (int i = 0; i < properties.length(); i++) {
			JSONObject property = assertType(properties.get(i), JSONObject.class, "properties[%d]".formatted(i));
			checkAllowedKeys(property, "name");
			String name = assertType(property.get("name"), String.class, "properties[%d].name".formatted(i));
			if (name == null || name.isBlank()) {
				throw new JSONException("properties[%d].name can't be blank".formatted(i));
			}
			ignoredProperties.add(name);
		}
		return IgnoredProperties.of(ignoredProperties);
	}

	@SuppressWarnings("unchecked")
	private static <T> T assertType(Object value, Class<T> type, String path) throws JSONException {
		if (value == null) {
			return null;
		}
		if (type.isAssignableFrom(value.getClass())) {
			return (T) value;
		}
		throw new JSONException(
				"Expected %s to be of type %s, but was %s".formatted(path, type.getName(), value.getClass().getName()));
	}

	@SuppressWarnings("unchecked")
	private static void checkAllowedKeys(JSONObject object, String... allowedKeys) {
		Set<String> availableKeys = new TreeSet<>();
		object.keys().forEachRemaining((key) -> availableKeys.add((String) key));
		Arrays.stream(allowedKeys).forEach(availableKeys::remove);
		if (!availableKeys.isEmpty()) {
			throw new IllegalStateException("Expected only keys %s, but found additional keys %s"
				.formatted(new TreeSet<>(Arrays.asList(allowedKeys)), availableKeys));
		}
	}

	private static String toString(InputStream inputStream) throws IOException {
		return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
	}

}
