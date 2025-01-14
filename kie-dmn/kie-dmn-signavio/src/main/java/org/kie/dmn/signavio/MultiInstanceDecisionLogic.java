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
package org.kie.dmn.signavio;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.thoughtworks.xstream.annotations.XStreamAlias;
import org.kie.dmn.api.core.DMNContext;
import org.kie.dmn.api.core.DMNModel;
import org.kie.dmn.api.core.DMNResult;
import org.kie.dmn.api.core.ast.DMNNode;
import org.kie.dmn.api.core.event.DMNRuntimeEventManager;
import org.kie.dmn.core.api.DMNExpressionEvaluator;
import org.kie.dmn.api.core.EvaluatorResult;
import org.kie.dmn.api.core.EvaluatorResult.ResultType;
import org.kie.dmn.core.ast.DMNBaseNode;
import org.kie.dmn.core.ast.DecisionNodeImpl;
import org.kie.dmn.core.ast.EvaluatorResultImpl;
import org.kie.dmn.core.compiler.DMNCompilerContext;
import org.kie.dmn.core.compiler.DMNCompilerImpl;
import org.kie.dmn.core.compiler.DecisionCompiler;
import org.kie.dmn.core.impl.DMNContextImpl;
import org.kie.dmn.core.impl.DMNModelImpl;
import org.kie.dmn.core.impl.DMNResultImpl;
import org.kie.dmn.core.internal.utils.DRGAnalysisUtils;
import org.kie.dmn.core.internal.utils.DRGAnalysisUtils.DRGDependency;
import org.kie.dmn.feel.FEEL;
import org.kie.dmn.feel.runtime.functions.AllFunction;
import org.kie.dmn.feel.runtime.functions.AnyFunction;
import org.kie.dmn.feel.runtime.functions.FEELFnResult;
import org.kie.dmn.feel.runtime.functions.MaxFunction;
import org.kie.dmn.feel.runtime.functions.MinFunction;
import org.kie.dmn.feel.runtime.functions.SumFunction;
import org.kie.dmn.feel.util.NumberEvalHelper;
import org.kie.dmn.model.api.DMNElement.ExtensionElements;

import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.stream.Collectors.toSet;

@XStreamAlias("MultiInstanceDecisionLogic")
public class MultiInstanceDecisionLogic {


    @XStreamAlias("iterationExpression")
    private String iterationExpression;
    
    @XStreamAlias("iteratorShapeId")
    private String iteratorShapeId;
    
    @XStreamAlias("aggregationFunction")
    private String aggregationFunction;
    
    @XStreamAlias("topLevelDecisionId")
    private String topLevelDecisionId;
    
    public String getIterationExpression() {
        return iterationExpression;
    }

    public String getIteratorShapeId() {
        return iteratorShapeId;
    }

    public String getAggregationFunction() {
        return aggregationFunction;
    }

    public String getTopLevelDecisionId() {
        return topLevelDecisionId;
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("MultiInstanceDecisionLogic [iterationExpression=").append(iterationExpression).append(", iteratorShapeId=").append(iteratorShapeId).append(", aggregationFunction=").append(aggregationFunction).append(", topLevelDecisionId=").append(topLevelDecisionId).append("]");
        return builder.toString();
    }
    
    public static class MultiInstanceDecisionNodeCompiler extends DecisionCompiler {

        private Optional<MultiInstanceDecisionLogic> getMIDL(DMNNode node) {
            if ( node instanceof DecisionNodeImpl ) {
                DecisionNodeImpl nodeImpl = (DecisionNodeImpl) node;
                ExtensionElements extElementsList = nodeImpl.getSource().getExtensionElements();
                if ( extElementsList != null && extElementsList.getAny() != null ) {
                    return extElementsList.getAny().stream()
                        .filter(MultiInstanceDecisionLogic.class::isInstance)
                        .map(MultiInstanceDecisionLogic.class::cast)
                        .findFirst();
                }
            }
            return Optional.empty();
        }
        
