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
package org.kie.pmml.evaluator.core.utils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.kie.api.pmml.PMML4Result;
import org.kie.pmml.api.enums.RESULT_FEATURE;
import org.kie.pmml.api.exceptions.KiePMMLException;
import org.kie.pmml.commons.model.KiePMMLModel;
import org.kie.pmml.commons.model.KiePMMLOutputField;
import org.kie.pmml.commons.model.expressions.KiePMMLApply;
import org.kie.pmml.commons.model.expressions.KiePMMLConstant;
import org.kie.pmml.commons.model.expressions.KiePMMLExpression;
import org.kie.pmml.commons.model.expressions.KiePMMLFieldRef;
import org.kie.pmml.commons.model.tuples.KiePMMLNameValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class meant to provide static methods related to <b>post-process</b> manipulation
 */
public class PostProcess {

    private static final Logger logger = LoggerFactory.getLogger(PostProcess.class);

      
    private PostProcess() {
        // Avoid instantiation
    }


    /**
     * Populated the <code>PMML4Result</code> with <code>OutputField</code> results
     * @param model
     * @param toUpdate
     * @param kiePMMLNameValues
     */
    public static void populateOutputFields(final KiePMMLModel model, final PMML4Result toUpdate,
                              final List<KiePMMLNameValue> kiePMMLNameValues) {
        logger.debug("populateOutputFields {} {} {}", model, toUpdate, kiePMMLNameValues);
        final Map<RESULT_FEATURE, List<KiePMMLOutputField>> outputFieldsByFeature = model.getKiePMMLOutputFields()
                .stream()
                .collect(Collectors.groupingBy(KiePMMLOutputField::getResultFeature));
        List<KiePMMLOutputField> predictedOutputFields = outputFieldsByFeature.get(RESULT_FEATURE.PREDICTED_VALUE);
        if (predictedOutputFields != null) {
            predictedOutputFields
                    .forEach(outputField -> populatePredictedOutputField(outputField, toUpdate,
                                                                         model,
                                                                         kiePMMLNameValues));
        }
        List<KiePMMLOutputField> transformedOutputFields = outputFieldsByFeature.get(RESULT_FEATURE.TRANSFORMED_VALUE);
        if (transformedOutputFields != null) {
            transformedOutputFields
                    .forEach(outputField -> populateTransformedOutputField(outputField, toUpdate,
                                                                           model,
                                                                           kiePMMLNameValues));
        }
    }

    static void populatePredictedOutputField(final KiePMMLOutputField outputField,
                                      final PMML4Result toUpdate,
                                      final KiePMMLModel model,
                                      final List<KiePMMLNameValue> kiePMMLNameValues) {
        logger.debug("populatePredictedOutputField {} {} {} {}", outputField, toUpdate, model, kiePMMLNameValues);
        if (!RESULT_FEATURE.PREDICTED_VALUE.equals(outputField.getResultFeature())) {
            throw new KiePMMLException("Unexpected " + outputField.getResultFeature());
        }
        String targetFieldName = outputField.getTargetField().orElse(toUpdate.getResultObjectName());
        Optional<Object> variableValue = Optional.empty();
        if (targetFieldName != null) {
            variableValue = Stream.of(getValueFromPMMLResultByVariableName(targetFieldName, toUpdate),
                                      getValueFromKiePMMLNameValuesByVariableName(targetFieldName, kiePMMLNameValues))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .findFirst();
        }
        variableValue.ifPresent(objValue -> toUpdate.addResultVariable(outputField.getName(), objValue));
    }

    static void populateTransformedOutputField(final KiePMMLOutputField outputField,
                                        final PMML4Result toUpdate,
                                        final KiePMMLModel model,
                                        final List<KiePMMLNameValue> kiePMMLNameValues) {
        logger.debug("populateTransformedOutputField {} {} {} {}", outputField, toUpdate, model, kiePMMLNameValues);
        if (!RESULT_FEATURE.TRANSFORMED_VALUE.equals(outputField.getResultFeature())) {
            throw new KiePMMLException("Unexpected " + outputField.getResultFeature());
        }
        String variableName = outputField.getName();
        Optional<Object> variableValue = Optional.empty();
        if (outputField.getKiePMMLExpression() != null) {
            final KiePMMLExpression kiePMMLExpression = outputField.getKiePMMLExpression();
            variableValue = getValueFromKiePMMLExpression(kiePMMLExpression, model, kiePMMLNameValues);
        }
        variableValue.ifPresent(objValue -> toUpdate.addResultVariable(variableName, objValue));
    }

