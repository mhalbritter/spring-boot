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

package org.springframework.boot.jdbc;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Enumeration of common database drivers.
 *
 * @author Phillip Webb
 * @author Maciej Walkowiak
 * @author Marten Deinum
 * @author Stephane Nicoll
 * @since 1.4.0
 */
public enum DatabaseDriver {

	/**
	 * Unknown type.
	 */
	UNKNOWN(null, null),

	/**
	 * Apache Derby.
	 */
	DERBY("Apache Derby", "org.apache.derby.jdbc.EmbeddedDriver", "org.apache.derby.jdbc.EmbeddedXADataSource",
			"SELECT 1 FROM SYSIBM.SYSDUMMY1"),

	/**
	 * H2.
	 */
	H2("H2", "org.h2.Driver", "org.h2.jdbcx.JdbcDataSource", "SELECT 1"),

	/**
	 * HyperSQL DataBase.
	 */
	HSQLDB("HSQL Database Engine", "org.hsqldb.jdbc.JDBCDriver", "org.hsqldb.jdbc.pool.JDBCXADataSource",
			"SELECT COUNT(*) FROM INFORMATION_SCHEMA.SYSTEM_USERS"),

	/**
	 * SQLite.
	 */
	SQLITE("SQLite", "org.sqlite.JDBC"),

	/**
	 * MySQL.
	 */
	MYSQL("MySQL", "com.mysql.cj.jdbc.Driver", "com.mysql.cj.jdbc.MysqlXADataSource", "/* ping */ SELECT 1"),

	/**
	 * Maria DB.
	 */
	MARIADB("MariaDB", "org.mariadb.jdbc.Driver", "org.mariadb.jdbc.MariaDbDataSource", "SELECT 1"),

	/**
	 * Oracle.
	 */
	ORACLE("Oracle", "oracle.jdbc.OracleDriver", "oracle.jdbc.xa.client.OracleXADataSource",
			"SELECT 'Hello' from DUAL"),

	/**
	 * Postgres.
	 */
	POSTGRESQL("PostgreSQL", "org.postgresql.Driver", "org.postgresql.xa.PGXADataSource", "SELECT 1"),

	/**
	 * Amazon Redshift.
	 * @since 2.2.0
	 */
	REDSHIFT("Redshift", "com.amazon.redshift.jdbc.Driver", null, "SELECT 1"),

	/**
	 * HANA - SAP HANA Database - HDB.
	 * @since 2.1.0
	 */
	HANA("HDB", "com.sap.db.jdbc.Driver", "com.sap.db.jdbcext.XADataSourceSAP", "SELECT 1 FROM SYS.DUMMY") {

		@Override
		protected Collection<String> getUrlPrefixes() {
			return Collections.singleton("sap");
		}

	},

	/**
	 * jTDS. As it can be used for several databases, there isn't a single product name we
	 * could rely on.
	 */
	JTDS(null, "net.sourceforge.jtds.jdbc.Driver"),

	/**
	 * SQL Server.
	 */
	SQLSERVER("Microsoft SQL Server", "com.microsoft.sqlserver.jdbc.SQLServerDriver",
			"com.microsoft.sqlserver.jdbc.SQLServerXADataSource", "SELECT 1") {

		@Override
		protected boolean matchProductName(String productName) {
			return super.matchProductName(productName) || "SQL SERVER".equalsIgnoreCase(productName);
		}

	},

	/**
	 * Firebird.
	 */
	FIREBIRD("Firebird", "org.firebirdsql.jdbc.FBDriver", "org.firebirdsql.ds.FBXADataSource",
			"SELECT 1 FROM RDB$DATABASE") {

		@Override
		protected Collection<String> getUrlPrefixes() {
			return Arrays.asList("firebirdsql", "firebird");
		}

		@Override
		protected boolean matchProductName(String productName) {
			return super.matchProductName(productName)
					|| productName.toLowerCase(Locale.ENGLISH).startsWith("firebird");
		}
	},

	/**
	 * DB2 Server.
	 */
	DB2("DB2", "com.ibm.db2.jcc.DB2Driver", "com.ibm.db2.jcc.DB2XADataSource", "SELECT 1 FROM SYSIBM.SYSDUMMY1") {

		@Override
		protected boolean matchProductName(String productName) {
			return super.matchProductName(productName) || productName.toLowerCase(Locale.ENGLISH).startsWith("db2/");
		}
	},

	/**
	 * DB2 AS400 Server.
	 */
	DB2_AS400("DB2 UDB for AS/400", "com.ibm.as400.access.AS400JDBCDriver",
			"com.ibm.as400.access.AS400JDBCXADataSource", "SELECT 1 FROM SYSIBM.SYSDUMMY1") {

		@Override
		public String getId() {
			return "db2";
		}

		@Override
		protected Collection<String> getUrlPrefixes() {
			return Collections.singleton("as400");
		}

		@Override
		protected boolean matchProductName(String productName) {
			return super.matchProductName(productName) || productName.toLowerCase(Locale.ENGLISH).contains("as/400");
		}
	},

