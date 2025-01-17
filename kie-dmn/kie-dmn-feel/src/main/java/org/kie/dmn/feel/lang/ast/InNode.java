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
import org.kie.dmn.feel.lang.Type;
import org.kie.dmn.feel.lang.types.BuiltInType;
import org.kie.dmn.feel.runtime.Range;
import org.kie.dmn.feel.runtime.UnaryTest;
import org.kie.dmn.feel.util.BooleanEvalHelper;
import org.kie.dmn.feel.util.Msg;

public class InNode
        extends BaseNode {

    private BaseNode value;
    private BaseNode exprs;

    public InNode(ParserRuleContext ctx, BaseNode value, BaseNode exprs) {
        super( ctx );
        this.value = value;
        this.exprs = exprs;
    }

    public InNode(BaseNode value, BaseNode exprs, String text) {
        this.value = value;
        this.exprs = exprs;
        this.setText(text);
    }

    public BaseNode getValue() {
        return value;
    }

    public void setValue(BaseNode value) {
        this.value = value;
    }

    public BaseNode getExprs() {
        return exprs;
    }

    public void setExprs(BaseNode exprs) {
        this.exprs = exprs;
    }

    @Override
    public Boolean evaluate(EvaluationContext ctx) {
        if (exprs == null) return null;
        Object value = this.value.evaluate( ctx );
        Object expr = this.exprs.evaluate( ctx );
        if ( expr != null ) {
            if ( expr instanceof Iterable ) {
                // evaluate in the collection
                for ( Object e : ((Iterable) expr) ) {
                    // have to compare to Boolean.TRUE because in() might return null
                    if ( in( ctx, value, e ) == Boolean.TRUE ) {
                        return true;
                    }
                }
                return false;
            } else {
                // evaluate single entity
                return in( ctx, value, expr );
            }
        }
        ctx.notifyEvt( astEvent(Severity.ERROR, Msg.createMessage(Msg.IS_NULL, "Expression")) );
        return null;
    }

    private Boolean in(EvaluationContext ctx, Object value, Object expr) {
        // need to improve this to work with unary tests
        if ( expr == null ) {
            return value == expr;
        } else if ( expr instanceof UnaryTest ) {
            return ((UnaryTest) expr).apply( ctx, value );
        } else if ( expr instanceof Range ) {
            try {
                return ((Range) expr).includes(ctx.getFEELDialect(), value );
            } catch ( Exception e ) {
                ctx.notifyEvt( astEvent(Severity.ERROR, Msg.createMessage(Msg.EXPRESSION_IS_RANGE_BUT_VALUE_IS_NOT_COMPARABLE, value.toString(), expr.toString() ), e ) );
                return null;
            }
        } else if ( value != null ) {
            return BooleanEvalHelper.isEqualsStringCompare(value, expr);
        } else {
            // value == null, expr != null
            return Boolean.FALSE;
        }
    }

    @Override
    public Type getResultType() {
        return BuiltInType.BOOLEAN;
    }

    @Override
    public ASTNode[] getChildrenNode() {
        return new ASTNode[] { value, exprs };
    }

    @Override
    public <T> T accept(Visitor<T> v) {
        return v.visit(this);
    }
}
