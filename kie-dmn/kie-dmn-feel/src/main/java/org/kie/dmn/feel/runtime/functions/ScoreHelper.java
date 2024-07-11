/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.kie.dmn.feel.runtime.functions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Stream;

import org.kie.dmn.feel.lang.EvaluationContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class used to evaluate <code>Method</code> score based on the given inputs.
 * It compares the original input with the "adapted" one to match a given <code>Method</code>.
 * For each condition, a value is provided. The top score is obtained when all the conditions are met.
 * <p>
 * Conditions considered (from most  to less relevant):
 * Condition                                        Score
 * 1. last input not array                  ->      100000
 * 1. last parameter not array              ->      10000
 * 2. number of parameters                  ->      1000
 * 3. type identity of all parameters ->    ->      weighted value of matching parameters and values 0-1000
 * 4. coerced to varargs                    ->      -10
 * 4. null  counts                          ->      null objects * -1
 */
public class ScoreHelper {

    private final static Logger logger = LoggerFactory.getLogger(ScoreHelper.class);

    private static final List<Function<Compares, Integer>> SCORER_LIST;

    static int lastInputNotArrayNotArrayScore = 100000;
    static int lastParameterNotArrayScore = 10000;
    static int numberOfParametersScore = 1000;
    static int coercedToVarargsScore = -10;

    static final Function<Compares, Integer> numberOfParameters = compares -> {
        int toReturn = compares.originalInput.length == compares.parameterTypes.length ? numberOfParametersScore : 0;
        logger.trace("numberOfParameters {} -> {}", compares, toReturn);
        return toReturn;
    };

    static final Function<Compares, Integer> typeIdentityOfParameters = compares -> {
        int index = Math.min(compares.originalInput.length, compares.parameterTypes.length);
        boolean automaticallyAddedEvaluationContext = compares.parameterTypes.length > 0 &&
                compares.parameterTypes[0] != null &&
                compares.parameterTypes[0].equals(EvaluationContext.class) &&
                compares.originalInput.length > 0 &&
                compares.originalInput[0] != null &&
                !(compares.originalInput[0] instanceof EvaluationContext);
        int counter = 0;
        int matchedEvaluationContext = 0;
        for (int i = 0; i < index; i++) {
            if (compares.parameterTypes[i].equals(EvaluationContext.class) &&
                    compares.originalInput[i] != null &&
                    compares.originalInput[i] instanceof EvaluationContext) {
                // Do not consider EvaluationContext for score
                matchedEvaluationContext += 1;
                continue;
            }
            // In this case, we switch the parameter comparison, ignoring the first parameterType
            int inputIndex = automaticallyAddedEvaluationContext ? i + 1 : i;
            Class<?> expectedType = compares.parameterTypes[inputIndex];
            Object originalValue = compares.originalInput[i];
            if (expectedType.equals(Object.class)) {
                // parameter type is Object
                counter += 1;
            }
            if (originalValue == null) {
                // null value has a potential match
                counter += 1;
            } else if (!(expectedType.isInstance(originalValue))) {
                // do not count it
                continue;
            } else if (!(expectedType.equals(Object.class)) &&
                    (expectedType.isInterface() ||
                            expectedType.equals(originalValue.getClass()) ||
                            expectedType.isAssignableFrom(originalValue.getClass()))) {
                counter += 2;
            }
            logger.trace("typeIdentityOfParameters {} {} -> {}", expectedType, originalValue, counter);
        }
        int elementsToConsider = index - matchedEvaluationContext;
        int toReturn = counter > 0 ? Math.round(((float) counter / elementsToConsider) * 500) : 0;
        logger.trace("typeIdentityOfParameters {} -> {}", compares, toReturn);
        return toReturn;
    };

    static final Function<Compares, Integer> lastInputNotArray =
            compares -> {
                int toReturn = isLastInputArray(compares.adaptedInput) ? 0 :
                        lastInputNotArrayNotArrayScore;
                logger.trace("lastInputNotArray {} -> {}", compares, toReturn);
                return toReturn;
            };