	/**
	 * Teradata.
	 */
	TERADATA("Teradata", "com.teradata.jdbc.TeraDriver"),

	/**
	 * Informix.
	 */
	INFORMIX("Informix Dynamic Server", "com.informix.jdbc.IfxDriver", null, "select count(*) from systables") {

		@Override
		protected Collection<String> getUrlPrefixes() {
			return Arrays.asList("informix-sqli", "informix-direct");
		}

	},

	/**
	 * Apache Phoenix.
	 * @since 2.5.0
	 */
	PHOENIX("Apache Phoenix", "org.apache.phoenix.jdbc.PhoenixDriver", null, "SELECT 1 FROM SYSTEM.CATALOG LIMIT 1"),

	/**
	 * Testcontainers.
	 */
	TESTCONTAINERS(null, "org.testcontainers.jdbc.ContainerDatabaseDriver") {

		@Override
		protected Collection<String> getUrlPrefixes() {
			return Collections.singleton("tc");
		}

	},

	/**
	 * ClickHouse.
	 * @since 3.4.0
	 */
	CLICKHOUSE("ClickHouse", "com.clickhouse.jdbc.ClickHouseDriver", null, "SELECT 1") {

		@Override
		protected Collection<String> getUrlPrefixes() {
			return Arrays.asList("ch", "clickhouse");
		}

	},

	/**
	 * AWS Advanced JDBC Wrapper.
	 * @since 3.5.0
	 */
	AWS_WRAPPER(null, "software.amazon.jdbc.Driver") {

		@Override
		protected Collection<String> getUrlPrefixes() {
			return Collections.singleton("aws-wrapper");
		}

	};

	private final @Nullable String productName;

	private final @Nullable String driverClassName;

	private final @Nullable String xaDataSourceClassName;

	private final @Nullable String validationQuery;

	DatabaseDriver(@Nullable String productName, @Nullable String driverClassName) {
		this(productName, driverClassName, null);
	}

	DatabaseDriver(@Nullable String productName, @Nullable String driverClassName,
			@Nullable String xaDataSourceClassName) {
		this(productName, driverClassName, xaDataSourceClassName, null);
	}

	DatabaseDriver(@Nullable String productName, @Nullable String driverClassName,
			@Nullable String xaDataSourceClassName, @Nullable String validationQuery) {
		this.productName = productName;
		this.driverClassName = driverClassName;
		this.xaDataSourceClassName = xaDataSourceClassName;
		this.validationQuery = validationQuery;
	}

	/**
	 * Return the identifier of this driver.
	 * @return the identifier
	 */
	public String getId() {
		return name().toLowerCase(Locale.ENGLISH);
	}

	/**
	 * Return the url prefixes of this driver.
	 * @return the url prefixes
	 */
	protected Collection<String> getUrlPrefixes() {
		return Collections.singleton(name().toLowerCase(Locale.ENGLISH));
	}

	protected boolean matchProductName(String productName) {
		return this.productName != null && this.productName.equalsIgnoreCase(productName);
	}

	/**
	 * Return the driver class name.
	 * @return the class name or {@code null}
	 */
	public @Nullable String getDriverClassName() {
		return this.driverClassName;
	}

	/**
	 * Return the XA driver source class name.
	 * @return the class name or {@code null}
	 */
	public @Nullable String getXaDataSourceClassName() {
		return this.xaDataSourceClassName;
	}

	/**
	 * Return the validation query.
	 * @return the validation query or {@code null}
	 */
	public @Nullable String getValidationQuery() {
		return this.validationQuery;
	}

	/**
	 * Find a {@link DatabaseDriver} for the given URL.
	 * @param url the JDBC URL
	 * @return the database driver or {@link #UNKNOWN} if not found
	 */
	public static DatabaseDriver fromJdbcUrl(@Nullable String url) {
		if (StringUtils.hasLength(url)) {
			Assert.isTrue(url.startsWith("jdbc"), "'url' must start with \"jdbc\"");
			String urlWithoutPrefix = url.substring("jdbc".length()).toLowerCase(Locale.ENGLISH);
			for (DatabaseDriver driver : values()) {
				for (String urlPrefix : driver.getUrlPrefixes()) {
					String prefix = ":" + urlPrefix + ":";
					if (driver != UNKNOWN && urlWithoutPrefix.startsWith(prefix)) {
						return driver;
					}
				}
			}
		}
		return UNKNOWN;
	}

	/**
	 * Find a {@link DatabaseDriver} for the given product name.
	 * @param productName product name
	 * @return the database driver or {@link #UNKNOWN} if not found
	 */
	public static DatabaseDriver fromProductName(@Nullable String productName) {
		if (StringUtils.hasLength(productName)) {
			for (DatabaseDriver candidate : values()) {
				if (candidate.matchProductName(productName)) {
					return candidate;
				}
			}
		}
		return UNKNOWN;
	}

}
