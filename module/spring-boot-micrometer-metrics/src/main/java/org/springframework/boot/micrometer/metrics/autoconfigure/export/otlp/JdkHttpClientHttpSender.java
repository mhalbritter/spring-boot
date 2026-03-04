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

package org.springframework.boot.micrometer.metrics.autoconfigure.export.otlp;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import java.util.Map;

import io.micrometer.core.ipc.http.HttpSender;

/**
 * {@link HttpSender} implementation using the JDK {@link HttpClient}.
 *
 * @author Moritz Halbritter
 */
class JdkHttpClientHttpSender implements HttpSender {

	private final HttpClient httpClient;

	private final Duration timeout;

	JdkHttpClientHttpSender(Duration connectTimeout, Duration timeout) {
		this.httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
		this.timeout = timeout;
	}

	@Override
	public Response send(Request request) throws IOException {
		HttpRequest.Builder httpRequest = HttpRequest.newBuilder()
			.uri(URI.create(request.getUrl().toString()))
			.timeout(this.timeout);
		for (Map.Entry<String, String> header : request.getRequestHeaders().entrySet()) {
			httpRequest.header(header.getKey(), header.getValue());
		}
		httpRequest.method(request.getMethod().name(), getBodyPublisher(request));
		try {
			HttpResponse<String> httpResponse = this.httpClient.send(httpRequest.build(), BodyHandlers.ofString());
			return new Response(httpResponse.statusCode(), httpResponse.body());
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IOException("HTTP request interrupted", ex);
		}
	}

	private static BodyPublisher getBodyPublisher(Request request) {
		if (request.getMethod() == Method.GET) {
			return BodyPublishers.noBody();
		}
		return BodyPublishers.ofByteArray(request.getEntity());
	}

}
