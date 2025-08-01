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

package org.springframework.boot.integration.autoconfigure.endpoint;

import org.springframework.boot.actuate.autoconfigure.endpoint.condition.ConditionalOnAvailableEndpoint;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.integration.autoconfigure.IntegrationAutoConfiguration;
import org.springframework.boot.integration.endpoint.IntegrationGraphEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.integration.config.IntegrationConfigurationBeanFactoryPostProcessor;
import org.springframework.integration.graph.IntegrationGraphServer;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for the
 * {@link IntegrationGraphEndpoint}.
 *
 * @author Tim Ysewyn
 * @author Stephane Nicoll
 * @since 4.0.0
 */
@AutoConfiguration(after = IntegrationAutoConfiguration.class)
@ConditionalOnClass({ IntegrationGraphServer.class, IntegrationGraphEndpoint.class,
		ConditionalOnAvailableEndpoint.class })
@ConditionalOnBean(IntegrationConfigurationBeanFactoryPostProcessor.class)
@ConditionalOnAvailableEndpoint(IntegrationGraphEndpoint.class)
public final class IntegrationGraphEndpointAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	IntegrationGraphEndpoint integrationGraphEndpoint(IntegrationGraphServer integrationGraphServer) {
		return new IntegrationGraphEndpoint(integrationGraphServer);
	}

	@Bean
	@ConditionalOnMissingBean
	IntegrationGraphServer integrationGraphServer() {
		return new IntegrationGraphServer();
	}

}
