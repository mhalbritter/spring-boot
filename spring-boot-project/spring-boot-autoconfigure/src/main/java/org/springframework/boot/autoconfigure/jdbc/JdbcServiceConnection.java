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

package org.springframework.boot.autoconfigure.jdbc;

import java.util.Collection;

import org.springframework.boot.autoconfigure.sql.SqlServiceConnection;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.boot.jdbc.DatabaseDriver;

/**
 * Adds JDBC operations onto {@link SqlServiceConnection}.
 *
 * @author Moritz Halbritter
 * @author Andy Wilkinson
 */
final class JdbcServiceConnection {

	private final SqlServiceConnection sqlServiceConnection;

	private final DatabaseDriver databaseDriver;

	private JdbcServiceConnection(SqlServiceConnection sqlServiceConnection, DatabaseDriver databaseDriver) {
		this.sqlServiceConnection = sqlServiceConnection;
		this.databaseDriver = databaseDriver;
	}

	/**
	 * Returns the JDBC subprotocol (the part following the 'jdbc:' in a JDBC url).
	 * @return the JDBC subprotocol
	 * @throws IllegalStateException if no URL prefixes could be found
	 */
	String getJdbcSubProtocol() {
		Collection<String> urlPrefixes = this.databaseDriver.getUrlPrefixes();
		if (urlPrefixes.isEmpty()) {
			throw new IllegalStateException("URL prefixes for driver %s are empty".formatted(this.databaseDriver));
		}
		return urlPrefixes.iterator().next();
	}

	/**
	 * Returns the JDBC url.
	 * @return the JDBC url
	 */
	String getJdbcUrl() {
		return "jdbc:%s://%s:%d/%s".formatted(getJdbcSubProtocol(), this.sqlServiceConnection.getHostname(),
				this.sqlServiceConnection.getPort(), this.sqlServiceConnection.getDatabase());
	}

	/**
	 * Initializes a {@link DataSourceBuilder} for this connection.
	 * @param classLoader the classloader to use
	 * @return the inialized datasource builder
	 */
	DataSourceBuilder<?> initializeDataSourceBuilder(ClassLoader classLoader) {
		return DataSourceBuilder.create(classLoader)
			.url(getJdbcUrl())
			.username(this.sqlServiceConnection.getUsername())
			.password(this.sqlServiceConnection.getPassword());
	}

	/**
	 * Return the validation query.
	 * @return the validation query or {@code null}
	 */
	String getValidationQuery() {
		return this.databaseDriver.getValidationQuery();
	}

	/**
	 * Return the XA driver source class name.
	 * @return the class name or {@code null}
	 */
	String getXaDataSourceClassName() {
		return this.databaseDriver.getXaDataSourceClassName();
	}

	/**
	 * Creates a new {@link JdbcServiceConnection} for the given
	 * {@link SqlServiceConnection}.
	 * @param serviceConnection the SQL service connection
	 * @return the JDBC service connection
	 * @throws IllegalArgumentException if no {@link DatabaseDriver} can be found to
	 * support the {@link SqlServiceConnection}.
	 */
	static JdbcServiceConnection of(SqlServiceConnection serviceConnection) {
		DatabaseDriver databaseDriver = DatabaseDriver.fromProductName(serviceConnection.getProductName());
		if (databaseDriver == DatabaseDriver.UNKNOWN) {
			throw new IllegalArgumentException("Unable to find database driver for product name '%s'"
				.formatted(serviceConnection.getProductName()));
		}
		return new JdbcServiceConnection(serviceConnection, databaseDriver);
	}

}
