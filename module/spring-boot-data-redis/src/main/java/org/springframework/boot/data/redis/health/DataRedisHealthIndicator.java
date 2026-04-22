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

package org.springframework.boot.data.redis.health;

import org.springframework.boot.health.contributor.AbstractHealthIndicator;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.TimeoutSupport;
import org.springframework.data.redis.connection.RedisClusterConnection;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisConnectionUtils;
import org.springframework.util.Assert;

/**
 * Simple implementation of a
 * {@link org.springframework.boot.health.contributor.HealthIndicator} returning status
 * information for Redis data stores.
 * <p>
 * Timeout participation depends on the {@link RedisConnectionFactory} type:
 * {@link LettuceConnectionFactory} uses {@link TimeoutSupport#INTERRUPTION}.
 * {@link JedisConnectionFactory} (and any other non-Lettuce factory) uses
 * {@link TimeoutSupport#NONE}: Jedis performs blocking {@link java.net.Socket} reads that
 * are not reliably cut short by thread interruption, so a configured health timeout
 * cannot be enforced predictably.
 *
 * @author Christian Dupuis
 * @author Richard Santana
 * @author Scott Frederick
 * @author Moritz Halbritter
 * @since 4.0.0
 */
public class DataRedisHealthIndicator extends AbstractHealthIndicator {

	private final RedisConnectionFactory redisConnectionFactory;

	public DataRedisHealthIndicator(RedisConnectionFactory connectionFactory) {
		super("Redis health check failed");
		Assert.notNull(connectionFactory, "'connectionFactory' must not be null");
		this.redisConnectionFactory = connectionFactory;
	}

	@Override
	public TimeoutSupport getTimeoutSupport() {
		return (this.redisConnectionFactory instanceof LettuceConnectionFactory) ? TimeoutSupport.INTERRUPTION
				: TimeoutSupport.NONE;
	}

	@Override
	protected void doHealthCheck(Health.Builder builder) throws Exception {
		RedisConnection connection = RedisConnectionUtils.getConnection(this.redisConnectionFactory);
		try {
			doHealthCheck(builder, connection);
		}
		finally {
			RedisConnectionUtils.releaseConnection(connection, this.redisConnectionFactory);
		}
	}

	private void doHealthCheck(Health.Builder builder, RedisConnection connection) {
		if (connection instanceof RedisClusterConnection clusterConnection) {
			DataRedisHealth.fromClusterInfo(builder, clusterConnection.clusterGetClusterInfo());
		}
		else {
			DataRedisHealth.up(builder, connection.serverCommands().info());
		}
	}

}
