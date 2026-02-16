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

package org.springframework.boot.health.contributor;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link DownHealth}.
 *
 * @author Moritz Halbritter
 */
@ExtendWith(OutputCaptureExtension.class)
class DownHealthTests {

	private static final Log logger = LogFactory.getLog(DownHealthTests.class);

	@Test
	void shouldReportReasonAndExceptionAsDetails() {
		Health health = DownHealth.of(new IllegalStateException("boom"), DownReason.EXECUTION_FAILED, true);
		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		assertThat(health.getDetails()).containsEntry("reason", "execution-failed")
			.containsEntry("error", "java.lang.IllegalStateException: boom");
	}

	@Test
	void shouldOmitDetailsWhenTheyAreNotIncluded() {
		Health health = DownHealth.of(new IllegalStateException("boom"), DownReason.TIMEOUT, false);
		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		assertThat(health.getDetails()).isEmpty();
	}

	@Test
	void shouldLogFailure(CapturedOutput output) {
		Health health = DownHealth.logged(logger, new IllegalStateException("boom"), DownReason.EXECUTION_FAILED,
				"test", false);
		assertThat(health.getStatus()).isEqualTo(Status.DOWN);
		assertThat(output).contains("Health indicator test failed").contains("java.lang.IllegalStateException: boom");
	}

}
