package org.drools.core.reteoo;

import org.drools.base.base.ObjectType;
import org.drools.base.common.RuleBasePartitionId;
import org.drools.base.reteoo.NodeTypeEnums;
import org.drools.base.rule.Pattern;
import org.drools.core.common.BaseNode;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.InternalWorkingMemory;
import org.drools.core.common.PropagationContext;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.common.TupleSets;
import org.drools.core.common.UpdateContext;
import org.drools.core.reteoo.AccumulateNode.AccumulateMemory;
import org.drools.core.reteoo.builder.BuildContext;
import org.drools.util.bitmask.AllSetBitMask;
import org.drools.util.bitmask.BitMask;
import org.drools.util.bitmask.EmptyBitMask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

import static org.drools.base.reteoo.PropertySpecificUtil.isPropertyReactive;
import static org.drools.core.phreak.RuleNetworkEvaluator.doUpdatesReorderChildLeftTuple;
import static org.drools.core.phreak.TupleEvaluationUtil.flushLeftTupleIfNecessary;

public abstract class RightInputAdapterNode<T extends  BetaNode> extends BaseNode
        implements ObjectSinkNode,
                   RightTupleSink {

    protected static final Logger    log               = LoggerFactory.getLogger(RightInputAdapterNode.class);
    protected static final boolean   isLogTraceEnabled = log.isTraceEnabled();

    protected T                      betaNode;

    private ObjectSinkNode           previousObjectSinkNode;
    protected BitMask                declaredMask = EmptyBitMask.get();
    protected BitMask                inferredMask = EmptyBitMask.get();
    private ObjectSinkNode           nextObjectSinkNode;
    private BitMask                  negativeMask = EmptyBitMask.get();
    private ObjectTypeNodeId         inputOtnId   = ObjectTypeNodeId.DEFAULT_ID;

    private ObjectSource             source;

    private Collection<String>       listenedProperties;

    protected boolean                inputIsTupleToObjectNode;

    private boolean                  inputIsPassive;

    private transient ObjectTypeNode objectTypeNode;

    public RightInputAdapterNode(int id, ObjectSource input, BuildContext context) {
        super(id, context.getPartitionId());
        inputIsTupleToObjectNode = NodeTypeEnums.TupleToObjectNode == input.getType();

        source = input;

        this.hashcode = calculateHashCode();

        if (context.isStreamMode() ) {
            ObjectTypeNode otn = getObjectTypeNode();
            if (otn != null) {
                setStreamMode(otn.getObjectType().isEvent() );
            }
        }
    }
    public ObjectSource getParent() {
        return source;
    }

    public T getBetaNode() {
        return betaNode;
    }

    public void setBetaNode(T betaNode) {
        this.betaNode = betaNode;
    }

    @Override
    public void assertObject( InternalFactHandle factHandle, PropagationContext pctx, ReteEvaluator reteEvaluator ) {
        final BetaMemory memory = getBetaMemoryFromRightInput(betaNode, reteEvaluator);

        RightTuple rightTuple = createRightTuple(factHandle, this, pctx);

        boolean stagedInsertWasEmpty = memory.getStagedRightTuples().addInsert(rightTuple);
        if ( isLogTraceEnabled ) {
            log.trace("BetaNode stagedInsertWasEmpty={}", stagedInsertWasEmpty );
        }

        boolean shouldFlush = isStreamMode();
        if ( memory.getAndIncCounter() == 0 ) {
            if ( stagedInsertWasEmpty ) {
                memory.setNodeDirtyWithoutNotify();
            }
            shouldFlush = memory.linkNode( betaNode, reteEvaluator, !inputIsPassive) | shouldFlush;
        } else if ( stagedInsertWasEmpty ) {
            shouldFlush = memory.setNodeDirty( betaNode, reteEvaluator, !inputIsPassive) | shouldFlush;
        }

        if (shouldFlush) {
            flushLeftTupleIfNecessary( reteEvaluator, memory.getOrCreateSegmentMemory( betaNode, reteEvaluator ), isStreamMode() );
        }
    }

    public void modifyObject(InternalFactHandle factHandle, ModifyPreviousTuples modifyPreviousTuples, PropagationContext context, ReteEvaluator reteEvaluator) {
        TupleImpl rightTuple = modifyPreviousTuples.peekRightTuple(partitionId);

        // if the peek is for a different OTN we assume that it is after the current one and then this is an assert
        while ( rightTuple != null && rightTuple.getInputOtnId().before(getInputOtnId()) ) {
            modifyPreviousTuples.removeRightTuple(partitionId);

            // we skipped this node, due to alpha hashing, so retract now
            rightTuple.setPropagationContext( context );
            BetaMemory bm = BetaNode.getBetaMemory(((RightInputAdapterNode)rightTuple.getSink()).getBetaNode(), reteEvaluator);
            ((RightInputAdapterNode) rightTuple.getSink()).doDeleteRightTuple(rightTuple, reteEvaluator, bm);
            rightTuple = modifyPreviousTuples.peekRightTuple(partitionId);
        }

        if ( rightTuple != null && rightTuple.getInputOtnId().equals(getInputOtnId()) ) {
            modifyPreviousTuples.removeRightTuple(partitionId);
            rightTuple.reAdd();
            if ( context.getModificationMask().intersects(inferredMask) ) {
                // RightTuple previously existed, so continue as modify
                rightTuple.setPropagationContext( context );  // only update, if the mask intersects

                BetaMemory bm = BetaNode.getBetaMemory(betaNode, reteEvaluator);
                rightTuple.setPropagationContext( context );
                doUpdateRightTuple(rightTuple, reteEvaluator, bm);
            } else if (rightTuple.getMemory() != null) {
                reorderRightTuple(reteEvaluator, rightTuple);
            }
        } else {
            if ( context.getModificationMask().intersects(inferredMask) ) {
                // RightTuple does not exist for this node, so create and continue as assert
                assertObject( factHandle, context, reteEvaluator );
            }
        }
    }

    protected void reorderRightTuple(ReteEvaluator reteEvaluator, TupleImpl rightTuple) {
        BetaNode.getBetaMemory(betaNode, reteEvaluator).getRightTupleMemory().removeAdd(rightTuple);
        doUpdatesReorderChildLeftTuple(rightTuple);
    }

    public void doDeleteRightTuple(final TupleImpl rightTuple,
                                   final ReteEvaluator reteEvaluator,
                                   final BetaMemory memory) {
        TupleSets stagedRightTuples = memory.getStagedRightTuples();

        boolean stagedDeleteWasEmpty = stagedRightTuples.addDelete(rightTuple);

        boolean shouldFlush = isStreamMode();
        if ( memory.getAndDecCounter() == 1 ) {
            if ( stagedDeleteWasEmpty ) {
                memory.setNodeDirtyWithoutNotify();
            }
            shouldFlush = memory.unlinkNode(reteEvaluator) | shouldFlush;
        } else if ( stagedDeleteWasEmpty ) {
            // nothing staged before, notify rule, so it can evaluate network
            shouldFlush = memory.setNodeDirty( betaNode, reteEvaluator ) | shouldFlush;
        }

        if (shouldFlush) {
            flushLeftTupleIfNecessary( reteEvaluator, memory.getOrCreateSegmentMemory( betaNode, reteEvaluator ), isStreamMode() );
        }
    }

    public void doUpdateRightTuple(final TupleImpl rightTuple,
                                   final ReteEvaluator reteEvaluator,
                                   final BetaMemory memory) {
        TupleSets stagedRightTuples = memory.getStagedRightTuples();

        boolean stagedUpdateWasEmpty = stagedRightTuples.addUpdate( rightTuple );

        boolean shouldFlush = isStreamMode();
        if ( stagedUpdateWasEmpty  ) {
            shouldFlush = memory.setNodeDirty( betaNode, reteEvaluator ) | shouldFlush;
        }

        if (shouldFlush) {
            flushLeftTupleIfNecessary( reteEvaluator, memory.getOrCreateSegmentMemory( betaNode, reteEvaluator ), isStreamMode() );
        }
    }

    public static BetaMemory getBetaMemoryFromRightInput(BetaNode betaNode, ReteEvaluator reteEvaluator) {
        return NodeTypeEnums.AccumulateNode == betaNode.getType() ?
               ((AccumulateMemory)reteEvaluator.getNodeMemory(betaNode)).getBetaMemory() :
               (BetaMemory) reteEvaluator.getNodeMemory( betaNode );
    }


    public RightTuple createRightTuple(InternalFactHandle handle,
                                       RightTupleSink sink,
                                       PropagationContext context) {
        RightTuple rightTuple = new RightTuple(handle, sink );
        rightTuple.setPropagationContext( context );
        return rightTuple;
    }

    public void initDeclaredMask(BuildContext context) {
        if (context == null || context.getLastBuiltPatterns() == null) {
            // only happens during unit tests
            declaredMask = AllSetBitMask.get();
            return;
        }

        Pattern pattern = context.getLastBuiltPatterns()[0]; // right input pattern
        inputIsPassive = pattern.isPassive();

        if (!inputIsTupleToObjectNode()) {
            ObjectType objectType = pattern.getObjectType();

            if (isPropertyReactive(context.getRuleBase(), objectType)) {
                listenedProperties = pattern.getListenedProperties();
                List<String> accessibleProperties = pattern.getAccessibleProperties( context.getRuleBase() );
                declaredMask      = pattern.getPositiveWatchMask(accessibleProperties);
                declaredMask = declaredMask.setAll(betaNode.getRawConstraints().getListenedPropertyMask(pattern, objectType, accessibleProperties));
                negativeMask = pattern.getNegativeWatchMask(accessibleProperties);
            } else {
                // if property reactive is not on, then accept all modification propagations
                declaredMask = AllSetBitMask.get();
            }
        } else {
            declaredMask = AllSetBitMask.get();
            // There would have been no right input pattern, so swap current to first, so leftInput can still work
            context.setLastBuiltPattern( context.getLastBuiltPatterns()[0] );
        }
    }

    protected void initInferredMask() {
        BaseNode unwrappedRight = getParent();
        if ( unwrappedRight.getType() == NodeTypeEnums.AlphaNode ) {
            AlphaNode alphaNode = (AlphaNode) unwrappedRight;
            inferredMask = alphaNode.updateMask(declaredMask);
        } else {
            inferredMask = declaredMask;
        }
        inferredMask = inferredMask.resetAll(negativeMask);
    }

    @Override
    public boolean inputIsTupleToObjectNode() {
        return inputIsTupleToObjectNode;
    }

    @Override
    public void byPassModifyToBetaNode (final InternalFactHandle factHandle,
                                        final ModifyPreviousTuples modifyPreviousTuples,
                                        final PropagationContext context,
                                        final ReteEvaluator reteEvaluator) {
        modifyObject( factHandle, modifyPreviousTuples, context, reteEvaluator );
    }


    public BitMask calculateDeclaredMask(ObjectType modifiedType, List<String> settableProperties) {
        return null;
    }


    public void updateSink(ObjectSink sink, PropagationContext context, InternalWorkingMemory workingMemory) {
        source.updateSink(this, context,  workingMemory );
    }

    @Override
    public abstract void retractRightTuple(TupleImpl rightTuple, PropagationContext context, ReteEvaluator reteEvaluator);


    @Override
    public abstract void modifyRightTuple(TupleImpl rightTuple, PropagationContext context, ReteEvaluator reteEvaluator);

    /**
     * Returns the next node
     * @return
     *      The next ObjectSinkNode
     */
    public ObjectSinkNode getNextObjectSinkNode() {
        return this.nextObjectSinkNode;
    }

    /**
     * Sets the next node
     * @param next
     *      The next ObjectSinkNode
     */
    public void setNextObjectSinkNode(final ObjectSinkNode next) {
        this.nextObjectSinkNode = next;
    }

    /**
     * Returns the previous node
     * @return
     *      The previous ObjectSinkNode
     */
    public ObjectSinkNode getPreviousObjectSinkNode() {
        return this.previousObjectSinkNode;
    }

    /**
     * Sets the previous node
     * @param previous
     *      The previous ObjectSinkNode
     */
    public void setPreviousObjectSinkNode(final ObjectSinkNode previous) {
        this.previousObjectSinkNode = previous;
    }

    void disablePropertyReactivity() {
        inferredMask = AllSetBitMask.get();
    }

    public BitMask getNegativeMask() {
        return negativeMask;
    }

    public ObjectTypeNodeId getInputOtnId() {
        return inputOtnId;
    }

    public void setInputOtnId(ObjectTypeNodeId inputOtnId) {
        this.inputOtnId = inputOtnId;
    }

    public boolean isRightInputPassive() {
        return inputIsPassive;
    }

    @Override
    public ObjectTypeNode getObjectTypeNode() {
        if (objectTypeNode == null) {
            ObjectSource source = this.source;
            while ( source != null ) {
                if ( NodeTypeEnums.ObjectTypeNode == source.getType()) {
                    objectTypeNode = (ObjectTypeNode) source;
                    break;
                }
                source = source.source;
            }
        }
        return objectTypeNode;
    }

    @Override
    public void doAttach(BuildContext context) {
        super.doAttach(context);
        this.source.addObjectSink(this);
    }

    @Override
    public boolean doRemove(RuleRemovalContext context, ReteooBuilder builder) {
        if ( !isInUse() ) {
            source.removeObjectSink(this);
            return true;
        }
        return false;
    }

    @Override
    public boolean isInUse() {
        return betaNode.isInUse();
    }

    protected int calculateHashCode() {
        int hash = 29 * source.hashCode();
        if (listenedProperties != null) {
            hash += 41 * listenedProperties.hashCode();
        }
        return hash + (inputIsPassive ? 43 : 0);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if ( //!NodeTypeEnums.isBetaNode((NetworkNode)object) ||
            this.hashCode() != object.hashCode()) {
            return false;
        }

        RightInputAdapterNode other = (RightInputAdapterNode) object;
        return this.getClass() == other.getClass() &&
               this.inputIsPassive == other.inputIsPassive &&
               Objects.equals(this.listenedProperties, other.listenedProperties) &&
               this.source.getId() == other.source.getId();
    }

    @Override
    public String toString() {
        return "RightInputAdapterNode(" + id + ")[ source="
               + source + ", associations=" + associations + ", partitionId=" + partitionId + "]";
    }

    public void networkUpdated(UpdateContext updateContext) {
        this.source.networkUpdated(updateContext);
    }

    public final void setPartitionIdWithSinks( RuleBasePartitionId partitionId) {
        this.partitionId = partitionId;
    }

    public BitMask getDeclaredMask() {
        return declaredMask;
    }

    public BitMask getInferredMask() {
        return inferredMask;
    }
}
