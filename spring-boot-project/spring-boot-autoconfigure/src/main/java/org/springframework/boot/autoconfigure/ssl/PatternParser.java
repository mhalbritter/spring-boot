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

import org.springframework.util.StringUtils;

/**
 * @author Moritz Halbritter
 */
final class PatternParser {

	private PatternParser() {
	}

	/**
	 * Parses patterns in the form '*.txt' or '/some/directory/*.txt'.
	 * @param input input
	 * @return parsed pattern
	 */
	static Pattern parse(String input) {
		int numberOfStars = StringUtils.countOccurrencesOf(input, "*");
		if (numberOfStars == 0) {
			return null;
		}
		if (numberOfStars > 1) {
			throw new IllegalArgumentException("Found multiple wildcards in '%s'. Only one is allowed.".formatted(input));
		}
		int wildcard = findWildcardIndex(input);
		int lastSlash = input.lastIndexOf('/');
		int lastBackslash = input.lastIndexOf('\\');
		if (lastSlash > wildcard || lastBackslash > wildcard) {
			throw new IllegalArgumentException(
					"Found slash after wildcard in '%s', which is now allowed".formatted(input));
		}
		return new Pattern(input.substring(0, wildcard), input.substring(wildcard + 1));
	}

	private static int findWildcardIndex(String input) {
		int wildcard = input.lastIndexOf("*");
		if (wildcard == input.length() - 1 || input.charAt(wildcard + 1) != '.') {
			throw new IllegalArgumentException("Expected extension after wildcard in '%s'".formatted(input));
		}
		return wildcard;
	}


	record Pattern(String directory, String extension) {
	}

}
