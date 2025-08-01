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

package org.springframework.boot.kafka.autoconfigure;

import java.io.IOException;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.jspecify.annotations.Nullable;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.PropertyMapper;
import org.springframework.boot.context.properties.source.MutuallyExclusiveConfigurationPropertiesException;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.core.io.Resource;
import org.springframework.kafka.listener.ContainerProperties.AckMode;
import org.springframework.kafka.security.jaas.KafkaJaasLoginModuleInitializer;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;

/**
 * Configuration properties for Spring for Apache Kafka.
 * <p>
 * Users should refer to Kafka documentation for complete descriptions of these
 * properties.
 *
 * @author Gary Russell
 * @author Stephane Nicoll
 * @author Artem Bilan
 * @author Nakul Mishra
 * @author Tomaz Fernandes
 * @author Andy Wilkinson
 * @author Scott Frederick
 * @author Yanming Zhou
 * @since 4.0.0
 */
@ConfigurationProperties("spring.kafka")
public class KafkaProperties {

	/**
	 * List of host:port pairs to use for establishing the initial connections to the
	 * Kafka cluster. Applies to all components unless overridden.
	 */
	private List<String> bootstrapServers = new ArrayList<>(Collections.singletonList("localhost:9092"));

	/**
	 * ID to pass to the server when making requests. Used for server-side logging.
	 */
	private @Nullable String clientId;

	/**
	 * Additional properties, common to producers and consumers, used to configure the
	 * client.
	 */
	private final Map<String, String> properties = new HashMap<>();

	private final Consumer consumer = new Consumer();

	private final Producer producer = new Producer();

	private final Admin admin = new Admin();

	private final Streams streams = new Streams();

	private final Listener listener = new Listener();

	private final Ssl ssl = new Ssl();

	private final Jaas jaas = new Jaas();

	private final Template template = new Template();

	private final Security security = new Security();

	private final Retry retry = new Retry();

	public List<String> getBootstrapServers() {
		return this.bootstrapServers;
	}

	public void setBootstrapServers(List<String> bootstrapServers) {
		this.bootstrapServers = bootstrapServers;
	}

	public @Nullable String getClientId() {
		return this.clientId;
	}

	public void setClientId(@Nullable String clientId) {
		this.clientId = clientId;
	}

	public Map<String, String> getProperties() {
		return this.properties;
	}

	public Consumer getConsumer() {
		return this.consumer;
	}

	public Producer getProducer() {
		return this.producer;
	}

	public Listener getListener() {
		return this.listener;
	}

	public Admin getAdmin() {
		return this.admin;
	}

	public Streams getStreams() {
		return this.streams;
	}

	public Ssl getSsl() {
		return this.ssl;
	}

	public Jaas getJaas() {
		return this.jaas;
	}

	public Template getTemplate() {
		return this.template;
	}

	public Security getSecurity() {
		return this.security;
	}

	public Retry getRetry() {
		return this.retry;
	}

