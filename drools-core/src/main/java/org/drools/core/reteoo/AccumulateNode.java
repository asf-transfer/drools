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
package org.drools.core.reteoo;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.drools.base.base.ClassObjectType;
import org.drools.base.base.ObjectType;
import org.drools.base.common.NetworkNode;
import org.drools.base.reteoo.AccumulateContextEntry;
import org.drools.base.reteoo.BaseTuple;
import org.drools.base.reteoo.NodeTypeEnums;
import org.drools.base.rule.Accumulate;
import org.drools.base.rule.Declaration;
import org.drools.base.rule.Pattern;
import org.drools.base.rule.TypeDeclaration;
import org.drools.base.rule.accessor.Accumulator;
import org.drools.base.rule.constraint.AlphaNodeFieldConstraint;
import org.drools.core.RuleBaseConfiguration;
import org.drools.core.common.BetaConstraints;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.Memory;
import org.drools.core.common.PropagationContext;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.impl.InternalRuleBase;
import org.drools.core.phreak.PhreakAccumulateNode;
import org.drools.core.reteoo.builder.BuildContext;
import org.drools.core.util.AbstractLinkedListNode;
import org.drools.core.util.index.TupleList;
import org.drools.core.util.index.TupleListWithContext;
import org.drools.util.bitmask.BitMask;

/**
 * AccumulateNode
 * A beta node capable of doing accumulate logic.
 *
 *
 */
public class AccumulateNode extends BetaNode {

    private static final long          serialVersionUID = 510L;

    protected Accumulate                 accumulate;
    protected AlphaNodeFieldConstraint[] resultConstraints;
    protected BetaConstraints            resultBinder;

    public AccumulateNode() {

    }

    public AccumulateNode(final int id,
                          final LeftTupleSource leftInput,
                          final RightInputAdapterNode rightInput,
                          final AlphaNodeFieldConstraint[] resultConstraints,
                          final BetaConstraints sourceBinder,
                          final BetaConstraints resultBinder,
                          final Accumulate accumulate,
                          final BuildContext context) {
        super( id,
               leftInput,
               rightInput,
               sourceBinder,
               context );
        this.setObjectCount(leftInput.getObjectCount() + 1); // 'accumulate' node increases the object count
        this.resultBinder = resultBinder;
        this.resultBinder.init( context, getType() );
        this.resultConstraints = resultConstraints;
        this.accumulate = accumulate;
        this.tupleMemoryEnabled = context.isTupleMemoryEnabled();

        addAccFunctionDeclarationsToLeftMask( context.getRuleBase(), leftInput, accumulate );

        hashcode = this.leftInput.hashCode() ^
                   this.rightInput.hashCode() ^
                   this.accumulate.hashCode() ^
                   this.resultBinder.hashCode() ^
                   Arrays.hashCode( this.resultConstraints );

    }

    private void addAccFunctionDeclarationsToLeftMask(InternalRuleBase ruleBase, LeftTupleSource leftInput, Accumulate accumulate) {
        BitMask leftMask = getInferredMask();
        ObjectType leftObjectType = leftInput.getObjectType();
        if (leftObjectType instanceof ClassObjectType ) {
            TypeDeclaration typeDeclaration = ruleBase.getExactTypeDeclaration(((ClassObjectType) leftObjectType).getClassType() );
            if (typeDeclaration != null && typeDeclaration.isPropertyReactive()) {
                List<String> accessibleProperties = typeDeclaration.getAccessibleProperties();
                for ( Declaration decl : accumulate.getRequiredDeclarations() ) {
                    if ( leftObjectType.equals( decl.getPattern().getObjectType() ) ) {
                        leftMask = leftMask.setAll( decl.getPattern().getPositiveWatchMask(accessibleProperties) );
                    }
                }
            }
        }
        setInferredMask(leftMask);
    }

    @Override
    protected ObjectType getObjectTypeForPropertyReactivity( LeftInputAdapterNode leftInput, Pattern pattern ) {
        return pattern != null && rightInput.inputIsTupleToObjectNode() ?
               pattern.getObjectType() :
               leftInput.getParentObjectSource().getObjectTypeNode().getObjectType();
    }

