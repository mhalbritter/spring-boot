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

package org.springframework.boot.micrometer.metrics.autoconfigure.export.opentsdb;

import io.micrometer.opentsdb.OpenTSDBFlavor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.boot.micrometer.metrics.autoconfigure.export.opentsdb.OpenTSDBMetricsExportAutoConfiguration.PropertiesOpenTSDBMetricsConnectionDetails;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.opentsdb.OpenTSDBMetricsProperties.Flavor;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link OpenTSDBMetricsPropertiesConfigAdapter}.
 *
 * @author Moritz Halbritter
 */
class OpenTSDBMetricsPropertiesConfigAdapterTests {

	private OpenTSDBMetricsProperties properties;

	private OpenTSDBMetricsConnectionDetails connectionDetails;

	@BeforeEach
	void setUp() {
		this.properties = new OpenTSDBMetricsProperties();
		this.connectionDetails = new PropertiesOpenTSDBMetricsConnectionDetails(this.properties);
	}

	@Test
	void whenPropertiesUriIsNotSetAdapterReturnsDefault() {
		assertThat(this.properties.getUri()).isNull();
		assertThat(createAdapter().uri()).isEqualTo("http://localhost:4242/api/put");
	}

	@Test
	void whenPropertiesUriIsSetAdapterReturnsIt() {
		this.properties.setUri("http://another-url:4318/v1/metrics");
		assertThat(createAdapter().uri()).isEqualTo("http://another-url:4318/v1/metrics");
	}

	@Test
	void whenPropertiesUsernameIsNotSetAdapterReturnsDefault() {
		assertThat(this.properties.getUsername()).isNull();
		assertThat(createAdapter().userName()).isNull();
	}

	@Test
	void whenPropertiesUsernameIsSetAdapterReturnsIt() {
		this.properties.setUsername("test");
		assertThat(createAdapter().userName()).isEqualTo("test");
	}

	@Test
	void whenPropertiesPasswordIsNotSetAdapterReturnsDefault() {
		assertThat(this.properties.getPassword()).isNull();
		assertThat(createAdapter().password()).isNull();
	}

	@Test
	void whenPropertiesPasswordIsSetAdapterReturnsIt() {
		this.properties.setPassword("test");
		assertThat(createAdapter().password()).isEqualTo("test");
	}

	@Test
	void whenPropertiesFlavorIsNotSetAdapterReturnsDefault() {
		assertThat(this.properties.getFlavor()).isNull();
		assertThat(createAdapter().flavor()).isNull();
	}

	@Test
	void whenPropertiesFlavorIsSetAdapterReturnsIt() {
		this.properties.setFlavor(Flavor.VICTORIA_METRICS);
		assertThat(createAdapter().flavor()).isEqualTo(OpenTSDBFlavor.VictoriaMetrics);
	}

	@Test
	void shouldUseValuesFromCustomConnectionDetails() {
		OpenTSDBMetricsConnectionDetails connectionDetails = new OpenTSDBMetricsConnectionDetails() {

			@Override
			public String getUri() {
				return "http://another-url:4318/v1/metrics";
			}

			@Override
			public String getUsername() {
				return "username";
			}

			@Override
			public String getPassword() {
				return "password";
			}
		};
		OpenTSDBMetricsPropertiesConfigAdapter adapter = new OpenTSDBMetricsPropertiesConfigAdapter(this.properties,
				connectionDetails);
		assertThat(adapter.uri()).isEqualTo("http://another-url:4318/v1/metrics");
		assertThat(adapter.userName()).isEqualTo("username");
		assertThat(adapter.password()).isEqualTo("password");
	}

	private OpenTSDBMetricsPropertiesConfigAdapter createAdapter() {
		return new OpenTSDBMetricsPropertiesConfigAdapter(this.properties, this.connectionDetails);
	}

}
