/*
 * Copyright 2012-2025 the original author or authors.
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

package org.springframework.boot.web.embedded.tomcat;

import org.apache.catalina.core.AprLifecycleListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledForJreRange;
import org.junit.jupiter.api.condition.JRE;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.testsupport.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link AprAvailability}.
 *
 * @author Moritz Halbritter
 */
@ExtendWith(OutputCaptureExtension.class)
class AprAvailabilityTests {

	@Test
	void available() {
		assertThat(AprAvailability.AVAILABLE.isAvailable()).isTrue();
	}

	@Test
	void notAvailable() {
		assertThat(AprAvailability.NOT_AVAILABLE.isAvailable()).isFalse();
	}

	@Test
	@EnabledForJreRange(max = JRE.JAVA_23)
	void autoDetectUntilJava24() {
		boolean available = AprLifecycleListener.isAprAvailable();
		assertThat(AprAvailability.AUTO_DETECT.isAvailable()).isEqualTo(available);
	}

	@Test
	@EnabledForJreRange(min = JRE.JAVA_24)
	void autoDetectJava24AndNewer() {
		assertThat(AprAvailability.AUTO_DETECT.isAvailable()).isEqualTo(false);
	}

}
