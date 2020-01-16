/*
 * Copyright 2020 Red Hat, Inc. and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kie.pmml.runtime.core.executor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.drools.core.definitions.InternalKnowledgePackage;
import org.drools.core.impl.InternalKnowledgeBase;
import org.kie.api.io.ResourceType;
import org.kie.api.pmml.PMML4Result;
import org.kie.pmml.api.enums.PMMLModelType;
import org.kie.pmml.api.model.KiePMMLModel;
import org.kie.pmml.runtime.api.executor.PMMLContext;
import org.kie.pmml.runtime.api.executor.PMMLRuntime;
import org.kie.pmml.runtime.api.model.PMMLPackage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PMMLRuntimeImpl implements PMMLRuntime {

    private static final Logger logger = LoggerFactory.getLogger(PMMLRuntimeImpl.class );

    private final InternalKnowledgeBase knowledgeBase;

    public PMMLRuntimeImpl(InternalKnowledgeBase knowledgeBase) {
        this.knowledgeBase = knowledgeBase;
    }

    @Override
    public List<KiePMMLModel> getModels() {
        logger.info("getModels");
        List<KiePMMLModel> models = new ArrayList<>(  );
        knowledgeBase.getKiePackages().forEach( kpkg -> {
            PMMLPackage pmmlPackage = (PMMLPackage) ((InternalKnowledgePackage) kpkg).getResourceTypePackages().get(ResourceType.PMML );
            if( pmmlPackage != null ) {
                models.addAll(pmmlPackage.getAllModels().values());
            }
        } );
        return models;
    }

    @Override
    public Optional<KiePMMLModel> getModel(String modelName) {
        logger.info("getModels " + modelName);
        return getModels()
                .parallelStream()
                .filter(model -> Objects.equals(modelName, model.getModelName()))
                .findFirst();
    }

    @Override
    public PMML4Result evaluate(KiePMMLModel model, PMMLContext context) {
        logger.info("evaluate " + model + " " + context);
        Optional<PMMLModelExecutor> pmmlModelExecutor = getFromPMMLModelType(model.getPmmlModelType());
        return pmmlModelExecutor.isPresent() ? pmmlModelExecutor.get().evaluate(model, context) : new PMML4Result();
    }

    /**
     * Returns an <code>Optional&lt;PMMLModelExecutor&gt;</code> to allow
     * incremental development of different model-specific executors
     *
     * @param pmmlModelType
     * @return
     */
    private Optional<PMMLModelExecutor> getFromPMMLModelType(PMMLModelType pmmlModelType) {
        logger.info("getFromPMMLModelType " + pmmlModelType);
        // TODO {gcardosi}
        return Optional.empty();
    }
}
