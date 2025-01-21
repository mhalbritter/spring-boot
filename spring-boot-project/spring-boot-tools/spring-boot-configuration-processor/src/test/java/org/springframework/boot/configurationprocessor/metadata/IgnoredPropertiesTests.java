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

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.springframework.boot.configurationprocessor.json.JSONException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Tests for {@link IgnoredProperties}.
 *
 * @author Moritz Halbritter
 */
class IgnoredPropertiesTests {

	@Test
	void shouldReturnIfPropertyIsIgnored() {
		IgnoredProperties ignoredProperties = IgnoredProperties.of(Set.of("prop1", "prop2"));
		assertThat(ignoredProperties.isIgnored("prop1")).isTrue();
		assertThat(ignoredProperties.isIgnored("prop2")).isTrue();
		assertThat(ignoredProperties.isIgnored("prop3")).isFalse();
	}

	@Test
	void shouldLoadFromJson() throws JSONException, IOException {
		IgnoredProperties ignoredProperties = IgnoredProperties.loadFromJson(asStream("""
				{
					"properties": [
						{ "name": "prop1" },
						{ "name": "prop2" }
					]
				}
				"""));
		assertThat(ignoredProperties.isIgnored("prop1")).isTrue();
		assertThat(ignoredProperties.isIgnored("prop2")).isTrue();
		assertThat(ignoredProperties.isIgnored("prop3")).isFalse();
	}

	@Test
	void shouldFailOnSuperfluousKeys() {
		assertThatIllegalStateException().isThrownBy(() -> IgnoredProperties.loadFromJson(asStream("""
				{
					"properties": [
						{ "name": "prop1" },
						{ "name": "prop2" }
					],
					"someKey": true
				}
				"""))).withMessage("Expected only keys [properties], but found additional keys [someKey]");
	}

	private InputStream asStream(String string) {
		return new ByteArrayInputStream(string.getBytes(StandardCharsets.UTF_8));
	}

}
