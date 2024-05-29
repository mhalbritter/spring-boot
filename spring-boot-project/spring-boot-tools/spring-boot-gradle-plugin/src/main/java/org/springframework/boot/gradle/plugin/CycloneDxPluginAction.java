/*
 * Copyright 2012-2024 the original author or authors.
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

package org.springframework.boot.gradle.plugin;

import org.cyclonedx.gradle.CycloneDxPlugin;
import org.cyclonedx.gradle.CycloneDxTask;
import org.gradle.api.Action;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.UnknownTaskException;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.tasks.Copy;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.Jar;

import org.springframework.boot.gradle.tasks.bundling.BootJar;
import org.springframework.boot.gradle.tasks.bundling.BootWar;

/**
 * {@link Action} that is executed in response to the {@link CycloneDxPlugin} being
 * applied.
 *
 * @author Moritz Halbritter
 */
final class CycloneDxPluginAction implements PluginApplicationAction {

	@Override
	public Class<? extends Plugin<? extends Project>> getPluginClass() {
		return CycloneDxPlugin.class;
	}

	@Override
	public void execute(Project project) {
		SourceSet main = project.getExtensions()
			.getByType(JavaPluginExtension.class)
			.getSourceSets()
			.getByName(SourceSet.MAIN_SOURCE_SET_NAME);
		TaskProvider<CycloneDxTask> cycloneDxTaskProvider = project.getTasks()
			.named("cyclonedxBom", CycloneDxTask.class);
		cycloneDxTaskProvider.configure((task) -> {
			task.getProjectType().convention("application");
			task.getOutputFormat().convention("json");
			task.getOutputName().convention("application.cdx");
			task.getIncludeLicenseText().convention(false);
		});
		TaskProvider<Copy> processResourcesProvider = project.getTasks()
			.named(main.getProcessResourcesTaskName(), Copy.class);
		TaskProvider<BootJar> bootJarProvider = getTaskIfAvailable(project, SpringBootPlugin.BOOT_JAR_TASK_NAME,
				BootJar.class);
		TaskProvider<BootWar> bootWarProvider = getTaskIfAvailable(project, SpringBootPlugin.BOOT_WAR_TASK_NAME,
				BootWar.class);
		processResourcesProvider.configure((processResources) -> {
			processResources.dependsOn(cycloneDxTaskProvider);
			CycloneDxTask cycloneDxTask = cycloneDxTaskProvider.get();
			String sbomFileName = cycloneDxTask.getOutputName().get() + getSbomExtension(cycloneDxTask);
			processResources.from(cycloneDxTask, (spec) -> spec.include(sbomFileName).into("META-INF/sbom"));
		});
		if (bootJarProvider != null) {
			bootJarProvider.configure((bootJar) -> configureTask(bootJar, cycloneDxTaskProvider));
		}
		if (bootWarProvider != null) {
			bootWarProvider.configure((bootWar) -> configureTask(bootWar, cycloneDxTaskProvider));
		}

	}

	private void configureTask(Jar task, TaskProvider<CycloneDxTask> cycloneDxTaskTaskProvider) {
		CycloneDxTask cycloneDxTask = cycloneDxTaskTaskProvider.get();
		String sbomFileName = cycloneDxTask.getOutputName().get() + getSbomExtension(cycloneDxTask);
		task.manifest((manifest) -> {
			manifest.getAttributes().put("Sbom-Format", "CycloneDX");
			manifest.getAttributes().put("Sbom-Location", "META-INF/sbom/" + sbomFileName);
		});
	}

	private <T extends Task> TaskProvider<T> getTaskIfAvailable(Project project, String name, Class<T> type) {
		try {
			return project.getTasks().named(name, type);
		}
		catch (UnknownTaskException ex) {
			return null;
		}
	}

	private String getSbomExtension(CycloneDxTask task) {
		String format = task.getOutputFormat().get();
		if ("all".equals(format)) {
			return ".json";
		}
		return "." + format;
	}

}
