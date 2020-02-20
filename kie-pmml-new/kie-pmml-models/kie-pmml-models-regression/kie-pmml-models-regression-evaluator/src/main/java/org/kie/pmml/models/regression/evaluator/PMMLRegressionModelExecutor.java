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
package org.kie.pmml.models.regression.evaluator;

import org.drools.core.util.StringUtils;
import org.kie.api.pmml.PMML4Result;
import org.kie.pmml.commons.exceptions.KiePMMLInternalException;
import org.kie.pmml.commons.model.KiePMMLModel;
import org.kie.pmml.commons.model.enums.PMML_MODEL;
import org.kie.pmml.evaluator.api.exceptions.KiePMMLModelException;
import org.kie.pmml.evaluator.api.executor.PMMLContext;
import org.kie.pmml.evaluator.core.executor.PMMLModelExecutor;
import org.kie.pmml.models.regression.model.KiePMMLRegressionModel;

import static org.kie.pmml.models.regression.evaluator.PMMLRegresssionModelEvaluator.evaluateRegression;

public class PMMLRegressionModelExecutor implements PMMLModelExecutor {

    private static final String INVALID_NORMALIZATION_METHOD = "Invalid Normalization Method ";

    @Override
    public PMML_MODEL getPMMLModelType() {
        return PMML_MODEL.REGRESSION_MODEL;
    }

    @Override
    public PMML4Result evaluate(KiePMMLModel model, PMMLContext pmmlContext, String releaseId) {
        validate(model);
        final KiePMMLRegressionModel regressionModel = (KiePMMLRegressionModel) model;
        return (regressionModel).isRegression() ? evaluateRegression(regressionModel, pmmlContext) : PMMLClassificationModelEvaluator.evaluateClassification(regressionModel, pmmlContext);
    }

    private void validate(KiePMMLModel toValidate) {
        if (!(toValidate instanceof KiePMMLRegressionModel)) {
            throw new KiePMMLModelException("Expected a KiePMMLRegressionModel, received a " + toValidate.getClass().getName());
        }
        if (((KiePMMLRegressionModel) toValidate).getRegressionTables() == null || ((KiePMMLRegressionModel) toValidate).getRegressionTables().isEmpty()) {
            throw new KiePMMLModelException("At least one RegressionTable required");
        }
        if (((KiePMMLRegressionModel) toValidate).isRegression()) {
            validateRegression((KiePMMLRegressionModel) toValidate);
        } else {
            validateClassification((KiePMMLRegressionModel) toValidate);
        }
    }

    private void validateRegression(KiePMMLRegressionModel toValidate) {
        if (toValidate.getTargetField() == null || StringUtils.isEmpty(toValidate.getTargetField().trim())) {
            throw new KiePMMLInternalException("TargetField required, retrieved " + toValidate.getTargetField());
        }
        if (toValidate.getRegressionTables().size() != 1) {
            throw new KiePMMLModelException("Expected one RegressionTable, retrieved " + toValidate.getRegressionTables().size());
        }
        switch (toValidate.getRegressionNormalizationMethod()) {
            case NONE:
            case SOFTMAX:
            case LOGIT:
            case EXP:
            case PROBIT:
            case CLOGLOG:
            case LOGLOG:
            case CAUCHIT:
                return;
            default:
                throw new KiePMMLModelException(INVALID_NORMALIZATION_METHOD + toValidate.getRegressionNormalizationMethod());
        }
    }

    private void validateClassification(KiePMMLRegressionModel toValidate) {
        switch (toValidate.getTargetOpType()) {
            case CATEGORICAL:
                validateClassificationCategorical(toValidate);
                break;
            case ORDINAL:
                validateClassificationOrdinal(toValidate);
                break;
            default:
                throw new KiePMMLModelException("Invalid target type " + toValidate.getTargetOpType());
        }
    }

    private void validateClassificationCategorical(KiePMMLRegressionModel toValidate) {
        if (toValidate.isBinary()) {
            validateClassificationCategoricalBinary(toValidate);
        } else {
            validateClassificationCategoricalNotBinary(toValidate);
        }
    }

    private void validateClassificationCategoricalBinary(KiePMMLRegressionModel toValidate) {
        switch (toValidate.getRegressionNormalizationMethod()) {
            case LOGIT:
            case PROBIT:
            case CAUCHIT:
            case CLOGLOG:
            case LOGLOG:
            case NONE:
                if (toValidate.getRegressionTables().size() != 2) {
                    throw new KiePMMLModelException("Expected two RegressionTables, retrieved " + toValidate.getRegressionTables().size());
                }
                return;
            default:
                throw new KiePMMLModelException(INVALID_NORMALIZATION_METHOD + toValidate.getRegressionNormalizationMethod());
        }
    }

    private void validateClassificationCategoricalNotBinary(KiePMMLRegressionModel toValidate) {
        switch (toValidate.getRegressionNormalizationMethod()) {
            case SOFTMAX:
            case SIMPLEMAX:
                if (toValidate.getRegressionTables().size() < 2) {
                    throw new KiePMMLModelException("Expected at least two RegressionTables, retrieved " + toValidate.getRegressionTables().size());
                }
                return;
            case NONE:
                if (toValidate.getRegressionTables().size() < 3) {
                    throw new KiePMMLModelException("Expected three RegressionTables, retrieved " + toValidate.getRegressionTables().size());
                }
                return;
            default:
                throw new KiePMMLModelException(INVALID_NORMALIZATION_METHOD + toValidate.getRegressionNormalizationMethod());
        }
    }

    private void validateClassificationOrdinal(KiePMMLRegressionModel toValidate) {
        switch (toValidate.getRegressionNormalizationMethod()) {
            case LOGIT:
            case PROBIT:
            case CAUCHIT:
            case CLOGLOG:
            case LOGLOG:
            case NONE:
                if (toValidate.getRegressionTables().size() < 2) {
                    throw new KiePMMLModelException("Expected at least two RegressionTables, retrieved " + toValidate.getRegressionTables().size());
                }
                return;
            default:
                throw new KiePMMLModelException(INVALID_NORMALIZATION_METHOD + toValidate.getRegressionNormalizationMethod());
        }
    }
}
