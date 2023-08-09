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

package org.springframework.boot.testsupport.assertj;

import java.time.Duration;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicReference;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingStream;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assert;

import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * AssertJ {@link Assert} for virtual thread pinning.
 *
 * @author Moritz Halbritter
 * @since 3.2.0
 */
public final class VirtualThreadPinningAssert extends AbstractAssert<VirtualThreadPinningAssert, ThrowingRunnable> {

	private VirtualThreadPinningAssert(ThrowingRunnable runnable) {
		super(runnable, VirtualThreadPinningAssert.class);
	}

	/**
	 * Verifies that code does not pin a carrier thread.
	 * @return {@code this} assertion object
	 * @throws AssertionError if the code pins a carrier thread
	 */
	public VirtualThreadPinningAssert doesNotPin() throws Exception {
		isNotNull();
		Queue<RecordedEvent> events = new ConcurrentLinkedQueue<>();
		try (RecordingStream rs = new RecordingStream()) {
			rs.setSettings(Map.of("jdk.VirtualThreadPinned#enabled", "true", "jdk.VirtualThreadPinned#stackTrace",
					"true", "jdk.VirtualThreadPinned#threshold", "0 ms"));
			rs.setReuse(false);
			// See https://openjdk.org/jeps/425#JDK-Flight-Recorder-JFR
			rs.onEvent("jdk.VirtualThreadPinned", events::add);
			rs.startAsync();
			Exception exception = startVirtualThread(this.actual);
			if (exception != null) {
				throw exception;
			}
			// rs.stop() is only available on JDK >= 20
			ReflectionTestUtils.invokeMethod(rs, "stop");
		}
		if (!events.isEmpty()) {
			StringBuilder details = new StringBuilder();
			for (RecordedEvent event : events) {
				details.append("Thread '%s' pinned for %d ms%n".formatted(event.getThread().getJavaName(),
						event.getDuration().toMillis()));
				details.append("details = ").append(event).append("\n");
			}
			failWithMessage("Expected no pinning to happen, but found pinning:%n%s", details);
		}
		return this;
	}

	private Exception startVirtualThread(ThrowingRunnable runnable) {
		try {
			AtomicReference<Exception> thrown = new AtomicReference<>();
			Thread thread = virtualThreadFactory().newThread(() -> {
				try {
					runnable.run();
				}
				catch (Exception ex) {
					thrown.set(ex);
				}
			});
			thread.setName("test-subject");
			thread.start();
			thread.join(Duration.ofMinutes(1).toMillis());
			if (thread.isAlive()) {
				failWithMessage("Timeout of 1 minute reached while waiting for virtual thread to stop");
			}
			return thrown.get();
		}
		catch (InterruptedException ex) {
			failWithMessage("Got interrupted while waiting for virtual thread");
		}
		throw new AssertionError("Unreachable");
	}

	private ThreadFactory virtualThreadFactory() {
		return new VirtualThreadTaskExecutor().getVirtualThreadFactory();
	}

	/**
	 * Creates a new assertion class with the given {@link ThrowingRunnable}.
	 * @param runnable the {@link ThrowingRunnable}
	 * @return the assertion class
	 */
	public static VirtualThreadPinningAssert assertThatCode(ThrowingRunnable runnable) {
		return new VirtualThreadPinningAssert(runnable);
	}

}