        @Override
        public boolean accept(DMNNode node) {
            return getMIDL(node).isPresent();
        }

        @Override
        public void compileEvaluator(DMNNode node, DMNCompilerImpl compiler, DMNCompilerContext ctx, DMNModelImpl model) {
            DecisionNodeImpl di = (DecisionNodeImpl) node;
            compiler.linkRequirements(model, di);
            
            MultiInstanceDecisionLogic midl =
                    getMIDL(node).orElseThrow(() -> new IllegalStateException("Node doesn't contain multi instance decision logic!" + node.toString()));
            
            // set the evaluator accordingly to Signavio logic.
            final MultiInstanceDecisionNodeEvaluator miEvaluator = new MultiInstanceDecisionNodeEvaluator(midl, model, di, ctx.getFeelHelper().newFEELInstance());
            di.setEvaluator(miEvaluator);
            
            compiler.addCallbackForModel((cCompiler, cCtx, cModel) -> {
                MIDDependenciesProcessor processor = new MIDDependenciesProcessor(midl, cModel);
                addRequiredDecisions(miEvaluator, processor);
                removeChildElementsFromIndex(cModel, processor);
            }, model);
        }

        private void addRequiredDecisions(MultiInstanceDecisionNodeEvaluator miEvaluator,
                                          MIDDependenciesProcessor processor) {
            processor
                .findAllDependencies()
                .stream()
                .filter(DecisionNodeImpl.class::isInstance)
                .map(DecisionNodeImpl.class::cast)
                .forEach(miEvaluator::addReqDecision);
        }

        private void removeChildElementsFromIndex(DMNModelImpl model, MIDDependenciesProcessor processor) {
            processor.findAllChildElements().forEach(model::removeDMNNodeFromIndexes);
        }

    }

    public static class MIDDependenciesProcessor {

        private final MultiInstanceDecisionLogic mid;
        private final DMNModel model;

        public MIDDependenciesProcessor(MultiInstanceDecisionLogic mid, DMNModel model) {
            this.mid = mid;
            this.model = model;
        }

        public Collection<DMNNode> findAllChildElements() {
            return new HashSet<>(processNode(topLevelDecision()));
        }

        public Collection<DMNNode> findAllDependencies() {
            return DRGAnalysisUtils
                .dependencies(model, topLevelDecision())
                .stream()
                .map(DRGDependency::getDependency)
                .collect(toSet());
        }

        private Set<DMNNode> processNode(DMNBaseNode currentNode) {
            if (currentNodeIsTheIterator(currentNode)) {
                return singleton(currentNode);
            }

            Set<DMNNode> pathToIterator = findPathToIterator(currentNode);
            if (pathToIterator.isEmpty()) {
                return emptySet();
            }
            return extendPathBy(currentNode, pathToIterator);
        }

        private Set<DMNNode> extendPathBy(DMNBaseNode node, Set<DMNNode> pathToIterator) {
            Set<DMNNode> extendedPath = new HashSet<>(pathToIterator);
            extendedPath.add(node);
            return extendedPath;
        }

        private Set<DMNNode> findPathToIterator(DMNBaseNode currentNode) {
            return currentNode
                .getDependencies()
                .values()
                .stream()
                .map(DMNBaseNode.class::cast)
                .map(this::processNode)
                .flatMap(Collection::stream)
                .collect(toSet());
        }

        private boolean currentNodeIsTheIterator(DMNBaseNode node) {
            return mid.getIteratorShapeId().equals(node.getId());
        }

        private DMNBaseNode topLevelDecision() {
            return (DMNBaseNode) model.getDecisionById(mid.getTopLevelDecisionId());
        }

    }

    /**
     * Implements the Multi instance Decision node of Signavio as a DMNExpressionEvaluator
     */
    public static class MultiInstanceDecisionNodeEvaluator implements DMNExpressionEvaluator {
        
