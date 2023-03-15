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

import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link JdbcServiceConnectionDbcp2BeanPostProcessor}.
 *
 * @author Moritz Halbritter
 * @author Andy Wilkinson
 */
class JdbcServiceConnectionDbcp2BeanPostProcessorTests {

	@Test
	void setUsernamePasswordAndUrl() {
		BasicDataSource dataSource = new BasicDataSource();
		dataSource.setUrl("will-be-overwritten");
		dataSource.setUsername("will-be-overwritten");
		dataSource.setPassword("will-be-overwritten");
		new JdbcServiceConnectionDbcp2BeanPostProcessor().processDataSource(dataSource,
				new TestJdbcServiceConnection());
		assertThat(dataSource.getUrl()).isEqualTo("jdbc:postgresql://postgres.example.com:12345/database-1");
		assertThat(dataSource.getUsername()).isEqualTo("user-1");
		assertThat(dataSource.getPassword()).isEqualTo("password-1");
	}

}
