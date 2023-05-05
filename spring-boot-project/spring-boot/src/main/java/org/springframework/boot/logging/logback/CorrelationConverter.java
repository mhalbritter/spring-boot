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

import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Context;
import ch.qos.logback.core.CoreConstants;

import org.springframework.util.StringUtils;

/**
 * Provides the formatted value of the {@code LOG_CORRELATION_PATTERN} system property.
 * The {@code nospaces} option can be set to prevent padding the value with spaces.
 *
 * @author Moritz Halbritter
 * @since 3.2.0
 */
public class CorrelationConverter extends ClassicConverter {

	private final PatternLayout patternLayout;

	private String padding;

	public CorrelationConverter() {
		this(System.getProperty("LOG_CORRELATION_PATTERN"));
	}

	public CorrelationConverter(String logCorrelationPattern) {
		if (StringUtils.hasLength(logCorrelationPattern)) {
			this.patternLayout = new PatternLayout();
			this.patternLayout.setPattern(logCorrelationPattern);
		}
		else {
			this.patternLayout = null;
		}
	}

	@Override
	public String convert(ILoggingEvent event) {
		if (this.patternLayout != null) {
			return this.padding + this.patternLayout.doLayout(event) + this.padding;
		}
		return this.padding;
	}

	@Override
	public void setContext(Context context) {
		if (this.patternLayout != null) {
			this.patternLayout.setContext(context);
		}
		super.setContext(context);
	}

	@Override
	public void start() {
		if (this.patternLayout != null) {
			this.patternLayout.start();
		}
		this.padding = (hasNoSpacesOption()) ? CoreConstants.EMPTY_STRING : " ";
		super.start();
	}

	@Override
	public void stop() {
		if (this.patternLayout != null) {
			this.patternLayout.stop();
		}
		super.stop();
	}

	private boolean hasNoSpacesOption() {
		if (this.getOptionList() == null) {
			return false;
		}
		return this.getOptionList().stream().anyMatch("nospaces"::equals);
	}

}
