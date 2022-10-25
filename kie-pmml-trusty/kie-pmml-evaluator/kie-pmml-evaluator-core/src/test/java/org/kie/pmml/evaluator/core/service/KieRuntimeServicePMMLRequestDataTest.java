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
package org.kie.pmml.evaluator.core.service;

import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kie.api.pmml.PMMLRequestData;
import org.kie.efesto.common.api.identifiers.EfestoAppRoot;
import org.kie.efesto.common.api.identifiers.ModelLocalUriId;
import org.kie.efesto.runtimemanager.api.model.BaseEfestoInput;
import org.kie.efesto.runtimemanager.api.model.EfestoInput;
import org.kie.efesto.runtimemanager.api.model.EfestoRuntimeContext;
import org.kie.memorycompiler.KieMemoryCompiler;
import org.kie.pmml.api.identifiers.KiePmmlComponentRoot;
import org.kie.pmml.api.identifiers.PmmlIdFactory;
import org.kie.pmml.evaluator.core.model.EfestoOutputPMML;

import static org.assertj.core.api.Assertions.assertThat;
import static org.kie.pmml.TestingHelper.getEfestoContext;
import static org.kie.pmml.TestingHelper.getPMMLContext;
import static org.kie.pmml.TestingHelper.getPMMLRequestData;
import static org.kie.pmml.TestingHelper.getPMMLRequestDataWithInputData;
import static org.kie.pmml.commons.utils.KiePMMLModelUtils.getSanitizedClassName;

class KieRuntimeServicePMMLRequestDataTest {
    private static final String MODEL_NAME = "TestMod";
    private static final String FILE_NAME = "FileName";
    private static KieRuntimeServicePMMLRequestData kieRuntimeServicePMMLRequestData;
    private static KieMemoryCompiler.MemoryCompilerClassLoader memoryCompilerClassLoader;

    private static  ModelLocalUriId modelLocalUriId;

    @BeforeAll
    public static void setup() {
        kieRuntimeServicePMMLRequestData = new KieRuntimeServicePMMLRequestData();
        memoryCompilerClassLoader =
                new KieMemoryCompiler.MemoryCompilerClassLoader(Thread.currentThread().getContextClassLoader());
        modelLocalUriId = new EfestoAppRoot()
                .get(KiePmmlComponentRoot.class)
                .get(PmmlIdFactory.class)
                .get(FILE_NAME, getSanitizedClassName(MODEL_NAME));
    }
    @Test
    void canManageEfestoInput() {
        EfestoRuntimeContext runtimeContext = getEfestoContext(memoryCompilerClassLoader);
        PMMLRequestData pmmlRequestData = new PMMLRequestData();
        EfestoInput<PMMLRequestData> inputPMML = new BaseEfestoInput<>(modelLocalUriId, pmmlRequestData);
        assertThat(kieRuntimeServicePMMLRequestData.canManageInput(inputPMML, runtimeContext)).isTrue();
    }

    @Test
    void evaluateCorrectInput() {
        PMMLRequestData pmmlRequestData = getPMMLRequestDataWithInputData(MODEL_NAME, FILE_NAME);
        EfestoInput<PMMLRequestData> efestoInput = new BaseEfestoInput<>(modelLocalUriId, pmmlRequestData);
        Optional<EfestoOutputPMML> retrieved = kieRuntimeServicePMMLRequestData.evaluateInput(efestoInput,
                                                                                              getEfestoContext(memoryCompilerClassLoader));
        assertThat(retrieved).isNotNull().isPresent();
    }

    @Test
    void evaluateWrongIdentifier() {
        ModelLocalUriId modelLocalUriId = new EfestoAppRoot()
                .get(KiePmmlComponentRoot.class)
                .get(PmmlIdFactory.class)
                .get(FILE_NAME, getSanitizedClassName("wrongmodel"));
        PMMLRequestData pmmlRequestData = getPMMLRequestData(MODEL_NAME, FILE_NAME);
        EfestoInput<PMMLRequestData> efestoInput = new BaseEfestoInput<>(modelLocalUriId, pmmlRequestData);
        Optional<EfestoOutputPMML> retrieved = kieRuntimeServicePMMLRequestData.evaluateInput(efestoInput,
                                                                                              getEfestoContext(memoryCompilerClassLoader));
        assertThat(retrieved).isNotNull().isNotPresent();
    }

    @Test
    void evaluatePMMLRuntimeContext() {
        EfestoRuntimeContext runtimeContext =
                getPMMLContext(FILE_NAME, MODEL_NAME, memoryCompilerClassLoader);
        PMMLRequestData pmmlRequestData = getPMMLRequestData(MODEL_NAME, FILE_NAME);
        EfestoInput<PMMLRequestData> efestoInput = new BaseEfestoInput<>(modelLocalUriId, pmmlRequestData);

        Optional<EfestoOutputPMML> retrieved = kieRuntimeServicePMMLRequestData.evaluateInput(efestoInput, runtimeContext);
        assertThat(retrieved).isNotNull().isPresent();
    }
}