/*
 * Copyright 2012-2023 the original author or authors.
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

package org.springframework.boot.autoconfigure.ssl;

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.ssl.PatternParser.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Tests for {@link PatternParser}.
 *
 * @author Moritz Halbritter
 */
class PatternParserTests {

	@Test
	void noPattern() {
		assertThat(parse("")).isNull();
		assertThat(parse("test.txt")).isNull();
		assertThat(parse("/some/directory/test.txt")).isNull();
		assertThat(parse("file:/some/directory/test.txt")).isNull();
		assertThat(parse("classpath:test.txt")).isNull();
		assertThat(parse("classpath:/some/directory/test.txt")).isNull();
	}

	@Test
	void relative() {
		Pattern pattern = parse("*.txt");
		assertThat(pattern.directory()).isEmpty();
		assertThat(pattern.extension()).isEqualTo(".txt");
	}

	@Test
	void absolute() {
		Pattern pattern = parse("/some/directory/*.txt");
		assertThat(pattern.directory()).isEqualTo("/some/directory/");
		assertThat(pattern.extension()).isEqualTo(".txt");
	}

	@Test
	void shouldFailIfMoreThanOneWildcard() {
		assertThatIllegalArgumentException().isThrownBy(() -> parse("/some/*/directory/*.txt"))
			.withMessageContaining("Found multiple wildcards");
	}

	@Test
	void shouldFailIfDirectoryAfterWildcard() {
		assertThatIllegalArgumentException().isThrownBy(() -> parse("/some/directory/*.txt/1.txt"))
			.withMessageContaining("Found slash after wildcard");
	}

	@Test
	void shouldFailIfNoExtension() {
		assertThatIllegalArgumentException().isThrownBy(() -> parse("/some/directory/*"))
			.withMessageContaining("Expected extension");
		assertThatIllegalArgumentException().isThrownBy(() -> parse("/some/directory/*foo"))
			.withMessageContaining("Expected extension");
	}

	private Pattern parse(String input) {
		return PatternParser.parse(input);
	}

}
