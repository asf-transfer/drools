/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.kie.dmn.feel.runtime.functions;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.kie.dmn.api.feel.runtime.events.FEELEvent;
import org.kie.dmn.api.feel.runtime.events.FEELEvent.Severity;
import org.kie.dmn.feel.lang.EvaluationContext;
import org.kie.dmn.feel.lang.FEELDialect;
import org.kie.dmn.feel.lang.Symbol;
import org.kie.dmn.feel.lang.impl.NamedParameter;
import org.kie.dmn.feel.lang.types.FunctionSymbol;
import org.kie.dmn.feel.runtime.FEELFunction;
import org.kie.dmn.feel.runtime.events.FEELEventBase;
import org.kie.dmn.feel.runtime.events.InvalidParametersEvent;
import org.kie.dmn.feel.util.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseFEELFunction
        implements FEELFunction {

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private String name;
    private Symbol symbol;

    public BaseFEELFunction(String name) {
        this.name = name;
        this.symbol = new FunctionSymbol(name, this);
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
        ((FunctionSymbol) this.symbol).setId(name);
    }

    @Override
    public Symbol getSymbol() {
        return symbol;
    }

    @Override
    public Object invokeReflectively(EvaluationContext ctx, Object[] params) {
        // use reflection to call the appropriate invoke method
        try {
            boolean isNamedParams = params.length > 0 && params[0] instanceof NamedParameter;
            if (!isCustomFunction()) {

                CandidateMethod cm = getCandidateMethod(ctx, params, isNamedParams);

                if (cm != null) {
                    if (cm.actualParams != null) {
                        for (int i = 0; i < cm.actualParams.length; i++) {
                            if (cm.actualParams[i] instanceof List list) {
                                cm.actualParams[i] =  getFEELDialectAdaptedList(ctx, list);
                            }
                        }
                    }

                    Object result = cm.actualMethod.invoke(this, cm.actualParams);

                    if (result instanceof Either) {
                        @SuppressWarnings("unchecked")
                        Either<FEELEvent, Object> either = (Either<FEELEvent, Object>) result;
                        return getEitherResult(ctx,
                                               either,
                                               () -> Stream.of(cm.actualMethod.getParameters()).map(p -> p.getAnnotation(ParameterName.class).value()).collect(Collectors.toList()),
                                               () -> Arrays.asList(cm.actualParams));
                    }

                    return result;
                } else {
                    // CandidateMethod cm could be null also if reflection failed on Platforms not supporting
                    // getClass().getDeclaredMethods()
                    String ps = getClass().toString();
                    logger.error("Unable to find function '" + getName() + "( " + ps.substring(1, ps.length() - 1) +
                                         " )'");
                    ctx.notifyEvt(() -> new FEELEventBase(Severity.ERROR, "Unable to find function '" + getName() +
                            "( " + ps.substring(1, ps.length() - 1) + " )'", null));
                }
            } else {
                if (isNamedParams) {
                    // This is inherently frail because it expects that, if, the first parameter is NamedParameter
                    // and the function is a CustomFunction, then all parameters are NamedParameter
                    NamedParameter[] namedParams =
                            Arrays.stream(params).map(NamedParameter.class::cast).toArray(NamedParameter[]::new);
                    params = BaseFEELFunctionHelper.rearrangeParameters(namedParams,
                                                                        this.getParameters().get(0).stream().map(Param::getName).collect(Collectors.toList()));
                }
                Object result = invoke(ctx, params);
                if (result instanceof Either) {
                    @SuppressWarnings("unchecked")
                    Either<FEELEvent, Object> either = (Either<FEELEvent, Object>) result;

                    final Object[] usedParams = params;
                    Object eitherResult = getEitherResult(ctx,
                                                          either,
                                                          () -> IntStream.of(0, usedParams.length).mapToObj(i -> "arg"
                                                                  + i).collect(Collectors.toList()),
                                                          () -> Arrays.asList(usedParams));
                    return BaseFEELFunctionHelper.normalizeResult(eitherResult);
                }
                return BaseFEELFunctionHelper.normalizeResult(result);
            }
        } catch (Exception e) {
            logger.error("Error trying to call function " + getName() + ".", e);
            ctx.notifyEvt(() -> new FEELEventBase(Severity.ERROR, "Error trying to call function " + getName() + ".",
                                                  e));
        }
        return getFEELDialectAdaptedObject(ctx, null);
    }

    @Override
    public List<List<Param>> getParameters() {
        // TODO: we could implement this method using reflection, just for consistency,
        // but it is not used at the moment
        return Collections.emptyList();
    }

    /**
     * this method should be overriden by custom function implementations that should be invoked reflectively
     * @param ctx
     * @param params
     * @return
     */
    public Object invoke(EvaluationContext ctx, Object[] params) {
        throw new RuntimeException("This method should be overriden by classes that implement custom feel functions");
    }

    /**
     * @param ctx
     * @param originalInput
     * @param isNamedParams <code>true</code> if the parameter refers to value to be retrieved inside
     * <code>ctx</code>; <code>false</code> if the parameter is the actual value
     * @return
     */
    protected CandidateMethod getCandidateMethod(EvaluationContext ctx, Object[] originalInput, boolean isNamedParams) {
        CandidateMethod toReturn = null;
        for (Method method : getClass().getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers()) && method.getName().equals("invoke")) {
                CandidateMethod candidateMethod = getCandidateMethod(ctx, originalInput, isNamedParams, method);
                if (candidateMethod == null) {
                    continue;
                }
                if (toReturn == null) {
                    toReturn = candidateMethod;
                } else if (candidateMethod.score > toReturn.score) {
                    toReturn = candidateMethod;
                } else if (candidateMethod.score == toReturn.score) {
                    toReturn = getBestScoredCandidateMethod(originalInput, candidateMethod, toReturn);
                }
            }
        }
        return toReturn;
    }

    private CandidateMethod getCandidateMethod(EvaluationContext ctx, Object[] originalInput,
                                               boolean isNamedParams, Method m) {
        Object[] adaptedInput = BaseFEELFunctionHelper.getAdjustedParametersForMethod(ctx, originalInput,
                                                                                      isNamedParams, m);
        if (adaptedInput == null) {
            // incompatible method
            return null;
        }

        Class<?>[] parameterTypes = m.getParameterTypes();
        if (parameterTypes.length != adaptedInput.length) {
            return null;
        }

        ScoreHelper.Compares compares = new ScoreHelper.Compares(originalInput, adaptedInput, parameterTypes);
        return new CandidateMethod(m, ScoreHelper.grossScore(compares), adaptedInput);
    }

    /**
     * Returns the <b>left</b> <code>CandidateMethod</code> if its <b>fineScore</b> is greater than the <b>right</b> one,
     * otherwise returns the <b>right</b> <code>CandidateMethod</code>
     * @param originalInput
     * @param left
     * @param right
     * @return
     */
    private CandidateMethod getBestScoredCandidateMethod(Object[] originalInput, CandidateMethod left,
                                                         CandidateMethod right) {

        ScoreHelper.Compares compares = new ScoreHelper.Compares(originalInput, left.getActualParams(),
                                                                 left.getParameterTypes());
        int leftScore = ScoreHelper.fineScore(compares);
        compares = new ScoreHelper.Compares(originalInput, right.getActualParams(), right.getParameterTypes());
        int rightScore = ScoreHelper.fineScore(compares);
        return leftScore > rightScore ? left : right;
    }

    private Object getEitherResult(EvaluationContext ctx, Either<FEELEvent, Object> source,
                                   Supplier<List<String>> parameterNamesSupplier,
                                   Supplier<List<Object>> parameterValuesSupplier) {
        source = getFEELDialectAdaptedEither(ctx, source);
        return source.cata((left) -> {
            ctx.notifyEvt(() -> {
                              if (left instanceof InvalidParametersEvent invalidParametersEvent) {
                                  invalidParametersEvent.setNodeName(getName());
                                  invalidParametersEvent.setActualParameters(parameterNamesSupplier.get(),
                                                                             parameterValuesSupplier.get());
                              }
                              return left;
                          }
            );
            return null;
        }, Function.identity());
    }

    /**
     * Adapt the given <code>Either&lt;FEELEvent, Object&gt;</code> to contain FEEL-Dialect-specific value, if needed
     *
     * @see FEELFunction#defaultValue()
     * @param ctx
     * @param source
     * @return
     */
    private Either<FEELEvent, Object> getFEELDialectAdaptedEither(EvaluationContext ctx, Either<FEELEvent, Object> source) {
        if (ctx.getFEELDialect().equals(FEELDialect.BFEEL)
                && source.getOrElse(null) == null) {
            return Either.ofRight(defaultValue());
        } else {
            return source;
        }
    }

    /**
     * Adapt the given <code>Object</code> to FEEL-Dialect-specific value, if needed
     *
     * @see FEELFunction#defaultValue()
     * @param ctx
     * @param source
     * @return
     */
    private Object getFEELDialectAdaptedObject(EvaluationContext ctx, Object source) {
        if (ctx.getFEELDialect().equals(FEELDialect.BFEEL)
                && source == null) {
            return defaultValue();
        } else {
            return source;
        }
    }

    /**
     * Adapt the given <code>List</code> to FEEL-Dialect-specific values, if needed
     *
     * @see FEELFunction#feelDialectAdaptedInputList(List)
     * @param ctx
     * @param source
     * @return
     */
    private List getFEELDialectAdaptedList(EvaluationContext ctx, List source) {
        if (ctx.getFEELDialect().equals(FEELDialect.BFEEL)
                && source != null) {
            return feelDialectAdaptedInputList(source);
        } else {
            return source;
        }
    }

    protected boolean isCustomFunction() {
        return false;
    }

    protected static class CandidateMethod {

        private Method actualMethod = null;
        private Object[] actualParams;
        private int score;

        public CandidateMethod(Method actualMethod, int score, Object[] actualParams) {
            this.actualMethod = actualMethod;
            this.score = score;
            this.actualParams = actualParams;
        }

        public Method getActualMethod() {
            return actualMethod;
        }

        public int getScore() {
            return score;
        }

        public Object[] getActualParams() {
            return actualParams;
        }

        public Class<?>[] getParameterTypes() {
            return actualMethod.getParameterTypes();
        }
    }
}
