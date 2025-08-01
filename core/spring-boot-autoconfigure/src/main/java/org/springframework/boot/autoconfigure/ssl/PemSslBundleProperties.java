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

package org.springframework.boot.autoconfigure.ssl;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.ssl.pem.PemSslStoreBundle;

/**
 * {@link SslBundleProperties} for PEM-encoded certificates and private keys.
 *
 * @author Scott Frederick
 * @author Phillip Webb
 * @author Moritz Halbritter
 * @since 3.1.0
 * @see PemSslStoreBundle
 */
public class PemSslBundleProperties extends SslBundleProperties {

	/**
	 * Keystore properties.
	 */
	private final Store keystore = new Store();

	/**
	 * Truststore properties.
	 */
	private final Store truststore = new Store();

	public Store getKeystore() {
		return this.keystore;
	}

	public Store getTruststore() {
		return this.truststore;
	}

	/**
	 * Store properties.
	 */
	public static class Store {

		/**
		 * Type of the store to create, e.g. JKS.
		 */
		private @Nullable String type;

		/**
		 * Location or content of the certificate or certificate chain in PEM format.
		 */
		private @Nullable String certificate;

		/**
		 * Location or content of the private key in PEM format.
		 */
		private @Nullable String privateKey;

		/**
		 * Password used to decrypt an encrypted private key.
		 */
		private @Nullable String privateKeyPassword;

		/**
		 * Whether to verify that the private key matches the public key.
		 */
		private boolean verifyKeys;

		public @Nullable String getType() {
			return this.type;
		}

		public void setType(@Nullable String type) {
			this.type = type;
		}

		public @Nullable String getCertificate() {
			return this.certificate;
		}

		public void setCertificate(@Nullable String certificate) {
			this.certificate = certificate;
		}

		public @Nullable String getPrivateKey() {
			return this.privateKey;
		}

		public void setPrivateKey(@Nullable String privateKey) {
			this.privateKey = privateKey;
		}

		public @Nullable String getPrivateKeyPassword() {
			return this.privateKeyPassword;
		}

		public void setPrivateKeyPassword(@Nullable String privateKeyPassword) {
			this.privateKeyPassword = privateKeyPassword;
		}

		public boolean isVerifyKeys() {
			return this.verifyKeys;
		}

		public void setVerifyKeys(boolean verifyKeys) {
			this.verifyKeys = verifyKeys;
		}

	}

}
