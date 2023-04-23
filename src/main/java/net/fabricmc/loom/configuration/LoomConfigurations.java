/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2023 FabricMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.fabricmc.loom.configuration;

import javax.inject.Inject;

import org.gradle.api.NamedDomainObjectProvider;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.attributes.Usage;
import org.gradle.api.plugins.JavaPlugin;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.gradle.GradleUtils;
import net.fabricmc.loom.util.gradle.SourceSetHelper;

public abstract class LoomConfigurations implements Runnable {
	@Inject
	protected abstract Project getProject();

	@Inject
	protected abstract ConfigurationContainer getConfigurations();

	@Inject
	protected abstract DependencyHandler getDependencies();

	@Override
	public void run() {
		final LoomGradleExtension extension = LoomGradleExtension.get(getProject());

		register(Constants.Configurations.MOD_COMPILE_CLASSPATH, Type.CONSUMABLE);
		registerNonTransitive(Constants.Configurations.MOD_COMPILE_CLASSPATH_MAPPED, Type.DEFAULT);

		// Set up the Minecraft compile configurations.
		var minecraftClientCompile = registerNonTransitive(Constants.Configurations.MINECRAFT_CLIENT_COMPILE_LIBRARIES, Type.DEFAULT);
		var minecraftServerCompile = registerNonTransitive(Constants.Configurations.MINECRAFT_SERVER_COMPILE_LIBRARIES, Type.DEFAULT);
		var minecraftCompile = registerNonTransitive(Constants.Configurations.MINECRAFT_COMPILE_LIBRARIES, Type.RESOLVABLE);
		minecraftCompile.configure(configuration -> {
			configuration.extendsFrom(minecraftClientCompile.get());
			configuration.extendsFrom(minecraftServerCompile.get());
		});

		// Set up the minecraft runtime configurations, this extends from the compile configurations.
		var minecraftClientRuntime = registerNonTransitive(Constants.Configurations.MINECRAFT_CLIENT_RUNTIME_LIBRARIES, Type.DEFAULT);
		var minecraftServerRuntime = registerNonTransitive(Constants.Configurations.MINECRAFT_SERVER_RUNTIME_LIBRARIES, Type.DEFAULT);

		// Runtime extends from compile
		minecraftClientRuntime.configure(configuration -> configuration.extendsFrom(minecraftClientCompile.get()));
		minecraftServerRuntime.configure(configuration -> configuration.extendsFrom(minecraftServerCompile.get()));

		registerNonTransitive(Constants.Configurations.MINECRAFT_RUNTIME_LIBRARIES, Type.RESOLVABLE).configure(minecraftRuntime -> {
			minecraftRuntime.extendsFrom(minecraftClientRuntime.get());
			minecraftRuntime.extendsFrom(minecraftServerRuntime.get());
		});

		registerNonTransitive(Constants.Configurations.MINECRAFT_NATIVES, Type.DEFAULT);
		registerNonTransitive(Constants.Configurations.LOADER_DEPENDENCIES, Type.CONSUMABLE);

		registerNonTransitive(Constants.Configurations.MINECRAFT, Type.CONSUMABLE);
		registerNonTransitive(Constants.Configurations.INCLUDE, Type.DEFAULT);
		registerNonTransitive(Constants.Configurations.MAPPING_CONSTANTS, Type.DEFAULT);

		register(Constants.Configurations.NAMED_ELEMENTS, Type.CONSUMABLE).configure(configuration -> {
			configuration.extendsFrom(getConfigurations().getByName(JavaPlugin.API_CONFIGURATION_NAME));
		});

		extendsFrom(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME, Constants.Configurations.MAPPING_CONSTANTS);

		register(Constants.Configurations.MAPPINGS, Type.DEFAULT);
		register(Constants.Configurations.MAPPINGS_FINAL, Type.DEFAULT);
		register(Constants.Configurations.LOOM_DEVELOPMENT_DEPENDENCIES, Type.CONSUMABLE);
		register(Constants.Configurations.UNPICK_CLASSPATH, Type.DEFAULT);
		register(Constants.Configurations.LOCAL_RUNTIME, Type.CONSUMABLE);
		extendsFrom(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME, Constants.Configurations.LOCAL_RUNTIME);

		extension.createRemapConfigurations(SourceSetHelper.getMainSourceSet(getProject()));

		extendsFrom(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME, Constants.Configurations.MAPPINGS_FINAL);
		extendsFrom(JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME, Constants.Configurations.MAPPINGS_FINAL);

		extendsFrom(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME, Constants.Configurations.MINECRAFT_RUNTIME_LIBRARIES);

		// Add the dev time dependencies
		getDependencies().add(Constants.Configurations.LOOM_DEVELOPMENT_DEPENDENCIES, Constants.Dependencies.DEV_LAUNCH_INJECTOR + Constants.Dependencies.Versions.DEV_LAUNCH_INJECTOR);
		getDependencies().add(Constants.Configurations.LOOM_DEVELOPMENT_DEPENDENCIES, Constants.Dependencies.TERMINAL_CONSOLE_APPENDER + Constants.Dependencies.Versions.TERMINAL_CONSOLE_APPENDER);
		getDependencies().add(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME, Constants.Dependencies.JETBRAINS_ANNOTATIONS + Constants.Dependencies.Versions.JETBRAINS_ANNOTATIONS);
		getDependencies().add(JavaPlugin.TEST_COMPILE_ONLY_CONFIGURATION_NAME, Constants.Dependencies.JETBRAINS_ANNOTATIONS + Constants.Dependencies.Versions.JETBRAINS_ANNOTATIONS);

		GradleUtils.afterSuccessfulEvaluation(getProject(), () -> {
			if (extension.shouldGenerateSrgTiny()) {
				registerNonTransitive(Constants.Configurations.SRG, Type.RESOLVABLE);
			}
		});

		if (extension.isForge()) {
			// Set up Forge configurations
			registerNonTransitive(Constants.Configurations.FORGE, Type.RESOLVABLE);
			registerNonTransitive(Constants.Configurations.FORGE_USERDEV, Type.RESOLVABLE);
			registerNonTransitive(Constants.Configurations.FORGE_INSTALLER, Type.RESOLVABLE);
			registerNonTransitive(Constants.Configurations.FORGE_UNIVERSAL, Type.RESOLVABLE);
			register(Constants.Configurations.FORGE_DEPENDENCIES, Type.BUCKET);
			registerNonTransitive(Constants.Configurations.FORGE_NAMED, Type.BUCKET);
			registerNonTransitive(Constants.Configurations.FORGE_EXTRA, Type.BUCKET);
			registerNonTransitive(Constants.Configurations.MCP_CONFIG, Type.RESOLVABLE);
			register(Constants.Configurations.FORGE_RUNTIME_LIBRARY, Type.RESOLVABLE).configure(configuration -> {
				// Resolve for runtime usage
				Usage javaRuntime = getProject().getObjects().named(Usage.class, Usage.JAVA_RUNTIME);
				configuration.attributes(attributes -> attributes.attribute(Usage.USAGE_ATTRIBUTE, javaRuntime));
			});

			extendsFrom(Constants.Configurations.MINECRAFT_COMPILE_LIBRARIES, Constants.Configurations.FORGE_DEPENDENCIES);
			extendsFrom(Constants.Configurations.MINECRAFT_RUNTIME_LIBRARIES, Constants.Configurations.FORGE_DEPENDENCIES);

			extendsFrom(Constants.Configurations.FORGE_RUNTIME_LIBRARY, Constants.Configurations.FORGE_DEPENDENCIES);
			extendsFrom(Constants.Configurations.FORGE_RUNTIME_LIBRARY, Constants.Configurations.MINECRAFT_RUNTIME_LIBRARIES);
			extendsFrom(Constants.Configurations.FORGE_RUNTIME_LIBRARY, Constants.Configurations.FORGE_EXTRA);
			extendsFrom(Constants.Configurations.FORGE_RUNTIME_LIBRARY, Constants.Configurations.FORGE_NAMED);
			// Include any user-defined libraries on the runtime CP.
			// (All the other superconfigurations are already on there.)
			extendsFrom(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME, Constants.Configurations.FORGE_RUNTIME_LIBRARY);
			extendsFrom(JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME, Constants.Configurations.FORGE_RUNTIME_LIBRARY);

			extendsFrom(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME, Constants.Configurations.FORGE_NAMED);
			extendsFrom(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME, Constants.Configurations.FORGE_NAMED);
			extendsFrom(JavaPlugin.TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME, Constants.Configurations.FORGE_NAMED);
			extendsFrom(JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME, Constants.Configurations.FORGE_NAMED);
			extendsFrom(JavaPlugin.COMPILE_CLASSPATH_CONFIGURATION_NAME, Constants.Configurations.FORGE_EXTRA);
			extendsFrom(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME, Constants.Configurations.FORGE_EXTRA);
			extendsFrom(JavaPlugin.TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME, Constants.Configurations.FORGE_EXTRA);
			extendsFrom(JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME, Constants.Configurations.FORGE_EXTRA);

			// Add Forge dev-time dependencies
			getDependencies().add(Constants.Configurations.FORGE_EXTRA, Constants.Dependencies.FORGE_RUNTIME + Constants.Dependencies.Versions.FORGE_RUNTIME);
			getDependencies().add(Constants.Configurations.FORGE_EXTRA, Constants.Dependencies.UNPROTECT + Constants.Dependencies.Versions.UNPROTECT);
			getDependencies().add(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME, Constants.Dependencies.JAVAX_ANNOTATIONS + Constants.Dependencies.Versions.JAVAX_ANNOTATIONS);
		}
	}

	private NamedDomainObjectProvider<Configuration> register(String name, Type type) {
		return getConfigurations().register(name, type::apply);
	}

	private NamedDomainObjectProvider<Configuration> registerNonTransitive(String name, Type type) {
		final NamedDomainObjectProvider<Configuration> provider = register(name, type);
		provider.configure(configuration -> configuration.setTransitive(false));
		return provider;
	}

	public void extendsFrom(String a, String b) {
		getConfigurations().getByName(a, configuration -> configuration.extendsFrom(getConfigurations().getByName(b)));
	}

	enum Type {
		BUCKET(false, false), // Gradle docs call this a "bucket of dependencies"
		CONSUMABLE(true, false),
		RESOLVABLE(false, true),
		DEFAULT(true, true);

		private final boolean canBeConsumed;
		private final boolean canBeResolved;

		Type(boolean canBeConsumed, boolean canBeResolved) {
			this.canBeConsumed = canBeConsumed;
			this.canBeResolved = canBeResolved;
		}

		void apply(Configuration configuration) {
			configuration.setCanBeConsumed(canBeConsumed);
			configuration.setCanBeResolved(canBeResolved);
		}
	}
}