    public int getType() {
        return NodeTypeEnums.AccumulateNode;
    }

    public Accumulate getAccumulate() {
        return this.accumulate;
    }       

    public AlphaNodeFieldConstraint[] getResultConstraints() {
        return resultConstraints;
    }

    public BetaConstraints getResultBinder() {
        return resultBinder;
    }

    public InternalFactHandle createResultFactHandle(final PropagationContext context,
                                                     final ReteEvaluator reteEvaluator,
                                                     final TupleImpl leftTuple,
                                                     final Object result) {
        InternalFactHandle handle = null;
        if ( context.getReaderContext() != null ) {
            handle = context.getReaderContext().createAccumulateHandle( context.getEntryPoint(), reteEvaluator, leftTuple, result, getId() );
        }
        if (handle == null) {
            handle = reteEvaluator.createFactHandle( result,
                                                     null, // no need to retrieve the ObjectTypeConf, acc result is never an event or a trait
                                                     null );
        }
        return handle;
    }

    @Override
    public void doAttach( BuildContext context ) {
        super.doAttach( context );
    }

    protected int calculateHashCode() {
        return 0;
    }

    @Override
    public boolean equals( final Object object ) {
        if (this == object) {
            return true;
        }

        if (((NetworkNode)object).getType() != NodeTypeEnums.AccumulateNode || this.hashCode() != object.hashCode()) {
            return false;
        }

        AccumulateNode other = (AccumulateNode) object;
        return this.leftInput.getId() == other.leftInput.getId() && this.rightInput.equals(other.rightInput)&&
               this.constraints.equals( other.constraints ) &&
               this.accumulate.equals( other.accumulate ) &&
               this.resultBinder.equals( other.resultBinder ) &&
               Arrays.equals( this.resultConstraints, other.resultConstraints );
    }

    /**
     * Creates a BetaMemory for the BetaNode's memory.
     */
    public Memory createMemory(final RuleBaseConfiguration config, ReteEvaluator reteEvaluator) {
        BetaMemory betaMemory = (BetaMemory) this.constraints.createBetaMemory(config, NodeTypeEnums.AccumulateNode);
        AccumulateMemory memory = this.accumulate.isMultiFunction() ?
                                  new MultiAccumulateMemory(betaMemory, this.accumulate.getAccumulators()) :
                                  new SingleAccumulateMemory(betaMemory, this.accumulate.getAccumulators()[0]);

        memory.workingMemoryContext = this.accumulate.createWorkingMemoryContext();
        memory.resultsContext = this.resultBinder.createContext();
        return memory;
    }

    public static abstract class AccumulateMemory extends AbstractLinkedListNode<Memory>
        implements
        SegmentNodeMemory {

        public        Object            workingMemoryContext;
        private final BetaMemory<?> betaMemory;
        public        Object            resultsContext;

        protected AccumulateMemory( BetaMemory betaMemory) {
            this.betaMemory = betaMemory;
        }

        public BetaMemory getBetaMemory() {
            return this.betaMemory;
        }

        public int getNodeType() {
            return NodeTypeEnums.AccumulateNode;
        }

        public SegmentMemory getSegmentMemory() {
            return betaMemory.getSegmentMemory();
        }

        public void setSegmentMemory(SegmentMemory segmentMemory) {
            betaMemory.setSegmentMemory(segmentMemory);
        }

        public abstract void reset();

        @Override
        public long getNodePosMaskBit() {
            return betaMemory.getNodePosMaskBit();
        }

        @Override
        public void setNodePosMaskBit( long segmentPos ) {
            betaMemory.setNodePosMaskBit( segmentPos );
        }

        @Override
        public void setNodeDirtyWithoutNotify() {
            betaMemory.setNodeDirtyWithoutNotify();
        }

        @Override
        public void setNodeCleanWithoutNotify() {
            betaMemory.setNodeCleanWithoutNotify();
        }
    }