    static boolean isLastInputArray(Object[] adaptedInput) {
        return adaptedInput != null &&
                adaptedInput.length > 0 &&
                adaptedInput[adaptedInput.length - 1] != null &&
                adaptedInput[adaptedInput.length - 1].getClass().isArray();
    }

    static final Function<Compares, Integer> lastParameterNotArray =
            compares -> {
                int toReturn = isLastParameterArray(compares.parameterTypes) ? 0 :
                        lastParameterNotArrayScore;
                logger.trace("lastParameterNotArray {} -> {}", compares, toReturn);
                return toReturn;
            };

    static boolean isLastParameterArray(Class<?>[] parameterTypes) {
        return parameterTypes != null &&
                parameterTypes.length > 0 &&
                parameterTypes[parameterTypes.length - 1] != null &&
                parameterTypes[parameterTypes.length - 1].isArray();
    }

    static final Function<Compares, Integer> coercedToVarargs =
            compares -> {
                Object[] amendedOriginalInput = compares.originalInput != null ? Arrays.stream(compares.originalInput)
                        .filter(o -> !(o instanceof EvaluationContext)).toArray() : new Object[0];
                Object[] amendedAdaptedInput = compares.adaptedInput != null ? Arrays.stream(compares.adaptedInput)
                        .filter(o -> !(o instanceof EvaluationContext)).toArray() : new Object[0];
                int toReturn = 0;
                if (amendedOriginalInput.length >= amendedAdaptedInput.length &&
                        amendedAdaptedInput.length == 1 &&
                        isCoercedToVarargs(amendedOriginalInput[amendedOriginalInput.length - 1],
                                           amendedAdaptedInput[0])) {
                    toReturn = coercedToVarargsScore;
                }
                logger.trace("coercedToVarargs {} -> {}", compares, toReturn);
                return toReturn;
            };

    static boolean isCoercedToVarargs(Object originalInput, Object adaptedInput) {
        boolean isOriginalInputCandidate =
                originalInput == null || !originalInput.getClass().equals(Object.class.arrayType());
        boolean isAdaptedInputCandidate =
                adaptedInput != null && adaptedInput.getClass().equals(Object.class.arrayType());
        return isOriginalInputCandidate && isAdaptedInputCandidate;
    }

    static final Function<Compares, Integer> nullCounts =
            compares -> {
                int toReturn = nullCount(compares.adaptedInput) * -1;
                logger.trace("nullCounts {} -> {}", compares, toReturn);
                return toReturn;
            };

    static {
        SCORER_LIST = new ArrayList<>();
        SCORER_LIST.add(lastInputNotArray);
        SCORER_LIST.add(lastParameterNotArray);
        SCORER_LIST.add(numberOfParameters);
        SCORER_LIST.add(typeIdentityOfParameters);
        SCORER_LIST.add(coercedToVarargs);
        SCORER_LIST.add(nullCounts);
    }

    static int score(Compares toScore) {
        int toReturn = SCORER_LIST.stream()
                .map(comparesIntegerFunction ->
                             comparesIntegerFunction.apply(toScore))
                .reduce(0, Integer::sum);
        logger.trace("score {} -> {}", toScore, toReturn);
        return toReturn;
    }

    static int nullCount(Object[] params) {
        int toReturn = params != null ? (int) Stream.of(params).filter(Objects::isNull).count() : 0;
        logger.trace("nullCount {} -> {}", params, toReturn);
        return toReturn;
    }

    private ScoreHelper() {
    }

    static class Compares {

        private final Object[] originalInput;
        private final Object[] adaptedInput;
        private final Class<?>[] parameterTypes;

        public Compares(Object[] originalInput, Object[] adaptedInput, Class<?>[] parameterTypes) {
            this.originalInput = originalInput;
            this.adaptedInput = adaptedInput;
            this.parameterTypes = parameterTypes;
        }

        @Override
        public String toString() {
            return "Compares{" +
                    "originalInput=" + Arrays.toString(originalInput) +
                    ", adaptedInput=" + Arrays.toString(adaptedInput) +
                    ", parameterTypes=" + Arrays.toString(parameterTypes) +
                    '}';
        }
    }
}