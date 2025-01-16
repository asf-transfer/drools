/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.drools.compiler.rule.builder.util;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.kie.api.runtime.rule.AccumulateFunction;
import org.kie.internal.builder.conf.AccumulateFunctionOption;
import org.kie.internal.utils.ChainedProperties;

import static org.drools.util.ClassUtils.convertFromPrimitiveType;

public class AccumulateUtil {

    public static String getFunctionName(Supplier<Class<?>> exprClassSupplier, String functionName) {
        if (functionName.equals("sum")) {
            final Class<?> exprClass = exprClassSupplier.get();
            if (exprClass == int.class || exprClass == Integer.class) {
                functionName = "sumI";
            } else if (exprClass == long.class || exprClass == Long.class) {
                functionName = "sumL";
            } else if (exprClass == BigInteger.class) {
                functionName = "sumBI";
            } else if (exprClass == BigDecimal.class) {
                functionName = "sumBD";
            }
        } else if (functionName.equals("average")) {
            final Class<?> exprClass = exprClassSupplier.get();
            if (exprClass == BigDecimal.class) {
                functionName = "averageBD";
            }
        } else if (functionName.equals("max")) {
            final Class<?> exprClass = convertFromPrimitiveType( exprClassSupplier.get() );
            if (exprClass == Integer.class) {
                functionName = "maxI";
            } else if (exprClass == Long.class) {
                functionName = "maxL";
            } else if (exprClass == BigInteger.class) {
                functionName = "maxBI";
            } else if (exprClass == BigDecimal.class) {
                functionName = "maxBD";
            } else if (Number.class.isAssignableFrom( exprClass )) {
                functionName = "maxN";
            }
        } else if (functionName.equals("min")) {
            final Class<?> exprClass = convertFromPrimitiveType( exprClassSupplier.get() );
            if (exprClass == Integer.class) {
                functionName = "minI";
            } else if (exprClass == Long.class) {
                functionName = "minL";
            } else if (exprClass == BigInteger.class) {
                functionName = "minBI";
            } else if (exprClass == BigDecimal.class) {
                functionName = "minBD";
            } else if (Number.class.isAssignableFrom( exprClass )) {
                functionName = "minN";
            }
        }
        return functionName;
    }

    @SuppressWarnings("unchecked")
    public static AccumulateFunction loadAccumulateFunction(ClassLoader classLoader, String identifier,
                                                            String className) {
        try {
            Class<? extends AccumulateFunction> clazz = (Class<? extends AccumulateFunction>) classLoader.loadClass(className);
            return clazz.newInstance();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Error loading accumulate function for identifier " + identifier + ". Class " + className + " not found",
                                       e);
        } catch (InstantiationException e) {
            throw new RuntimeException("Error loading accumulate function for identifier " + identifier + ". Instantiation failed for class " + className,
                                       e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("Error loading accumulate function for identifier " + identifier + ". Illegal access to class " + className,
                                       e);
        }
    }

    public static Map<String, AccumulateFunction> buildAccumulateFunctionsMap(ChainedProperties chainedProperties, ClassLoader typesClassLoader) {
        Map<String, AccumulateFunction> accumulateFunctions = new HashMap<>();
        Map<String, String> temp = new HashMap<>();
        chainedProperties.mapStartsWith(temp,
                                        AccumulateFunctionOption.PROPERTY_NAME,
                                        true);
        int index = AccumulateFunctionOption.PROPERTY_NAME.length();
        for (Map.Entry<String, String> entry : temp.entrySet()) {
            String identifier = entry.getKey().trim().substring(index);
            accumulateFunctions.put(identifier,
                                    AccumulateUtil.loadAccumulateFunction(typesClassLoader, identifier,
                                                                          entry.getValue()));
        }
        return accumulateFunctions;
    }
}
