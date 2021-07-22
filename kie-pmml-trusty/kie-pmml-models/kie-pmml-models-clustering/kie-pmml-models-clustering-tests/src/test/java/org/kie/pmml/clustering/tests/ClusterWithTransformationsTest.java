/*
 * Copyright 2021 Red Hat, Inc. and/or its affiliates.
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

package org.kie.pmml.clustering.tests;

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
import org.kie.pmml.api.runtime.PMMLRuntime;
import org.kie.pmml.models.tests.AbstractPMMLTest;

@RunWith(Parameterized.class)
public class ClusterWithTransformationsTest extends AbstractPMMLTest {

    private static final String FILE_NAME = "ClusterWithTransformations.pmml";
    private static final String MODEL_NAME = "ClusterWithTransformations";
    private static final String TARGET_FIELD = "class";
    private static final String OUT_NORMCONTINUOUS_FIELD = "out_normcontinuous_field";
    private static final String OUT_NORMDISCRETE_FIELD = "out_normdiscrete_field";
    private static final String OUT_DISCRETIZE_FIELD = "out_discretize_field";
    private static final String OUT_MAPVALUED_FIELD = "out_mapvalued_field";
    private static final String OUT_TEXT_INDEX_NORMALIZATION_FIELD = "out_text_index_normalization_field";
    private static final String OUT_TEXTAGGREGATION = "out_textAggregation";

    private static final String TEXT_INPUT = "Testing the app for a few days convinced me the interfaces are " +
            "excellent!";
    private static final String TRANSACTION = "1 2 3 4";
    private static final String ITEM = "1 1 1 2 2 3 4 4 5 6 7";
    private static final double EXPECTED_AGGREGATE = Arrays.stream(ITEM.split(" "))
            .mapToInt(Integer::valueOf)
            .sum();

    private static PMMLRuntime pmmlRuntime;

    private final double sepalLength;
    private final double sepalWidth;
    private final double petalLength;
    private final double petalWidth;
    private final String irisClass;
    private final double outNormcontinuousField;

    public ClusterWithTransformationsTest(double sepalLength, double sepalWidth, double petalLength,
                                          double petalWidth, String irisClass, double outNormcontinuousField) {
        this.sepalLength = sepalLength;
        this.sepalWidth = sepalWidth;
        this.petalLength = petalLength;
        this.petalWidth = petalWidth;
        this.irisClass = irisClass;
        this.outNormcontinuousField = outNormcontinuousField;
    }

    @BeforeClass
    public static void setupClass() {
        pmmlRuntime = getPMMLRuntime(FILE_NAME);
    }

    @Parameterized.Parameters
    public static Collection<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {4.4, 3.0, 1.3, 0.2, "3", 4.966666666666667},
                {5.0, 3.3, 1.4, 0.2, "3", 5.433333333333334},
                {7.0, 3.2, 4.7, 1.4, "2", 6.950000000000001},
                {5.7, 2.8, 4.1, 1.3, "4", 5.937500000000001},
                {6.3, 3.3, 6.0, 2.5, "1", 6.1625},
                {6.7, 3.0, 5.2, 2.3, "1", 6.575}
        });
    }

    @Test
    public void testClusterWithTransformations() throws Exception {
        final Map<String, Object> inputData = new HashMap<>();
        inputData.put("sepal_length", sepalLength);
        inputData.put("sepal_width", sepalWidth);
        inputData.put("petal_length", petalLength);
        inputData.put("petal_width", petalWidth);
        inputData.put("text_input", TEXT_INPUT);
        inputData.put("transaction", TRANSACTION);
        inputData.put("item", ITEM);

        PMML4Result pmml4Result = evaluate(pmmlRuntime, inputData, MODEL_NAME);

        Assertions.assertThat(pmml4Result.getResultVariables().get(TARGET_FIELD)).isNotNull();
        Assertions.assertThat(pmml4Result.getResultVariables().get(TARGET_FIELD)).isEqualTo(irisClass);
        Assertions.assertThat(pmml4Result.getResultVariables().get(OUT_NORMCONTINUOUS_FIELD)).isNotNull();
        Assertions.assertThat(pmml4Result.getResultVariables().get(OUT_NORMCONTINUOUS_FIELD)).isEqualTo(outNormcontinuousField);
        Assertions.assertThat(pmml4Result.getResultVariables().get(OUT_NORMDISCRETE_FIELD)).isNotNull();
        if (irisClass.equals("1")) {
            Assertions.assertThat(pmml4Result.getResultVariables().get(OUT_NORMDISCRETE_FIELD)).isEqualTo(1.0);
        } else {
            Assertions.assertThat(pmml4Result.getResultVariables().get(OUT_NORMDISCRETE_FIELD)).isEqualTo(0.0);
        }
        Assertions.assertThat(pmml4Result.getResultVariables().get(OUT_DISCRETIZE_FIELD)).isNotNull();
        if (sepalLength > 4.7 && sepalLength < 5.2) {
            Assertions.assertThat(pmml4Result.getResultVariables().get(OUT_DISCRETIZE_FIELD)).isEqualTo("abc");
        } else if (sepalLength >= 5.6 && sepalLength < 5.9) {
            Assertions.assertThat(pmml4Result.getResultVariables().get(OUT_DISCRETIZE_FIELD)).isEqualTo("def");
        } else {
            Assertions.assertThat(pmml4Result.getResultVariables().get(OUT_DISCRETIZE_FIELD)).isEqualTo("defaultValue");
        }
        Assertions.assertThat(pmml4Result.getResultVariables().get(OUT_MAPVALUED_FIELD)).isNotNull();
        String expected;
        switch (irisClass) {
            case "1":
            case "C_ONE":
                expected = "virginica";
                break;
            case "2":
            case "C_TWO":
                expected = "versicolor";
                break;
            case "3":
            case "C_THREE":
                expected = "setosa";
                break;
            case "4":
            case "C_FOUR":
                expected = "unknown";
                break;
            default:
                throw new Exception("Unexpected irisClass " + irisClass);
        }
        Assertions.assertThat(pmml4Result.getResultVariables().get(OUT_MAPVALUED_FIELD)).isEqualTo(expected);
        Assertions.assertThat(pmml4Result.getResultVariables().get(OUT_TEXT_INDEX_NORMALIZATION_FIELD)).isNotNull();
        Assertions.assertThat(pmml4Result.getResultVariables().get(OUT_TEXT_INDEX_NORMALIZATION_FIELD)).isEqualTo(1.0);
        Assertions.assertThat(pmml4Result.getResultVariables().get(OUT_TEXTAGGREGATION)).isNotNull();
        Assertions.assertThat(pmml4Result.getResultVariables().get(OUT_TEXTAGGREGATION)).isEqualTo(EXPECTED_AGGREGATE);
    }
}
