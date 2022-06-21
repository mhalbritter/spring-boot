/*
 * Copyright 2012-2022 the original author or authors.
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

package smoketest.httpinterface;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

@SpringBootApplication
@ConfigurationPropertiesScan
public class HttpInterfaceApplication {

	private static final Logger LOGGER = LoggerFactory.getLogger(HttpInterfaceApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(HttpInterfaceApplication.class, args);
	}

	@Bean
	CommandLineRunner commandLineRunner(RepositoryService repositoryService) {
		return args -> {
			Repository repository = repositoryService.getRepository("mhalbritter", "spring-boot");
			LOGGER.info("Got {}", repository);
		};
	}

	@Bean
	RepositoryService repositoryService(HttpServiceProxyFactory factory) {
		return factory.createClient(RepositoryService.class);
	}

}
