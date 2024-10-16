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
package org.drools.core.phreak;

import org.drools.base.reteoo.AccumulateContextEntry;
import org.drools.base.rule.Accumulate;
import org.drools.core.common.PropagationContext;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.common.TupleSets;
import org.drools.core.reteoo.AccumulateNode;
import org.drools.core.reteoo.AccumulateNode.AccumulateMemory;
import org.drools.core.reteoo.AccumulateNode.GroupByContext;
import org.drools.core.reteoo.LeftTupleSink;
import org.drools.core.reteoo.TupleImpl;
import org.drools.core.util.index.TupleListWithContext;
import org.kie.api.runtime.rule.FactHandle;

public class PhreakGroupByNode extends PhreakAccumulateNode {

    @Override
    AccumulateNode.BaseAccumulation initAccumulationContext(AccumulateMemory am, ReteEvaluator reteEvaluator, Accumulate accumulate, TupleImpl leftTuple) {
        GroupByContext accContext = new GroupByContext();
        leftTuple.setContextObject( accContext );
        // A lot less is done here, compared to super, as it needs to be done on demand during the Group creation.
        return accContext;
    }

    @Override
    protected Object createResult( AccumulateNode accNode, Object key, Object result ) {
        Object[] array;
        if (accNode.getAccumulate().isMultiFunction()) {
            array = (Object[]) result;
            array[array.length-1] = key;
        } else {
            array = new Object[] {result, key};
        }
        return array;
    }

    @Override
    protected void evaluateResultConstraints(final AccumulateNode accNode,
                                             final LeftTupleSink sink,
                                             final Accumulate accumulate,
                                             final TupleImpl leftTuple,
                                             final PropagationContext context,
                                             final ReteEvaluator reteEvaluator,
                                             final AccumulateMemory memory,
                                             final AccumulateNode.BaseAccumulation accctx,
                                             final TupleSets trgLeftTuples,
                                             final TupleSets stagedLeftTuples) {

        PropagationContext propagationContext = accctx.getPropagationContext();
        accctx.setPropagationContext( null );

        GroupByContext groupByContext = (GroupByContext)accctx;

        for (TupleListWithContext<AccumulateContextEntry> tupleList = groupByContext.takeToPropagateList(); tupleList != null; tupleList = (TupleListWithContext<AccumulateContextEntry>) tupleList.getNext()) {
            AccumulateContextEntry contextEntry = tupleList.getContext();

            Object result = accumulate.getResult(memory.workingMemoryContext, contextEntry, leftTuple, reteEvaluator);

            propagateResult( accNode, sink, leftTuple, context, reteEvaluator, memory, trgLeftTuples, stagedLeftTuples,
                             contextEntry.getKey(), result, contextEntry, propagationContext, false ); // don't want to propagate null

            contextEntry.setToPropagate(false);
        }
    }

    @Override
    protected void reaccumulateForLeftTuple(final AccumulateNode accNode,
                                            final Accumulate accumulate,
                                            final TupleImpl leftTuple,
                                            final TupleImpl rightParent,
                                            final TupleImpl match,
                                            final ReteEvaluator reteEvaluator,
                                            final AccumulateMemory am,
                                            final AccumulateNode.BaseAccumulation accctx,
                                            final boolean reaccumulate) {
        GroupByContext groupByContext = (GroupByContext) accctx;

        if (match != null) {
            // re-accumulate just for the sub group
            TupleListWithContext<AccumulateContextEntry> tupleList = (TupleListWithContext<AccumulateContextEntry>) match.getMemory();
            tupleList.remove(match);

            if (reaccumulate) {
                // re-init function context for the group
                Object functionContext = accumulate.createFunctionContext();
                tupleList.getContext().setFunctionContext(functionContext);

                for (TupleImpl childMatch = tupleList.getFirst(); childMatch != null; childMatch = childMatch.getNext()) {
                    TupleImpl         rightTuple  = childMatch.getRightParent();
                    FactHandle childHandle = rightTuple.getFactHandle();
                    TupleImpl tuple       = leftTuple;
                    if (accNode.getRightInput().inputIsTupleToObjectNode()) {
                        // if there is a subnetwork, handle must be unwrapped
                        tuple = rightTuple;
                        childHandle = rightTuple.getFactHandleForEvaluation();
                    }

                    Object value = accumulate.accumulate(am.workingMemoryContext, tuple, childHandle,
                                                         groupByContext, tupleList, reteEvaluator);

                    match.setContextObject(value);
                }
            }
        } else {
            // re-accumulate all groups
            groupByContext.clear();
            super.reaccumulateForLeftTuple(accNode,
                                     accumulate,
                                     leftTuple,
                                     null,
                                     null,
                                     reteEvaluator,
                                     am,
                                     accctx,
                                     true);
        }
    }

    @Override
    protected void propagateDelete(TupleSets trgLeftTuples, TupleSets stagedLeftTuples, Object accctx ) {
        GroupByContext groupByContext = (GroupByContext)accctx;
        for ( TupleListWithContext<AccumulateContextEntry> tupleList : groupByContext.getGroups().values()) {
            super.propagateDelete(trgLeftTuples, stagedLeftTuples, tupleList.getContext());
        }
    }

    void postAccumulate(AccumulateNode accNode, Object accctx, TupleImpl match) {
        ((GroupByContext)accctx).addMatchOnLastTupleList(match);
    }

}