     static Optional<Object> getValueFromKiePMMLExpression(final KiePMMLExpression kiePMMLExpression,
                                                   final KiePMMLModel model,
                                                   final List<KiePMMLNameValue> kiePMMLNameValues) {
        String expressionType = kiePMMLExpression.getClass().getSimpleName();
        Optional<Object> toReturn = Optional.empty();
        switch (expressionType) {
            case "KiePMMLApply":
                toReturn = Stream.of(getValueFromKiePMMLApplyFunction((KiePMMLApply) kiePMMLExpression,
                                                                      model,
                                                                      kiePMMLNameValues),
                                     getValueFromKiePMMLApplyMapMissingTo((KiePMMLApply) kiePMMLExpression),
                                     getValueFromKiePMMLApplyMapDefaultValue((KiePMMLApply) kiePMMLExpression))
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .findFirst();
                break;
            case "KiePMMLConstant":
                toReturn = getValueFromKiePMMLConstant((KiePMMLConstant) kiePMMLExpression);
                break;
            case "KiePMMLFieldRef":
                toReturn =
                        Stream.of(getValueFromKiePMMLNameValuesByVariableName(((KiePMMLFieldRef) kiePMMLExpression).getName(), kiePMMLNameValues),
                                  getMissingValueFromKiePMMLFieldRefMapMissingTo((KiePMMLFieldRef) kiePMMLExpression))
                                .filter(Optional::isPresent)
                                .map(Optional::get)
                                .findFirst();
                break;
            default:
                // Not implemented, yet
                break;
        }
        return toReturn;
    }

    static Optional<Object> getValueFromKiePMMLApplyFunction(final KiePMMLApply kiePMMLApply,
                                                              final KiePMMLModel model,
                                                              final List<KiePMMLNameValue> kiePMMLNameValues) {
        Optional<Object> optionalObjectParameter = Optional.empty();
        if (kiePMMLApply.getKiePMMLExpressions() != null && !kiePMMLApply.getKiePMMLExpressions().isEmpty()) {
            optionalObjectParameter = getValueFromKiePMMLExpression(kiePMMLApply.getKiePMMLExpressions().get(0),
                                                                    model,
                                                                    kiePMMLNameValues);
        }
        return getValueFromFunctionsMapByFunctionName(model.getFunctionsMap(),
                                                      kiePMMLApply.getFunction(),
                                                      kiePMMLNameValues,
                                                      optionalObjectParameter.orElse(null));
    }

    static Optional<Object> getValueFromFunctionsMapByFunctionName(final Map<String, BiFunction<List<KiePMMLNameValue>, Object, Object>> functionsMap,
                                                                   final String functionName,
                                                                   final List<KiePMMLNameValue> kiePMMLNameValues,
                                                                   final Object objectParameter) {
        return functionsMap.keySet()
                .stream()
                .filter(funName -> funName.equals(functionName))
                .findFirst()
                .map(functionsMap::get)
                .map(function -> function.apply(kiePMMLNameValues, objectParameter));
    }

    static Optional<Object> getValueFromKiePMMLConstant(final KiePMMLConstant kiePMMLConstant) {
        return Optional.ofNullable(kiePMMLConstant.getValue());
    }

    static Optional<Object> getValueFromKiePMMLApplyMapMissingTo(final KiePMMLApply kiePMMLApply) {
        return Optional.ofNullable(kiePMMLApply.getMapMissingTo());
    }

    static Optional<Object> getValueFromKiePMMLApplyMapDefaultValue(final KiePMMLApply kiePMMLApply) {
        return Optional.ofNullable(kiePMMLApply.getDefaultValue());
    }

    static Optional<Object> getValueFromKiePMMLNameValuesByVariableName(final String variableName,
                                                                        final List<KiePMMLNameValue> kiePMMLNameValues) {
        return kiePMMLNameValues.stream()
                .filter(kiePMMLNameValue -> kiePMMLNameValue.getName().equals(variableName))
                .findFirst()
                .map(KiePMMLNameValue::getValue);
    }

    static Optional<Object> getValueFromPMMLResultByVariableName(final String variableName,
                                                                  final PMML4Result pmml4Result) {
        return Optional.ofNullable(pmml4Result.getResultVariables().get(variableName));
    }

    static Optional<Object> getMissingValueFromKiePMMLFieldRefMapMissingTo(final KiePMMLFieldRef kiePMMLFieldRef) {
        return Optional.ofNullable(kiePMMLFieldRef.getMapMissingTo());
    }

}
