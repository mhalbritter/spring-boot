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

package org.springframework.boot.ssl.pem;

import java.security.KeyStore;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Details for an individual trust or key store in a {@link PemSslStoreBundle}.
 *
 * @param type the key store type, for example {@code JKS} or {@code PKCS11}. A
 * {@code null} value will use {@link KeyStore#getDefaultType()}).
 * @param alias the alias used when setting entries in the {@link KeyStore}
 * @param password the password used
 * {@link KeyStore#setKeyEntry(String, java.security.Key, char[], java.security.cert.Certificate[])
 * setting key entries} in the {@link KeyStore}
 * @param multipleCertificates the certificates contents (either the PEM content itself or
 * references to the resources to load). When a {@link #privateKey() private key} is
 * present this value is treated as a certificate chain, otherwise it is treated a list of
 * certificates that should all be registered.
 * @param privateKey the private key content (either the PEM content itself or a reference
 * to the resource to load)
 * @param privateKeyPassword a password used to decrypt an encrypted private key
 * @author Scott Frederick
 * @author Phillip Webb
 * @since 3.1.0
 * @see PemSslStore#load(PemSslStoreDetails)
 */
public record PemSslStoreDetails(String type, String alias, String password, List<String> multipleCertificates,
		String privateKey, String privateKeyPassword) {

	/**
	 * Create a new {@link PemSslStoreDetails} instance.
	 * @param type the key store type, for example {@code JKS} or {@code PKCS11}. A
	 * {@code null} value will use {@link KeyStore#getDefaultType()}).
	 * @param alias the alias used when setting entries in the {@link KeyStore}
	 * @param password the password used
	 * {@link KeyStore#setKeyEntry(String, java.security.Key, char[], java.security.cert.Certificate[])
	 * setting key entries} in the {@link KeyStore}
	 * @param certificate the certificate content (either the PEM content itself or a
	 * reference to the resource to load)
	 * @param privateKey the private key content (either the PEM content itself or a
	 * reference to the resource to load)
	 * @param privateKeyPassword a password used to decrypt an encrypted private key
	 * @since 3.2.0
	 */
	public PemSslStoreDetails(String type, String alias, String password, String certificate, String privateKey,
			String privateKeyPassword) {
		this(type, alias, password, StringUtils.hasText(certificate) ? List.of(certificate) : Collections.emptyList(),
				privateKey, privateKeyPassword);
	}

	/**
	 * Create a new {@link PemSslStoreDetails} instance.
	 * @param type the key store type, for example {@code JKS} or {@code PKCS11}. A
	 * {@code null} value will use {@link KeyStore#getDefaultType()}).
	 * @param alias the alias used when setting entries in the {@link KeyStore}
	 * @param password the password used
	 * {@link KeyStore#setKeyEntry(String, java.security.Key, char[], java.security.cert.Certificate[])
	 * setting key entries} in the {@link KeyStore}
	 * @param multipleCertificates the certificate contents (either the PEM contents
	 * itself or references to the resources to load)
	 * @param privateKey the private key content (either the PEM content itself or a
	 * reference to the resource to load)
	 * @param privateKeyPassword a password used to decrypt an encrypted private key
	 * @since 3.5.0
	 */
	public PemSslStoreDetails {
		Assert.notNull(multipleCertificates, "'multipleCertificates' must not be null");
	}

	/**
	 * Create a new {@link PemSslStoreDetails} instance.
	 * @param type the key store type, for example {@code JKS} or {@code PKCS11}. A
	 * {@code null} value will use {@link KeyStore#getDefaultType()}).
	 * @param certificate the certificate content (either the PEM content itself or a
	 * reference to the resource to load)
	 * @param privateKey the private key content (either the PEM content itself or a
	 * reference to the resource to load)
	 * @param privateKeyPassword a password used to decrypt an encrypted private key
	 */
	public PemSslStoreDetails(String type, String certificate, String privateKey, String privateKeyPassword) {
		this(type, null, null, certificate, privateKey, privateKeyPassword);
	}

	/**
	 * Create a new {@link PemSslStoreDetails} instance.
	 * @param type the key store type, for example {@code JKS} or {@code PKCS11}. A
	 * {@code null} value will use {@link KeyStore#getDefaultType()}).
	 * @param certificate the certificate content (either the PEM content itself or a
	 * reference to the resource to load)
	 * @param privateKey the private key content (either the PEM content itself or a
	 * reference to the resource to load)
	 */
	public PemSslStoreDetails(String type, String certificate, String privateKey) {
		this(type, certificate, privateKey, null);
	}

	/**
	 * Return a new {@link PemSslStoreDetails} instance with a new alias.
	 * @param alias the new alias
	 * @return a new {@link PemSslStoreDetails} instance
	 * @since 3.2.0
	 */
	public PemSslStoreDetails withAlias(String alias) {
		return new PemSslStoreDetails(this.type, alias, this.password, this.multipleCertificates, this.privateKey,
				this.privateKeyPassword);
	}

	/**
	 * Return a new {@link PemSslStoreDetails} instance with a new password.
	 * @param password the new password
	 * @return a new {@link PemSslStoreDetails} instance
	 * @since 3.2.0
	 */
	public PemSslStoreDetails withPassword(String password) {
		return new PemSslStoreDetails(this.type, this.alias, password, this.multipleCertificates, this.privateKey,
				this.privateKeyPassword);
	}

	/**
	 * Return a new {@link PemSslStoreDetails} instance with a new private key.
	 * @param privateKey the new private key
	 * @return a new {@link PemSslStoreDetails} instance
	 */
	public PemSslStoreDetails withPrivateKey(String privateKey) {
		return new PemSslStoreDetails(this.type, this.alias, this.password, this.multipleCertificates, privateKey,
				this.privateKeyPassword);
	}

	/**
	 * Return a new {@link PemSslStoreDetails} instance with a new private key password.
	 * @param privateKeyPassword the new private key password
	 * @return a new {@link PemSslStoreDetails} instance
	 */
	public PemSslStoreDetails withPrivateKeyPassword(String privateKeyPassword) {
		return new PemSslStoreDetails(this.type, this.alias, this.password, this.multipleCertificates, this.privateKey,
				privateKeyPassword);
	}

	/**
	 * Return the certificate content (either the PEM content itself or a reference to the
	 * resource to load).
	 * @return the certificate content (either the PEM content itself or a reference to
	 * the resource to load)
	 * @deprecated in 3.5.0 for removal in 3.7.0 in favor of
	 * {@link #multipleCertificates()}
	 */
	@Deprecated(since = "3.5.0", forRemoval = true)
	public String certificates() {
		if (this.multipleCertificates.isEmpty()) {
			return null;
		}
		return this.multipleCertificates.get(0);
	}

	boolean isEmpty() {
		return isEmpty(this.type) && isEmpty(this.multipleCertificates) && isEmpty(this.privateKey);
	}

	private boolean isEmpty(List<String> certificates) {
		return CollectionUtils.isEmpty(certificates);
	}

	private boolean isEmpty(String value) {
		return !StringUtils.hasText(value);
	}

	/**
	 * Factory method to create a new {@link PemSslStoreDetails} instance for the given
	 * certificate. <b>Note:</b> This method doesn't actually check if the provided value
	 * only contains a single certificate. It is functionally equivalent to
	 * {@link #forCertificates(String)}.
	 * @param certificate the certificate content (either the PEM content itself or a
	 * reference to the resource to load)
	 * @return a new {@link PemSslStoreDetails} instance.
	 */
	public static PemSslStoreDetails forCertificate(String certificate) {
		return forCertificates(certificate);
	}

	/**
	 * Factory method to create a new {@link PemSslStoreDetails} instance for the given
	 * certificates.
	 * @param certificates the certificates content (either the PEM content itself or a
	 * reference to the resource to load)
	 * @return a new {@link PemSslStoreDetails} instance.
	 * @since 3.2.0
	 */
	public static PemSslStoreDetails forCertificates(String certificates) {
		return new PemSslStoreDetails(null, certificates, null);
	}

	/**
	 * Factory method to create a new {@link PemSslStoreDetails} instance for the given
	 * certificates.
	 * @param certificates the certificates contents (either the PEM content itself or
	 * references to the resources to load)
	 * @return a new {@link PemSslStoreDetails} instance.
	 * @since 3.5.0
	 */
	public static PemSslStoreDetails forMultipleCertificates(String... certificates) {
		return new PemSslStoreDetails(null, null, null, List.of(certificates), null, null);
	}

	/**
	 * Factory method to create a new {@link PemSslStoreDetails} instance for the given
	 * certificates.
	 * @param certificates the certificates contents (either the PEM content itself or
	 * references to the resources to load)
	 * @return a new {@link PemSslStoreDetails} instance.
	 * @since 3.5.0
	 */
	public static PemSslStoreDetails forMultipleCertificates(Collection<String> certificates) {
		return new PemSslStoreDetails(null, null, null, List.copyOf(certificates), null, null);
	}

}
