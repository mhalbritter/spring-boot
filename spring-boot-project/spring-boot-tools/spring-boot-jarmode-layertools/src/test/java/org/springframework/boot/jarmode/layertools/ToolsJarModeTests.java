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

package org.springframework.boot.jarmode.layertools;

import java.io.File;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * Tests for {@link ToolsJarMode}.
 *
 * @author Moritz Halbritter
 */
class ToolsJarModeTests {

	private ToolsJarMode mode;

	@TempDir
	private Path temp;

	@BeforeEach
	void setUp() {
		Context context = Mockito.mock(Context.class);
		given(context.getArchiveFile())
			.willReturn(new File("/Users/mkammerer/Downloads/demo/build/libs/demo-0.0.1-SNAPSHOT.jar"));
		ToolsJarMode.contextOverride = context;
		this.mode = new ToolsJarMode();
	}

	@Test
	void shouldAcceptToolsMode() {
		assertThat(this.mode.accepts("tools")).isTrue();
		assertThat(this.mode.accepts("something-else")).isFalse();
	}

	@Test
	@Disabled("for manual running")
	void extract() {
		// run("extract", "--launcher", "--layers=dependencies", "--destination",
		// "/Users/mkammerer/tmp");
		// run("extract", "--launcher", "--layers", "--destination",
		// "/Users/mkammerer/tmp");
		// run("extract", "--launcher", "--destination", "/Users/mkammerer/tmp");
		// run("extract", "--destination", "/Users/mkammerer/tmp");
	}

	private void run(String... args) {
		this.mode.run("tools", args);
	}

}
