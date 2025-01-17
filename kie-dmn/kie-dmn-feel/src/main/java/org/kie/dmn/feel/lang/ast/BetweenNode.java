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
package org.kie.dmn.feel.lang.ast;

import org.antlr.v4.runtime.ParserRuleContext;
import org.kie.dmn.api.feel.runtime.events.FEELEvent.Severity;
import org.kie.dmn.feel.lang.EvaluationContext;
import org.kie.dmn.feel.lang.FEELDialect;
import org.kie.dmn.feel.lang.Type;
import org.kie.dmn.feel.lang.ast.infixexecutors.InfixExecutorUtils;
import org.kie.dmn.feel.lang.types.BuiltInType;
import org.kie.dmn.feel.util.BooleanEvalHelper;
import org.kie.dmn.feel.util.Msg;

public class BetweenNode
        extends BaseNode {

    private BaseNode value;
    private BaseNode start;
    private BaseNode end;

    public BetweenNode(ParserRuleContext ctx, BaseNode value, BaseNode start, BaseNode end) {
        super(ctx);
        this.value = value;
        this.start = start;
        this.end = end;
    }

    public BetweenNode(BaseNode value, BaseNode start, BaseNode end, String text) {
        this.value = value;
        this.start = start;
        this.end = end;
        this.setText(text);
    }

    public BaseNode getValue() {
        return value;
    }

    public void setValue(BaseNode value) {
        this.value = value;
    }

    public BaseNode getStart() {
        return start;
    }

    public void setStart(BaseNode start) {
        this.start = start;
    }

    public BaseNode getEnd() {
        return end;
    }

    public void setEnd(BaseNode end) {
        this.end = end;
    }

    @Override
    public Object evaluate(EvaluationContext ctx) {
        boolean problem = false;
        if (value == null) {
            ctx.notifyEvt(astEvent(Severity.ERROR, Msg.createMessage(Msg.IS_NULL, "value")));
            problem = true;
        }
        if (start == null) {
            ctx.notifyEvt(astEvent(Severity.ERROR, Msg.createMessage(Msg.IS_NULL, "start")));
            problem = true;
        }
        if (end == null) {
            ctx.notifyEvt(astEvent(Severity.ERROR, Msg.createMessage(Msg.IS_NULL, "end")));
            problem = true;
        }
        if (problem) return null;

        Object o_val = value.evaluate(ctx);
        Object o_s = start.evaluate(ctx);
        Object o_e = end.evaluate(ctx);
        if (o_val == null) {
            ctx.notifyEvt(astEvent(Severity.ERROR, Msg.createMessage(Msg.IS_NULL, "value")));
            problem = true;
        }
        if (o_s == null) {
            ctx.notifyEvt(astEvent(Severity.ERROR, Msg.createMessage(Msg.IS_NULL, "start")));
            problem = true;
        }
        if (o_e == null) {
            ctx.notifyEvt(astEvent(Severity.ERROR, Msg.createMessage(Msg.IS_NULL, "end")));
            problem = true;
        }
        if (problem && ctx.getFEELDialect() != FEELDialect.BFEEL) return null;

        Object gte = InfixExecutorUtils.or(BooleanEvalHelper.compare(o_val, o_s, ctx.getFEELDialect(), (l, r) -> l.compareTo(r) > 0),
                                           BooleanEvalHelper.isEqual(o_val, o_s, ctx.getFEELDialect()),
                                           ctx); // do not use Java || to avoid potential NPE due to FEEL 3vl.
        if (gte == null) {
            ctx.notifyEvt(astEvent(Severity.ERROR, Msg.createMessage(Msg.X_TYPE_INCOMPATIBLE_WITH_Y_TYPE, "value", "start")));
        }

        Object lte = InfixExecutorUtils.or(BooleanEvalHelper.compare(o_val, o_e, ctx.getFEELDialect(), (l, r) -> l.compareTo(r) < 0),
                BooleanEvalHelper.isEqual(o_val, o_e, ctx.getFEELDialect()),
                ctx); // do not use Java || to avoid potential NPE due to FEEL 3vl.
        if (lte == null) {
            ctx.notifyEvt(astEvent(Severity.ERROR, Msg.createMessage(Msg.X_TYPE_INCOMPATIBLE_WITH_Y_TYPE, "value", "end")));
        }

        return InfixExecutorUtils.and(gte, lte, ctx); // do not use Java && to avoid potential NPE due to FEEL 3vl.
    }

    @Override
    public Type getResultType() {
        return BuiltInType.BOOLEAN;
    }

    @Override
    public ASTNode[] getChildrenNode() {
        return new ASTNode[]{value, start, end};
    }

    @Override
    public <T> T accept(Visitor<T> v) {
        return v.visit(this);
    }

}
