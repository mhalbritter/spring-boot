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

package org.springframework.boot.docs.nativeimage.advanced.customhints;

import reactor.core.publisher.Mono;

import org.springframework.aot.hint.annotation.RegisterReflectionForBinding;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.reactive.function.client.WebClient;

@Component
class JsonSerialization {

	@RegisterReflectionForBinding(MyDto.class)
	Mono<ResponseEntity<Void>> sendToServer(WebClient webClient) {
		// MyDto is serialized to JSON
		MyDto myDto = new MyDto();
		return webClient.post().header("Content-Type", "application/json").bodyValue(myDto).retrieve()
				.toBodilessEntity();
	}

	@RegisterReflectionForBinding(MyDto.class)
	ResponseEntity<MyDto> retrieveFromServer(RestTemplate restTemplate) {
		// MyDto is deserialized from JSON
		return restTemplate.getForEntity("/my-dto", MyDto.class);
	}

}
