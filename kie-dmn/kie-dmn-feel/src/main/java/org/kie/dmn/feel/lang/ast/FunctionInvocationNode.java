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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.antlr.v4.runtime.ParserRuleContext;
import org.kie.dmn.api.feel.runtime.events.FEELEvent.Severity;
import org.kie.dmn.feel.lang.EvaluationContext;
import org.kie.dmn.feel.lang.Type;
import org.kie.dmn.feel.runtime.FEELFunction;
import org.kie.dmn.feel.runtime.Range;
import org.kie.dmn.feel.runtime.UnaryTest;
import org.kie.dmn.feel.runtime.functions.AbstractCustomFEELFunction;
import org.kie.dmn.feel.util.Msg;
import org.kie.dmn.feel.util.MsgUtil;

public class FunctionInvocationNode
        extends BaseNode {


    private BaseNode name;
    private ListNode params;
    private TemporalConstantNode tcFolded; // this is NOT a child node intentionally.

    public FunctionInvocationNode(ParserRuleContext ctx, BaseNode name, ListNode params) {
        super( ctx );
        this.name = name;
        this.params = params;
    }

    public FunctionInvocationNode(BaseNode name, ListNode params, TemporalConstantNode tcFolded, String text) {
        this.name = name;
        this.params = params;
        this.tcFolded = tcFolded;
        this.setText(text);
    }

    public BaseNode getName() {
        return name;
    }

    public void setName(BaseNode name) {
        this.name = name;
    }

    public ListNode getParams() {
        return params;
    }

    public void setParams(ListNode params) {
        this.params = params;
    }

    public void setTcFolded(TemporalConstantNode tcFolded) {
        this.tcFolded = tcFolded;
    }

    public TemporalConstantNode getTcFolded() {
        return tcFolded;
    }

    @Override
    public Object evaluate(EvaluationContext ctx) {
        if (this.tcFolded != null) {
            return tcFolded.value;
        }
        FEELFunction function;
        Object value;
        if ( name instanceof NameRefNode ) {
            // simple name
            value = ctx.getValue( name.getText() );
        } else if (name instanceof QualifiedNameNode) {
            QualifiedNameNode qn = (QualifiedNameNode) name;
            String[] qns = qn.getPartsAsStringArray();
            value = ctx.getValue( qns );
        } else if (name instanceof PathExpressionNode) {
            PathExpressionNode pathExpressionNode = (PathExpressionNode) name;
            value = pathExpressionNode.evaluate(ctx);
        } else {
            value = name.evaluate(ctx);
        }
        if ( value instanceof FEELFunction ) {
            function = (FEELFunction) value;
            Object[] p = params.getElements().stream().map( e -> e.evaluate( ctx ) ).toArray( Object[]::new );
            List<String> functionNameParts;
            if (name instanceof NameRefNode) {
                functionNameParts = Collections.singletonList(name.getText());
            } else if (name instanceof QualifiedNameNode) {
                functionNameParts = Arrays.asList(((QualifiedNameNode) name).getPartsAsStringArray());
            } else if (name instanceof PathExpressionNode) {
                functionNameParts = Collections.singletonList(function.getName());
            } else {
                functionNameParts = Collections.emptyList();
            }
            Object result = invokeTheFunction(functionNameParts, function, ctx, p);
            return result;
        } else if( value instanceof UnaryTest ) {
            if( params.getElements().size() == 1 ) {
                Object p = params.getElements().get( 0 ).evaluate( ctx );
                return ((UnaryTest) value).apply( ctx, p );
            } else {
                ctx.notifyEvt( astEvent(Severity.ERROR, Msg.createMessage(Msg.CAN_T_INVOKE_AN_UNARY_TEST_WITH_S_PARAMETERS_UNARY_TESTS_REQUIRE_1_SINGLE_PARAMETER, params.getElements().size()) ) );
            }
        } else if (value instanceof Range) {
            if (params.getElements().size() == 1) {
                Object p = params.getElements().get(0).evaluate(ctx);
                return ((Range) value).includes(ctx.getFEELDialect(), p);
            } else {
                ctx.notifyEvt(astEvent(Severity.ERROR, Msg.createMessage(Msg.CAN_T_INVOKE_AN_UNARY_TEST_WITH_S_PARAMETERS_UNARY_TESTS_REQUIRE_1_SINGLE_PARAMETER, params.getElements().size())));
            }
        }
        ctx.notifyEvt(astEvent(Severity.ERROR, Msg.createMessage(Msg.CANNOT_INVOKE, MsgUtil.clipToString(value, 50))));
        return null;
    }

    private Object invokeTheFunction(List<String> names, FEELFunction fn, EvaluationContext ctx, Object[] params) {
        if (names.size() == 1 || names.isEmpty()) {
            if (fn instanceof AbstractCustomFEELFunction<?>) {
                AbstractCustomFEELFunction<?> ff = (AbstractCustomFEELFunction<?>) fn;
                if (ff.isProperClosure()) {
                    return ff.invokeReflectively(ff.getEvaluationContext(), params);
                }
            }
            return fn.invokeReflectively(ctx, params);
        } else {
            try {
                Object newRoot = ctx.getValue(names.get(0));
                ctx.enterFrame();
                try {
                    Map<String, Object> asMap = ((Map<String, Object>) newRoot);
                    asMap.forEach(ctx::setValue);
                } catch (ClassCastException e) {
                    ctx.setRootObject(newRoot); // gracefully handle the less common scenario.
                }
                return invokeTheFunction(names.subList(1, names.size()), fn, ctx, params);
            } finally {
                ctx.exitFrame();
            }
        }
    }

    @Override
    public Type getResultType() {
        return name.getResultType();
    }

    @Override
    public ASTNode[] getChildrenNode() {
        return new ASTNode[] { name, params };
    }

    @Override
    public <T> T accept(Visitor<T> v) {
        return v.visit(this);
    }
}
