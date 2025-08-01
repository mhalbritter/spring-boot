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

package org.springframework.boot.web.server;

import java.util.ArrayList;
import java.util.List;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.properties.ConfigurationPropertiesSource;
import org.springframework.lang.Contract;

/**
 * Simple server-independent abstraction for SSL configuration.
 *
 * @author Andy Wilkinson
 * @author Vladimir Tsanev
 * @author Stephane Nicoll
 * @author Scott Frederick
 * @since 2.0.0
 */
@ConfigurationPropertiesSource
public class Ssl {

	/**
	 * Whether to enable SSL support.
	 */
	private boolean enabled = true;

	/**
	 * Name of a configured SSL bundle.
	 */
	private @Nullable String bundle;

	/**
	 * Client authentication mode. Requires a trust store.
	 */
	private @Nullable ClientAuth clientAuth;

	/**
	 * Supported SSL ciphers.
	 */
	private String @Nullable [] ciphers;

	/**
	 * Enabled SSL protocols.
	 */
	private String @Nullable [] enabledProtocols;

	/**
	 * Alias that identifies the key in the key store.
	 */
	private @Nullable String keyAlias;

	/**
	 * Password used to access the key in the key store.
	 */
	private @Nullable String keyPassword;

	/**
	 * Path to the key store that holds the SSL certificate (typically a jks file).
	 */
	private @Nullable String keyStore;

	/**
	 * Password used to access the key store.
	 */
	private @Nullable String keyStorePassword;

	/**
	 * Type of the key store.
	 */
	private @Nullable String keyStoreType;

	/**
	 * Provider for the key store.
	 */
	private @Nullable String keyStoreProvider;

	/**
	 * Trust store that holds SSL certificates.
	 */
	private @Nullable String trustStore;

	/**
	 * Password used to access the trust store.
	 */
	private @Nullable String trustStorePassword;

	/**
	 * Type of the trust store.
	 */
	private @Nullable String trustStoreType;

	/**
	 * Provider for the trust store.
	 */
	private @Nullable String trustStoreProvider;

	/**
	 * Path to a PEM-encoded SSL certificate file.
	 */
	private @Nullable String certificate;

	/**
	 * Path to a PEM-encoded private key file for the SSL certificate.
	 */
	private @Nullable String certificatePrivateKey;

	/**
	 * Path to a PEM-encoded SSL certificate authority file.
	 */
	private @Nullable String trustCertificate;

	/**
	 * Path to a PEM-encoded private key file for the SSL certificate authority.
	 */
	private @Nullable String trustCertificatePrivateKey;

	/**
	 * SSL protocol to use.
	 */
	private String protocol = "TLS";

	/**
	 * Mapping of host names to SSL bundles for SNI configuration.
	 */
	private List<ServerNameSslBundle> serverNameBundles = new ArrayList<>();

