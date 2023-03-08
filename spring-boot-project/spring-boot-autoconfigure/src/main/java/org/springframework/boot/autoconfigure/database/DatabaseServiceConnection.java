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

package org.springframework.boot.autoconfigure.database;

import java.util.Collection;

import org.springframework.boot.autoconfigure.serviceconnection.ServiceConnection;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.jdbc.DatabaseDriver;

/**
 * A connection to a database service.
 *
 * @author Moritz Halbritter
 * @since 3.1.0
 */
// TODO: How to name this thing?
public interface DatabaseServiceConnection extends ServiceConnection {

	/**
	 * Type of the database.
	 * @return the type of the database
	 */
	DatabaseType getType();

	/**
	 * Hostname of the database.
	 * @return the hostname of the database.
	 */
	String getHostname();

	/**
	 * Port of the database.
	 * @return the port of the database
	 */
	int getPort();

	/**
	 * Hostname for the database.
	 * @return the username for the database
	 */
	String getUsername();

	/**
	 * Password for the database.
	 * @return the password for the database
	 */
	String getPassword();

	/**
	 * The name of the database to use.
	 * @return the name of the database to use.
	 */
	String getDatabase();

	default String getJdbcUrl() {
		return "jdbc:%s://%s:%d/%s".formatted(getType().getJdbcSubProtocol(), getHostname(), getPort(), getDatabase());
	}

	default String getR2dbcUrl() {
		return "r2dbc:%s://%s:%d/%s".formatted(getType().getR2dbcSubProtocol(), getHostname(), getPort(),
				getDatabase());
	}

	default DataSourceBuilder<?> initializeDataSourceBuilder(ClassLoader classLoader) {
		return DataSourceBuilder.create(classLoader)
			.driverClassName(getType().getDatabaseDriver().getDriverClassName())
			.url(getJdbcUrl())
			.username(getUsername())
			.password(getPassword());
	}

	/**
	 * Database type.
	 */
	enum DatabaseType {

		/**
		 * MySQL.
		 */
		MYSQL(DatabaseDriver.MYSQL),
		/**
		 * Maria DB.
		 */
		MARIADB(DatabaseDriver.MARIADB),
		/**
		 * Postgres.
		 */
		POSTGRESQL(DatabaseDriver.POSTGRESQL);

		private final DatabaseDriver databaseDriver;

		DatabaseType(DatabaseDriver databaseDriver) {
			this.databaseDriver = databaseDriver;
		}

		/**
		 * Returns the {@link DatabaseDriver}.
		 * @return the database driver
		 */
		public DatabaseDriver getDatabaseDriver() {
			return this.databaseDriver;
		}

		String getJdbcSubProtocol() {
			Collection<String> prefixes = this.databaseDriver.getUrlPrefixes();
			if (prefixes.isEmpty()) {
				throw new IllegalStateException("Unable to get JDBC sub protocol: prefixes collection is empty");
			}
			return prefixes.iterator().next();
		}

		String getR2dbcSubProtocol() {
			Collection<String> prefixes = this.databaseDriver.getUrlPrefixes();
			if (prefixes.isEmpty()) {
				throw new IllegalStateException("Unable to get R2DBC sub protocol: prefixes collection is empty");
			}
			return prefixes.iterator().next();
		}

	}

}
