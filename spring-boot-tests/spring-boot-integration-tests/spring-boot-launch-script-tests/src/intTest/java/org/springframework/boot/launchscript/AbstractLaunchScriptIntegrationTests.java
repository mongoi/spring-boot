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

package org.springframework.boot.launchscript;

import java.io.File;
import java.net.URI;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.assertj.core.api.Condition;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.output.ToStringConsumer;
import org.testcontainers.containers.startupcheck.OneShotStartupCheckStrategy;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.utility.MountableFile;

import org.springframework.boot.ansi.AnsiColor;
import org.springframework.util.Assert;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;

/**
 * Abstract base class for testing the launch script.
 *
 * @author Andy Wilkinson
 * @author Ali Shahbour
 * @author Alexey Vinogradov
 * @author Moritz Halbritter
 */
abstract class AbstractLaunchScriptIntegrationTests {

	private static final Map<Architecture, URI> JAVA_DOWNLOAD_URLS;
	static {
		Map<Architecture, URI> urls = new HashMap<>();
		urls.put(Architecture.AMD64,
				URI.create("https://download.bell-sw.com/java/8u382+6/bellsoft-jdk8u382+6-linux-amd64.tar.gz"));
		urls.put(Architecture.AARCH64,
				URI.create("https://download.bell-sw.com/java/8u382+6/bellsoft-jdk8u382+6-linux-aarch64.tar.gz"));
		JAVA_DOWNLOAD_URLS = Collections.unmodifiableMap(urls);
	}

	protected static final char ESC = 27;

	private final String scriptsDir;

	protected AbstractLaunchScriptIntegrationTests(String scriptsDir) {
		this.scriptsDir = scriptsDir;
	}

	static List<Object[]> parameters(Predicate<File> osFilter) {
		List<Object[]> parameters = new ArrayList<>();
		for (File os : new File("src/intTest/resources/conf").listFiles()) {
			if (osFilter.test(os)) {
				for (File version : os.listFiles()) {
					parameters.add(new Object[] { os.getName(), version.getName() });
				}
			}
		}
		return parameters;
	}

	protected Condition<String> coloredString(AnsiColor color, String string) {
		String colorString = ESC + "[0;" + color + "m" + string + ESC + "[0m";
		return new Condition<String>() {

			@Override
			public boolean matches(String value) {
				return containsString(colorString).matches(value);
			}

		};
	}

	protected void doLaunch(String os, String version, String script) throws Exception {
		assertThat(doTest(os, version, script)).contains("Launched");
	}

	protected String doTest(String os, String version, String script) throws Exception {
		ToStringConsumer consumer = new ToStringConsumer().withRemoveAnsiCodes(false);
		try (LaunchScriptTestContainer container = new LaunchScriptTestContainer(os, version, this.scriptsDir,
				script)) {
			container.withLogConsumer(consumer);
			container.withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger("docker")));
			container.start();
			while (container.isRunning()) {
				Thread.sleep(100);
			}
		}
		return consumer.toUtf8String();
	}

	private static final class LaunchScriptTestContainer extends GenericContainer<LaunchScriptTestContainer> {

		private LaunchScriptTestContainer(String os, String version, String scriptsDir, String testScript) {
			super(new ImageFromDockerfile("spring-boot-launch-script/" + os.toLowerCase() + "-" + version)
				.withDockerfile(Paths.get("src/intTest/resources/conf/" + os + "/" + version + "/Dockerfile"))
				.withBuildArg("JAVA_DOWNLOAD_URL", getJavaDownloadUrl()));
			withCopyFileToContainer(MountableFile.forHostPath(findApplication().getAbsolutePath()), "/app.jar");
			withCopyFileToContainer(
					MountableFile.forHostPath("src/intTest/resources/scripts/" + scriptsDir + "test-functions.sh"),
					"/test-functions.sh");
			withCopyFileToContainer(
					MountableFile.forHostPath("src/intTest/resources/scripts/" + scriptsDir + testScript),
					"/" + testScript);
			withCommand("/bin/bash", "-c",
					"chown root:root *.sh && chown root:root *.jar && chmod +x " + testScript + " && ./" + testScript);
			withStartupCheckStrategy(new OneShotStartupCheckStrategy().withTimeout(Duration.ofMinutes(5)));
		}

		private static String getJavaDownloadUrl() {
			Architecture architecture = Architecture.current();
			Assert.notNull(architecture,
					() -> String.format("Failed to find current architecture. Value of os.arch is: '%s'",
							System.getProperty("os.arch")));
			URI uri = JAVA_DOWNLOAD_URLS.get(architecture);
			Assert.notNull(uri, () -> String.format("No JDK download URL for architecture %s found", architecture));
			return uri.toString();
		}

		private static File findApplication() {
			String name = String.format("build/%1$s/build/libs/%1$s.jar", "spring-boot-launch-script-tests-app");
			File jar = new File(name);
			Assert.state(jar.isFile(), () -> "Could not find " + name + ". Have you built it?");
			return jar;
		}

	}

	private enum Architecture {

		AMD64, AARCH64;

		/**
		 * Returns the current architecture.
		 * @return the current architecture or {@code null}
		 */
		static Architecture current() {
			String arch = System.getProperty("os.arch");
			if (arch == null) {
				return null;
			}
			switch (arch) {
				case "amd64":
					return AMD64;
				case "aarch64":
					return AARCH64;
				default:
					return null;
			}
		}

	}

}
