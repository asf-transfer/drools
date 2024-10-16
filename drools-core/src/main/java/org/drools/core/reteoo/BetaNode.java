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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

import org.drools.base.common.NetworkNode;
import org.drools.base.common.RuleBasePartitionId;
import org.drools.base.reteoo.BaseTerminalNode;
import org.drools.base.reteoo.NodeTypeEnums;
import org.drools.base.rule.IndexableConstraint;
import org.drools.base.util.index.IndexUtil;
import org.drools.core.RuleBaseConfiguration;
import org.drools.core.common.BetaConstraints;
import org.drools.core.common.DefaultFactHandle;
import org.drools.core.common.DoubleBetaConstraints;
import org.drools.core.common.DoubleNonIndexSkipBetaConstraints;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.Memory;
import org.drools.core.common.MemoryFactory;
import org.drools.core.common.PropagationContext;
import org.drools.core.common.QuadroupleBetaConstraints;
import org.drools.core.common.QuadroupleNonIndexSkipBetaConstraints;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.common.SingleBetaConstraints;
import org.drools.core.common.SingleNonIndexSkipBetaConstraints;
import org.drools.core.common.TripleBetaConstraints;
import org.drools.core.common.TripleNonIndexSkipBetaConstraints;
import org.drools.core.common.UpdateContext;
import org.drools.core.phreak.DetachedTuple;
import org.drools.core.reteoo.AccumulateNode.AccumulateMemory;
import org.drools.core.reteoo.builder.BuildContext;
import org.drools.base.rule.constraint.BetaConstraint;
import org.drools.core.util.FastIterator;
import org.kie.api.definition.rule.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.drools.core.phreak.TupleEvaluationUtil.flushLeftTupleIfNecessary;

