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

package org.springframework.boot.test.autoconfigure.data.elasticsearch;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.testsupport.testcontainers.DockerImageNames;
import org.springframework.context.annotation.Bean;
import org.springframework.data.elasticsearch.client.elc.ReactiveElasticsearchTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link DataElasticsearchTest @DataElasticsearchTest} with
 * Elasticsearch 8 using reactive repositories.
 *
 * @author Scott Frederick
 */
@Testcontainers(disabledWithoutDocker = true)
@DataElasticsearchTest(properties = { "spring.elasticsearch.restclient.ssl.bundle=elasticsearch-container" })
class DataElasticsearchTestVersion8ReactiveIntegrationTests {

	@Container
	@ServiceConnection
	static final ElasticsearchContainer elasticsearch = new ElasticsearchContainer(DockerImageNames.elasticsearch8())
		.withEnv("ES_JAVA_OPTS", "-Xms32m -Xmx512m")
		.withStartupAttempts(5)
		.withStartupTimeout(Duration.ofMinutes(10));

	@Autowired
	private ReactiveElasticsearchTemplate elasticsearchTemplate;

	@Autowired
	private ExampleReactiveRepository exampleReactiveRepository;

	@Test
	void testRepository() {
		ExampleDocument exampleDocument = new ExampleDocument();
		exampleDocument.setText("Look, new @DataElasticsearchTest!");
		exampleDocument = this.exampleReactiveRepository.save(exampleDocument).block(Duration.ofSeconds(30));
		assertThat(exampleDocument.getId()).isNotNull();
		assertThat(this.elasticsearchTemplate.exists(exampleDocument.getId(), ExampleDocument.class)
			.block(Duration.ofSeconds(30))).isTrue();
	}

	@TestConfiguration
	static class ElasticsearchContainerSslBundleConfiguration {

		@Bean
		ElasticsearchContainerSslBundleRegistrar elasticsearchContainerSslBundleRegistrar() {
			return new ElasticsearchContainerSslBundleRegistrar("elasticsearch-container", elasticsearch);
		}

	}

}
