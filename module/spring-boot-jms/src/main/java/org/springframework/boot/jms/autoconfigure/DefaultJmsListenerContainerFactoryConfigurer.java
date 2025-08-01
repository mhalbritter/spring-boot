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

package org.springframework.boot.jms.autoconfigure;

import java.time.Duration;

import io.micrometer.observation.ObservationRegistry;
import jakarta.jms.ConnectionFactory;
import jakarta.jms.ExceptionListener;
import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.boot.jms.autoconfigure.JmsProperties.Listener.Session;
import org.springframework.jms.config.DefaultJmsListenerContainerFactory;
import org.springframework.jms.support.converter.MessageConverter;
import org.springframework.jms.support.destination.DestinationResolver;
import org.springframework.transaction.jta.JtaTransactionManager;
import org.springframework.util.Assert;

/**
 * Configure {@link DefaultJmsListenerContainerFactory} with sensible defaults tuned using
 * configuration properties.
 * <p>
 * Can be injected into application code and used to define a custom
 * {@code DefaultJmsListenerContainerFactory} whose configuration is based upon that
 * produced by auto-configuration.
 *
 * @author Stephane Nicoll
 * @author Eddú Meléndez
 * @author Vedran Pavic
 * @author Lasse Wulff
 * @since 4.0.0
 */
public final class DefaultJmsListenerContainerFactoryConfigurer {

	private @Nullable DestinationResolver destinationResolver;

	private @Nullable MessageConverter messageConverter;

	private @Nullable ExceptionListener exceptionListener;

	private @Nullable JtaTransactionManager transactionManager;

	private @Nullable JmsProperties jmsProperties;

	private @Nullable ObservationRegistry observationRegistry;

	/**
	 * Set the {@link DestinationResolver} to use or {@code null} if no destination
	 * resolver should be associated with the factory by default.
	 * @param destinationResolver the {@link DestinationResolver}
	 */
	void setDestinationResolver(@Nullable DestinationResolver destinationResolver) {
		this.destinationResolver = destinationResolver;
	}

	/**
	 * Set the {@link MessageConverter} to use or {@code null} if the out-of-the-box
	 * converter should be used.
	 * @param messageConverter the {@link MessageConverter}
	 */
	void setMessageConverter(@Nullable MessageConverter messageConverter) {
		this.messageConverter = messageConverter;
	}

	/**
	 * Set the {@link ExceptionListener} to use or {@code null} if no exception listener
	 * should be associated by default.
	 * @param exceptionListener the {@link ExceptionListener}
	 */
	void setExceptionListener(@Nullable ExceptionListener exceptionListener) {
		this.exceptionListener = exceptionListener;
	}

	/**
	 * Set the {@link JtaTransactionManager} to use or {@code null} if the JTA support
	 * should not be used.
	 * @param transactionManager the {@link JtaTransactionManager}
	 */
	void setTransactionManager(@Nullable JtaTransactionManager transactionManager) {
		this.transactionManager = transactionManager;
	}

	/**
	 * Set the {@link JmsProperties} to use.
	 * @param jmsProperties the {@link JmsProperties}
	 */
	void setJmsProperties(@Nullable JmsProperties jmsProperties) {
		this.jmsProperties = jmsProperties;
	}

	/**
	 * Set the {@link ObservationRegistry} to use.
	 * @param observationRegistry the {@link ObservationRegistry}
	 */
	void setObservationRegistry(@Nullable ObservationRegistry observationRegistry) {
		this.observationRegistry = observationRegistry;
	}

	/**
	 * Configure the specified jms listener container factory. The factory can be further
	 * tuned and default settings can be overridden.
	 * @param factory the {@link DefaultJmsListenerContainerFactory} instance to configure
	 * @param connectionFactory the {@link ConnectionFactory} to use
	 */
	public void configure(DefaultJmsListenerContainerFactory factory, ConnectionFactory connectionFactory) {
		Assert.notNull(factory, "'factory' must not be null");
		Assert.notNull(connectionFactory, "'connectionFactory' must not be null");
		Assert.state(this.jmsProperties != null, "'jmsProperties' must not be null");
		JmsProperties.Listener listenerProperties = this.jmsProperties.getListener();
		Session sessionProperties = listenerProperties.getSession();
		factory.setConnectionFactory(connectionFactory);
		PropertyMapper map = PropertyMapper.get().alwaysApplyingWhenNonNull();
		map.from(this.jmsProperties::isPubSubDomain).to(factory::setPubSubDomain);
		map.from(this.jmsProperties::isSubscriptionDurable).to(factory::setSubscriptionDurable);
		map.from(this.jmsProperties::getClientId).to(factory::setClientId);
		map.from(this.transactionManager).to(factory::setTransactionManager);
		map.from(this.destinationResolver).to(factory::setDestinationResolver);
		map.from(this.messageConverter).to(factory::setMessageConverter);
		map.from(this.exceptionListener).to(factory::setExceptionListener);
		map.from(sessionProperties.getAcknowledgeMode()::getMode).to(factory::setSessionAcknowledgeMode);
		if (this.transactionManager == null && sessionProperties.getTransacted() == null) {
			factory.setSessionTransacted(true);
		}
		map.from(this.observationRegistry).to(factory::setObservationRegistry);
		map.from(sessionProperties::getTransacted).to(factory::setSessionTransacted);
		map.from(listenerProperties::isAutoStartup).to(factory::setAutoStartup);
		map.from(listenerProperties::formatConcurrency).to(factory::setConcurrency);
		map.from(listenerProperties::getReceiveTimeout).as(Duration::toMillis).to(factory::setReceiveTimeout);
		map.from(listenerProperties::getMaxMessagesPerTask).to(factory::setMaxMessagesPerTask);
	}

}
