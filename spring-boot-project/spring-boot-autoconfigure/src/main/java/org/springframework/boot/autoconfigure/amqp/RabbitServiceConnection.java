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

package org.springframework.boot.autoconfigure.amqp;

import java.util.List;

import org.springframework.boot.autoconfigure.serviceconnection.ServiceConnection;

/**
 * A connection to a RabbitMQ service.
 *
 * @author Moritz Halbritter
 * @author Andy Wilkinson
 * @since 3.1.0
 */
public interface RabbitServiceConnection extends ServiceConnection {

	/**
	 * Login user to authenticate to the broker. Can be null.
	 * @return the login user to authenticate to the broker
	 */
	String getUsername();

	/**
	 * Login to authenticate against the broker. Can be null.
	 * @return the login to authenticate against the broker
	 */
	String getPassword();

	/**
	 * Virtual host to use when connecting to the broker. Can be null.
	 * @return the virtual host to use when connecting to the broker
	 */
	String getVirtualHost();

	/**
	 * List of addresses to which the client should connect. Must return at least one
	 * address.
	 * @return the list of addresses to which the client should connect
	 */
	List<Address> getAddresses();

	/**
	 * Returns the first address.
	 * @return the first address
	 * @throws IllegalStateException if the address list is empty
	 */
	default Address getFirstAddress() {
		List<Address> addresses = getAddresses();
		if (addresses.isEmpty()) {
			throw new IllegalStateException("Address list is empty");
		}
		return addresses.get(0);
	}

	/**
	 * A RabbitMQ address.
	 *
	 * @param host the host
	 * @param port the port
	 */
	record Address(String host, int port) {
	}

}
