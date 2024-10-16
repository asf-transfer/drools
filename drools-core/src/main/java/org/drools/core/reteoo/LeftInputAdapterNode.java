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

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.drools.base.base.ClassObjectType;
import org.drools.base.base.ObjectType;
import org.drools.base.common.NetworkNode;
import org.drools.base.common.RuleBasePartitionId;
import org.drools.base.reteoo.BaseTerminalNode;
import org.drools.base.reteoo.NodeTypeEnums;
import org.drools.base.rule.Pattern;
import org.drools.core.RuleBaseConfiguration;
import org.drools.core.common.BaseNode;
import org.drools.core.common.DefaultFactHandle;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.Memory;
import org.drools.core.common.MemoryFactory;
import org.drools.core.common.PropagationContext;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.common.SuperCacheFixer;
import org.drools.core.common.TupleSets;
import org.drools.core.common.UpdateContext;
import org.drools.core.phreak.DetachedTuple;
import org.drools.core.phreak.RuntimeSegmentUtilities;
import org.drools.core.reteoo.builder.BuildContext;
import org.drools.core.rule.consequence.InternalMatch;
import org.drools.core.util.AbstractLinkedListNode;
import org.drools.util.bitmask.AllSetBitMask;
import org.drools.util.bitmask.BitMask;
import org.kie.api.definition.rule.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.drools.base.reteoo.PropertySpecificUtil.isPropertyReactive;
import static org.drools.core.phreak.TupleEvaluationUtil.createLeftTupleTupleSets;
import static org.drools.core.phreak.TupleEvaluationUtil.findPathToFlush;
import static org.drools.core.phreak.TupleEvaluationUtil.findPathsToFlushFromSubnetwork;
import static org.drools.core.phreak.TupleEvaluationUtil.flushLeftTupleIfNecessary;
import static org.drools.core.phreak.TupleEvaluationUtil.forceFlushLeftTuple;
import static org.drools.core.phreak.TupleEvaluationUtil.forceFlushPath;

/**
 * All asserting Facts must propagated into the right <code>ObjectSink</code> side of a BetaNode, if this is the first Pattern
 * then there are no BetaNodes to propagate to. <code>LeftInputAdapter</code> is used to adapt an ObjectSink propagation into a
 * <code>TupleSource</code> which propagates a <code>ReteTuple</code> suitable fot the right <code>ReteTuple</code> side
 * of a <code>BetaNode</code>.
 */
