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

package org.springframework.boot.rsocket.autoconfigure;

import io.rsocket.transport.netty.server.TcpServerTransport;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.messaging.rsocket.RSocketStrategies;
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for Spring RSocket support in Spring
 * Messaging.
 *
 * @author Brian Clozel
 * @since 4.0.0
 */
@AutoConfiguration(after = RSocketStrategiesAutoConfiguration.class)
@ConditionalOnClass({ RSocketRequester.class, io.rsocket.RSocket.class, TcpServerTransport.class })
public final class RSocketMessagingAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	RSocketMessageHandler messageHandler(RSocketStrategies rSocketStrategies,
			ObjectProvider<RSocketMessageHandlerCustomizer> customizers) {
		RSocketMessageHandler messageHandler = new RSocketMessageHandler();
		messageHandler.setRSocketStrategies(rSocketStrategies);
		customizers.orderedStream().forEach((customizer) -> customizer.customize(messageHandler));
		return messageHandler;
	}

}
