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

package org.springframework.boot.test.autoconfigure.serviceconnection;

import java.util.List;
import java.util.function.Supplier;

import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.autoconfigure.serviceconnection.ServiceConnection;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.MergedContextConfiguration;

/**
 * {@link ContextCustomizer} to support registering {@link ServiceConnection service
 * connections}.
 *
 * @author Moritz Halbritter
 * @author Andy Wilkinson
 */
@SuppressWarnings("rawtypes")
class ServiceConnectionContextCustomizer implements ContextCustomizer {

	private final List<ServiceConnection> serviceConnections;

	ServiceConnectionContextCustomizer(List<ServiceConnection> serviceConnections) {
		this.serviceConnections = serviceConnections;
	}

	@Override
	public void customizeContext(ConfigurableApplicationContext context, MergedContextConfiguration mergedConfig) {
		ConfigurableListableBeanFactory beanFactory = context.getBeanFactory();
		if (beanFactory instanceof BeanDefinitionRegistry) {
			registerServiceConnections((BeanDefinitionRegistry) beanFactory);
		}
	}

	private void registerServiceConnections(BeanDefinitionRegistry registry) {
		for (ServiceConnection serviceConnections : this.serviceConnections) {
			registerServiceConnection(serviceConnections, registry);
		}
	}

	@SuppressWarnings("unchecked")
	private void registerServiceConnection(ServiceConnection connection, BeanDefinitionRegistry registry) {
		registerBean(connection.getName(), connection.getClass(), (Supplier) () -> connection, registry);
	}

	private <T> void registerBean(String name, Class<T> type, Supplier<T> supplier, BeanDefinitionRegistry registry) {
		registry.registerBeanDefinition(name,
				BeanDefinitionBuilder.rootBeanDefinition(type, supplier).getBeanDefinition());
	}

}