public class LeftInputAdapterNode extends LeftTupleSource
        implements
        ObjectSinkNode,
        MemoryFactory<LeftInputAdapterNode.LiaNodeMemory> {

    protected static final transient Logger log = LoggerFactory.getLogger(LeftInputAdapterNode.class);

    private static final long serialVersionUID = 510L;
    private ObjectSource objectSource;

    private ObjectSinkNode previousRightTupleSinkNode;
    private ObjectSinkNode nextRightTupleSinkNode;

    private boolean leftTupleMemoryEnabled;

    protected BitMask sinkMask;

    public LeftInputAdapterNode() {

    }

    /**
     * Constructus a LeftInputAdapterNode with a unique id that receives <code>FactHandle</code> from a
     * parent <code>ObjectSource</code> and adds it to a given pattern in the resulting Tuples.
     *
     * @param id
     *      The unique id of this node in the current Rete network
     * @param source
     *      The parent node, where Facts are propagated from
     */
    public LeftInputAdapterNode(final int id,
                                final ObjectSource source,
                                final BuildContext context) {
        super(id, context);
        this.setObjectCount(1); // 'lia' start at 1
        this.objectSource = source;
        this.leftTupleMemoryEnabled = context.isTupleMemoryEnabled();
        BaseNode current = source;
        while (current.getType() != NodeTypeEnums.ObjectTypeNode) {
            current = current.getParent();
        }

        setStreamMode( context.isStreamMode() && context.getRootObjectTypeNode().getObjectType().isEvent() );
        sinkMask = calculateSinkMask(context);

        hashcode = calculateHashCode();
    }

    public ObjectSource getParent() {
        return objectSource;
    }

    private BitMask calculateSinkMask(BuildContext context) {
        Pattern pattern = context.getLastBuiltPatterns() != null ? context.getLastBuiltPatterns()[0] : null;
        if (pattern == null) {
            return AllSetBitMask.get();
        }
        ObjectType objectType = pattern.getObjectType();
        if ( !(objectType instanceof ClassObjectType) ) {
            // Only ClassObjectType can use property specific
            return AllSetBitMask.get();
        }

        return isPropertyReactive( context.getRuleBase(), objectType ) ?
               pattern.getPositiveWatchMask( pattern.getAccessibleProperties( context.getRuleBase() ) ) :
               AllSetBitMask.get();
    }

    public ObjectSource getObjectSource() {
        return this.objectSource;
    }

    public int getType() {
        return NodeTypeEnums.LeftInputAdapterNode;
    }

    @Override
    public boolean isLeftTupleMemoryEnabled() {
        return leftTupleMemoryEnabled;
    }

    public ObjectSource getParentObjectSource() {
        return this.objectSource;
    }

    public void doAttach( BuildContext context ) {
        super.doAttach(context);
        this.objectSource.addObjectSink( this );
    }

    public void networkUpdated(UpdateContext updateContext) {
        this.objectSource.networkUpdated(updateContext);
    }

    public void assertObject(final InternalFactHandle factHandle,
                             final PropagationContext context,
                             final ReteEvaluator reteEvaluator) {
        LiaNodeMemory lm = reteEvaluator.getNodeMemory( this );
        doInsertObject( factHandle, context, this, reteEvaluator,
                        lm, true, // queries are handled directly, and not through here
                        true );
    }

    public static void doInsertObject(final InternalFactHandle factHandle,
                                      final PropagationContext context,
                                      final LeftInputAdapterNode liaNode,
                                      final ReteEvaluator reteEvaluator,
                                      final LiaNodeMemory lm,
                                      boolean linkOrNotify,
                                      boolean useLeftMemory) {
        SegmentMemory sm = lm.getOrCreateSegmentMemory( liaNode, reteEvaluator );
        if ( sm.getTipNode() == liaNode) {
            // liaNode in its own segment and child segments not yet created
            if ( sm.isEmpty() ) {
                RuntimeSegmentUtilities.createChildSegments(reteEvaluator,
                                                            sm,
                                                            liaNode.getSinkPropagator());
            }
            sm = sm.getFirst(); // repoint to the child sm
        }

        int counter = lm.getAndIncreaseCounter();
        // node is not linked, so notify will happen when we link the node
        boolean notifySegment = linkOrNotify && counter != 0;

        if ( counter == 0) {
            // if there is no left memory, then there is no linking or notification
            if ( linkOrNotify ) {
                // link and notify
                lm.linkNode( reteEvaluator );
            } else {
                // link without notify, when driven by a query, as we don't want it, placed on the agenda
                lm.linkNodeWithoutRuleNotify();
            }
        }

        LeftTupleSink sink = liaNode.getSinkPropagator().getFirstLeftTupleSink();
        TupleImpl leftTuple = TupleFactory.createLeftTuple( sink, factHandle, useLeftMemory );
        leftTuple.setPropagationContext( context );

        if ( sm.getRootNode() == liaNode ) {
            doInsertSegmentMemoryWithFlush(reteEvaluator, notifySegment, lm, sm, leftTuple, liaNode.isStreamMode());
        } else {
            // sm points to lia child sm, so iterate for all remaining children
            // all peer tuples must be created before propagation, or eager evaluation subnetworks have problem
            TupleImpl     peer      = leftTuple;
            SegmentMemory originaSm = sm;
            for ( sm = sm.getNext(); sm != null; sm = sm.getNext() ) {
                sink =  sm.getSinkFactory();
                peer = TupleFactory.createPeer( sink, peer ); // pctx is set during peer cloning
            }

            sm = originaSm;
            Set<PathMemory> pathsToFlush = new HashSet<>();
            pathsToFlush.addAll( doInsertSegmentMemory( reteEvaluator, notifySegment, lm, sm, leftTuple, liaNode.isStreamMode() ) );
            if ( sm.getRootNode() != liaNode ) {
                // sm points to lia child sm, so iterate for all remaining children
                peer = leftTuple;
                for ( sm = sm.getNext(); sm != null; sm = sm.getNext() ) {
                    peer = peer.getPeer();
                    pathsToFlush.addAll( doInsertSegmentMemory( reteEvaluator, notifySegment, lm, sm, peer, liaNode.isStreamMode() ) );
                }
            }

            for (PathMemory outPmem : pathsToFlush) {
                forceFlushPath(reteEvaluator, outPmem);
            }
        }
    }

    public static void doInsertSegmentMemoryWithFlush(ReteEvaluator reteEvaluator, boolean notifySegment, LiaNodeMemory lm, SegmentMemory sm, TupleImpl leftTuple, boolean streamMode) {
        for (PathMemory outPmem : doInsertSegmentMemory(reteEvaluator, notifySegment, lm, sm, leftTuple, streamMode )) {
            forceFlushPath(reteEvaluator, outPmem);
        }
    }

    public static List<PathMemory> doInsertSegmentMemory(ReteEvaluator reteEvaluator, boolean linkOrNotify, LiaNodeMemory lm, SegmentMemory sm, TupleImpl leftTuple, boolean streamMode) {
        PathMemory pmem = findPathToFlush(sm, leftTuple, streamMode);
        if ( pmem != null ) {
            forceFlushLeftTuple( pmem, sm, reteEvaluator, createLeftTupleTupleSets(leftTuple, Tuple.INSERT) );
            if ( linkOrNotify ) {
                lm.setNodeDirty( reteEvaluator );
            }
            return findPathsToFlushFromSubnetwork(reteEvaluator, pmem);
        }

        // mask check is necessary if insert is a result of a modify
        boolean stagedInsertWasEmpty = sm.getStagedLeftTuples().addInsert( leftTuple );

        if ( stagedInsertWasEmpty && linkOrNotify  ) {
            // staged is empty, so notify rule, to force re-evaluation.
            lm.setNodeDirty(reteEvaluator);
        }
        return Collections.emptyList();
    }

    public static void doDeleteObject(TupleImpl leftTuple,
                                      PropagationContext context,
                                      SegmentMemory sm,
                                      final ReteEvaluator reteEvaluator,
                                      final LeftInputAdapterNode liaNode,
                                      final boolean linkOrNotify,
                                      final LiaNodeMemory lm) {
        if ( sm.getTipNode() == liaNode ) {
            // liaNode in it's own segment and child segments not yet created
            if ( sm.isEmpty() ) {
                RuntimeSegmentUtilities.createChildSegments(reteEvaluator,
                                                            sm,
                                                            liaNode.getSinkPropagator());
            }
            sm = sm.getFirst(); // repoint to the child sm
        }

        doDeleteSegmentMemory(leftTuple, context, lm, sm, reteEvaluator, linkOrNotify, liaNode.isStreamMode());

        if ( sm.getNext() != null) {
            // sm points to lia child sm, so iterate for all remaining children

            for ( sm = sm.getNext(); sm != null; sm = sm.getNext() ) {
                // iterate for peers segment memory
                leftTuple = leftTuple.getPeer();
                if (leftTuple == null) {
                    break;
                }
                doDeleteSegmentMemory(leftTuple, context, lm, sm, reteEvaluator, linkOrNotify, liaNode.isStreamMode());
            }
        }

        if ( lm.getAndDecreaseCounter() == 1 ) {
            if ( linkOrNotify ) {
                lm.unlinkNode( reteEvaluator );
            } else {
                lm.unlinkNodeWithoutRuleNotify();
            }
        }
    }

    private static void doDeleteSegmentMemory(TupleImpl leftTuple, PropagationContext pctx, final LiaNodeMemory lm,
                                              SegmentMemory sm, ReteEvaluator reteEvaluator, boolean linkOrNotify, boolean streamMode) {
        leftTuple.setPropagationContext( pctx );
        if ( flushLeftTupleIfNecessary( reteEvaluator, sm, leftTuple, streamMode, Tuple.DELETE ) ) {
            if ( linkOrNotify ) {
                lm.setNodeDirty( reteEvaluator );
            }
            return;
        }

        TupleSets leftTuples = sm.getStagedLeftTuples();
        boolean stagedDeleteWasEmpty = leftTuples.addDelete(leftTuple);

        if (  stagedDeleteWasEmpty && linkOrNotify ) {
            // staged is empty, so notify rule, to force re-evaluation
            lm.setNodeDirty(reteEvaluator);
        }
    }

    public static void doUpdateObject(TupleImpl leftTuple,
                                      PropagationContext context,
                                      final ReteEvaluator reteEvaluator,
                                      final LeftInputAdapterNode liaNode,
                                      final boolean linkOrNotify,
                                      final LiaNodeMemory lm,
                                      SegmentMemory sm) {
        if ( sm.getTipNode() == liaNode) {
            // liaNode in it's own segment and child segments not yet created
            if ( sm.isEmpty() ) {
                RuntimeSegmentUtilities.createChildSegments(reteEvaluator,
                                                            sm,
                                                            liaNode.getSinkPropagator());
            }
            sm = sm.getFirst(); // repoint to the child sm
        }

        doUpdateSegmentMemory(leftTuple, context, reteEvaluator, linkOrNotify, lm, sm, liaNode.isStreamMode() );

        if (  sm.getNext() != null ) {
            // sm points to lia child sm, so iterate for all remaining children
            for ( sm = sm.getNext(); sm != null; sm = sm.getNext() ) {
                // iterate for peers segment memory
                leftTuple = leftTuple.getPeer();

                doUpdateSegmentMemory(leftTuple, context, reteEvaluator, linkOrNotify, lm, sm, liaNode.isStreamMode() );
            }
        }
    }

    private static void doUpdateSegmentMemory(TupleImpl leftTuple, PropagationContext pctx, ReteEvaluator reteEvaluator, boolean linkOrNotify,
                                              final LiaNodeMemory lm, SegmentMemory sm, boolean streamMode ) {
        leftTuple.setPropagationContext( pctx );
        TupleSets leftTuples = sm.getStagedLeftTuples();

        if ( leftTuple.getStagedType() == LeftTuple.NONE ) {
            if ( flushLeftTupleIfNecessary( reteEvaluator, sm, leftTuple, streamMode, Tuple.UPDATE ) ) {
                if ( linkOrNotify ) {
                    lm.setNodeDirty( reteEvaluator );
                }
                return;
            }

            // if LeftTuple is already staged, leave it there
            boolean stagedUpdateWasEmpty = leftTuples.addUpdate(leftTuple);

            if ( stagedUpdateWasEmpty  && linkOrNotify ) {
                // staged is empty, so notify rule, to force re-evaluation
                lm.setNodeDirty(reteEvaluator);
            }
        }
    }

    public void retractLeftTuple(TupleImpl leftTuple,
                                 PropagationContext context,
                                 ReteEvaluator reteEvaluator) {
        LiaNodeMemory lm = reteEvaluator.getNodeMemory( this );
        SegmentMemory smem = lm.getSegmentMemory();
        if ( smem.getTipNode() == this ) {
            // segment with only a single LiaNode in it, skip to next segment
            // as a liaNode only segment has no staging
            smem = smem.getFirst();
        }

        doDeleteObject( leftTuple, context, smem, reteEvaluator,
                        this, true, lm );
    }

    public void modifyObject(InternalFactHandle factHandle,
                             final ModifyPreviousTuples modifyPreviousTuples,
                             PropagationContext context,
                             ReteEvaluator reteEvaluator) {
        ObjectTypeNodeId otnId = this.sink.getFirstLeftTupleSink().getInputOtnId();

        TupleImpl     leftTuple = processDeletesFromModify(modifyPreviousTuples, context, reteEvaluator, otnId);
        LiaNodeMemory lm        = reteEvaluator.getNodeMemory( this );

        LeftTupleSink sink = getSinkPropagator().getFirstLeftTupleSink();
        BitMask mask = sink.getInferredMask();

        if ( leftTuple != null && leftTuple.getInputOtnId().equals(otnId) ) {
            modifyPreviousTuples.removeLeftTuple(partitionId);
            leftTuple.reAdd();
            if ( context.getModificationMask().intersects( mask) ) {
                doUpdateObject(leftTuple, context, reteEvaluator, (LeftInputAdapterNode) SuperCacheFixer.getLeftTupleSource(leftTuple), true, lm, lm.getOrCreateSegmentMemory(this, reteEvaluator));
                if (leftTuple.isFullMatch()) {
                    ((InternalMatch)leftTuple).setActive(true);
                }
            }
        } else {
            if ( context.getModificationMask().intersects( mask) ) {
                doInsertObject(factHandle, context, this, reteEvaluator, lm, true, true);
            }

        }
    }

    protected TupleImpl processDeletesFromModify(ModifyPreviousTuples modifyPreviousTuples, PropagationContext context, ReteEvaluator reteEvaluator, ObjectTypeNodeId otnId) {
        TupleImpl leftTuple = modifyPreviousTuples.peekLeftTuple(partitionId);
        while ( leftTuple != null && leftTuple.getInputOtnId().before(otnId) ) {
            modifyPreviousTuples.removeLeftTuple(partitionId);
            modifyPreviousTuples.doDeleteObject(context, reteEvaluator, leftTuple);
            leftTuple = modifyPreviousTuples.peekLeftTuple(partitionId);
        }
        return leftTuple;
    }

    public void byPassModifyToBetaNode(InternalFactHandle factHandle,
                                       ModifyPreviousTuples modifyPreviousTuples,
                                       PropagationContext context,
                                       ReteEvaluator reteEvaluator) {
        modifyObject(factHandle, modifyPreviousTuples, context, reteEvaluator );
    }




    protected boolean doRemove(final RuleRemovalContext context,
                            final ReteooBuilder builder) {
        if (!isInUse()) {
            objectSource.removeObjectSink(this);
            return true;
        }
        return false;
    }


    public LeftTuple createPeer(TupleImpl original) {
        return null;
    }

    /**
     * Returns the next node
     * @return
     *      The next ObjectSinkNode
     */
    public ObjectSinkNode getNextObjectSinkNode() {
        return this.nextRightTupleSinkNode;
    }

    /**
     * Sets the next node
     * @param next
     *      The next ObjectSinkNode
     */
    public void setNextObjectSinkNode(final ObjectSinkNode next) {
        this.nextRightTupleSinkNode = next;
    }

    /**
     * Returns the previous node
     * @return
     *      The previous ObjectSinkNode
     */
    public ObjectSinkNode getPreviousObjectSinkNode() {
        return this.previousRightTupleSinkNode;
    }

    /**
     * Sets the previous node
     * @param previous
     *      The previous ObjectSinkNode
     */
    public void setPreviousObjectSinkNode(final ObjectSinkNode previous) {
        this.previousRightTupleSinkNode = previous;
    }

    private int calculateHashCode() {
        return 31 * this.objectSource.hashCode() + 37 * sinkMask.hashCode();
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }

        if ( object.getClass() != LeftInputAdapterNode.class || this.hashCode() != object.hashCode() ) {
            return false;
        }
        return this.objectSource.getId() == ((LeftInputAdapterNode)object).objectSource.getId() && this.sinkMask.equals( ((LeftInputAdapterNode) object).sinkMask );
    }

    @Override
    public ObjectTypeNode getObjectTypeNode() {
        ObjectSource source = this.objectSource;
        while ( source != null ) {
            if ( source.getType() == NodeTypeEnums.ObjectTypeNode) {
                return (ObjectTypeNode) source;
            }
            source = source.source;
        }
        return null;
    }

    public LiaNodeMemory createMemory(RuleBaseConfiguration config, ReteEvaluator reteEvaluator) {
        return new LiaNodeMemory();
    }

    public static class LiaNodeMemory extends AbstractLinkedListNode<Memory> implements SegmentNodeMemory {
        private int               counter;

        private SegmentMemory     segmentMemory;

        private long              nodePosMaskBit;

        public LiaNodeMemory() {
        }


        public int getCounter() {
            return counter;
        }

        public int getAndIncreaseCounter() {
            return this.counter++;
        }

        public int getAndDecreaseCounter() {
            return this.counter--;
        }

        public void setCounter(int counter) {
            this.counter = counter;
        }

        public SegmentMemory getSegmentMemory() {
            return segmentMemory;
        }

        public void setSegmentMemory(SegmentMemory segmentNodes) {
            this.segmentMemory = segmentNodes;
        }

        public long getNodePosMaskBit() {
            return nodePosMaskBit;
        }

        public void setNodePosMaskBit(long nodePosMask) {
            nodePosMaskBit = nodePosMask;
        }

        public void setNodeDirtyWithoutNotify() { }

        public void setNodeCleanWithoutNotify() { }

        public void linkNodeWithoutRuleNotify() {
            segmentMemory.linkNodeWithoutRuleNotify(nodePosMaskBit);
        }

        public void linkNode(ReteEvaluator reteEvaluator) {
            segmentMemory.linkNode(nodePosMaskBit, reteEvaluator);
        }

        public boolean unlinkNode(ReteEvaluator reteEvaluator) {
            return segmentMemory.unlinkNode(nodePosMaskBit, reteEvaluator);
        }

        public void unlinkNodeWithoutRuleNotify() {
            segmentMemory.unlinkNodeWithoutRuleNotify(nodePosMaskBit);
        }

        public int getNodeType() {
            return NodeTypeEnums.LeftInputAdapterNode;
        }

        public void setNodeDirty(ReteEvaluator reteEvaluator) {
            segmentMemory.notifyRuleLinkSegment(reteEvaluator, nodePosMaskBit);
        }

        public void reset() {
            counter = 0;
        }
    }

    /**
     * Used with the updateSink method, so that the parent ObjectSource
     * can  update the  TupleSink
     */
    public static class LeftTupleSinkAdapter
            implements
            ObjectSink {
        private LeftInputAdapterNode liaNode;

        private List<DetachedTuple> detachedTuples;

        public LeftTupleSinkAdapter(LeftInputAdapterNode liaNode, List<DetachedTuple> detachedTuples) {
            this.liaNode = liaNode;
            this.detachedTuples = detachedTuples;
        }

        /**
         * Do not use this constructor. It should be used just by deserialization.
         */
        public LeftTupleSinkAdapter() {
        }

        public void assertObject(final InternalFactHandle factHandle,
                                 final PropagationContext context,
                                 final ReteEvaluator reteEvaluator) {
            ObjectTypeNodeId otnId = liaNode.getSinkPropagator().getFirstLeftTupleSink().getInputOtnId();
            TupleImpl detached = factHandle.getLinkedTuples().detachLeftTupleAfter(getPartitionId(), otnId);
            if (detached != null) {
                detachedTuples.add(new DetachedTuple((DefaultFactHandle) factHandle, detached));
            }

            liaNode.assertObject(factHandle, context, reteEvaluator);
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
            return liaNode.getPartitionId();
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
            return liaNode.getAssociatedRules();
        }

        public boolean isAssociatedWith(Rule rule) {
            return liaNode.isAssociatedWith( rule );
        }

        @Override
        public NetworkNode[] getSinks() {
            return new NetworkNode[0];
        }

        @Override
        public void addAssociatedTerminal(BaseTerminalNode terminalNode) {
            liaNode.addAssociatedTerminal(terminalNode);
        }

        @Override
        public void removeAssociatedTerminal(BaseTerminalNode terminalNode) {
            liaNode.removeAssociatedTerminal(terminalNode);
        }

        @Override
        public int getAssociatedTerminalsSize() {
            return liaNode.getAssociatedTerminalsSize();
        }

        @Override
        public boolean hasAssociatedTerminal(BaseTerminalNode terminalNode) {
            return liaNode.hasAssociatedTerminal(terminalNode);
        }
    }

    @Override
    public void setSourcePartitionId(BuildContext context, RuleBasePartitionId partitionId) {
        setSourcePartitionId(objectSource, context, partitionId);
    }

    @Override
    public void setPartitionId(BuildContext context, RuleBasePartitionId partitionId) {
        if (this.partitionId != null && this.partitionId != partitionId) {
            objectSource.sink.changeSinkPartition(this, this.partitionId, partitionId, objectSource.alphaNodeHashingThreshold, objectSource.alphaNodeRangeIndexThreshold );
        }
        this.partitionId = partitionId;
    }

    public boolean isTerminal() {
        return false;
    }
}