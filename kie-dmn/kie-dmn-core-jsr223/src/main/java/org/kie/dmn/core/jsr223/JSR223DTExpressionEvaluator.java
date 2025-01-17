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
package org.kie.dmn.core.jsr223;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import javax.script.ScriptException;

import org.drools.model.functions.Function1;
import org.kie.dmn.api.core.DMNResult;
import org.kie.dmn.api.core.DMNRuntime;
import org.kie.dmn.api.core.ast.DMNNode;
import org.kie.dmn.api.core.event.DMNRuntimeEventManager;
import org.kie.dmn.api.feel.runtime.events.FEELEvent;
import org.kie.dmn.api.feel.runtime.events.FEELEventListener;
import org.kie.dmn.core.api.DMNExpressionEvaluator;
import org.kie.dmn.api.core.EvaluatorResult;
import org.kie.dmn.api.core.EvaluatorResult.ResultType;
import org.kie.dmn.core.ast.DMNDTExpressionEvaluator;
import org.kie.dmn.core.ast.DMNDTExpressionEvaluator.EventResults;
import org.kie.dmn.core.ast.EvaluatorResultImpl;
import org.kie.dmn.core.compiler.alphanetbased.Results;
import org.kie.dmn.core.impl.DMNResultImpl;
import org.kie.dmn.core.impl.DMNRuntimeEventManagerUtils;
import org.kie.dmn.feel.lang.EvaluationContext;
import org.kie.dmn.feel.lang.FEELDialect;
import org.kie.dmn.feel.runtime.decisiontables.HitPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JSR223DTExpressionEvaluator implements DMNExpressionEvaluator {
    private static Logger LOG = LoggerFactory.getLogger( JSR223DTExpressionEvaluator.class );

    private final DMNNode node;
    private final org.kie.dmn.model.api.DecisionTable dt;
    private final List<JSR223LiteralExpressionEvaluator> ins;
    private final List<JSR223Rule> rules;

    private final HitPolicy hitPolicy;
    private final org.kie.dmn.feel.runtime.decisiontables.DecisionTable decisionTableModel;

    public JSR223DTExpressionEvaluator(DMNNode node, org.kie.dmn.model.api.DecisionTable dt, List<JSR223LiteralExpressionEvaluator> ins, List<JSR223Rule> rules) {
        this.node = node;
        this.dt = dt;
        this.ins = ins;
        this.rules = rules;
        String policy = dt.getHitPolicy().value() + (dt.getAggregation() != null ? " " + dt.getAggregation().value() : "");
        this.hitPolicy = HitPolicy.fromString(policy);
        decisionTableModel = new JSR223WrappingDecisionTable(node.getName());
    }

    @Override
    public EvaluatorResult evaluate(DMNRuntimeEventManager dmrem, DMNResult dmnr) {
        final List<FEELEvent> events = new ArrayList<>();
        DMNResultImpl result = (DMNResultImpl) dmnr;
        EventResults r = null;
        try {
            DMNRuntimeEventManagerUtils.fireBeforeEvaluateDecisionTable( dmrem, node.getName(), node.getName(), dt.getId(), result );
            Map<String, Object> contextValues = result.getContext().getAll();

            List<Object> params = new ArrayList<>(ins.size());
            for (JSR223LiteralExpressionEvaluator in : ins) {
                params.add(in.getEval().eval(contextValues));
            }
            Results results = new Results();
            for (int rIndex = 0; rIndex < rules.size(); rIndex++) {
                final JSR223Rule rule = rules.get(rIndex);
                boolean match = true;
                for (int i = 0; i < params.size(); i++) {
                    JSR223ScriptEngineEvaluator test = rule.ruleTests.get(i);
                    match &= test.test(params.get(i), contextValues);
                    if (!match) {
                        break;
                    }
                }
                if (match) {
                    results.addResult(rIndex, "", new Fn(rule.outLiteralExpr.getEval()));
                }
            }
            Object dtr = results.applyHitPolicy(new JSR223WrappingEC(contextValues, events), hitPolicy, decisionTableModel);

            r = DMNDTExpressionEvaluator.processEvents( events, dmrem, result, node );
            return new EvaluatorResultImpl( dtr, r.hasErrors ? ResultType.FAILURE : ResultType.SUCCESS );
        } catch (ScriptException e) {
            LOG.debug("failed evaluate", e);
            throw new RuntimeException(e);
        } finally {
            DMNRuntimeEventManagerUtils.fireAfterEvaluateDecisionTable( dmrem, node.getName(), node.getName(), dt.getId(), result,
                                                                        (r != null ? r.matchedRules : null),
                                                                        (r != null ? r.fired : null),
                                                                        (r != null ? r.matchedIds : null),
                                                                        (r != null ? r.firedIds : null));
        }
    }
    
    public static class JSR223Rule {
        public final List<JSR223ScriptEngineEvaluator> ruleTests;
        public final JSR223LiteralExpressionEvaluator outLiteralExpr;
        public JSR223Rule(List<JSR223ScriptEngineEvaluator> ruleTests, JSR223LiteralExpressionEvaluator outLiteralExpr) {
            this.ruleTests = ruleTests;
            this.outLiteralExpr = outLiteralExpr;
        }
    }

    @SuppressWarnings("serial")
    public static class Fn implements Function1<EvaluationContext, Object> {
        
        private final JSR223ScriptEngineEvaluator eval;

        public Fn(JSR223ScriptEngineEvaluator eval) {
            this.eval = eval;
        }

        @Override
        public Object apply(EvaluationContext ctx) {
            try {
                return eval.eval(ctx.getAllValues());
            } catch (ScriptException e) {
                LOG.debug("failed Fn eval", e);
            }
            return null;
        }
        
    }
    
    private static class JSR223WrappingDecisionTable implements org.kie.dmn.feel.runtime.decisiontables.DecisionTable {

        private final String name;
        
        public JSR223WrappingDecisionTable(String name) {
            super();
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public List<? extends OutputClause> getOutputs() {
            return Collections.emptyList();
        }
        
    }
    
    private static class JSR223WrappingEC implements EvaluationContext {
        
        private final Map<String, Object> values;
        private final List<FEELEvent> events;
        // Defaulting FEELDialect to FEEL
        private final FEELDialect dialect = FEELDialect.FEEL;

        public JSR223WrappingEC(Map<String, Object> values, List<FEELEvent> events) {
            this.values = Collections.unmodifiableMap(values);
            this.events = events;
        }

        @Override
        public void enterFrame() {
            throw new UnsupportedOperationException("not implemented for this impl.");
        }

        @Override
        public void exitFrame() {
            throw new UnsupportedOperationException("not implemented for this impl.");
        }

        @Override
        public EvaluationContext current() {
            throw new UnsupportedOperationException("not implemented for this impl.");
        }

        @Override
        public void setValue(String name, Object value) {
            throw new UnsupportedOperationException("not implemented for this impl.");
        }

        @Override
        public Object getValue(String name) {
            return values.get(name);
        }

        @Override
        public Object getValue(String[] name) {
            throw new UnsupportedOperationException("not implemented for this impl.");
        }

        @Override
        public boolean isDefined(String name) {
            return values.containsKey(name);
        }

        @Override
        public boolean isDefined(String[] name) {
            throw new UnsupportedOperationException("not implemented for this impl.");
        }

        @Override
        public Map<String, Object> getAllValues() {
            return values;
        }

        @Override
        public DMNRuntime getDMNRuntime() {
            throw new UnsupportedOperationException("not implemented for this impl.");
        }

        @Override
        public ClassLoader getRootClassLoader() {
            throw new UnsupportedOperationException("not implemented for this impl.");
        }

        @Override
        public void notifyEvt(Supplier<FEELEvent> event) {
            events.add(event.get());
        }

        @Override
        public Collection<FEELEventListener> getListeners() {
            throw new UnsupportedOperationException("not implemented for this impl.");
        }

        @Override
        public void setRootObject(Object v) {
            throw new UnsupportedOperationException("not implemented for this impl.");
        }

        @Override
        public Object getRootObject() {
            throw new UnsupportedOperationException("not implemented for this impl.");
        }

        @Override
        public FEELDialect getFEELDialect() {
            return dialect;
        }
    }

}
