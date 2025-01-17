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
package org.kie.dmn.feel.runtime;

import java.util.List;

import org.kie.dmn.feel.lang.EvaluationContext;
import org.kie.dmn.feel.lang.Symbol;
import org.kie.dmn.feel.lang.Type;

/**
 * An interface for all FEEL functions, custom or built-in
 */
public interface FEELFunction {

    /**
     * Returns the name of the function
     * @return
     */
    String getName();

    /**
     * Returns the Symbol of the function
     * @return
     */
    Symbol getSymbol();

    /**
     * Returns the parameter for each supported signature.
     *
     * @return a List of Lists of Strings with the parameters. For
     *         a function with multiple signatures, each element of the
     *         list returns the parameters of one signature.
     *         E.g.:
     *
     *         the substring function has 2 supported signatures:
     *
     *         substring( string, start position )
     *         substring( string, start position, length )
     *
     *         So this method will return:
     *
     *         { { "string", "start position" },
     *           { "string", "start position", "length" } }
     */
    List<List<Param>> getParameters();

    /**
     * Invokes the function reflectively based on the parameters
     *
     * @param ctx
     * @param params
     * @return
     */
    Object invokeReflectively(EvaluationContext ctx, Object[] params);

    /**
     * The default value to return instead of <code>null</code>, to be used with the B-FEEL (and other different dialects) syntax
     * @return
     */
    default Object defaultValue() {
        // To be overridden by specific classes for B-FEEL compliance
        return null;
    }

    /**
     * The list to use as input, instead of the original one; to be used with the B-FEEL syntax
     * @return
     */
    default List feelDialectAdaptedInputList(List toAdapt) {
        // To be overridden by specific classes for B-FEEL compliance
        return toAdapt;
    }

    class Param {

        public final String name;
        public final Type type;

        public Param(String name, Type type) {
            this.name = name;
            this.type = type;
        }

        public String getName() {
            return name;
        }

        public Type getType() {
            return type;
        }

        public String prettyFEEL() {
            return name + " : " + type.getName();
        }

    }
}
