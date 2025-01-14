/*
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
package org.kie.yard.core;

import org.kie.yard.api.model.LiteralExpression;

import java.util.Objects;

public class LiteralExpressionBuilder {

    private final String expressionLang;
    private final YaRDDefinitions definitions;
    private final String name;
    private final LiteralExpression decisionLogic;

    public LiteralExpressionBuilder(final String expressionLang,
                                    final YaRDDefinitions definitions,
                                    final String name,
                                    final LiteralExpression decisionLogic) {
        this.expressionLang = expressionLang;
        this.definitions = definitions;
        this.name = name;
        this.decisionLogic = decisionLogic;
    }

    public Firable build() {
        final String expr = decisionLogic.getExpression();
        definitions.outputs().put(name, StoreHandle.empty(Object.class));
        if(Objects.equals(expressionLang, "jshell")){
            return new JShellLiteralExpressionInterpreter(name, QuotedExprParsed.from(expr));
        }
        else {
            return new MVELLiteralExpressionInterpreter(name,QuotedExprParsed.from(expr));
        }
    }
}
