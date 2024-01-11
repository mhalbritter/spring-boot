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

package org.springframework.boot.web.server;

import org.springframework.boot.ssl.NoSuchSslBundleException;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundleKey;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.boot.ssl.SslManagerBundle;
import org.springframework.boot.ssl.SslOptions;
import org.springframework.boot.ssl.SslStoreBundle;
import org.springframework.boot.ssl.jks.JksSslStoreBundle;
import org.springframework.boot.ssl.jks.JksSslStoreDetails;
import org.springframework.boot.ssl.pem.PemSslStoreBundle;
import org.springframework.boot.ssl.pem.PemSslStoreDetails;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * {@link SslBundle} backed by {@link Ssl}.
 *
 * @author Scott Frederick
 * @author Phillip Webb
 * @since 3.1.0
 */
public final class WebServerSslBundle implements SslBundle {

	private final SslStoreBundle stores;

	private final SslBundleKey key;

	private final SslOptions options;

	private final String protocol;

	private final SslManagerBundle managers;

	private WebServerSslBundle(SslStoreBundle stores, String keyPassword, Ssl ssl) {
		this.stores = stores;
		this.key = SslBundleKey.of(keyPassword, ssl.getKeyAlias());
		this.protocol = ssl.getProtocol();
		this.options = SslOptions.of(ssl.getCiphers(), ssl.getEnabledProtocols());
		this.managers = SslManagerBundle.from(this.stores, this.key);
	}

	private static SslStoreBundle createPemStoreBundle(Ssl ssl) {
		PemSslStoreDetails keyStoreDetails = new PemSslStoreDetails(ssl.getKeyStoreType(), ssl.getCertificate(),
				ssl.getCertificatePrivateKey())
			.withAlias(ssl.getKeyAlias());
		PemSslStoreDetails trustStoreDetails = new PemSslStoreDetails(ssl.getTrustStoreType(),
				ssl.getTrustCertificate(), ssl.getTrustCertificatePrivateKey());
		return new PemSslStoreBundle(keyStoreDetails, trustStoreDetails);
	}

	private static SslStoreBundle createJksStoreBundle(Ssl ssl) {
		JksSslStoreDetails keyStoreDetails = new JksSslStoreDetails(ssl.getKeyStoreType(), ssl.getKeyStoreProvider(),
				ssl.getKeyStore(), ssl.getKeyStorePassword());
		JksSslStoreDetails trustStoreDetails = new JksSslStoreDetails(ssl.getTrustStoreType(),
				ssl.getTrustStoreProvider(), ssl.getTrustStore(), ssl.getTrustStorePassword());
		return new JksSslStoreBundle(keyStoreDetails, trustStoreDetails);
	}

	@Override
	public SslStoreBundle getStores() {
		return this.stores;
	}

	@Override
	public SslBundleKey getKey() {
		return this.key;
	}

	@Override
	public SslOptions getOptions() {
		return this.options;
	}

	@Override
	public String getProtocol() {
		return this.protocol;
	}

	@Override
	public SslManagerBundle getManagers() {
		return this.managers;
	}

	@Override
	public String toString() {
		return "WebServerSslBundle[stores=%s, key=%s, options=%s, protocol=%s, managers=%s]".formatted(this.stores, this.key, this.options, this.protocol, this.managers);
	}

	/**
	 * Get the {@link SslBundle} that should be used for the given {@link Ssl} instance.
	 * @param ssl the source ssl instance
	 * @return a {@link SslBundle} instance
	 * @throws NoSuchSslBundleException if a bundle lookup fails
	 */
	public static SslBundle get(Ssl ssl) throws NoSuchSslBundleException {
		return get(ssl, null);
	}

	/**
	 * Get the {@link SslBundle} that should be used for the given {@link Ssl} instance.
	 * @param ssl the source ssl instance
	 * @param sslBundles the bundles that should be used when {@link Ssl#getBundle()} is
	 * set
	 * @return a {@link SslBundle} instance
	 * @throws NoSuchSslBundleException if a bundle lookup fails
	 */
	public static SslBundle get(Ssl ssl, SslBundles sslBundles) throws NoSuchSslBundleException {
		Assert.state(Ssl.isEnabled(ssl), "SSL is not enabled");
		String keyPassword = ssl.getKeyPassword();
		String bundleName = ssl.getBundle();
		if (StringUtils.hasText(bundleName)) {
			Assert.state(sslBundles != null,
					() -> "SSL bundle '%s' was requested but no SslBundles instance was provided"
						.formatted(bundleName));
			return sslBundles.getBundle(bundleName);
		}
		SslStoreBundle stores = createStoreBundle(ssl);
		return new WebServerSslBundle(stores, keyPassword, ssl);
	}

	private static SslStoreBundle createStoreBundle(Ssl ssl) {
		if (hasCertificateProperties(ssl)) {
			return createPemStoreBundle(ssl);
		}
		if (hasJavaKeyStoreProperties(ssl)) {
			return createJksStoreBundle(ssl);
		}
		throw new IllegalStateException("SSL is enabled but no trust material is configured");
	}

	static SslBundle createCertificateFileSslStoreProviderDelegate(Ssl ssl) {
		if (!hasCertificateProperties(ssl)) {
			return null;
		}
		SslStoreBundle stores = createPemStoreBundle(ssl);
		return new WebServerSslBundle(stores, ssl.getKeyPassword(), ssl);
	}

	private static boolean hasCertificateProperties(Ssl ssl) {
		return Ssl.isEnabled(ssl) && ssl.getCertificate() != null && ssl.getCertificatePrivateKey() != null;
	}

	private static boolean hasJavaKeyStoreProperties(Ssl ssl) {
		return Ssl.isEnabled(ssl) && ssl.getKeyStore() != null
				|| (ssl.getKeyStoreType() != null && ssl.getKeyStoreType().equals("PKCS11"));
	}

}
