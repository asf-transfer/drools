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
package org.drools.model.project.codegen;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.drools.drl.extensions.DecisionTableFactory;
import org.drools.model.project.codegen.context.DroolsModelBuildContext;
import org.drools.model.project.codegen.io.CollectedResource;
import org.kie.api.io.Resource;
import org.kie.api.io.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.stream.Collectors.toList;

public class RuleCodegen {

    public static final GeneratedFileType RULE_TYPE = GeneratedFileType.of("RULE", GeneratedFileType.Category.SOURCE);
    public static final String TEMPLATE_RULE_FOLDER = "/class-templates/rules/";
    public static final String GENERATOR_NAME = "rules";

    private static final Logger LOGGER = LoggerFactory.getLogger(RuleCodegen.class);

    private final DroolsModelBuildContext context;
    private final String name;

    public static RuleCodegen ofCollectedResources(DroolsModelBuildContext context, Collection<CollectedResource> resources) {
        List<Resource> generatedRules = resources.stream()
                .map(CollectedResource::resource)
                .filter(r -> isRuleFile(r) || r.getResourceType() == ResourceType.PROPERTIES)
                .collect(toList());
        return ofResources(context, generatedRules);
    }

    public static RuleCodegen ofResources(DroolsModelBuildContext context, Collection<Resource> resources) {
        return new RuleCodegen(context, resources);
    }

    private final Collection<Resource> resources;

    private boolean hotReloadMode = false;
    private final boolean decisionTableSupported;

    private RuleCodegen(DroolsModelBuildContext context, Collection<Resource> resources) {
        Objects.requireNonNull(context, "context cannot be null");
        this.name = GENERATOR_NAME;
        this.context = context;
        this.resources = resources;
        this.decisionTableSupported = DecisionTableFactory.getDecisionTableProvider() != null;
    }

    public boolean isEmpty() {
        return resources.isEmpty();
    }

    protected Collection<GeneratedFile> internalGenerate() {

        DroolsModelBuilder droolsModelBuilder =
                new DroolsModelBuilder(
                        context(), resources, decisionTableSupported, hotReloadMode);

        droolsModelBuilder.build();
        Collection<GeneratedFile> generatedFiles = droolsModelBuilder.generateCanonicalModelSources();

        KieSessionModelBuilder kieSessionModelBuilder =
                new KieSessionModelBuilder(context(), droolsModelBuilder.packageSources());
        generatedFiles.addAll(kieSessionModelBuilder.generate());

        if (LOGGER.isDebugEnabled()) {
            generatedFiles.stream().forEach(genFile -> LOGGER.debug(genFile.dumpContent()));
        }

        return generatedFiles;
    }

    public boolean isEnabled() {
        return !isEmpty();
    }

    public DroolsModelBuildContext context() {
        return this.context;
    }

    public String name() {
        return name;
    }

    public final Collection<GeneratedFile> generate() {
        if (isEmpty()) {
            return Collections.emptySet();
        }
        return internalGenerate();
    }

    public RuleCodegen withHotReloadMode() {
        hotReloadMode = true;
        return this;
    }

    private static boolean isRuleFile(Resource resource) {
        return resource.getResourceType() == ResourceType.DRL || resource.getResourceType() == ResourceType.DTABLE;
    }
}