	private Map<String, Object> buildCommonProperties() {
		Map<String, Object> properties = new HashMap<>();
		if (this.bootstrapServers != null) {
			properties.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, this.bootstrapServers);
		}
		if (this.clientId != null) {
			properties.put(CommonClientConfigs.CLIENT_ID_CONFIG, this.clientId);
		}
		properties.putAll(this.ssl.buildProperties());
		properties.putAll(this.security.buildProperties());
		if (!CollectionUtils.isEmpty(this.properties)) {
			properties.putAll(this.properties);
		}
		return properties;
	}

	/**
	 * Create an initial map of consumer properties from the state of this instance.
	 * <p>
	 * This allows you to add additional properties, if necessary, and override the
	 * default {@code kafkaConsumerFactory} bean.
	 * @return the consumer properties initialized with the customizations defined on this
	 * instance
	 */
	public Map<String, Object> buildConsumerProperties() {
		Map<String, Object> properties = buildCommonProperties();
		properties.putAll(this.consumer.buildProperties());
		return properties;
	}

	/**
	 * Create an initial map of producer properties from the state of this instance.
	 * <p>
	 * This allows you to add additional properties, if necessary, and override the
	 * default {@code kafkaProducerFactory} bean.
	 * @return the producer properties initialized with the customizations defined on this
	 * instance
	 */
	public Map<String, Object> buildProducerProperties() {
		Map<String, Object> properties = buildCommonProperties();
		properties.putAll(this.producer.buildProperties());
		return properties;
	}

	/**
	 * Create an initial map of admin properties from the state of this instance.
	 * <p>
	 * This allows you to add additional properties, if necessary, and override the
	 * default {@code kafkaAdmin} bean.
	 * @return the admin properties initialized with the customizations defined on this
	 * instance
	 */
	public Map<String, Object> buildAdminProperties() {
		Map<String, Object> properties = buildCommonProperties();
		properties.putAll(this.admin.buildProperties());
		return properties;
	}

	/**
	 * Create an initial map of streams properties from the state of this instance.
	 * <p>
	 * This allows you to add additional properties, if necessary.
	 * @return the streams properties initialized with the customizations defined on this
	 * instance
	 */
	public Map<String, Object> buildStreamsProperties() {
		Map<String, Object> properties = buildCommonProperties();
		properties.putAll(this.streams.buildProperties());
		return properties;
	}

	public static class Consumer {

		private final Ssl ssl = new Ssl();

		private final Security security = new Security();

		/**
		 * Frequency with which the consumer offsets are auto-committed to Kafka if
		 * 'enable.auto.commit' is set to true.
		 */
		private @Nullable Duration autoCommitInterval;

		/**
		 * What to do when there is no initial offset in Kafka or if the current offset no
		 * longer exists on the server.
		 */
		private @Nullable String autoOffsetReset;

		/**
		 * List of host:port pairs to use for establishing the initial connections to the
		 * Kafka cluster. Overrides the global property, for consumers.
		 */
		private @Nullable List<String> bootstrapServers;

		/**
		 * ID to pass to the server when making requests. Used for server-side logging.
		 */
		private @Nullable String clientId;

		/**
		 * Whether the consumer's offset is periodically committed in the background.
		 */
		private @Nullable Boolean enableAutoCommit;

		/**
		 * Maximum amount of time the server blocks before answering the fetch request if
		 * there isn't sufficient data to immediately satisfy the requirement given by
		 * "fetch-min-size".
		 */
		private @Nullable Duration fetchMaxWait;

		/**
		 * Minimum amount of data the server should return for a fetch request.
		 */
		private @Nullable DataSize fetchMinSize;

		/**
		 * Unique string that identifies the consumer group to which this consumer
		 * belongs.
		 */
		private @Nullable String groupId;

		/**
		 * Expected time between heartbeats to the consumer coordinator.
		 */
		private @Nullable Duration heartbeatInterval;

		/**
		 * Isolation level for reading messages that have been written transactionally.
		 */
		private IsolationLevel isolationLevel = IsolationLevel.READ_UNCOMMITTED;

		/**
		 * Deserializer class for keys.
		 */
		private Class<?> keyDeserializer = StringDeserializer.class;

		/**
		 * Deserializer class for values.
		 */
		private Class<?> valueDeserializer = StringDeserializer.class;

		/**
		 * Maximum number of records returned in a single call to poll().
		 */
		private @Nullable Integer maxPollRecords;

		/**
		 * Maximum delay between invocations of poll() when using consumer group
		 * management.
		 */
		private @Nullable Duration maxPollInterval;

		/**
		 * Additional consumer-specific properties used to configure the client.
		 */
		private final Map<String, String> properties = new HashMap<>();

		public Ssl getSsl() {
			return this.ssl;
		}

		public Security getSecurity() {
			return this.security;
		}

		public @Nullable Duration getAutoCommitInterval() {
			return this.autoCommitInterval;
		}

		public void setAutoCommitInterval(@Nullable Duration autoCommitInterval) {
			this.autoCommitInterval = autoCommitInterval;
		}

		public @Nullable String getAutoOffsetReset() {
			return this.autoOffsetReset;
		}

		public void setAutoOffsetReset(@Nullable String autoOffsetReset) {
			this.autoOffsetReset = autoOffsetReset;
		}

		public @Nullable List<String> getBootstrapServers() {
			return this.bootstrapServers;
		}

		public void setBootstrapServers(@Nullable List<String> bootstrapServers) {
			this.bootstrapServers = bootstrapServers;
		}

		public @Nullable String getClientId() {
			return this.clientId;
		}

		public void setClientId(@Nullable String clientId) {
			this.clientId = clientId;
		}

		public @Nullable Boolean getEnableAutoCommit() {
			return this.enableAutoCommit;
		}

		public void setEnableAutoCommit(@Nullable Boolean enableAutoCommit) {
			this.enableAutoCommit = enableAutoCommit;
		}

		public @Nullable Duration getFetchMaxWait() {
			return this.fetchMaxWait;
		}

		public void setFetchMaxWait(@Nullable Duration fetchMaxWait) {
			this.fetchMaxWait = fetchMaxWait;
		}

		public @Nullable DataSize getFetchMinSize() {
			return this.fetchMinSize;
		}

		public void setFetchMinSize(@Nullable DataSize fetchMinSize) {
			this.fetchMinSize = fetchMinSize;
		}

		public @Nullable String getGroupId() {
			return this.groupId;
		}

		public void setGroupId(@Nullable String groupId) {
			this.groupId = groupId;
		}

		public @Nullable Duration getHeartbeatInterval() {
			return this.heartbeatInterval;
		}

		public void setHeartbeatInterval(@Nullable Duration heartbeatInterval) {
			this.heartbeatInterval = heartbeatInterval;
		}

		public IsolationLevel getIsolationLevel() {
			return this.isolationLevel;
		}

		public void setIsolationLevel(IsolationLevel isolationLevel) {
			this.isolationLevel = isolationLevel;
		}

		public Class<?> getKeyDeserializer() {
			return this.keyDeserializer;
		}

		public void setKeyDeserializer(Class<?> keyDeserializer) {
			this.keyDeserializer = keyDeserializer;
		}

		public Class<?> getValueDeserializer() {
			return this.valueDeserializer;
		}

		public void setValueDeserializer(Class<?> valueDeserializer) {
			this.valueDeserializer = valueDeserializer;
		}

		public @Nullable Integer getMaxPollRecords() {
			return this.maxPollRecords;
		}

		public void setMaxPollRecords(@Nullable Integer maxPollRecords) {
			this.maxPollRecords = maxPollRecords;
		}

		public @Nullable Duration getMaxPollInterval() {
			return this.maxPollInterval;
		}

		public void setMaxPollInterval(@Nullable Duration maxPollInterval) {
			this.maxPollInterval = maxPollInterval;
		}

		public Map<String, String> getProperties() {
			return this.properties;
		}

		public Map<String, Object> buildProperties() {
			Properties properties = new Properties();
			PropertyMapper map = PropertyMapper.get().alwaysApplyingWhenNonNull();
			map.from(this::getAutoCommitInterval)
				.asInt(Duration::toMillis)
				.to(properties.in(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG));
			map.from(this::getAutoOffsetReset).to(properties.in(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG));
			map.from(this::getBootstrapServers).to(properties.in(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG));
			map.from(this::getClientId).to(properties.in(ConsumerConfig.CLIENT_ID_CONFIG));
			map.from(this::getEnableAutoCommit).to(properties.in(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG));
			map.from(this::getFetchMaxWait)
				.asInt(Duration::toMillis)
				.to(properties.in(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG));
			map.from(this::getFetchMinSize)
				.asInt(DataSize::toBytes)
				.to(properties.in(ConsumerConfig.FETCH_MIN_BYTES_CONFIG));
			map.from(this::getGroupId).to(properties.in(ConsumerConfig.GROUP_ID_CONFIG));
			map.from(this::getHeartbeatInterval)
				.asInt(Duration::toMillis)
				.to(properties.in(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG));
			map.from(() -> getIsolationLevel().name().toLowerCase(Locale.ROOT))
				.to(properties.in(ConsumerConfig.ISOLATION_LEVEL_CONFIG));
			map.from(this::getKeyDeserializer).to(properties.in(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG));
			map.from(this::getValueDeserializer).to(properties.in(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG));
			map.from(this::getMaxPollRecords).to(properties.in(ConsumerConfig.MAX_POLL_RECORDS_CONFIG));
			map.from(this::getMaxPollInterval)
				.asInt(Duration::toMillis)
				.to(properties.in(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG));
			return properties.with(this.ssl, this.security, this.properties);
		}

	}

	public static class Producer {

		private final Ssl ssl = new Ssl();

		private final Security security = new Security();

		/**
		 * Number of acknowledgments the producer requires the leader to have received
		 * before considering a request complete.
		 */
		private @Nullable String acks;

		/**
		 * Default batch size. A small batch size will make batching less common and may
		 * reduce throughput (a batch size of zero disables batching entirely).
		 */
		private @Nullable DataSize batchSize;

		/**
		 * List of host:port pairs to use for establishing the initial connections to the
		 * Kafka cluster. Overrides the global property, for producers.
		 */
		private @Nullable List<String> bootstrapServers;

		/**
		 * Total memory size the producer can use to buffer records waiting to be sent to
		 * the server.
		 */
		private @Nullable DataSize bufferMemory;

		/**
		 * ID to pass to the server when making requests. Used for server-side logging.
		 */
		private @Nullable String clientId;

		/**
		 * Compression type for all data generated by the producer.
		 */
		private @Nullable String compressionType;

		/**
		 * Serializer class for keys.
		 */
		private Class<?> keySerializer = StringSerializer.class;

		/**
		 * Serializer class for values.
		 */
		private Class<?> valueSerializer = StringSerializer.class;

		/**
		 * When greater than zero, enables retrying of failed sends.
		 */
		private @Nullable Integer retries;

		/**
		 * When non empty, enables transaction support for producer.
		 */
		private @Nullable String transactionIdPrefix;

		/**
		 * Additional producer-specific properties used to configure the client.
		 */
		private final Map<String, String> properties = new HashMap<>();

		public Ssl getSsl() {
			return this.ssl;
		}

		public Security getSecurity() {
			return this.security;
		}

		public @Nullable String getAcks() {
			return this.acks;
		}

		public void setAcks(@Nullable String acks) {
			this.acks = acks;
		}

		public @Nullable DataSize getBatchSize() {
			return this.batchSize;
		}

		public void setBatchSize(@Nullable DataSize batchSize) {
			this.batchSize = batchSize;
		}

		public @Nullable List<String> getBootstrapServers() {
			return this.bootstrapServers;
		}

		public void setBootstrapServers(@Nullable List<String> bootstrapServers) {
			this.bootstrapServers = bootstrapServers;
		}

		public @Nullable DataSize getBufferMemory() {
			return this.bufferMemory;
		}

		public void setBufferMemory(@Nullable DataSize bufferMemory) {
			this.bufferMemory = bufferMemory;
		}

		public @Nullable String getClientId() {
			return this.clientId;
		}

		public void setClientId(@Nullable String clientId) {
			this.clientId = clientId;
		}

		public @Nullable String getCompressionType() {
			return this.compressionType;
		}

		public void setCompressionType(@Nullable String compressionType) {
			this.compressionType = compressionType;
		}

		public Class<?> getKeySerializer() {
			return this.keySerializer;
		}

		public void setKeySerializer(Class<?> keySerializer) {
			this.keySerializer = keySerializer;
		}

		public Class<?> getValueSerializer() {
			return this.valueSerializer;
		}

		public void setValueSerializer(Class<?> valueSerializer) {
			this.valueSerializer = valueSerializer;
		}

		public @Nullable Integer getRetries() {
			return this.retries;
		}

		public void setRetries(@Nullable Integer retries) {
			this.retries = retries;
		}

		public @Nullable String getTransactionIdPrefix() {
			return this.transactionIdPrefix;
		}

		public void setTransactionIdPrefix(@Nullable String transactionIdPrefix) {
			this.transactionIdPrefix = transactionIdPrefix;
		}

		public Map<String, String> getProperties() {
			return this.properties;
		}

		public Map<String, Object> buildProperties() {
			Properties properties = new Properties();
			PropertyMapper map = PropertyMapper.get().alwaysApplyingWhenNonNull();
			map.from(this::getAcks).to(properties.in(ProducerConfig.ACKS_CONFIG));
			map.from(this::getBatchSize).asInt(DataSize::toBytes).to(properties.in(ProducerConfig.BATCH_SIZE_CONFIG));
			map.from(this::getBootstrapServers).to(properties.in(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG));
			map.from(this::getBufferMemory)
				.as(DataSize::toBytes)
				.to(properties.in(ProducerConfig.BUFFER_MEMORY_CONFIG));
			map.from(this::getClientId).to(properties.in(ProducerConfig.CLIENT_ID_CONFIG));
			map.from(this::getCompressionType).to(properties.in(ProducerConfig.COMPRESSION_TYPE_CONFIG));
			map.from(this::getKeySerializer).to(properties.in(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG));
			map.from(this::getRetries).to(properties.in(ProducerConfig.RETRIES_CONFIG));
			map.from(this::getValueSerializer).to(properties.in(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG));
			return properties.with(this.ssl, this.security, this.properties);
		}

	}

	public static class Admin {

		private final Ssl ssl = new Ssl();

		private final Security security = new Security();

		/**
		 * ID to pass to the server when making requests. Used for server-side logging.
		 */
		private @Nullable String clientId;

		/**
		 * Additional admin-specific properties used to configure the client.
		 */
		private final Map<String, String> properties = new HashMap<>();

		/**
		 * Close timeout.
		 */
		private @Nullable Duration closeTimeout;

		/**
		 * Operation timeout.
		 */
		private @Nullable Duration operationTimeout;

		/**
		 * Whether to fail fast if the broker is not available on startup.
		 */
		private boolean failFast;

		/**
		 * Whether to enable modification of existing topic configuration.
		 */
		private boolean modifyTopicConfigs;

		/**
		 * Whether to automatically create topics during context initialization. When set
		 * to false, disables automatic topic creation during context initialization.
		 */
		private boolean autoCreate = true;

		public Ssl getSsl() {
			return this.ssl;
		}

		public Security getSecurity() {
			return this.security;
		}

		public @Nullable String getClientId() {
			return this.clientId;
		}

		public void setClientId(@Nullable String clientId) {
			this.clientId = clientId;
		}

		public @Nullable Duration getCloseTimeout() {
			return this.closeTimeout;
		}

		public void setCloseTimeout(@Nullable Duration closeTimeout) {
			this.closeTimeout = closeTimeout;
		}

		public @Nullable Duration getOperationTimeout() {
			return this.operationTimeout;
		}

		public void setOperationTimeout(@Nullable Duration operationTimeout) {
			this.operationTimeout = operationTimeout;
		}

		public boolean isFailFast() {
			return this.failFast;
		}

		public void setFailFast(boolean failFast) {
			this.failFast = failFast;
		}

		public boolean isModifyTopicConfigs() {
			return this.modifyTopicConfigs;
		}

		public void setModifyTopicConfigs(boolean modifyTopicConfigs) {
			this.modifyTopicConfigs = modifyTopicConfigs;
		}

		public boolean isAutoCreate() {
			return this.autoCreate;
		}

		public void setAutoCreate(boolean autoCreate) {
			this.autoCreate = autoCreate;
		}

		public Map<String, String> getProperties() {
			return this.properties;
		}

		public Map<String, Object> buildProperties() {
			Properties properties = new Properties();
			PropertyMapper map = PropertyMapper.get().alwaysApplyingWhenNonNull();
			map.from(this::getClientId).to(properties.in(ProducerConfig.CLIENT_ID_CONFIG));
			return properties.with(this.ssl, this.security, this.properties);
		}

	}

	/**
	 * High (and some medium) priority Streams properties and a general properties bucket.
	 */
	public static class Streams {

		private final Ssl ssl = new Ssl();

		private final Security security = new Security();

		private final Cleanup cleanup = new Cleanup();

		/**
		 * Kafka streams application.id property; default spring.application.name.
		 */
		private @Nullable String applicationId;

		/**
		 * Whether to auto-start the streams factory bean.
		 */
		private boolean autoStartup = true;

		/**
		 * List of host:port pairs to use for establishing the initial connections to the
		 * Kafka cluster. Overrides the global property, for streams.
		 */
		private @Nullable List<String> bootstrapServers;

		/**
		 * Maximum size of the in-memory state store cache across all threads.
		 */
		private @Nullable DataSize stateStoreCacheMaxSize;

		/**
		 * ID to pass to the server when making requests. Used for server-side logging.
		 */
		private @Nullable String clientId;

		/**
		 * The replication factor for change log topics and repartition topics created by
		 * the stream processing application.
		 */
		private @Nullable Integer replicationFactor;

		/**
		 * Directory location for the state store.
		 */
		private @Nullable String stateDir;

		/**
		 * Additional Kafka properties used to configure the streams.
		 */
		private final Map<String, String> properties = new HashMap<>();

		public Ssl getSsl() {
			return this.ssl;
		}

		public Security getSecurity() {
			return this.security;
		}

		public Cleanup getCleanup() {
			return this.cleanup;
		}

		public @Nullable String getApplicationId() {
			return this.applicationId;
		}

		public void setApplicationId(@Nullable String applicationId) {
			this.applicationId = applicationId;
		}

		public boolean isAutoStartup() {
			return this.autoStartup;
		}

		public void setAutoStartup(boolean autoStartup) {
			this.autoStartup = autoStartup;
		}

		public @Nullable List<String> getBootstrapServers() {
			return this.bootstrapServers;
		}

		public void setBootstrapServers(@Nullable List<String> bootstrapServers) {
			this.bootstrapServers = bootstrapServers;
		}

		public @Nullable DataSize getStateStoreCacheMaxSize() {
			return this.stateStoreCacheMaxSize;
		}

		public void setStateStoreCacheMaxSize(@Nullable DataSize stateStoreCacheMaxSize) {
			this.stateStoreCacheMaxSize = stateStoreCacheMaxSize;
		}

		public @Nullable String getClientId() {
			return this.clientId;
		}

		public void setClientId(@Nullable String clientId) {
			this.clientId = clientId;
		}

		public @Nullable Integer getReplicationFactor() {
			return this.replicationFactor;
		}

		public void setReplicationFactor(@Nullable Integer replicationFactor) {
			this.replicationFactor = replicationFactor;
		}

		public @Nullable String getStateDir() {
			return this.stateDir;
		}

		public void setStateDir(@Nullable String stateDir) {
			this.stateDir = stateDir;
		}

		public Map<String, String> getProperties() {
			return this.properties;
		}

		public Map<String, Object> buildProperties() {
			Properties properties = new Properties();
			PropertyMapper map = PropertyMapper.get().alwaysApplyingWhenNonNull();
			map.from(this::getApplicationId).to(properties.in("application.id"));
			map.from(this::getBootstrapServers).to(properties.in(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG));
			map.from(this::getStateStoreCacheMaxSize)
				.asInt(DataSize::toBytes)
				.to(properties.in("statestore.cache.max.bytes"));
			map.from(this::getClientId).to(properties.in(CommonClientConfigs.CLIENT_ID_CONFIG));
			map.from(this::getReplicationFactor).to(properties.in("replication.factor"));
			map.from(this::getStateDir).to(properties.in("state.dir"));
			return properties.with(this.ssl, this.security, this.properties);
		}

	}

	public static class Template {

		/**
		 * Default topic to which messages are sent.
		 */
		private @Nullable String defaultTopic;

		/**
		 * Transaction id prefix, override the transaction id prefix in the producer
		 * factory.
		 */
		private @Nullable String transactionIdPrefix;

		/**
		 * Whether to enable observation.
		 */
		private boolean observationEnabled;

		public @Nullable String getDefaultTopic() {
			return this.defaultTopic;
		}

		public void setDefaultTopic(@Nullable String defaultTopic) {
			this.defaultTopic = defaultTopic;
		}

		public @Nullable String getTransactionIdPrefix() {
			return this.transactionIdPrefix;
		}

		public void setTransactionIdPrefix(@Nullable String transactionIdPrefix) {
			this.transactionIdPrefix = transactionIdPrefix;
		}

		public boolean isObservationEnabled() {
			return this.observationEnabled;
		}

		public void setObservationEnabled(boolean observationEnabled) {
			this.observationEnabled = observationEnabled;
		}

	}

	public static class Listener {

		public enum Type {

			/**
			 * Invokes the endpoint with one ConsumerRecord at a time.
			 */
			SINGLE,

			/**
			 * Invokes the endpoint with a batch of ConsumerRecords.
			 */
			BATCH

		}

		/**
		 * Listener type.
		 */
		private Type type = Type.SINGLE;

		/**
		 * Listener AckMode. See the spring-kafka documentation.
		 */
		private @Nullable AckMode ackMode;

		/**
		 * Support for asynchronous record acknowledgements. Only applies when
		 * spring.kafka.listener.ack-mode is manual or manual-immediate.
		 */
		private @Nullable Boolean asyncAcks;

		/**
		 * Prefix for the listener's consumer client.id property.
		 */
		private @Nullable String clientId;

		/**
		 * Number of threads to run in the listener containers.
		 */
		private @Nullable Integer concurrency;

		/**
		 * Timeout to use when polling the consumer.
		 */
		private @Nullable Duration pollTimeout;

		/**
		 * Multiplier applied to "pollTimeout" to determine if a consumer is
		 * non-responsive.
		 */
		private @Nullable Float noPollThreshold;

		/**
		 * Number of records between offset commits when ackMode is "COUNT" or
		 * "COUNT_TIME".
		 */
		private @Nullable Integer ackCount;

		/**
		 * Time between offset commits when ackMode is "TIME" or "COUNT_TIME".
		 */
		private @Nullable Duration ackTime;

		/**
		 * Sleep interval between Consumer.poll(Duration) calls.
		 */
		private Duration idleBetweenPolls = Duration.ZERO;

		/**
		 * Time between publishing idle consumer events (no data received).
		 */
		private @Nullable Duration idleEventInterval;

		/**
		 * Time between publishing idle partition consumer events (no data received for
		 * partition).
		 */
		private @Nullable Duration idlePartitionEventInterval;

		/**
		 * Time between checks for non-responsive consumers. If a duration suffix is not
		 * specified, seconds will be used.
		 */
		@DurationUnit(ChronoUnit.SECONDS)
		private @Nullable Duration monitorInterval;

		/**
		 * Whether to log the container configuration during initialization (INFO level).
		 */
		private @Nullable Boolean logContainerConfig;

		/**
		 * Whether the container should fail to start if at least one of the configured
		 * topics are not present on the broker.
		 */
		private boolean missingTopicsFatal = false;

		/**
		 * Whether the container stops after the current record is processed or after all
		 * the records from the previous poll are processed.
		 */
		private boolean immediateStop = false;

		/**
		 * Whether to auto start the container.
		 */
		private boolean autoStartup = true;

		/**
		 * Whether to instruct the container to change the consumer thread name during
		 * initialization.
		 */
		private @Nullable Boolean changeConsumerThreadName;

		/**
		 * Whether to enable observation.
		 */
		private boolean observationEnabled;

		/**
		 * Time between retries after authentication exceptions.
		 */
		private @Nullable Duration authExceptionRetryInterval;

		public Type getType() {
			return this.type;
		}

		public void setType(Type type) {
			this.type = type;
		}

		public @Nullable AckMode getAckMode() {
			return this.ackMode;
		}

		public void setAckMode(@Nullable AckMode ackMode) {
			this.ackMode = ackMode;
		}

		public @Nullable Boolean getAsyncAcks() {
			return this.asyncAcks;
		}

		public void setAsyncAcks(@Nullable Boolean asyncAcks) {
			this.asyncAcks = asyncAcks;
		}

		public @Nullable String getClientId() {
			return this.clientId;
		}

		public void setClientId(@Nullable String clientId) {
			this.clientId = clientId;
		}

		public @Nullable Integer getConcurrency() {
			return this.concurrency;
		}

		public void setConcurrency(@Nullable Integer concurrency) {
			this.concurrency = concurrency;
		}

		public @Nullable Duration getPollTimeout() {
			return this.pollTimeout;
		}

		public void setPollTimeout(@Nullable Duration pollTimeout) {
			this.pollTimeout = pollTimeout;
		}

		public @Nullable Float getNoPollThreshold() {
			return this.noPollThreshold;
		}

		public void setNoPollThreshold(@Nullable Float noPollThreshold) {
			this.noPollThreshold = noPollThreshold;
		}

		public @Nullable Integer getAckCount() {
			return this.ackCount;
		}

		public void setAckCount(@Nullable Integer ackCount) {
			this.ackCount = ackCount;
		}

		public @Nullable Duration getAckTime() {
			return this.ackTime;
		}

		public void setAckTime(@Nullable Duration ackTime) {
			this.ackTime = ackTime;
		}

		public Duration getIdleBetweenPolls() {
			return this.idleBetweenPolls;
		}

		public void setIdleBetweenPolls(Duration idleBetweenPolls) {
			this.idleBetweenPolls = idleBetweenPolls;
		}

		public @Nullable Duration getIdleEventInterval() {
			return this.idleEventInterval;
		}

		public void setIdleEventInterval(@Nullable Duration idleEventInterval) {
			this.idleEventInterval = idleEventInterval;
		}

		public @Nullable Duration getIdlePartitionEventInterval() {
			return this.idlePartitionEventInterval;
		}

		public void setIdlePartitionEventInterval(@Nullable Duration idlePartitionEventInterval) {
			this.idlePartitionEventInterval = idlePartitionEventInterval;
		}

		public @Nullable Duration getMonitorInterval() {
			return this.monitorInterval;
		}

		public void setMonitorInterval(@Nullable Duration monitorInterval) {
			this.monitorInterval = monitorInterval;
		}

		public @Nullable Boolean getLogContainerConfig() {
			return this.logContainerConfig;
		}

		public void setLogContainerConfig(@Nullable Boolean logContainerConfig) {
			this.logContainerConfig = logContainerConfig;
		}

		public boolean isMissingTopicsFatal() {
			return this.missingTopicsFatal;
		}

		public void setMissingTopicsFatal(boolean missingTopicsFatal) {
			this.missingTopicsFatal = missingTopicsFatal;
		}

		public boolean isImmediateStop() {
			return this.immediateStop;
		}

		public void setImmediateStop(boolean immediateStop) {
			this.immediateStop = immediateStop;
		}

		public boolean isAutoStartup() {
			return this.autoStartup;
		}

		public void setAutoStartup(boolean autoStartup) {
			this.autoStartup = autoStartup;
		}

		public @Nullable Boolean getChangeConsumerThreadName() {
			return this.changeConsumerThreadName;
		}

		public void setChangeConsumerThreadName(@Nullable Boolean changeConsumerThreadName) {
			this.changeConsumerThreadName = changeConsumerThreadName;
		}

		public boolean isObservationEnabled() {
			return this.observationEnabled;
		}

		public void setObservationEnabled(boolean observationEnabled) {
			this.observationEnabled = observationEnabled;
		}

		public @Nullable Duration getAuthExceptionRetryInterval() {
			return this.authExceptionRetryInterval;
		}

		public void setAuthExceptionRetryInterval(@Nullable Duration authExceptionRetryInterval) {
			this.authExceptionRetryInterval = authExceptionRetryInterval;
		}

	}

	public static class Ssl {

		/**
		 * Name of the SSL bundle to use.
		 */
		private @Nullable String bundle;

		/**
		 * Password of the private key in either key store key or key store file.
		 */
		private @Nullable String keyPassword;

		/**
		 * Certificate chain in PEM format with a list of X.509 certificates.
		 */
		private @Nullable String keyStoreCertificateChain;

		/**
		 * Private key in PEM format with PKCS#8 keys.
		 */
		private @Nullable String keyStoreKey;

		/**
		 * Location of the key store file.
		 */
		private @Nullable Resource keyStoreLocation;

		/**
		 * Store password for the key store file.
		 */
		private @Nullable String keyStorePassword;

		/**
		 * Type of the key store.
		 */
		private @Nullable String keyStoreType;

		/**
		 * Trusted certificates in PEM format with X.509 certificates.
		 */
		private @Nullable String trustStoreCertificates;

		/**
		 * Location of the trust store file.
		 */
		private @Nullable Resource trustStoreLocation;

		/**
		 * Store password for the trust store file.
		 */
		private @Nullable String trustStorePassword;

		/**
		 * Type of the trust store.
		 */
		private @Nullable String trustStoreType;

		/**
		 * SSL protocol to use.
		 */
		private @Nullable String protocol;

		public @Nullable String getBundle() {
			return this.bundle;
		}

		public void setBundle(@Nullable String bundle) {
			this.bundle = bundle;
		}

		public @Nullable String getKeyPassword() {
			return this.keyPassword;
		}

		public void setKeyPassword(@Nullable String keyPassword) {
			this.keyPassword = keyPassword;
		}

		public @Nullable String getKeyStoreCertificateChain() {
			return this.keyStoreCertificateChain;
		}

		public void setKeyStoreCertificateChain(@Nullable String keyStoreCertificateChain) {
			this.keyStoreCertificateChain = keyStoreCertificateChain;
		}

		public @Nullable String getKeyStoreKey() {
			return this.keyStoreKey;
		}

		public void setKeyStoreKey(@Nullable String keyStoreKey) {
			this.keyStoreKey = keyStoreKey;
		}

		public @Nullable Resource getKeyStoreLocation() {
			return this.keyStoreLocation;
		}

		public void setKeyStoreLocation(@Nullable Resource keyStoreLocation) {
			this.keyStoreLocation = keyStoreLocation;
		}

		public @Nullable String getKeyStorePassword() {
			return this.keyStorePassword;
		}

		public void setKeyStorePassword(@Nullable String keyStorePassword) {
			this.keyStorePassword = keyStorePassword;
		}

		public @Nullable String getKeyStoreType() {
			return this.keyStoreType;
		}

		public void setKeyStoreType(@Nullable String keyStoreType) {
			this.keyStoreType = keyStoreType;
		}

		public @Nullable String getTrustStoreCertificates() {
			return this.trustStoreCertificates;
		}

		public void setTrustStoreCertificates(@Nullable String trustStoreCertificates) {
			this.trustStoreCertificates = trustStoreCertificates;
		}

		public @Nullable Resource getTrustStoreLocation() {
			return this.trustStoreLocation;
		}

		public void setTrustStoreLocation(@Nullable Resource trustStoreLocation) {
			this.trustStoreLocation = trustStoreLocation;
		}

		public @Nullable String getTrustStorePassword() {
			return this.trustStorePassword;
		}

		public void setTrustStorePassword(@Nullable String trustStorePassword) {
			this.trustStorePassword = trustStorePassword;
		}

		public @Nullable String getTrustStoreType() {
			return this.trustStoreType;
		}

		public void setTrustStoreType(@Nullable String trustStoreType) {
			this.trustStoreType = trustStoreType;
		}

		public @Nullable String getProtocol() {
			return this.protocol;
		}

		public void setProtocol(@Nullable String protocol) {
			this.protocol = protocol;
		}

		public Map<String, Object> buildProperties() {
			validate();
			String bundleName = getBundle();
			Properties properties = new Properties();
			if (StringUtils.hasText(bundleName)) {
				return properties;
			}
			PropertyMapper map = PropertyMapper.get().alwaysApplyingWhenNonNull();
			map.from(this::getKeyPassword).to(properties.in(SslConfigs.SSL_KEY_PASSWORD_CONFIG));
			map.from(this::getKeyStoreCertificateChain)
				.to(properties.in(SslConfigs.SSL_KEYSTORE_CERTIFICATE_CHAIN_CONFIG));
			map.from(this::getKeyStoreKey).to(properties.in(SslConfigs.SSL_KEYSTORE_KEY_CONFIG));
			map.from(this::getKeyStoreLocation)
				.as(this::resourceToPath)
				.to(properties.in(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG));
			map.from(this::getKeyStorePassword).to(properties.in(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG));
			map.from(this::getKeyStoreType).to(properties.in(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG));
			map.from(this::getTrustStoreCertificates).to(properties.in(SslConfigs.SSL_TRUSTSTORE_CERTIFICATES_CONFIG));
			map.from(this::getTrustStoreLocation)
				.as(this::resourceToPath)
				.to(properties.in(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG));
			map.from(this::getTrustStorePassword).to(properties.in(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG));
			map.from(this::getTrustStoreType).to(properties.in(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG));
			map.from(this::getProtocol).to(properties.in(SslConfigs.SSL_PROTOCOL_CONFIG));
			return properties;
		}

		private void validate() {
			MutuallyExclusiveConfigurationPropertiesException.throwIfMultipleMatchingValuesIn((entries) -> {
				entries.put("spring.kafka.ssl.key-store-key", getKeyStoreKey());
				entries.put("spring.kafka.ssl.key-store-location", getKeyStoreLocation());
			}, this::hasValue);
			MutuallyExclusiveConfigurationPropertiesException.throwIfMultipleMatchingValuesIn((entries) -> {
				entries.put("spring.kafka.ssl.trust-store-certificates", getTrustStoreCertificates());
				entries.put("spring.kafka.ssl.trust-store-location", getTrustStoreLocation());
			}, this::hasValue);
			MutuallyExclusiveConfigurationPropertiesException.throwIfMultipleMatchingValuesIn((entries) -> {
				entries.put("spring.kafka.ssl.bundle", getBundle());
				entries.put("spring.kafka.ssl.key-store-key", getKeyStoreKey());
			}, this::hasValue);
			MutuallyExclusiveConfigurationPropertiesException.throwIfMultipleMatchingValuesIn((entries) -> {
				entries.put("spring.kafka.ssl.bundle", getBundle());
				entries.put("spring.kafka.ssl.key-store-location", getKeyStoreLocation());
			}, this::hasValue);
			MutuallyExclusiveConfigurationPropertiesException.throwIfMultipleMatchingValuesIn((entries) -> {
				entries.put("spring.kafka.ssl.bundle", getBundle());
				entries.put("spring.kafka.ssl.trust-store-certificates", getTrustStoreCertificates());
			}, this::hasValue);
			MutuallyExclusiveConfigurationPropertiesException.throwIfMultipleMatchingValuesIn((entries) -> {
				entries.put("spring.kafka.ssl.bundle", getBundle());
				entries.put("spring.kafka.ssl.trust-store-location", getTrustStoreLocation());
			}, this::hasValue);
		}

		private boolean hasValue(@Nullable Object value) {
			return (value instanceof String string) ? StringUtils.hasText(string) : value != null;
		}

		private String resourceToPath(Resource resource) {
			try {
				return resource.getFile().getAbsolutePath();
			}
			catch (IOException ex) {
				throw new IllegalStateException("Resource '" + resource + "' must be on a file system", ex);
			}
		}

	}

	public static class Jaas {

		/**
		 * Whether to enable JAAS configuration.
		 */
		private boolean enabled;

		/**
		 * Login module.
		 */
		private String loginModule = "com.sun.security.auth.module.Krb5LoginModule";

		/**
		 * Control flag for login configuration.
		 */
		private KafkaJaasLoginModuleInitializer.ControlFlag controlFlag = KafkaJaasLoginModuleInitializer.ControlFlag.REQUIRED;

		/**
		 * Additional JAAS options.
		 */
		private final Map<String, String> options = new HashMap<>();

		public boolean isEnabled() {
			return this.enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public String getLoginModule() {
			return this.loginModule;
		}

		public void setLoginModule(String loginModule) {
			this.loginModule = loginModule;
		}

		public KafkaJaasLoginModuleInitializer.ControlFlag getControlFlag() {
			return this.controlFlag;
		}

		public void setControlFlag(KafkaJaasLoginModuleInitializer.ControlFlag controlFlag) {
			this.controlFlag = controlFlag;
		}

		public Map<String, String> getOptions() {
			return this.options;
		}

		public void setOptions(Map<String, String> options) {
			if (options != null) {
				this.options.putAll(options);
			}
		}

	}

	public static class Security {

		/**
		 * Security protocol used to communicate with brokers.
		 */
		private @Nullable String protocol;

		public @Nullable String getProtocol() {
			return this.protocol;
		}

		public void setProtocol(@Nullable String protocol) {
			this.protocol = protocol;
		}

		public Map<String, Object> buildProperties() {
			Properties properties = new Properties();
			PropertyMapper map = PropertyMapper.get().alwaysApplyingWhenNonNull();
			map.from(this::getProtocol).to(properties.in(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG));
			return properties;
		}

	}

	public static class Retry {

		private final Topic topic = new Topic();

		public Topic getTopic() {
			return this.topic;
		}

		/**
		 * Properties for non-blocking, topic-based retries.
		 */
		public static class Topic {

			/**
			 * Whether to enable topic-based non-blocking retries.
			 */
			private boolean enabled;

			/**
			 * Total number of processing attempts made before sending the message to the
			 * DLT.
			 */
			private int attempts = 3;

			public boolean isEnabled() {
				return this.enabled;
			}

			public void setEnabled(boolean enabled) {
				this.enabled = enabled;
			}

			public int getAttempts() {
				return this.attempts;
			}

			public void setAttempts(int attempts) {
				this.attempts = attempts;
			}

			private final Backoff backoff = new Backoff();

			public Backoff getBackoff() {
				return this.backoff;
			}

			public static class Backoff {

				/**
				 * Canonical backoff period. Used as an initial value in the exponential
				 * case, and as a minimum value in the uniform case.
				 */
				private Duration delay = Duration.ofSeconds(1);

				/**
				 * Multiplier to use for generating the next backoff delay.
				 */
				private double multiplier = 0.0;

				/**
				 * Maximum wait between retries. If less than the delay then the default
				 * of 30 seconds is applied.
				 */
				private Duration maxDelay = Duration.ZERO;

				/**
				 * Whether to have the backoff delays.
				 */
				private boolean random = false;

				public Duration getDelay() {
					return this.delay;
				}

				public void setDelay(Duration delay) {
					this.delay = delay;
				}

				public double getMultiplier() {
					return this.multiplier;
				}

				public void setMultiplier(double multiplier) {
					this.multiplier = multiplier;
				}

				public Duration getMaxDelay() {
					return this.maxDelay;
				}

				public void setMaxDelay(Duration maxDelay) {
					this.maxDelay = maxDelay;
				}

				public boolean isRandom() {
					return this.random;
				}

				public void setRandom(boolean random) {
					this.random = random;
				}

			}

		}

	}

	public static class Cleanup {

		/**
		 * Cleanup the application’s local state directory on startup.
		 */
		private boolean onStartup = false;

		/**
		 * Cleanup the application’s local state directory on shutdown.
		 */
		private boolean onShutdown = false;

		public boolean isOnStartup() {
			return this.onStartup;
		}

		public void setOnStartup(boolean onStartup) {
			this.onStartup = onStartup;
		}

		public boolean isOnShutdown() {
			return this.onShutdown;
		}

		public void setOnShutdown(boolean onShutdown) {
			this.onShutdown = onShutdown;
		}

	}

	public enum IsolationLevel {

		/**
		 * Read everything including aborted transactions.
		 */
		READ_UNCOMMITTED((byte) 0),

		/**
		 * Read records from committed transactions, in addition to records not part of
		 * transactions.
		 */
		READ_COMMITTED((byte) 1);

		private final byte id;

		IsolationLevel(byte id) {
			this.id = id;
		}

		public byte id() {
			return this.id;
		}

	}

	@SuppressWarnings("serial")
	private static final class Properties extends HashMap<String, Object> {

		<V> java.util.function.Consumer<V> in(String key) {
			return (value) -> put(key, value);
		}

		Properties with(Ssl ssl, Security security, Map<String, String> properties) {
			putAll(ssl.buildProperties());
			putAll(security.buildProperties());
			putAll(properties);
			return this;
		}

	}

}