        private MultiInstanceDecisionLogic mi;
        private DMNModelImpl model;
        private DecisionNodeImpl di;
        private String contextIteratorName;
        private DecisionNodeImpl topLevelDecision;
        private List<DecisionNodeImpl> reqDecisions = new ArrayList<>();
        private final FEEL feel;
        
        public MultiInstanceDecisionNodeEvaluator(MultiInstanceDecisionLogic mi, DMNModelImpl model, DecisionNodeImpl di, FEEL feel) {
            this.mi = mi;
            this.model = model;
            this.di = di;
            this.feel = feel;
            contextIteratorName = model.getInputById( mi.iteratorShapeId ).getName();
            topLevelDecision = (DecisionNodeImpl) model.getDecisionById(mi.topLevelDecisionId);
        }
        
        public void addReqDecision(DecisionNodeImpl reqDecision) {
            this.reqDecisions.add(reqDecision);
        }

        @Override
        public EvaluatorResult evaluate(DMNRuntimeEventManager eventManager, DMNResult dmnr) {
            DMNResultImpl result = (DMNResultImpl) dmnr;
            DMNContext previousContext = result.getContext();
            DMNContextImpl dmnContext = (DMNContextImpl) previousContext.clone();
            result.setContext( dmnContext );
            
            List<? super Object> invokationResults = new ArrayList<>();
            
            try {
                Object cycleOnRaw = feel.evaluate(mi.iterationExpression, dmnContext.getAll());
                Collection<?> cycleOn = null;
                if ( cycleOnRaw instanceof Collection ) {
                    cycleOn = (Collection<?>) cycleOnRaw;
                } else {
                    cycleOn = Collections.singletonList(cycleOnRaw);
                }
                for ( Object cycledValue : cycleOn ) {
                    DMNContext nonCycledContext = result.getContext();
                    DMNContextImpl cyclingContext = (DMNContextImpl) nonCycledContext.clone();
                    result.setContext( cyclingContext );
                    
                    cyclingContext.set(contextIteratorName, cycledValue);
                    for (DecisionNodeImpl reqDecision : this.reqDecisions) {
                        Object subResult = reqDecision.getEvaluator().evaluate(eventManager, result).getResult();
                        cyclingContext.set(reqDecision.getName(), subResult);
                    }
                    Object evaluationResult = topLevelDecision.getEvaluator().evaluate(eventManager, result).getResult();
                    invokationResults.add(evaluationResult);
                    
                    result.setContext( nonCycledContext );
                }
            } finally {
                result.setContext( previousContext );
            }
            
            FEELFnResult<?> r;
            switch (mi.aggregationFunction) {
                case "SUM":
                    r = SumFunction.INSTANCE.invoke(invokationResults);
                    break;
                case "MIN":
                    r = MinFunction.INSTANCE.invoke(invokationResults);
                    break;
                case "MAX":
                    r = MaxFunction.INSTANCE.invoke(invokationResults);
                    break;
                case "COUNT":
                    r = FEELFnResult.ofResult(NumberEvalHelper.getBigDecimalOrNull(invokationResults.size()));
                    break;
                case "ALLTRUE":
                    r = AllFunction.INSTANCE.invoke(invokationResults);
                    break;
                case "ANYTRUE":
                    r = AnyFunction.INSTANCE.invoke(invokationResults);
                    break;
                case "ALLFALSE":
                    FEELFnResult<Boolean> anyResult = AnyFunction.INSTANCE.invoke(invokationResults);
                    r = anyResult.map(b -> !b);
                    break;
                case "COLLECT":
                default:
                    r = FEELFnResult.ofResult(invokationResults);
                    break;
            }

            return new EvaluatorResultImpl(r.getOrElseThrow(e -> new RuntimeException(e.toString())), ResultType.SUCCESS);
        }
        
    }

}
