/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.drools.ruleunits.codegen.context;

import org.kie.kogito.KogitoGAV;
import org.kie.kogito.codegen.api.AddonsConfig;
import org.kie.kogito.codegen.api.ApplicationSection;
import org.kie.kogito.codegen.api.Generator;
import org.kie.kogito.codegen.api.di.DependencyInjectionAnnotator;
import org.kie.kogito.codegen.api.rest.RestAnnotator;
import org.kie.kogito.codegen.api.template.TemplatedGenerator;
import org.kie.kogito.codegen.api.utils.AppPaths;
import org.kie.kogito.codegen.api.utils.KogitoCodeGenConstants;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public interface KogitoBuildContext {

    String APPLICATION_PROPERTIES_FILE_NAME = "application.properties";
    String DEFAULT_PACKAGE_NAME = "org.kie.kogito.app";
    String KOGITO_GENERATE_REST = "kogito.generate.rest";
    String KOGITO_GENERATE_DI = "kogito.generate.di";

    static String generateRESTConfigurationKeyForResource(String generatorType) {
        return String.format("%s.%s", KOGITO_GENERATE_REST, generatorType);
    }

    boolean hasClassAvailable(String fqcn);

    /**
     * Return DependencyInjectionAnnotator if available or null
     */
    DependencyInjectionAnnotator getDependencyInjectionAnnotator();

    /**
     * Method to override default dependency injection annotator
     */
    void setDependencyInjectionAnnotator(DependencyInjectionAnnotator dependencyInjectionAnnotator);

    /**
     * Method to check if dependency injection is available and enabled.
     * This is platform/classpath specific (e.g. Quarkus) but it can also be explicitly disabled using
     * kogito.generate.di property
     */
    default boolean hasDI() {
        return getDependencyInjectionAnnotator() != null &&
                "true".equalsIgnoreCase(getApplicationProperty(KOGITO_GENERATE_DI).orElse("true"));
    }

    /**
     * Return RestAnnotator if available or null
     */
    RestAnnotator getRestAnnotator();

    /**
     * Method to override default REST annotator
     */
    void setRestAnnotator(RestAnnotator restAnnotator);

    /**
     * Return the {@link ApplicationSection}s generated by internal code-gen modules.
     * Each engine has its own implementation.
     * The client code should be aware of the dependency on a specific engine in order to leverage this method.
     * Calls to this method before the code-generation stage might end-up in an empty list.
     */
    Set<ApplicationSection> getApplicationSections();

    /**
     * Define the collection of {@link ApplicationSection}, usually during the code-gen phase.
     * It's possible that the context won't hold any application sections before the code-gen state.
     */
    void addAllApplicationSections(Set<ApplicationSection> applicationSections);

    /**
     * Adds an application section to the inner collection
     */
    void addApplicationSection(ApplicationSection applicationSection);

    /**
     * Method to check if REST available and enabled for a specific resource
     * This is platform/classpath specific (e.g. Quarkus)
     * It can also be explicitly disabled i.e. for "decisions"
     * <p>
     * kogito.generate.rest.decisions = false
     */
    default boolean hasRESTForGenerator(Generator generator) {
        return hasRESTGloballyAvailable() &&
                "true".equalsIgnoreCase(getApplicationProperty(generateRESTConfigurationKeyForResource(generator.name())).orElse("true"));
    }

    /**
     * Method to check if global REST generation is available and enabled.
     * This is platform/classpath specific (e.g. Quarkus) but it can also be explicitly disabled using
     * kogito.generate.rest property
     */
    default boolean hasRESTGloballyAvailable() {
        return getRestAnnotator() != null &&
                "true".equalsIgnoreCase(getApplicationProperty(KOGITO_GENERATE_REST).orElse("true"));
    }

    default boolean isValidationSupported() {
        return hasClassAvailable(KogitoCodeGenConstants.VALIDATION_CLASS);
    }

    default boolean isOpenApiSpecSupported() {
        return hasClassAvailable(KogitoCodeGenConstants.OPENAPI_SPEC_CLASS);
    }

    Optional<String> getApplicationProperty(String property);

    Collection<String> getApplicationProperties();

    void setApplicationProperty(String key, String value);

    String getPackageName();

    AddonsConfig getAddonsConfig();

    ClassLoader getClassLoader();

    AppPaths getAppPaths();

    /**
     * <strong>Note: This method is on experimental phase. Can disappear in future releases.</strong>
     * <p>
     * Attributes shared among generators and client code.
     * Any generator can write or read from this context.
     * </p>
     *
     * @return An unmodifiable map with all attributes. If you need to write to the context, use {@link #addContextAttribute(String, Object)}
     * @see ContextAttributesConstants for a list of possible attributes
     */
    Map<String, Object> getContextAttributes();

    void addContextAttribute(String key, Object value);

    /**
     * Get an attribute by a given key
     *
     * @param key the named key to retrieve from the context
     * @return The value in the context or null if it does not exist
     * @see ContextAttributesConstants for a list of possible attributes
     */
    <T> T getContextAttribute(String key, Class<T> asClass);

    /**
     * Name of the context (e.g. Quarkus, Spring) used to identify a context and for template naming conventions
     * (see {@link TemplatedGenerator})
     */
    String name();

    Optional<KogitoGAV> getGAV();

    default Map<String, String> getPropertiesMap() {
        return getApplicationProperties().stream()
                .filter(key -> getApplicationProperty(key).isPresent())
                .collect(Collectors.toUnmodifiableMap(key -> key,
                        key -> getApplicationProperty(key).get()));
    }

    interface Builder {

        Builder withPackageName(String packageName);

        Builder withApplicationPropertyProvider(KogitoApplicationPropertyProvider applicationProperties);

        Builder withApplicationProperties(Properties applicationProperties);

        Builder withApplicationProperties(File... files);

        Builder withAddonsConfig(AddonsConfig addonsConfig);

        Builder withClassAvailabilityResolver(Predicate<String> classAvailabilityResolver);

        Builder withClassLoader(ClassLoader classLoader);

        Builder withAppPaths(AppPaths appPaths);

        Builder withGAV(KogitoGAV gav);

        KogitoBuildContext build();
    }
}
