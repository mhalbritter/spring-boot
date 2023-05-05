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

package org.springframework.boot.logging.logback;

import java.util.List;
import java.util.Map;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link CorrelationConverter}.
 *
 * @author Moritz Halbritter
 */
class CorrelationConverterTests {

	private static final String LOG_CORRELATION_PATTERN = "[test-application,%X{traceId:-},%X{spanId:-}]";

	private final LoggingEvent event = new LoggingEvent();

	@BeforeEach
	void setUp() {
		MDC.setContextMap(Map.of("traceId", "1", "spanId", "2"));
	}

	@AfterEach
	void tearDown() {
		MDC.clear();
	}

	@Test
	void shouldBeSpaceOnlyWhenPatternIsNotSet() {
		CorrelationConverter converter = create(null);
		String converted = converter.convert(this.event);
		assertThat(converted).isEqualTo(" ");
	}

	@Test
	void shouldBeEmptyOnlyWhenPatternIsNotSet() {
		CorrelationConverter converter = create(null, "nospaces");
		String converted = converter.convert(this.event);
		assertThat(converted).isEmpty();
	}

	@Test
	void shouldPadWithSpaces() {
		CorrelationConverter converter = create(LOG_CORRELATION_PATTERN);
		String converted = converter.convert(this.event);
		assertThat(converted).isEqualTo(" [test-application,1,2] ");
	}

	@Test
	void shouldNotPadWithSpaces() {
		CorrelationConverter converter = create(LOG_CORRELATION_PATTERN, "nospaces");
		String converted = converter.convert(this.event);
		assertThat(converted).isEqualTo("[test-application,1,2]");
	}

	private static CorrelationConverter create(String pattern, String... options) {
		CorrelationConverter converter = new CorrelationConverter(pattern);
		converter.setContext(new LoggerContext());
		converter.setOptionList(List.of(options));
		converter.start();
		return converter;
	}

}
