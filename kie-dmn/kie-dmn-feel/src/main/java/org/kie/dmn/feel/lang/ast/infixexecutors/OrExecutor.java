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
package org.kie.dmn.feel.lang.ast.infixexecutors;

import org.kie.dmn.feel.lang.EvaluationContext;
import org.kie.dmn.feel.lang.ast.InfixOpNode;
import org.kie.dmn.feel.util.BooleanEvalHelper;

import static org.kie.dmn.feel.lang.ast.infixexecutors.InfixExecutorUtils.or;

public class OrExecutor implements InfixExecutor {

    private static final OrExecutor INSTANCE = new OrExecutor();

    private OrExecutor() {
    }

    public static OrExecutor instance() {
        return INSTANCE;
    }

    @Override
    public Object evaluate(Object left, Object right, EvaluationContext ctx) {
        return or(left, right, ctx);
    }

    @Override
    public Object evaluate(InfixOpNode infixNode, EvaluationContext ctx) {
        Boolean leftOR = BooleanEvalHelper.getBooleanOrDialectDefault(infixNode.getLeft().evaluate(ctx), ctx.getFEELDialect());
        if (leftOR != null) {
            if (!leftOR.booleanValue()) {
                return BooleanEvalHelper.getBooleanOrDialectDefault(infixNode.getRight().evaluate(ctx), ctx.getFEELDialect());
            } else {
                return Boolean.TRUE; //left hand operand is true, we do not need to evaluate right side
            }
        } else {
            return BooleanEvalHelper.getTrueOrDialectDefault(infixNode.getRight().evaluate(ctx), ctx.getFEELDialect());
        }
    }

}