	/**
	 * Return whether to enable SSL support.
	 * @return whether to enable SSL support
	 */
	public boolean isEnabled() {
		return this.enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	/**
	 * Return the name of the SSL bundle to use.
	 * @return the SSL bundle name
	 * @since 3.1.0
	 */
	public @Nullable String getBundle() {
		return this.bundle;
	}

	/**
	 * Set the name of the SSL bundle to use.
	 * @param bundle the SSL bundle name
	 * @since 3.1.0
	 */
	public void setBundle(@Nullable String bundle) {
		this.bundle = bundle;
	}

	/**
	 * Return Whether client authentication is not wanted ("none"), wanted ("want") or
	 * needed ("need"). Requires a trust store.
	 * @return the {@link ClientAuth} to use
	 */
	public @Nullable ClientAuth getClientAuth() {
		return this.clientAuth;
	}

	public void setClientAuth(@Nullable ClientAuth clientAuth) {
		this.clientAuth = clientAuth;
	}

	/**
	 * Return the supported SSL ciphers.
	 * @return the supported SSL ciphers
	 */
	public String @Nullable [] getCiphers() {
		return this.ciphers;
	}

	public void setCiphers(String @Nullable [] ciphers) {
		this.ciphers = ciphers;
	}

	/**
	 * Return the enabled SSL protocols.
	 * @return the enabled SSL protocols.
	 */
	public String @Nullable [] getEnabledProtocols() {
		return this.enabledProtocols;
	}

	public void setEnabledProtocols(String @Nullable [] enabledProtocols) {
		this.enabledProtocols = enabledProtocols;
	}

	/**
	 * Return the alias that identifies the key in the key store.
	 * @return the key alias
	 */
	public @Nullable String getKeyAlias() {
		return this.keyAlias;
	}

	public void setKeyAlias(@Nullable String keyAlias) {
		this.keyAlias = keyAlias;
	}

	/**
	 * Return the password used to access the key in the key store.
	 * @return the key password
	 */
	public @Nullable String getKeyPassword() {
		return this.keyPassword;
	}

	public void setKeyPassword(@Nullable String keyPassword) {
		this.keyPassword = keyPassword;
	}

	/**
	 * Return the path to the key store that holds the SSL certificate (typically a jks
	 * file).
	 * @return the path to the key store
	 */
	public @Nullable String getKeyStore() {
		return this.keyStore;
	}

	public void setKeyStore(@Nullable String keyStore) {
		this.keyStore = keyStore;
	}

	/**
	 * Return the password used to access the key store.
	 * @return the key store password
	 */
	public @Nullable String getKeyStorePassword() {
		return this.keyStorePassword;
	}

	public void setKeyStorePassword(@Nullable String keyStorePassword) {
		this.keyStorePassword = keyStorePassword;
	}

	/**
	 * Return the type of the key store.
	 * @return the key store type
	 */
	public @Nullable String getKeyStoreType() {
		return this.keyStoreType;
	}

	public void setKeyStoreType(@Nullable String keyStoreType) {
		this.keyStoreType = keyStoreType;
	}

	/**
	 * Return the provider for the key store.
	 * @return the key store provider
	 */
	public @Nullable String getKeyStoreProvider() {
		return this.keyStoreProvider;
	}

	public void setKeyStoreProvider(@Nullable String keyStoreProvider) {
		this.keyStoreProvider = keyStoreProvider;
	}

	/**
	 * Return the trust store that holds SSL certificates.
	 * @return the trust store
	 */
	public @Nullable String getTrustStore() {
		return this.trustStore;
	}

	public void setTrustStore(@Nullable String trustStore) {
		this.trustStore = trustStore;
	}

	/**
	 * Return the password used to access the trust store.
	 * @return the trust store password
	 */
	public @Nullable String getTrustStorePassword() {
		return this.trustStorePassword;
	}

	public void setTrustStorePassword(@Nullable String trustStorePassword) {
		this.trustStorePassword = trustStorePassword;
	}

	/**
	 * Return the type of the trust store.
	 * @return the trust store type
	 */
	public @Nullable String getTrustStoreType() {
		return this.trustStoreType;
	}

	public void setTrustStoreType(@Nullable String trustStoreType) {
		this.trustStoreType = trustStoreType;
	}

	/**
	 * Return the provider for the trust store.
	 * @return the trust store provider
	 */
	public @Nullable String getTrustStoreProvider() {
		return this.trustStoreProvider;
	}

	public void setTrustStoreProvider(@Nullable String trustStoreProvider) {
		this.trustStoreProvider = trustStoreProvider;
	}

	/**
	 * Return the location of the certificate in PEM format.
	 * @return the certificate location
	 */
	public @Nullable String getCertificate() {
		return this.certificate;
	}

	public void setCertificate(@Nullable String certificate) {
		this.certificate = certificate;
	}

	/**
	 * Return the location of the private key for the certificate in PEM format.
	 * @return the location of the certificate private key
	 */
	public @Nullable String getCertificatePrivateKey() {
		return this.certificatePrivateKey;
	}

	public void setCertificatePrivateKey(@Nullable String certificatePrivateKey) {
		this.certificatePrivateKey = certificatePrivateKey;
	}

	/**
	 * Return the location of the trust certificate authority chain in PEM format.
	 * @return the location of the trust certificate
	 */
	public @Nullable String getTrustCertificate() {
		return this.trustCertificate;
	}

	public void setTrustCertificate(@Nullable String trustCertificate) {
		this.trustCertificate = trustCertificate;
	}

	/**
	 * Return the location of the private key for the trust certificate in PEM format.
	 * @return the location of the trust certificate private key
	 */
	public @Nullable String getTrustCertificatePrivateKey() {
		return this.trustCertificatePrivateKey;
	}

	public void setTrustCertificatePrivateKey(@Nullable String trustCertificatePrivateKey) {
		this.trustCertificatePrivateKey = trustCertificatePrivateKey;
	}

	/**
	 * Return the SSL protocol to use.
	 * @return the SSL protocol
	 */
	public String getProtocol() {
		return this.protocol;
	}

	public void setProtocol(String protocol) {
		this.protocol = protocol;
	}

	/**
	 * Returns if SSL is enabled for the given instance.
	 * @param ssl the {@link Ssl SSL} instance or {@code null}
	 * @return {@code true} if SSL is enabled
	 * @since 3.1.0
	 */
	@Contract("null -> false")
	public static boolean isEnabled(@Nullable Ssl ssl) {
		return (ssl != null) && ssl.isEnabled();
	}

	/**
	 * Return the mapping of host names to SSL bundles for SNI configuration.
	 * @return the host name to SSL bundle mapping
	 */
	public List<ServerNameSslBundle> getServerNameBundles() {
		return this.serverNameBundles;
	}

	public void setServerNameBundles(List<ServerNameSslBundle> serverNameBundles) {
		this.serverNameBundles = serverNameBundles;
	}

	/**
	 * Factory method to create an {@link Ssl} instance for a specific bundle name.
	 * @param bundle the name of the bundle
	 * @return a new {@link Ssl} instance with the bundle set
	 * @since 3.1.0
	 */
	public static Ssl forBundle(String bundle) {
		Ssl ssl = new Ssl();
		ssl.setBundle(bundle);
		return ssl;
	}

	public record ServerNameSslBundle(String serverName, String bundle) {
	}

	/**
	 * Client authentication types.
	 */
	public enum ClientAuth {

		/**
		 * Client authentication is not wanted.
		 */
		NONE,

		/**
		 * Client authentication is wanted but not mandatory.
		 */
		WANT,

		/**
		 * Client authentication is needed and mandatory.
		 */
		NEED;

		/**
		 * Map an optional {@link ClientAuth} value to a different type.
		 * @param <R> the result type
		 * @param clientAuth the client auth to map (may be {@code null})
		 * @param none the value for {@link ClientAuth#NONE} or {@code null}
		 * @param want the value for {@link ClientAuth#WANT}
		 * @param need the value for {@link ClientAuth#NEED}
		 * @return the mapped value
		 * @since 3.1.0
		 */
		public static <R> @Nullable R map(@Nullable ClientAuth clientAuth, @Nullable R none, R want, R need) {
			return switch ((clientAuth != null) ? clientAuth : NONE) {
				case NONE -> none;
				case WANT -> want;
				case NEED -> need;
			};
		}

	}

}
