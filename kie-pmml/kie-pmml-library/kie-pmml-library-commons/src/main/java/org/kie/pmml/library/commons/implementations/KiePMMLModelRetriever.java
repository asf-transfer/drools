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
package org.kie.pmml.library.commons.implementations;

import java.util.Optional;
import org.dmg.pmml.DataDictionary;
import org.dmg.pmml.Model;
import org.kie.pmml.api.exceptions.KiePMMLException;
import org.kie.pmml.api.model.KiePMMLModel;
import org.kie.pmml.api.model.enums.PMML_MODEL;
import org.kie.pmml.library.api.implementations.ModelImplementationProviderFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.kie.pmml.api.interfaces.FunctionalWrapperFactory.throwingFunctionWrapper;

public class KiePMMLModelRetriever {

    private static final Logger logger = LoggerFactory.getLogger(KiePMMLModelRetriever.class.getName());
    private static final ModelImplementationProviderFinder modelImplementationProviderFinder = new ModelImplementationProviderFinderImpl();

    /**
     * Read the given <code>DataDictionary</code> and <code>Model</code>> to returns a <code>Optional&lt;KiePMMLModel&gt;</code>
     *
     *
     * @param dataDictionary
     * @param model
     * @return
     * @throws KiePMMLException
     */
    @SuppressWarnings("unchecked")
    public static Optional<KiePMMLModel> getFromDataDictionaryAndModel(DataDictionary dataDictionary, Model model) throws KiePMMLException {
        logger.info("getFromModel {}", model);
        final PMML_MODEL pmmlMODEL = PMML_MODEL.byName(model.getClass().getSimpleName());
        logger.info("pmmlModelType {}", pmmlMODEL);
        return modelImplementationProviderFinder.getImplementations(false)
                .stream()
                .filter(implementation -> pmmlMODEL.equals(implementation.getPMMLModelType()))
                .map(throwingFunctionWrapper(implementation -> implementation.getKiePMMLModel(dataDictionary, model)))
                .findFirst();
    }

}
