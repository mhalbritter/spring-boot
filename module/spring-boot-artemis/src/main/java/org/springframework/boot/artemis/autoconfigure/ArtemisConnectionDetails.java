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

package org.springframework.boot.artemis.autoconfigure;

import org.jspecify.annotations.Nullable;

import org.springframework.boot.autoconfigure.service.connection.ConnectionDetails;

/**
 * Details required to establish a connection to an Artemis service.
 *
 * @author Eddú Meléndez
 * @since 4.0.0
 */
public interface ArtemisConnectionDetails extends ConnectionDetails {

	/**
	 * Artemis deployment mode, auto-detected by default.
	 * @return the Artemis deployment mode, auto-detected by default
	 */
	@Nullable ArtemisMode getMode();

	/**
	 * Artemis broker url.
	 * @return the Artemis broker url
	 */
	@Nullable String getBrokerUrl();

	/**
	 * Login user of the broker.
	 * @return the login user of the broker
	 */
	@Nullable String getUser();

	/**
	 * Login password of the broker.
	 * @return the login password of the broker
	 */
	@Nullable String getPassword();

}
