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
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

import javax.sql.DataSource;

import org.jspecify.annotations.Nullable;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.health.contributor.TimeoutSupport;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.jdbc.IncorrectResultSetColumnCountException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * {@link HealthIndicator} that tests the status of a {@link DataSource} and optionally
 * runs a test query.
 *
 * @author Dave Syer
 * @author Christian Dupuis
 * @author Andy Wilkinson
 * @author Stephane Nicoll
 * @author Arthur Kalimullin
 * @since 4.0.0
 */
public class DataSourceHealthIndicator extends AbstractHealthIndicator implements InitializingBean {

	private @Nullable DataSource dataSource;

	private @Nullable String query;

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
	}

	@Override
	public void afterPropertiesSet() throws Exception {
		Assert.state(this.dataSource != null, "DataSource for DataSourceHealthIndicator must be specified");
	}

	@Override
	public TimeoutSupport getTimeoutSupport() {
		return TimeoutSupport.NATIVE;
	}

	@Override
	protected void doHealthCheck(Health.Builder builder) throws Exception {
		if (this.dataSource == null) {
			builder.up().withDetail("database", "unknown");
		}
		else {
			doDataSourceHealthCheck(builder, new JdbcTemplate(this.dataSource), 0);
		}
	}

	@Override
	protected void doHealthCheck(Health.Builder builder, Duration timeout) throws Exception {
		if (this.dataSource == null) {
			builder.up().withDetail("database", "unknown");
		}
		else {
			doDataSourceHealthCheck(builder, new JdbcTemplate(this.dataSource), toSeconds(timeout));
		}
	}

	private void doDataSourceHealthCheck(Health.Builder builder, JdbcTemplate jdbcTemplate, int timeoutSeconds)
			throws TimeoutException {
		builder.up().withDetail("database", getProduct(jdbcTemplate));
		String validationQuery = this.query;
		if (StringUtils.hasText(validationQuery)) {
			builder.withDetail("validationQuery", validationQuery);
			jdbcTemplate.setQueryTimeout(timeoutSeconds);
			try {
				// Avoid calling getObject as it breaks MySQL on Java 7 and later
				List<Object> results = jdbcTemplate.query(validationQuery, new SingleColumnRowMapper());
				Object result = DataAccessUtils.requiredSingleResult(results);
				builder.withDetail("result", result);
			}
			catch (QueryTimeoutException ex) {
				throw new TimeoutException(ex.getMessage());
			}
		}
		else {
			builder.withDetail("validationQuery", "isValid()");
			boolean valid = isConnectionValid(jdbcTemplate, timeoutSeconds);
			builder.status((valid) ? Status.UP : Status.DOWN);
		}
	}

	private String getProduct(JdbcTemplate jdbcTemplate) {
		return jdbcTemplate.execute((ConnectionCallback<String>) this::getProduct);
	}

	private String getProduct(Connection connection) throws SQLException {
		return connection.getMetaData().getDatabaseProductName();
	}

	private Boolean isConnectionValid(JdbcTemplate jdbcTemplate, int timeoutSeconds) {
		return jdbcTemplate.execute((ConnectionCallback<Boolean>) (connection) -> connection.isValid(timeoutSeconds));
	}

	/**
	 * Converts a {@link Duration} to a positive JDBC timeout in whole seconds (rounded up
	 * from milliseconds, minimum {@code 1}) for {@link Connection#isValid(int)} and query
	 * timeout.
	 * @param timeout the timeout
	 * @return timeout in seconds
	 */
	private int toSeconds(Duration timeout) {
		long seconds = (timeout.toMillis() + 999) / 1000;
		return (int) Math.max(1, seconds);
	}

	/**
	 * Set the {@link DataSource} to use.
	 * @param dataSource the data source
	 */
	public void setDataSource(DataSource dataSource) {
		this.dataSource = dataSource;
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
			Object result = JdbcUtils.getResultSetValue(rs, 1);
			Assert.state(result != null, "'result' must not be null");
			return result;
		}

	}

}
