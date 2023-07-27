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

package org.springframework.boot.task;

import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.TaskExecutor;

/**
 * Builder that can be used to create a {@link TaskExecutor}.
 * <p>
 * In a typical auto-configured Spring Boot application this builder is available as a
 * bean and can be injected whenever a {@link TaskExecutor} is needed.
 *
 * @author Moritz Halbritter
 * @since 3.2.0
 */
public interface TaskExecutorBuilder {

	/**
	 * Build a new {@link TaskExecutor} instance and configure it using this builder.
	 * @return a configured {@link TaskExecutor} instance
	 */
	TaskExecutor build();

	/**
	 * Set the {@link TaskDecorator} to use or {@code null} to not use any.
	 * @param taskDecorator the task decorator to use
	 * @return a new builder instance
	 */
	TaskExecutorBuilder taskDecorator(TaskDecorator taskDecorator);

	/**
	 * Set the prefix to use for the names of newly created threads.
	 * @param threadNamePrefix the thread name prefix to set
	 * @return a new builder instance
	 */
	TaskExecutorBuilder threadNamePrefix(String threadNamePrefix);

}
