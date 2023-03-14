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

package org.springframework.boot.devservices.dockercompose.database.mariadb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.boot.autoconfigure.serviceconnection.ServiceConnection;
import org.springframework.boot.autoconfigure.sql.SqlServiceConnection;
import org.springframework.boot.devservices.dockercompose.RunningServiceServiceConnectionProvider;
import org.springframework.boot.devservices.dockercompose.database.AbstractSqlServiceConnection;
import org.springframework.boot.devservices.dockercompose.interop.RunningService;
import org.springframework.util.ClassUtils;

/**
 * Handles connections to a MariaDB service.
 *
 * @author Moritz Halbritter
 * @author Andy Wilkinson
 */
class MariaDbConnectionProvider implements RunningServiceServiceConnectionProvider {

	private final boolean serviceConnectionPresent;

	MariaDbConnectionProvider(ClassLoader classLoader) {
		this.serviceConnectionPresent = ClassUtils
			.isPresent("org.springframework.boot.autoconfigure.sql.SqlServiceConnection", classLoader);
	}

	@Override
	public List<? extends ServiceConnection> provideServiceConnection(List<RunningService> services) {
		if (!this.serviceConnectionPresent) {
			return Collections.emptyList();
		}
		List<SqlServiceConnection> result = new ArrayList<>();
		for (RunningService service : services) {
			if (!MariaDbService.matches(service)) {
				continue;
			}
			MariaDbService mariaDbService = new MariaDbService(service);
			result.add(new DockerComposeMariaDbSqlServiceConnection(mariaDbService));
		}
		return result;
	}

	private static class DockerComposeMariaDbSqlServiceConnection extends AbstractSqlServiceConnection {

		DockerComposeMariaDbSqlServiceConnection(MariaDbService service) {
			super(service);
		}

		@Override
		public String getProductName() {
			return "MariaDB";
		}

		@Override
		public String getName() {
			return "docker-compose-mariadb-%s".formatted(this.service.getName());
		}

	}

}
