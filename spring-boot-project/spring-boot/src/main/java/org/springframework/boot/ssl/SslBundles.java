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

package org.springframework.boot.ssl;

import java.util.function.Consumer;

/**
 * A managed set of {@link SslBundle} instances that can be retrieved by name.
 *
 * @author Scott Frederick
 * @author Moritz Halbritter
 * @since 3.1.0
 */
public interface SslBundles {

	/**
	 * Return an {@link SslBundle} with the provided name.
	 * @param bundleName the bundle name
	 * @return the bundle
	 * @throws NoSuchSslBundleException if a bundle with the provided name does not exist
	 */
	SslBundle getBundle(String bundleName) throws NoSuchSslBundleException;

	/**
	 * Return an {@link SslBundle} with the provided name.
	 * @param bundleName the bundle name
	 * @param onUpdate the callback, which is called when the bundle is updated or
	 * {@code null}
	 * @return the bundle
	 * @throws NoSuchSslBundleException if a bundle with the provided name does not exist
	 * @since 3.2.0
	 */
	SslBundle getBundle(String bundleName, Consumer<SslBundle> onUpdate) throws NoSuchSslBundleException;

	// FIXME method above sort of does two things, gets and register a listener.

	/**
	 * Add a handler to that will be called each time the named bundle is updated.
	 * @param bundleName the bundle name
	 * @param bundleUpdateHandler the handler that should be called
	 * @throws NoSuchSslBundleException if a bundle with the provided name does not exist
	 * @since 3.2.0
	 */
	void addBundleUpdateHandler(String bundleName, Consumer<SslBundle> bundleUpdateHandler)
			throws NoSuchSslBundleException;

}
