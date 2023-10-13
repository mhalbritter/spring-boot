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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.boot.ssl.SslStoreBundle;
import org.springframework.util.Assert;

/**
 * A {@link SslStoreBundle} which uses a directory containing certificates and keys in PEM
 * encoding.
 *
 * @author Moritz Halbritter
 * @since 3.2.0
 * @see PemSslStoreBundle
 */
public class PemDirectorySslStoreBundle implements SslStoreBundle {

	private final SslStoreBundle delegate;

	public PemDirectorySslStoreBundle(StoreDetails keyStore, StoreDetails trustStore, String keyAlias,
			String keyPassword, boolean verifyKeys) {
		this.delegate = new PemSslStoreBundle(getStoreDetails(keyStore), getStoreDetails(trustStore), keyAlias,
				keyPassword, verifyKeys);
	}

	@Override
	public KeyStore getKeyStore() {
		return this.delegate.getKeyStore();
	}

	@Override
	public String getKeyStorePassword() {
		return this.delegate.getKeyStorePassword();
	}

	@Override
	public KeyStore getTrustStore() {
		return this.delegate.getTrustStore();
	}

	private static PemSslStoreDetails getStoreDetails(StoreDetails store) {
		if (store == null) {
			return null;
		}
		Certificate certificate = loadCertificate(store.certificate());
		PrivateKey privateKey = loadPrivateKey(store.privateKey(), certificate);
		return new PemSslStoreDetails(store.type(), certificate.content(), privateKey.content(), store.privateKeyPassword());
	}

	private static Certificate loadCertificate(CertificateDetails certificate) {
		if (certificate.fixedLocation()) {
			return Certificate.load(certificate.location());
		}
		else {
			List<String> files = findFiles(certificate.location());
			return certificate.selectCertificate(files);
		}
	}

	private static PrivateKey loadPrivateKey(PrivateKeyDetails privateKey, Certificate certificate) {
		if (privateKey == null) {
			return null;
		}
		if (privateKey.fixedLocation()) {
			return PrivateKey.load(privateKey.location());
		}
		else {
			List<String> files = findFiles(privateKey.location());
			String location = privateKey.locate(files, certificate);
			return PrivateKey.load(location);
		}
	}

	private static List<String> findFiles(String location) {
		Assert.state(!location.startsWith("classpath:"), "Wildcard with 'classpath:' locations not supported");
		Path directory = Path.of(removePrefix(location, "file:")).toAbsolutePath();
		try (Stream<Path> fileStream = Files.list(directory)) {
			return fileStream.map((p) -> "file:" + p.toAbsolutePath()).toList();
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Failed to list files in directory '%s'".formatted(directory), ex);
		}
	}

	private static String removePrefix(String input, String prefix) {
		if (input.startsWith(prefix)) {
			return input.substring(prefix.length());
		}
		return input;
	}

	/**
	 * Store details.
	 * @param type the key store type, for example {@code JKS} or {@code PKCS11}. A
	 * {@code null} value will use {@link KeyStore#getDefaultType()}).
	 * @param certificate the certificate
	 * @param privateKey the private key or {@code null}
	 * @param privateKeyPassword a password used to decrypt an encrypted private key or {@code null}
	 */
	public record StoreDetails(String type, CertificateDetails certificate, PrivateKeyDetails privateKey,
			String privateKeyPassword) {
		public StoreDetails {
			Assert.notNull(certificate, "Certificate must not be null");
		}
	}

	/**
	 * Certificate details.
	 * @param location the location of the certificate
	 * @param certificateMatcher the certificate matcher or {@code null}
	 * @param certificateSelector the certificate selector or {@code null}
	 */
	public record CertificateDetails(String location, CertificateMatcher certificateMatcher,
			CertificateSelector certificateSelector) {
		public CertificateDetails {
			Assert.notNull(location, "Location must not be null");
		}

		public static CertificateDetails forFixedLocation(String location) {
			return new CertificateDetails(location, null, null);
		}

		boolean fixedLocation() {
			return this.certificateMatcher == null || this.certificateSelector == null;
		}

		Certificate selectCertificate(List<String> locations) {
			List<Certificate> certificates = loadCertificates(locations);
			Assert.state(!certificates.isEmpty(), "No certificates found in %s".formatted(locations));
			Certificate selected = this.certificateSelector.select(certificates);
			Assert.notNull(selected, "No certificate has been selected from %s".formatted(locations));
			return selected;
		}

		private List<Certificate> loadCertificates(List<String> locations) {
			return locations
					.stream()
					.filter(this.certificateMatcher::matches)
					.map(Certificate::load)
					.toList();
		}
	}

