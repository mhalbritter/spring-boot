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

package org.springframework.boot.test.autoconfigure.data.redis;

import org.testcontainers.containers.GenericContainer;

import org.springframework.boot.autoconfigure.data.redis.RedisServiceConnection;
import org.springframework.boot.autoconfigure.serviceconnection.ServiceConnection;
import org.springframework.boot.origin.Origin;
import org.springframework.boot.test.autoconfigure.testcontainers.GenericContainerServiceConnectionAdapter;
import org.springframework.boot.test.autoconfigure.testcontainers.GenericContainerServiceConnectionSource;

/**
 * An adapter from a {@link GenericContainer} to a {@link RedisServiceConnection}.
 *
 * @author Andy Wilkinson
 */
class RedisContainerServiceConnectionAdapter implements GenericContainerServiceConnectionAdapter {

	@Override
	public ServiceConnection adapt(GenericContainerServiceConnectionSource source) {
		if (!RedisServiceConnection.class.isAssignableFrom(source.connectionType())) {
			return null;
		}
		return new RedisServiceConnection() {

			@Override
			public String getName() {
				return source.name();
			}

			@Override
			public Origin getOrigin() {
				return source.origin();
			}

			@Override
			public String getUsername() {
				return null;
			}

			@Override
			public String getPassword() {
				return null;
			}

			@Override
			public Standalone getStandalone() {
				return new Standalone() {

					@Override
					public int getDatabase() {
						return 0;
					}

					@Override
					public String getHost() {
						return source.container().getHost();
					}

					@Override
					public int getPort() {
						return source.container().getFirstMappedPort();
					}

				};
			}

			@Override
			public Sentinel getSentinel() {
				return null;
			}

			@Override
			public Cluster getCluster() {
				return null;
			}

		};
	}

}
