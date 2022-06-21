/*
 * Copyright 2012-2022 the original author or authors.
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

package org.springframework.boot.autoconfigure.http;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.support.WebClientAdapter;
import org.springframework.web.service.invoker.HttpClientAdapter;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

/**
 * @author Moritz Halbritter
 */
@AutoConfiguration(after = WebClientAutoConfiguration.class)
public class HttpInterfaceAutoConfiguration {

	@Bean
	@ConditionalOnBean(WebClient.Builder.class)
	WebClientAdapter webClientAdapter(WebClient.Builder webClientBuilder) {
		// TODO: How to get the baseUrl into here?
		return new WebClientAdapter(webClientBuilder.build());
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(HttpClientAdapter.class)
	HttpServiceProxyFactory httpServiceProxyFactory(HttpClientAdapter httpClientAdapter) {
		return new HttpServiceProxyFactory(httpClientAdapter);
	}

}
