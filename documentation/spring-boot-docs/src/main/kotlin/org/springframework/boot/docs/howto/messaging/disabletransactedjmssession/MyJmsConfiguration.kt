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

package org.springframework.boot.docs.howto.messaging.disabletransactedjmssession

import jakarta.jms.ConnectionFactory
import org.springframework.boot.jms.ConnectionFactoryUnwrapper
import org.springframework.boot.jms.autoconfigure.DefaultJmsListenerContainerFactoryConfigurer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.jms.config.DefaultJmsListenerContainerFactory

@Suppress("NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
@Configuration(proxyBeanMethods = false)
class MyJmsConfiguration {

	@Bean
	fun jmsListenerContainerFactory(connectionFactory: ConnectionFactory,
			configurer: DefaultJmsListenerContainerFactoryConfigurer): DefaultJmsListenerContainerFactory {
		val listenerFactory = DefaultJmsListenerContainerFactory()
		configurer.configure(listenerFactory, ConnectionFactoryUnwrapper.unwrapCaching(connectionFactory))
		listenerFactory.setTransactionManager(null)
		listenerFactory.setSessionTransacted(false)
		return listenerFactory
	}

}