public abstract class BetaNode extends LeftTupleSource
        implements
        LeftTupleSinkNode,
        MemoryFactory {

    protected static final Logger log = LoggerFactory.getLogger(BetaNode.class);
    protected static final boolean isLogTraceEnabled = log.isTraceEnabled();

    protected RightInputAdapterNode rightInput;

    protected BetaConstraints constraints;

    private LeftTupleSinkNode previousTupleSinkNode;
    private LeftTupleSinkNode nextTupleSinkNode;

    protected boolean objectMemory = true; // hard coded to true

    protected boolean tupleMemoryEnabled;

    protected boolean indexedUnificationJoin;

    private Collection<String> leftListenedProperties;

    private boolean indexable;



    // ------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------
    public BetaNode() {

    }

    /**
     * Constructs a <code>BetaNode</code> using the specified <code>BetaNodeBinder</code>.
     *
     * @param leftInput
     *            The left input <code>TupleSource</code>.
     * @param rightInput
     *            The right input <code>ObjectSource</code>.
     */
    protected BetaNode(final int id,
             final LeftTupleSource leftInput,
             final RightInputAdapterNode rightInput,
             final BetaConstraints constraints,
             final BuildContext context) {
        super(id, context);
        rightInput.setBetaNode(this);
        setLeftTupleSource(leftInput);
        this.rightInput = rightInput;

        setConstraints(constraints);

        if (this.constraints == null) {
            throw new RuntimeException("cannot have null constraints, must at least be an instance of EmptyBetaConstraints");
        }

        this.constraints.init(context, getType());
        this.constraints.registerEvaluationContext(context);

        initMasks(context);

        setStreamMode( context.isStreamMode() && getObjectTypeNode(context).getObjectType().isEvent() );

        this.hashcode = calculateHashCode();

        this.indexable = this.constraints.getConstraints().length > 0 && IndexUtil.isIndexable(this.constraints.getConstraints()[0], getType(), context.getRuleBase().getConfiguration());
    }

    private ObjectTypeNode getObjectTypeNode(BuildContext context) {
        ObjectTypeNode otn = getObjectTypeNode();
        // getObjectTypeNode() can return null if the BetaNode is in a subnetwork
        return otn != null ? otn : context.getRootObjectTypeNode();
    }

    @Override
    protected void initDeclaredMask(BuildContext context) {
        rightInput.initDeclaredMask(context);

        super.initDeclaredMask(context);
    }

    @Override
    public void setPartitionId(BuildContext context, RuleBasePartitionId partitionId ) {
        RuleBasePartitionId parentId = rightInput.getParent().getPartitionId();
        if (parentId != RuleBasePartitionId.MAIN_PARTITION && !parentId.equals(partitionId)) {
            this.partitionId = parentId;
            rightInput.setPartitionId(context, this.partitionId);
            context.setPartitionId( this.partitionId );
            leftInput.setSourcePartitionId( context, this.partitionId );
        } else {
            this.partitionId = partitionId;
        }
    }

    @Override
    protected void setLeftListenedProperties(Collection<String> leftListenedProperties) {
        this.leftListenedProperties = leftListenedProperties;
    }

    @Override
    protected void initInferredMask() {
        super.initInferredMask();
        rightInput.initInferredMask();
    }

    private void setUnificationJoin() {
        // If this join uses a indexed, ==, constraint on a query parameter then set indexedUnificationJoin to true
        // This ensure we get the correct iterator
        BetaConstraint[] betaCconstraints = this.constraints.getConstraints();
        if ( betaCconstraints.length > 0 ) {
            BetaConstraint c = betaCconstraints[0];
            if ( indexable && ((IndexableConstraint) c).isUnification() ) {
                if ( this.constraints instanceof SingleBetaConstraints ) {
                    setConstraints( new SingleNonIndexSkipBetaConstraints( (SingleBetaConstraints) this.constraints ) );
                } else if ( this.constraints instanceof DoubleBetaConstraints ) {
                    setConstraints( new DoubleNonIndexSkipBetaConstraints( (DoubleBetaConstraints) this.constraints ) );
                } else if ( this.constraints instanceof TripleBetaConstraints ) {
                    setConstraints( new TripleNonIndexSkipBetaConstraints( (TripleBetaConstraints) this.constraints ) );
                } else if ( this.constraints instanceof QuadroupleBetaConstraints ) {
                    setConstraints( new QuadroupleNonIndexSkipBetaConstraints( (QuadroupleBetaConstraints) this.constraints ) );
                }

                this.indexedUnificationJoin = true;
            }
        }
    }

    public RightInputAdapterNode getRightInput() {
        return this.rightInput;
    }

    public boolean inputIsTupleToObjectNode() {
        return rightInput.inputIsTupleToObjectNode();
    }

    public FastIterator<TupleImpl> getRightIterator(TupleMemory memory) {
        if ( this.indexedUnificationJoin ) {
            return memory.fullFastIterator();
        } else {
            return memory.fastIterator();
        }
    }

    public RightTuple getFirstRightTuple(final TupleImpl leftTuple,
                                         final TupleMemory memory,
                                         final FastIterator<TupleImpl> it) {
        if ( this.indexedUnificationJoin ) {
            return (RightTuple) it.next(null);
        } else {
            return (RightTuple) memory.getFirst(leftTuple);
        }
    }

    public FastIterator<TupleImpl> getLeftIterator(TupleMemory memory) {
        if (rightInput.inputIsTupleToObjectNode()) {
            return FastIterator.NullFastIterator.INSTANCE;
        } else {
            if ( this.indexedUnificationJoin ) {
                return memory.fullFastIterator();
            } else {
                return memory.fastIterator();
            }
        }
    }

    public TupleImpl getFirstLeftTuple(final TupleImpl rightTuple,
                                       final TupleMemory memory,
                                       final FastIterator<TupleImpl> it) {
        if (rightInput.inputIsTupleToObjectNode()) {
            return getStartTuple(rightTuple);
        } else {
            if ( this.indexedUnificationJoin ) {
                return it.next(null );
            } else {
                return memory.getFirst(rightTuple);
            }
        }
    }

    public TupleImpl getStartTuple(TupleImpl lt) {

        LeftTupleSource startTupleSource = ((TupleToObjectNode) rightInput.getParent()).getStartTupleSource();

        // Iterate find start
        while (lt.getIndex() != startTupleSource.getPathIndex()-1) { // -1 as it needs the split node, not the start of the branch
            lt = lt.getLeftParent();
        }

        // Now iterate to find peer. It is not guaranteed that the next node is the correct one, see testSubnetworkSharingWith2Sinks
        while (lt.getSink() != this) {
            lt = lt.getPeer();
        }

        return lt;
    }

    public static TupleImpl getFirstTuple(TupleMemory memory, FastIterator<TupleImpl> it) {
        if ( !memory.isIndexed() ) {
            return memory.getFirst( null );
        } else {
            return it.next( null );
        }
    }

    public boolean isIndexedUnificationJoin() {
        return indexedUnificationJoin;
    }

    public BetaConstraint[] getConstraints() {
        return constraints.getConstraints();
    }

    public BetaConstraints getRawConstraints() {
        return this.constraints;
    }
    
    private void setConstraints(BetaConstraints constraints) {
        this.constraints = (BetaConstraints) constraints.cloneIfInUse();
    }

    public void networkUpdated(UpdateContext updateContext) {
        updateContext.startVisitNode( leftInput );
        rightInput.networkUpdated(updateContext);
        updateContext.endVisit();
        if ( !updateContext.isVisiting( leftInput ) ) {
            leftInput.networkUpdated( updateContext );
        }
    }

    public List<String> getRules() {
        final List<String> list = new ArrayList<>();

        final LeftTupleSink[] sinks = this.sink.getSinks();
        for (LeftTupleSink sink1 : sinks) {
            if (sink1.getType() == NodeTypeEnums.RuleTerminalNode) {
                list.add(((RuleTerminalNode) sink1).getRule().getName());
            } else if (NodeTypeEnums.isBetaNode(sink1)) {
                list.addAll(((BetaNode) sink1).getRules());
            }
        }
        return list;
    }

    @Override
    public ObjectTypeNode getObjectTypeNode() {
        return rightInput.getObjectTypeNode();
    }

    public void doAttach(BuildContext context) {
        super.doAttach(context);
        setUnificationJoin();

        this.rightInput.doAttach(context);
        this.leftInput.addTupleSink( this, context );
    }



    public static BetaMemory getBetaMemory(NetworkNode node, ReteEvaluator reteEvaluator) {
        BetaMemory bm;
        if ( node.getType() == NodeTypeEnums.AccumulateNode ) {
            bm = ((AccumulateMemory)reteEvaluator.getNodeMemory((AccumulateNode)node)).getBetaMemory();
        } else {
            bm = ((BetaMemory)reteEvaluator.getNodeMemory((BetaNode)node));
        }
        return bm;
    }
    

    public boolean isObjectMemoryEnabled() {
        return objectMemory;
    }

    public boolean isLeftTupleMemoryEnabled() {
        return tupleMemoryEnabled;
    }

    public Memory createMemory(RuleBaseConfiguration config, ReteEvaluator reteEvaluator) {
        return (Memory) constraints.createBetaMemory(config, getType());
    }

    public String toString() {
        return "[ " + this.getClass().getSimpleName() + "(" + this.id + ") ]";
    }

    protected int calculateHashCode() {
        int hash = ( 23 * leftInput.hashCode() ) + (29 * rightInput.hashCode() ) + (31 * constraints.hashCode() );
        if (leftListenedProperties != null) {
            hash += 37 * leftListenedProperties.hashCode();
        }
        return hash;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if (!NodeTypeEnums.isBetaNode((NetworkNode)object) || this.hashCode() != object.hashCode()) {
            return false;
        }

        BetaNode other = (BetaNode) object;
        return this.getClass() == other.getClass() &&
               this.constraints.equals( other.constraints ) &&
               this.rightInput.equals(other.rightInput) &&
               Objects.equals(this.leftListenedProperties, other.leftListenedProperties) &&
               this.leftInput.getId() == other.leftInput.getId();
    }

    /**
     * Returns the next node
     * @return
     *      The next TupleSinkNode
     */
    public LeftTupleSinkNode getNextLeftTupleSinkNode() {
        return this.nextTupleSinkNode;
    }

    /**
     * Sets the next node
     * @param next
     *      The next TupleSinkNode
     */
    public void setNextLeftTupleSinkNode(final LeftTupleSinkNode next) {
        this.nextTupleSinkNode = next;
    }

    /**
     * Returns the previous node
     * @return
     *      The previous TupleSinkNode
     */
    public LeftTupleSinkNode getPreviousLeftTupleSinkNode() {
        return this.previousTupleSinkNode;
    }

    /**
     * Sets the previous node
     * @param previous
     *      The previous TupleSinkNode
     */
    public void setPreviousLeftTupleSinkNode(final LeftTupleSinkNode previous) {
        this.previousTupleSinkNode = previous;
    }
    void disablePropertyReactivity() {
        rightInput.disablePropertyReactivity();
        if (NodeTypeEnums.isBetaNode(leftInput)) {
            ((BetaNode)leftInput).disablePropertyReactivity();
        }
    }

    @Override
    public boolean doRemove(RuleRemovalContext context, ReteooBuilder builder) {
        if ( !isInUse() ) {
            getLeftTupleSource().removeTupleSink( this );
            return true;
        }
        return false;
    }

    /**
     * Associates this node with the give rule
     */
    public void addAssociation(Rule rule, BuildContext context) {
        super.addAssociation(rule, context);
        rightInput.addAssociation(rule, context);
    }

    @Override
    public boolean removeAssociation(Rule rule, RuleRemovalContext context) {
        boolean result =  super.removeAssociation(rule, context);
        rightInput.removeAssociation(rule, context);
        return  result;
    }

    @Override
    public void addAssociatedTerminal(BaseTerminalNode terminalNode) {
        super.addAssociatedTerminal(terminalNode);
        rightInput.addAssociatedTerminal(terminalNode);
    }

    @Override
    public void removeAssociatedTerminal(BaseTerminalNode terminalNode) {
        super.removeAssociatedTerminal(terminalNode);
        rightInput.removeAssociatedTerminal(terminalNode);
    }

    /**
     * Used with the updateSink method, so that the parent ObjectSource
     * can  update the  TupleSink
     */
    public static class RightTupleSinkAdapter
            implements
            ObjectSink {
        private BetaNode bnNode;

        private List<DetachedTuple> detachedTuples;

        public RightTupleSinkAdapter(BetaNode bnNode, List<DetachedTuple> detachedTuples) {
            this.bnNode = bnNode;
            this.detachedTuples = detachedTuples;
        }

        /**
         * Do not use this constructor. It should be used just by deserialization.
         */
        public RightTupleSinkAdapter() {
        }

        public void assertObject(final InternalFactHandle factHandle,
                                 final PropagationContext context,
                                 final ReteEvaluator reteEvaluator) {
            ObjectTypeNodeId otnId = bnNode.getRightInput().getInputOtnId();
            TupleImpl detached = factHandle.getLinkedTuples().detachRightTupleAfter(getPartitionId(), otnId);
            if (detached != null) {
                detachedTuples.add(new DetachedTuple((DefaultFactHandle) factHandle, detached));
            }

            bnNode.getRightInput().assertObject(factHandle, context, reteEvaluator);
        }

        public void modifyObject(InternalFactHandle factHandle,
                                 ModifyPreviousTuples modifyPreviousTuples,
                                 PropagationContext context,
                                 ReteEvaluator reteEvaluator) {
            throw new UnsupportedOperationException( "ObjectSinkAdapter onlys supports assertObject method calls" );
        }

        public int getId() {
            return 0;
        }

        public RuleBasePartitionId getPartitionId() {
            return bnNode.getPartitionId();
        }

        public void byPassModifyToBetaNode(InternalFactHandle factHandle,
                                           ModifyPreviousTuples modifyPreviousTuples,
                                           PropagationContext context,
                                           ReteEvaluator reteEvaluator) {
            throw new UnsupportedOperationException();
        }

        public int getType() {
            return NodeTypeEnums.LeftInputAdapterNode;
        }

        @Override public Rule[] getAssociatedRules() {
            return bnNode.getAssociatedRules();
        }

        public boolean isAssociatedWith(Rule rule) {
            return bnNode.isAssociatedWith( rule );
        }

        @Override
        public NetworkNode[] getSinks() {
            return new NetworkNode[0];
        }

        @Override
        public void addAssociatedTerminal(BaseTerminalNode terminalNode) {
            bnNode.addAssociatedTerminal(terminalNode);
        }

        @Override
        public void removeAssociatedTerminal(BaseTerminalNode terminalNode) {
            bnNode.removeAssociatedTerminal(terminalNode);
        }

        @Override
        public int getAssociatedTerminalsSize() {
            return bnNode.getAssociatedTerminalsSize();
        }

        @Override
        public boolean hasAssociatedTerminal(BaseTerminalNode terminalNode) {
            return bnNode.hasAssociatedTerminal(terminalNode);
        }
    }
}
