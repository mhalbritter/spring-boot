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

package org.springframework.boot.docs.actuator.endpoints.health.timeouts.writingtimeoutawarehealthindicators;

import java.time.Duration;

import org.springframework.boot.health.contributor.AbstractTimeoutAwareHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.TimeoutEnforcement;
import org.springframework.stereotype.Component;

@Component
public class MyHealthIndicator extends AbstractTimeoutAwareHealthIndicator {

	private final MyClient client;

	public MyHealthIndicator(MyClient client) {
		super(TimeoutEnforcement.INDICATOR);
		this.client = client;
	}

	@Override
	protected void doHealthCheck(Health.Builder builder, Duration timeout) throws Exception {
		if (this.client.ping(timeout)) {
			builder.up();
			return;
		}
		builder.down();
	}

}
