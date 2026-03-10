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

package org.springframework.boot.opentelemetry.autoconfigure;

import java.util.Collections;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.logging.DeferredLogs;
import org.springframework.boot.testsupport.system.CapturedOutput;
import org.springframework.boot.testsupport.system.OutputCaptureExtension;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link OpenTelemetryEnvironmentVariableEnvironmentPostProcessor}.
 *
 * @author Moritz Halbritter
 */
@ExtendWith(OutputCaptureExtension.class)
class OpenTelemetryEnvironmentVariableEnvironmentPostProcessorTests {

	@Test
	void shouldMapOtelSdkEnabled(CapturedOutput output) {
		Environment environment = runProcessor(Collections.emptyMap());
		assertThat(environment.getProperty("management.opentelemetry.enabled")).isNull();
		environment = runProcessor(Map.of("OTEL_SDK_DISABLED", "true"));
		assertThat(environment.getProperty("management.opentelemetry.enabled")).isEqualTo("false");
		environment = runProcessor(Map.of("OTEL_SDK_DISABLED", "false"));
		assertThat(environment.getProperty("management.opentelemetry.enabled")).isEqualTo("true");
		environment = runProcessor(Map.of("OTEL_SDK_DISABLED", "invalid-value"));
		assertThat(environment.getProperty("management.opentelemetry.enabled")).isNull();
		assertThat(output).contains("Invalid value for boolean environment variable 'OTEL_SDK_DISABLED': 'invalid-value'");
	}

	private Environment runProcessor(Map<String, String> environmentVariables) {
		DeferredLogs logFactory = new DeferredLogs();
		OpenTelemetryEnvironmentVariableEnvironmentPostProcessor processor = new OpenTelemetryEnvironmentVariableEnvironmentPostProcessor(logFactory, new OpenTelemetryEnvironmentVariables(logFactory, environmentVariables::get));
		ConfigurableEnvironment configurableEnvironment = new StandardEnvironment();
		processor.postProcessEnvironment(configurableEnvironment, new SpringApplication());
		logFactory.switchOverAll();
		return configurableEnvironment;
	}
}
