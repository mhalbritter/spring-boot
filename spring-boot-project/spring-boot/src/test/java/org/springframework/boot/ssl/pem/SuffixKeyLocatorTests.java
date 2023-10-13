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

import java.security.cert.X509Certificate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.boot.ssl.pem.PemDirectorySslStoreBundle.Certificate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Tests for {@link SuffixKeyLocator}.
 *
 * @author Moritz Halbritter
 */
class SuffixKeyLocatorTests {

	private final SuffixKeyLocator locator = new SuffixKeyLocator(".key");

	@Test
	void shouldMatchWithoutPrefix() {
		Certificate certificate = createCertificate("1.crt");
		String location = this.locator.locate(List.of("1.key", "2.key"), certificate);
		assertThat(location).isEqualTo("1.key");
	}

	@Test
	void shouldMatchWithPrefix() {
		Certificate certificate = createCertificate("file:1.crt");
		String location = this.locator.locate(List.of("file:1.key", "file:2.key"), certificate);
		assertThat(location).isEqualTo("file:1.key");
	}

	@Test
	void shouldMatchMixed() {
		Certificate certificate = createCertificate("1.crt");
		String location = this.locator.locate(List.of("file:1.key", "file:2.key"), certificate);
		assertThat(location).isEqualTo("file:1.key");
	}

	@Test
	void shouldMatchMixedOtherWayAround() {
		Certificate certificate = createCertificate("file:1.crt");
		String location = this.locator.locate(List.of("1.key", "2.key"), certificate);
		assertThat(location).isEqualTo("1.key");
	}

	@Test
	void shouldMatchWithoutPrefixDirectory() {
		Certificate certificate = createCertificate("/dir1/1.crt");
		String location = this.locator.locate(List.of("/dir2/1.key", "/dir2/2.key"), certificate);
		assertThat(location).isEqualTo("/dir2/1.key");
	}

	@Test
	void shouldMatchWithPrefixDirectory() {
		Certificate certificate = createCertificate("file:/dir1/1.crt");
		String location = this.locator.locate(List.of("file:/dir2/1.key", "file:/dir2/2.key"), certificate);
		assertThat(location).isEqualTo("file:/dir2/1.key");
	}

	@Test
	void shouldMatchMixedDirectory() {
		Certificate certificate = createCertificate("/dir2/1.crt");
		String location = this.locator.locate(List.of("file:/dir2/1.key", "file:/dir2/2.key"), certificate);
		assertThat(location).isEqualTo("file:/dir2/1.key");
	}

	@Test
	void shouldMatchMixedOtherWayAroundDirectory() {
		Certificate certificate = createCertificate("file:/dir1/1.crt");
		String location = this.locator.locate(List.of("/dir2/1.key", "/dir2/2.key"), certificate);
		assertThat(location).isEqualTo("/dir2/1.key");
	}

	@Test
	void shouldUseKeyExtension() {
		Certificate certificate = createCertificate("1.crt");
		String location = this.locator.locate(List.of("1.key", "1.dummy"), certificate);
		assertThat(location).isEqualTo("1.key");
	}

	@Test
	void shouldFailIfNoKeyIsFound() {
		Certificate certificate = createCertificate("1.crt");
		assertThatIllegalStateException().isThrownBy(() -> this.locator.locate(List.of("2.key", "3.key"), certificate))
			.withMessageContaining("Key for certificate '1.crt' named '1.key' not found in locations: [2.key, 3.key]");
	}

	private Certificate createCertificate(String location) {
		return new Certificate(location, "", Mockito.mock(X509Certificate.class));
	}

}
