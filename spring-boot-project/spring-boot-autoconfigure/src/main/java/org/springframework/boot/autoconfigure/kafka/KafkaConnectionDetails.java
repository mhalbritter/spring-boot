/*
 * Copyright 2012-2025 the original author or authors.
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

package org.springframework.boot.autoconfigure.kafka;

import java.util.List;

import org.springframework.boot.autoconfigure.service.connection.ConnectionDetails;
import org.springframework.boot.ssl.SslBundle;

/**
 * Details required to establish a connection to a Kafka service.
 *
 * @author Moritz Halbritter
 * @author Andy Wilkinson
 * @author Phillip Webb
 * @since 3.1.0
 */
public interface KafkaConnectionDetails extends ConnectionDetails {

	/**
	 * Returns the list of bootstrap servers.
	 * @return the list of bootstrap servers
	 */
	List<String> getBootstrapServers();

	/**
	 * Returns the list of bootstrap servers used for consumers.
	 * @return the list of bootstrap servers used for consumers
	 */
	default List<String> getConsumerBootstrapServers() {
		return getBootstrapServers();
	}

	/**
	 * Returns the list of bootstrap servers used for producers.
	 * @return the list of bootstrap servers used for producers
	 */
	default List<String> getProducerBootstrapServers() {
		return getBootstrapServers();
	}

	/**
	 * Returns the list of bootstrap servers used for the admin.
	 * @return the list of bootstrap servers used for the admin
	 */
	default List<String> getAdminBootstrapServers() {
		return getBootstrapServers();
	}

	/**
	 * Returns the list of bootstrap servers used for Kafka Streams.
	 * @return the list of bootstrap servers used for Kafka Streams
	 */
	default List<String> getStreamsBootstrapServers() {
		return getBootstrapServers();
	}

	/**
	 * Returns the SSL bundle.
	 * @return the SSL bundle
	 * @since 3.5.0
	 */
	default SslBundle getSslBundle() {
		return null;
	}

	/**
	 * Returns the SSL bundle used for consumers.
	 * @return the SSL bundle used for consumers
	 * @since 3.5.0
	 */
	default SslBundle getConsumerSslBundle() {
		return getSslBundle();
	}

	/**
	 * Returns the SSL bundle used for producers.
	 * @return the SSL bundle used for producers
	 * @since 3.5.0
	 */
	default SslBundle getProducerSslBundle() {
		return getSslBundle();
	}

	/**
	 * Returns the SSL bundle used for the admin.
	 * @return the SSL bundle used for the admin
	 * @since 3.5.0
	 */
	default SslBundle getAdminSslBundle() {
		return getSslBundle();
	}

	/**
	 * Returns the SSL bundle used for Kafka Streams.
	 * @return the SSL bundle used for Kafka Streams
	 * @since 3.5.0
	 */
	default SslBundle getStreamsSslBundle() {
		return getSslBundle();
	}

	/**
	 * Returns the security protocol.
	 * @return the security protocol
	 * @since 3.5.0
	 */
	default String getSecurityProtocol() {
		return null;
	}

	/**
	 * Returns the security protocol used for consumers.
	 * @return the security protocol used for consumers
	 * @since 3.5.0
	 */
	default String getConsumerSecurityProtocol() {
		return getSecurityProtocol();
	}

	/**
	 * Returns the security protocol used for producers.
	 * @return the security protocol used for producers
	 * @since 3.5.0
	 */
	default String getProducerSecurityProtocol() {
		return getSecurityProtocol();
	}

	/**
	 * Returns the security protocol used for the admin.
	 * @return the security protocol used for the admin
	 * @since 3.5.0
	 */

	default String getAdminSecurityProtocol() {
		return getSecurityProtocol();
	}

	/**
	 * Returns the security protocol used for Kafka Streams.
	 * @return the security protocol used for Kafka Streams
	 * @since 3.5.0
	 */
	default String getStreamsSecurityProtocol() {
		return getSecurityProtocol();
	}

}