    public static class SingleAccumulateMemory extends AccumulateMemory {

        private final Accumulator accumulator;

        public SingleAccumulateMemory(BetaMemory betaMemory, Accumulator accumulator) {
            super( betaMemory );
            this.accumulator = accumulator;
        }

        public void reset() {
            getBetaMemory().reset();
            workingMemoryContext = this.accumulator.createWorkingMemoryContext();
        }
    }

    public static class MultiAccumulateMemory extends AccumulateMemory {

        private final Accumulator[] accumulators;

        public MultiAccumulateMemory(BetaMemory betaMemory, Accumulator[] accumulators) {
            super( betaMemory );
            this.accumulators = accumulators;
        }

        public void reset() {
            getBetaMemory().reset();
            workingMemoryContext = new Object[ this.accumulators.length ];
            for( int i = 0; i < this.accumulators.length; i++ ) {
                ((Object[])workingMemoryContext)[i] = this.accumulators[i].createWorkingMemoryContext();
            }
        }
    }

    public interface BaseAccumulation {
        PropagationContext getPropagationContext();

        void setPropagationContext(PropagationContext propagationContext);
    }


    public static class AccumulateContext extends AccumulateContextEntry implements BaseAccumulation {
        private PropagationContext  propagationContext;

        public AccumulateContext() {
            super(null);
        }

        public PropagationContext getPropagationContext() {
            return propagationContext;
        }

        public void setPropagationContext(PropagationContext propagationContext) {
            this.propagationContext = propagationContext;
        }
    }

    public static class GroupByContext implements BaseAccumulation {
        private PropagationContext                                        propagationContext;
        private Map<Object, TupleListWithContext<AccumulateContextEntry>> groupsMap = new HashMap<>();
        private TupleListWithContext<AccumulateContextEntry>              lastTupleList;
        private TupleListWithContext<AccumulateContextEntry>              toPropagateList;

        public PropagationContext getPropagationContext() {
            return propagationContext;
        }

        public void setPropagationContext(PropagationContext propagationContext) {
            this.propagationContext = propagationContext;
        }

        public Map<Object, TupleListWithContext<AccumulateContextEntry>> getGroups() {
            return groupsMap;
        }

        public TupleListWithContext<AccumulateContextEntry> getGroup(Object workingMemoryContext, Accumulate accumulate, BaseTuple leftTuple,
                                                          Object key, ReteEvaluator reteEvaluator) {
            return groupsMap.computeIfAbsent(key, k -> {
                AccumulateContextEntry entry = new AccumulateContextEntry(key);
                entry.setFunctionContext( accumulate.init(workingMemoryContext, entry, accumulate.createFunctionContext(), leftTuple, reteEvaluator) );
                PhreakAccumulateNode.initContext(workingMemoryContext, reteEvaluator, accumulate, leftTuple, entry);
                return new TupleListWithContext(entry);
            });
        }

        public void removeGroup(Object key) {
            groupsMap.remove(key);
        }

        public void moveToPropagateTupleList(TupleListWithContext<AccumulateContextEntry> list) {
            this.lastTupleList = list;
            if ( list.getContext().isToPropagate()) {
                return;
            }

            // add list to head
            list.setNext(toPropagateList);
            toPropagateList = list;

            list.getContext().setToPropagate(true);
        }

        public TupleListWithContext<AccumulateContextEntry> takeToPropagateList() {
            TupleListWithContext<AccumulateContextEntry> list = toPropagateList;
            toPropagateList = null;
            return list;
        }

        public void addMatchOnLastTupleList(TupleImpl match) {
            lastTupleList.add(match);
            lastTupleList.getContext().setEmpty( false );
        }

        public void clear() {
            for (TupleList list : groupsMap.values()) {
                for ( TupleImpl tuple = list.getFirst(); list.getFirst() != null; tuple = list.getFirst()) {
                    list.remove(tuple);
                    tuple.setContextObject(null);
                }
            }
            groupsMap.clear();
            toPropagateList = null;
            lastTupleList = null;
        }
    }
}
