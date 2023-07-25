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

package smoketest.data.r2dbc;

import io.micrometer.observation.Observation.Context;
import io.micrometer.observation.ObservationHandler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class SampleR2dbcApplication {

	public static void main(String[] args) {
		SpringApplication.run(SampleR2dbcApplication.class, args);
	}

	// TODO: Remove this
	@Bean
	ObservationHandler<Context> observationHandler() {
		return new ObservationHandler<>() {
			@Override
			public boolean supportsContext(Context context) {
				return true;
			}

			@Override
			public void onStart(Context context) {
				System.out.println("START: " + context);
			}

			@Override
			public void onError(Context context) {
				System.out.println("ERROR: " + context);
			}

			@Override
			public void onStop(Context context) {
				System.out.println("STOP: " + context);
			}
		};
	}

}
