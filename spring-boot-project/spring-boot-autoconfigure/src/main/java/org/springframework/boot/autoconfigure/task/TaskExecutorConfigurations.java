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

package org.springframework.boot.autoconfigure.task;

import java.util.concurrent.Executor;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnThreading;
import org.springframework.boot.autoconfigure.thread.Threading;
import org.springframework.boot.task.TaskExecutorBuilder;
import org.springframework.boot.task.ThreadPoolTaskExecutorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.AsyncAnnotationBeanPostProcessor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * {@link TaskExecutor} configurations to be imported by
 * {@link TaskExecutionAutoConfiguration} in a specific order.
 *
 * @author Andy Wilkinson
 * @author Moritz Halbritter
 */
class TaskExecutorConfigurations {

	@ConditionalOnThreading(Threading.VIRTUAL)
	@Configuration(proxyBeanMethods = false)
	@ConditionalOnMissingBean(Executor.class)
	static class VirtualThreadTaskExecutorConfiguration {

		@Bean(name = { TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME,
				AsyncAnnotationBeanPostProcessor.DEFAULT_TASK_EXECUTOR_BEAN_NAME })
		SimpleAsyncTaskExecutor applicationTaskExecutor(TaskExecutionProperties properties,
				ObjectProvider<TaskDecorator> taskDecorator) {
			SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor(properties.getThreadNamePrefix());
			executor.setVirtualThreads(true);
			taskDecorator.ifUnique(executor::setTaskDecorator);
			return executor;
		}

	}

	@Configuration(proxyBeanMethods = false)
	@ConditionalOnMissingBean(Executor.class)
	@SuppressWarnings("removal")
	static class ThreadPoolTaskExecutorConfiguration {

		@Lazy
		@Bean(name = { TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME,
				AsyncAnnotationBeanPostProcessor.DEFAULT_TASK_EXECUTOR_BEAN_NAME })
		ThreadPoolTaskExecutor applicationTaskExecutor(TaskExecutorBuilder taskExecutorBuilder,
				ThreadPoolTaskExecutorBuilder threadPoolTaskExecutorBuilder) {
			// If a user has provided their own (deprecated) TaskExecutorBuilder, use it
			// for task execution
			if (!taskExecutorBuilder.isDefaultFromAutoConfiguration()) {
				return taskExecutorBuilder.build();
			}
			// Otherwise, use the new ThreadPoolTaskExecutorBuilder, which also applies
			// the deprecated customizers
			return threadPoolTaskExecutorBuilder.build();
		}

	}

}
