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

package org.kie.pmml.models.tree.tests;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.assertj.core.api.Assertions;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.kie.api.pmml.PMML4Result;
import org.kie.api.pmml.PMMLRequestData;
import org.kie.pmml.commons.model.KiePMMLModel;
import org.kie.pmml.evaluator.core.PMMLContextImpl;

@RunWith(Parameterized.class)
public class SimpleClassificationTreeTest extends AbstractPMMLTreeTest {

    private static final String MODEL_NAME = "SimpleTreeModel";
    private static final String PMML_SOURCE = "SimpleClassificationTree.pmml";
    private static final String TARGET_FIELD = "Predicted_result";
    private static KiePMMLModel pmmlModel;
    private double input1;
    private double input2;
    private double input3;
    private String expectedResult;

    public SimpleClassificationTreeTest(double input1, double input2, double input3, String expectedResult) {
        this.input1 = input1;
        this.input2 = input2;
        this.input3 = input3;
        this.expectedResult = expectedResult;
    }

    @BeforeClass
    public static void setupClass() {
        pmmlModel = loadPMMLModel(PMML_SOURCE);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {5, 0, 0, "classA"},
                {2, -5, 0, "classB"},
                {2, 7, 0, "classC"},
                {10, 7, 0, "classB"},
                {10, -7, 10, "classC"},
        });
    }

    @Test
    public void testSimpleClassificationTree() {
        final Map<String, Object> inputData = new HashMap<>();
        inputData.put("input1", input1);
        inputData.put("input2", input2);
        inputData.put("input3", input3);

        final PMMLRequestData pmmlRequestData = getPMMLRequestData(MODEL_NAME, inputData);
        PMML4Result pmml4Result = EXECUTOR.evaluate(pmmlModel, new PMMLContextImpl(pmmlRequestData), RELEASE_ID);
        Assertions.assertThat(pmml4Result.getResultVariables().get(TARGET_FIELD)).isNotNull();
        Assertions.assertThat(pmml4Result.getResultVariables().get(TARGET_FIELD)).isEqualTo(expectedResult);
    }
}
