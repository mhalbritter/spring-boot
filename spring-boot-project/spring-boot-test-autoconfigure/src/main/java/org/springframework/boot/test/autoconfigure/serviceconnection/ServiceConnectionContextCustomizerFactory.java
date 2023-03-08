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

package org.springframework.boot.test.autoconfigure.serviceconnection;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.serviceconnection.ServiceConnection;
import org.springframework.core.io.support.SpringFactoriesLoader;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;

/**
 * {@link ContextCustomizerFactory} to support service connections in tests.
 *
 * @author Moritz Halbritter
 * @author Andy Wilkinson
 */
class ServiceConnectionContextCustomizerFactory implements ContextCustomizerFactory {

	private final SpringFactoriesLoader loader;

	ServiceConnectionContextCustomizerFactory() {
		this(SpringFactoriesLoader
			.forDefaultResourceLocation(ServiceConnectionContextCustomizerFactory.class.getClassLoader()));
	}

	ServiceConnectionContextCustomizerFactory(SpringFactoriesLoader loader) {
		this.loader = loader;
	}

	@Override
	public ContextCustomizer createContextCustomizer(Class<?> testClass,
			List<ContextConfigurationAttributes> configAttributes) {
		List<ServiceConnectionResolver> resolvers = this.loader.load(ServiceConnectionResolver.class);
		Map<String, ResolvedConnection> resolvedConnections = new HashMap<>();
		for (ServiceConnectionResolver resolver : resolvers) {
			Iterable<ServiceConnection> connections = resolver.resolveConnections(testClass);
			for (ServiceConnection connection : connections) {
				ResolvedConnection existingConnection = resolvedConnections.putIfAbsent(connection.getName(),
						new ResolvedConnection(connection, resolver));
				if (existingConnection != null) {
					throw new IllegalStateException(
							"A service connection named '" + connection.getName() + "' from '" + connection.getOrigin()
									+ "' has already been resolved by '" + existingConnection.resolver());
				}
			}
		}
		if (resolvedConnections.isEmpty()) {
			return null;
		}
		return new ServiceConnectionContextCustomizer(
				resolvedConnections.values().stream().map(ResolvedConnection::connection).toList());
	}

	private record ResolvedConnection(ServiceConnection connection, ServiceConnectionResolver resolver) {
	}

}
