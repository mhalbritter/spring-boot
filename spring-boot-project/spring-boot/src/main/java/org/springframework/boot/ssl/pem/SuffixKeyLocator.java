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

package org.springframework.boot.ssl.pem;

import java.util.List;

import org.springframework.boot.ssl.pem.PemDirectorySslStoreBundle.Certificate;
import org.springframework.boot.ssl.pem.PemDirectorySslStoreBundle.KeyLocator;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * A {@link KeyLocator} which matches files with a given suffix.
 *
 * @author Moritz Halbritter
 */
class SuffixKeyLocator implements KeyLocator {

	private final String keySuffix;

	SuffixKeyLocator(String keySuffix) {
		Assert.notNull(keySuffix, "keySuffix must not be null");
		this.keySuffix = keySuffix;
	}

	@Override
	public String locate(List<String> locations, Certificate certificate) {
		String certFilenameWithoutExtension = getFilenameWithoutExtension(certificate.location());
		String keyFilename = certFilenameWithoutExtension + this.keySuffix;
		for (String location : locations) {
			String locationFilename = getFilename(location);
			if (keyFilename.equals(locationFilename)) {
				return location;
			}
		}
		throw new IllegalStateException("Key for certificate '%s' named '%s' not found in locations: %s".formatted(certificate.location(), keyFilename, locations));
	}

	private static String getFilename(String location) {
		return StringUtils.getFilename(stripPrefix(location));
	}

	private static String getFilenameWithoutExtension(String location) {
		return StringUtils.stripFilenameExtension(StringUtils.getFilename(stripPrefix(location)));
	}

	private static String stripPrefix(String input) {
		int colon = input.indexOf(':');
		if (colon == -1) {
			return input;
		}
		return input.substring(colon + 1);
	}

}
