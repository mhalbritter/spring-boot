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

package org.springframework.boot.autoconfigure.r2dbc;

import java.util.Collection;

import org.springframework.boot.autoconfigure.sql.SqlServiceConnection;

/**
 * Adds R2DBC operations onto {@link SqlServiceConnection}.
 *
 * @author Moritz Halbritter
 * @author Andy Wilkinson
 */
final class R2dbcServiceConnection {

	private final SqlServiceConnection sqlServiceConnection;

	private final DatabaseDriver databaseDriver;

	private R2dbcServiceConnection(SqlServiceConnection sqlServiceConnection, DatabaseDriver databaseDriver) {
		this.sqlServiceConnection = sqlServiceConnection;
		this.databaseDriver = databaseDriver;
	}

	/**
	 * Returns the R2DBC subprotocol (the part following the 'jdbc:' in a R2DBC url).
	 * @return the R2DBC subprotocol
	 * @throws IllegalStateException if no URL prefixes could be found
	 */
	String getR2dbcSubProtocol() {
		Collection<String> urlPrefixes = this.databaseDriver.getUrlPrefixes();
		if (urlPrefixes.isEmpty()) {
			throw new IllegalStateException("URL prefixes for driver %s are empty".formatted(this.databaseDriver));
		}
		return urlPrefixes.iterator().next();
	}

	/**
	 * Returns the R2DBC url.
	 * @return the R2DBC url
	 */
	String getR2dbcUrl() {
		return "r2dbc:%s://%s:%d/%s".formatted(getR2dbcSubProtocol(), this.sqlServiceConnection.getHostname(),
				this.sqlServiceConnection.getPort(), this.sqlServiceConnection.getDatabase());
	}

	/**
	 * Creates a new {@link R2dbcServiceConnection} for the given
	 * {@link SqlServiceConnection}.
	 * @param serviceConnection the SQL service connection
	 * @return the JDBC service connection
	 * @throws IllegalArgumentException if no {@link DatabaseDriver} can be found to
	 * support the {@link SqlServiceConnection}.
	 */
	static R2dbcServiceConnection of(SqlServiceConnection serviceConnection) {
		DatabaseDriver databaseDriver = DatabaseDriver.fromProductName(serviceConnection.getProductName());
		if (databaseDriver == DatabaseDriver.UNKNOWN) {
			throw new IllegalArgumentException("Unable to find database driver for product name '%s'"
				.formatted(serviceConnection.getProductName()));
		}
		return new R2dbcServiceConnection(serviceConnection, databaseDriver);
	}

}
