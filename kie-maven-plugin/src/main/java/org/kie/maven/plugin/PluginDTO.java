/*
 * Copyright 2022 Red Hat, Inc. and/or its affiliates.
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
package org.kie.maven.plugin;

import java.io.File;
import java.util.List;
import java.util.Map;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.kie.memorycompiler.JavaConfiguration;

import static org.kie.maven.plugin.helpers.ExecModelModeHelper.isModelCompilerInClassPath;

public class PluginDTO {

    private final String dumpKieSourcesFolder;
    private final String generateModel;
    private final String generateDMNModel;
    private final List<Resource> resources;
    private final String validateDMN;
    private final File projectDir;
    private final File targetDirectory;
    private final Map<String, String> properties;
    private final MavenProject project;
    private final MavenSession mavenSession;
    private final List<org.apache.maven.model.Resource> resourcesDirectories;
    private final File outputDirectory;
    private final File testDir;
    private final File resourceFolder;
    private final boolean isModelParameterEnabled;
    private final boolean isModelCompilerInClass;
    private final JavaConfiguration.CompilerType compilerType;
    private final Log log;

    public PluginDTO(String dumpKieSourcesFolder, String generateModel,
                     String generateDMNModel,
                     List<Resource> resources, String validateDMN,
                     File projectDir, File targetDirectory, Map<String, String> properties, MavenProject project,
                     MavenSession mavenSession, List<Resource> resourcesDirectories, File outputDirectory,
                     File testDir, File resourceFolder, boolean isModelParameterEnabled,
                     JavaConfiguration.CompilerType compilerType, Log log) {
        this.dumpKieSourcesFolder = dumpKieSourcesFolder;
        this.generateModel = generateModel;
        this.generateDMNModel = generateDMNModel;
        this.resources = resources;
        this.validateDMN = validateDMN;
        this.projectDir = projectDir;
        this.targetDirectory = targetDirectory;
        this.properties = properties;
        this.project = project;
        this.mavenSession = mavenSession;
        this.resourcesDirectories = resourcesDirectories;
        this.outputDirectory = outputDirectory;
        this.testDir = testDir;
        this.resourceFolder = resourceFolder;
        this.isModelParameterEnabled = isModelParameterEnabled;
        this.isModelCompilerInClass = isModelCompilerInClassPath(project.getDependencies());
        this.compilerType = compilerType;
        this.log = log;
    }

    public String getDumpKieSourcesFolder() {
        return dumpKieSourcesFolder;
    }

    public String getGenerateModel() {
        return generateModel;
    }

    public String getGenerateDMNModel() {
        return generateDMNModel;
    }

    public List<Resource> getResources() {
        return resources;
    }

    public String getValidateDMN() {
        return validateDMN;
    }

    public File getProjectDir() {
        return projectDir;
    }

    public File getTargetDirectory() {
        return targetDirectory;
    }

    public Map<String, String> getProperties() {
        return properties;
    }

    public MavenProject getProject() {
        return project;
    }

    public MavenSession getMavenSession() {
        return mavenSession;
    }

    public List<Resource> getResourcesDirectories() {
        return resourcesDirectories;
    }

    public File getOutputDirectory() {
        return outputDirectory;
    }

    public File getTestDir() {
        return testDir;
    }

    public File getResourceFolder() {
        return resourceFolder;
    }

    public boolean isModelParameterEnabled() {
        return isModelParameterEnabled;
    }

    public boolean isModelCompilerInClass() {
        return isModelCompilerInClass;
    }

    public JavaConfiguration.CompilerType getCompilerType() {
        return compilerType;
    }

    public Log getLog() {
        return log;
    }
}
