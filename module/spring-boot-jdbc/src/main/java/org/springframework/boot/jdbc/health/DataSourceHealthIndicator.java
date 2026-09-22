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

package org.springframework.boot.jdbc.health;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

import javax.sql.DataSource;

import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.health.contributor.AbstractTimeoutAwareHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.health.contributor.TimeoutEnforcement;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.IncorrectResultSetColumnCountException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.RowMapperResultSetExtractor;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * {@link HealthIndicator} that tests the status of a {@link DataSource} and optionally
 * runs a test query.
 * <p>
 * This indicator uses {@link TimeoutEnforcement#INDICATOR}: a configured health timeout
 * is applied to {@link Connection#isValid(int)} or, when a validation query is set, as
 * the {@link Statement#setQueryTimeout(int) query timeout}. JDBC only accepts whole
 * seconds, so the timeout is rounded up. Acquiring the connection is not covered and
 * stays bounded by the timeout of the connection pool, for example
 * {@code spring.datasource.hikari.connection-timeout}.
 *
 * @author Dave Syer
 * @author Christian Dupuis
 * @author Andy Wilkinson
 * @author Stephane Nicoll
 * @author Arthur Kalimullin
 * @author Moritz Halbritter
 * @since 4.0.0
 */
public class DataSourceHealthIndicator extends AbstractTimeoutAwareHealthIndicator implements InitializingBean {

	/**
	 * Value which tells JDBC not to apply a timeout.
	 */
	private static final int NO_TIMEOUT = 0;

	/**
	 * Shortest timeout JDBC can express, {@link #NO_TIMEOUT} being taken.
	 */
	private static final int MIN_TIMEOUT_SECONDS = 1;

	private static final RowMapperResultSetExtractor<Object> RESULT_SET_EXTRACTOR = new RowMapperResultSetExtractor<>(
			new SingleColumnRowMapper());

	private @Nullable DataSource dataSource;

	private @Nullable String query;

	private @Nullable JdbcTemplate jdbcTemplate;

	/**
	 * Create a new {@link DataSourceHealthIndicator} instance.
	 */
	public DataSourceHealthIndicator() {
		this(null, null);
	}

	/**
	 * Create a new {@link DataSourceHealthIndicator} using the specified
	 * {@link DataSource}.
	 * @param dataSource the data source
	 */
	public DataSourceHealthIndicator(@Nullable DataSource dataSource) {
		this(dataSource, null);
	}

	/**
	 * Create a new {@link DataSourceHealthIndicator} using the specified
	 * {@link DataSource} and validation query.
	 * @param dataSource the data source
	 * @param query the validation query to use (can be {@code null})
	 */
	public DataSourceHealthIndicator(@Nullable DataSource dataSource, @Nullable String query) {
		super("DataSource health check failed");
		this.dataSource = dataSource;
		this.query = query;
		this.jdbcTemplate = (dataSource != null) ? new JdbcTemplate(dataSource) : null;
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		Assert.state(this.dataSource != null, "DataSource for DataSourceHealthIndicator must be specified");
	}

	@Override
	protected void doHealthCheck(Health.Builder builder, @Nullable Duration timeout) throws Exception {
		if (this.dataSource == null) {
			builder.up().withDetail("database", "unknown");
			return;
		}
		Assert.state(this.jdbcTemplate != null, "'jdbcTemplate' must not be null");
		doDataSourceHealthCheck(builder, this.jdbcTemplate, timeout);
	}

	private void doDataSourceHealthCheck(Health.Builder builder, JdbcTemplate jdbcTemplate, @Nullable Duration timeout)
			throws TimeoutException {
		int timeoutSeconds = (timeout != null) ? toSeconds(timeout) : NO_TIMEOUT;
		try {
			// Both checks share one connection so that the health check needs a single
			// acquisition from the pool
			jdbcTemplate.execute((ConnectionCallback<@Nullable Void>) (connection) -> {
				checkConnection(builder, connection, timeoutSeconds);
				return null;
			});
		}
		catch (QueryTimeoutException ex) {
			// Without a health timeout the driver timed out on its own, which is a
			// failure of the check rather than of this indicator
			if (timeout == null) {
				throw ex;
			}
			TimeoutException timeoutException = new TimeoutException(ex.getMessage());
			timeoutException.initCause(ex);
			throw timeoutException;
		}
	}

	private void checkConnection(Health.Builder builder, Connection connection, int timeoutSeconds)
			throws SQLException {
		builder.up().withDetail("database", connection.getMetaData().getDatabaseProductName());
		String validationQuery = this.query;
		if (!StringUtils.hasText(validationQuery)) {
			builder.withDetail("validationQuery", "isValid()");
			builder.status(connection.isValid(timeoutSeconds) ? Status.UP : Status.DOWN);
			return;
		}
		builder.withDetail("validationQuery", validationQuery);
		builder.withDetail("result", runValidationQuery(connection, validationQuery, timeoutSeconds));
	}

	private Object runValidationQuery(Connection connection, String validationQuery, int timeoutSeconds)
			throws SQLException {
		try (Statement statement = connection.createStatement()) {
			statement.setQueryTimeout(timeoutSeconds);
			try (ResultSet resultSet = statement.executeQuery(validationQuery)) {
				List<Object> results = RESULT_SET_EXTRACTOR.extractData(resultSet);
				return DataAccessUtils.requiredSingleResult(results);
			}
		}
	}

	/**
	 * Converts a {@link Duration} to a JDBC timeout. JDBC takes whole seconds and treats
	 * {@code 0} as no timeout, so anything below a second has to be rounded up to one.
	 * @param timeout the timeout
	 * @return timeout in seconds
	 */
	private int toSeconds(Duration timeout) {
		long seconds = timeout.getSeconds() + ((timeout.getNano() > 0) ? 1 : 0);
		return (int) Math.min(Math.max(seconds, MIN_TIMEOUT_SECONDS), Integer.MAX_VALUE);
	}

	/**
	 * Set the {@link DataSource} to use.
	 * @param dataSource the data source
	 */
	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
		this.jdbcTemplate = new JdbcTemplate(dataSource);
	}

	/**
	 * Set a specific validation query to use to validate a connection. If none is set, a
	 * validation based on {@link Connection#isValid(int)} is used.
	 * @param query the validation query to use
	 */
	public void setQuery(String query) {
		this.query = query;
	}

	/**
	 * Return the validation query or {@code null}.
	 * @return the query
	 */
	public @Nullable String getQuery() {
		return this.query;
	}

	/**
	 * {@link RowMapper} that expects and returns results from a single column.
	 */
	private static final class SingleColumnRowMapper implements RowMapper<Object> {

		@Override
		public Object mapRow(ResultSet rs, int rowNum) throws SQLException {
			ResultSetMetaData metaData = rs.getMetaData();
			int columns = metaData.getColumnCount();
			if (columns != 1) {
				throw new IncorrectResultSetColumnCountException(1, columns);
			}
			// Avoid calling getObject as it breaks MySQL on Java 7 and later
			Object result = JdbcUtils.getResultSetValue(rs, 1);
			Assert.state(result != null, "'result' must not be null");
			return result;
		}

	}

}
