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
package  org.kie.pmml.models.clustering.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.kie.pmml.api.enums.MINING_FUNCTION;
import org.kie.pmml.api.enums.PMML_MODEL;
import org.kie.pmml.commons.model.KiePMMLModel;
import org.kie.pmml.models.clustering.model.aggregate.AggregateFunction;
import org.kie.pmml.models.clustering.model.compare.CompareFunction;
import org.kie.pmml.models.clustering.model.compare.CompareFunctions;

public class KiePMMLClusteringModel extends KiePMMLModel {

    private static final CompareFunction DEFAULT_COMPARE_FN = CompareFunctions.absDiff();

    private final ModelClass modelClass;
    private final List<KiePMMLCluster> clusters;
    private final List<KiePMMLClusteringField> clusteringFields;
    private final KiePMMLComparisonMeasure comparisonMeasure;
    private final KiePMMLMissingValueWeights missingValueWeights;

    public KiePMMLClusteringModel(
            String modelName,
            ModelClass modelClass,
            List<KiePMMLCluster> clusters,
            List<KiePMMLClusteringField> clusteringFields,
            KiePMMLComparisonMeasure comparisonMeasure,
            KiePMMLMissingValueWeights missingValueWeights
    ) {
        super(modelName, Collections.emptyList());
        this.modelClass = modelClass;
        this.clusters = clusters;
        this.clusteringFields = clusteringFields;
        this.comparisonMeasure = comparisonMeasure;
        this.missingValueWeights = missingValueWeights;

        this.miningFunction = MINING_FUNCTION.CLUSTERING;
        this.pmmlMODEL = PMML_MODEL.CLUSTERING_MODEL;
        this.targetField = "class";
    }

    @Override
    public Object evaluate(final Object knowledgeBase, final Map<String, Object> requestData) {
        CompareFunction[] compFn = clusteringFields.stream()
                .map(cf -> cf.getCompareFunction().orElseGet(comparisonMeasure::getCompareFunction))
                .toArray(CompareFunction[]::new);

        double[] inputs = clusteringFields.stream()
                .map(KiePMMLClusteringField::getField)
                .filter(requestData::containsKey)
                .map(requestData::get)
                .map(Double.class::cast)
                .mapToDouble(Double::doubleValue)
                .toArray();

        double[] weights = IntStream.range(0, inputs.length)
                .mapToDouble(x -> 1.0)
                .toArray();

        final AggregateFunction aggregateFunction = comparisonMeasure.getAggregateFunction();

        double[] aggregates = clusters.stream()
                .mapToDouble(c -> aggregateFunction.aggregate(compFn, inputs, c.getValuesArray(), weights, 1.0))
                .toArray();

        int minIndex = 0;
        double min = aggregates[minIndex];

        for (int i = 1; i < aggregates.length; i++) {
            if (aggregates[i] < min) {
                minIndex = i;
                min = aggregates[i];
            }
        }

        return minIndex + 1;
    }

    @Override
    public Map<String, Object> getOutputFieldsMap() {
        // TODO
//        throw new UnsupportedOperationException();
        return Collections.emptyMap();
    }

    public enum ModelClass {
        CENTER_BASED,
        DISTRIBUTION_BASED
    }

}
