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

import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.sql.SqlServiceConnection;
import org.springframework.boot.origin.Origin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link R2dbcServiceConnection}.
 *
 * @author Moritz Halbritter
 */
class R2dbcServiceConnectionTests {

	private final R2dbcServiceConnection r2dbcServiceConnection = R2dbcServiceConnection
		.of(new TestSqlServiceConnection());

	@Test
	void getR2dbcSubProtocol() {
		assertThat(this.r2dbcServiceConnection.getR2dbcSubProtocol()).isEqualTo("postgresql");
	}

	@Test
	void getR2dbcUrl() {
		assertThat(this.r2dbcServiceConnection.getR2dbcUrl())
			.isEqualTo("r2dbc:postgresql://postgres.example.com:12345/database-1");
	}

	@Test
	void throwsWhenUsingUnknownProduct() {
		SqlServiceConnection serviceConnection = new SqlServiceConnection() {
			@Override
			public String getHostname() {
				return null;
			}

			@Override
			public int getPort() {
				return 0;
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
			public String getDatabase() {
				return null;
			}

			@Override
			public String getProductName() {
				return "some-unknown-product-name";
			}

			@Override
			public String getName() {
				return null;
			}

			@Override
			public Origin getOrigin() {
				return null;
			}
		};
		assertThatThrownBy(() -> R2dbcServiceConnection.of(serviceConnection))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("'some-unknown-product-name'");
	}

}