	/**
	 * Private key details.
	 * @param location the location of the private key
	 * @param keyLocator the key locator or {@code null}
	 */
	public record PrivateKeyDetails(String location, KeyLocator keyLocator) {
		public PrivateKeyDetails {
			Assert.notNull(location, "Location must not be null");
		}

		public static PrivateKeyDetails forFixedLocation(String location) {
			return new PrivateKeyDetails(location, null);
		}

		boolean fixedLocation() {
			return this.keyLocator == null;
		}

		String locate(List<String> locations, Certificate certificate) {
			String key = this.keyLocator.locate(locations, certificate);
			if (key == null || !locations.contains(key)) {
				throw new IllegalStateException("Key could not be found in locations %s".formatted(locations));
			}
			return key;
		}
	}

	/**
	 * A Certificate.
	 *
	 * @param location the certificate location
	 * @param content the certificate content
	 * @param certificate the parsed certificate
	 */
	public record Certificate(String location, String content, X509Certificate certificate) {
		public Certificate {
			Assert.notNull(location, "Location must not be null");
			Assert.notNull(content, "Content must not be null");
			Assert.notNull(certificate, "Certificate must not be null");
		}

		static Certificate load(String location) {
			String content = PemContent.load(location);
			X509Certificate[] x509Certificates = PemCertificateParser.parse(content);
			if (x509Certificates == null || x509Certificates.length == 0) {
				throw new IllegalStateException("No certificates found at '%s'".formatted(location));
			}
			// Always use first certificate of the chain
			return new Certificate(location, content, x509Certificates[0]);
		}
	}

	private record PrivateKey(String location, String content) {
		static PrivateKey load(String location) {
			return new PrivateKey(location, PemContent.load(location));
		}
	}

	public interface KeyLocator {

		/**
		 * Locates the key belonging to the given {@code certificate}.
		 * @param locations the available key locations
		 * @param certificate the certificate to locate a key for
		 * @return the location to the key, or {@code null}
		 */
		String locate(List<String> locations, Certificate certificate);

		/**
		 * Creates a {@link KeyLocator} which selects keys based on the file extension.
		 * @param keyExtension the extension of key files
		 * @return the key locator
		 */
		static KeyLocator withExtension(String keyExtension) {
			return new SuffixKeyLocator(keyExtension);
		}

	}

	public interface CertificateMatcher {

		/**
		 * Decides whether the given {@code location} is a certificate.
		 * @param location the location to decide on
		 * @return whether the location is a certificate
		 */
		boolean matches(String location);

		/**
		 * Creates a {@link CertificateMatcher} which selects certificates based on the
		 * file extension.
		 * @param certificateExtension the extension of certificate files
		 * @return the certificate matcher
		 */
		static CertificateMatcher withExtension(String certificateExtension) {
			return new SuffixCertificateMatcher(certificateExtension);
		}

	}

	public interface CertificateSelector {

		/**
		 * Selects a certificate from the given {@code certificates}.
		 * @param certificates the certificates
		 * @return the selected certificate
		 */
		Certificate select(List<Certificate> certificates);

		/**
		 * Creates a {@link CertificateSelector} which selects the certificate with the
		 * maximum not after field.
		 * @return the certificate selector
		 */
		static CertificateSelector maximumNotAfter() {
			return new CertificateSelectors.MaximumNotAfterCertificateSelector();
		}

		/**
		 * Creates a {@link CertificateSelector} which selects the certificate with the
		 * maximum not before field.
		 * @return the certificate selector
		 */
		static CertificateSelector maximumNotBefore() {
			return new CertificateSelectors.MaximumNotBeforeCertificateSelector();
		}

		/**
		 * Creates a {@link CertificateSelector} which selects the certificate with the
		 * newest file creation date.
		 * @return the certificate selector
		 */
		static CertificateSelector newestFile() {
			// TODO
			return null;
			// return new CertificateSelectors.NewestFileCertificateSelector();
		}

	}

}
