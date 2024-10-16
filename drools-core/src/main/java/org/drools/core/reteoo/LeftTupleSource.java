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

import java.util.Collection;
import java.util.List;

import org.drools.base.base.ClassObjectType;
import org.drools.base.base.ObjectType;
import org.drools.base.common.RuleBasePartitionId;
import org.drools.base.reteoo.NodeTypeEnums;
import org.drools.base.rule.Pattern;
import org.drools.core.common.BaseNode;
import org.drools.core.phreak.BuildtimeSegmentUtilities;
import org.drools.core.reteoo.builder.BuildContext;
import org.drools.util.bitmask.AllSetBitMask;
import org.drools.util.bitmask.BitMask;
import org.drools.util.bitmask.EmptyBitMask;

import static org.drools.base.reteoo.PropertySpecificUtil.calculateNegativeMask;
import static org.drools.base.reteoo.PropertySpecificUtil.calculatePositiveMask;
import static org.drools.base.reteoo.PropertySpecificUtil.getAccessibleProperties;
import static org.drools.base.reteoo.PropertySpecificUtil.isPropertyReactive;

/**
 * A source of <code>ReteTuple</code> s for a <code>TupleSink</code>.
 *
 * <p>
 * Nodes that propagate <code>Tuples</code> extend this class.
 * </p>
 *
 * @see LeftTupleSource
 * @see LeftTuple
 */
public abstract class LeftTupleSource extends BaseNode implements LeftTupleNode {

    protected BitMask declaredMask     = EmptyBitMask.get();
    protected BitMask inferredMask = EmptyBitMask.get();
    protected BitMask negativeMask = EmptyBitMask.get();


    /** The left input <code>TupleSource</code>. */
    protected LeftTupleSource         leftInput;


    private ObjectTypeNodeId inputOtnId = ObjectTypeNodeId.DEFAULT_ID;

    /** The destination for <code>Tuples</code>. */
    protected LeftTupleSinkPropagator sink;

    private int pathIndex;

    private int objectCount;

    // ------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------
    public LeftTupleSource() {

    }

    /**
     * Single parameter constructor that specifies the unique id of the node.
     *
     * @param id
     */
    protected LeftTupleSource(int id, BuildContext context) {
        super(id, context.getPartitionId());
        this.sink = EmptyLeftTupleSinkAdapter.getInstance();
        initMemoryId( context );
    }

    // ------------------------------------------------------------
    // Instance methods
    // ------------------------------------------------------------

    public int getPathIndex() {
        return pathIndex;
    }

    public abstract int getType();

    public ObjectTypeNodeId getInputOtnId() {
        return inputOtnId;
    }

    public void setInputOtnId(ObjectTypeNodeId inputOtnId) {
        this.inputOtnId = inputOtnId;
    }


    public void addTupleSink(final LeftTupleSink tupleSink) {
        addTupleSink(tupleSink, null);
    }

    public LeftTupleSource getLeftTupleSource() {
        return leftInput;
    }

    public LeftTupleSource getLeftInput() {
        return leftInput;
    }

    public LeftTupleSinkPropagator getSink() {
        return sink;
    }

    public final void setLeftTupleSource(LeftTupleSource leftInput) {
        this.leftInput = leftInput;
        pathIndex = leftInput.getPathIndex() + 1;
    }

    public int getObjectCount() {
        return objectCount;
    }

    public void setObjectCount(int count) {
        objectCount = count;
    }

    public BaseNode getParent() {
        return leftInput;
    }

    /**
     * Adds the <code>TupleSink</code> so that it may receive
     * <code>Tuples</code> propagated from this <code>TupleSource</code>.
     *
     * @param tupleSink
     *            The <code>TupleSink</code> to receive propagated
     *            <code>Tuples</code>.
     */
    public void addTupleSink(final LeftTupleSink tupleSink, final BuildContext context) {
        this.sink = addTupleSink(this.sink, tupleSink, context);
    }

    protected LeftTupleSinkPropagator addTupleSink(final LeftTupleSinkPropagator sinkPropagator, final LeftTupleSink tupleSink, final BuildContext context) {
        if ( sinkPropagator instanceof EmptyLeftTupleSinkAdapter ) {
            // otherwise, we use the lighter synchronous propagator
            return new SingleLeftTupleSinkAdapter( this.getPartitionId(), tupleSink );
        }

        if ( sinkPropagator instanceof SingleLeftTupleSinkAdapter ) {
            CompositeLeftTupleSinkAdapter sinkAdapter = new CompositeLeftTupleSinkAdapter( this.getPartitionId() );
            sinkAdapter.addTupleSink( sinkPropagator.getSinks()[0] );
            sinkAdapter.addTupleSink( tupleSink );
            return sinkAdapter;
        }

        ((CompositeLeftTupleSinkAdapter) sinkPropagator).addTupleSink( tupleSink );
        return sinkPropagator;
    }

    /**
     * Removes the <code>TupleSink</code>
     *
     * @param tupleSink
     *            The <code>TupleSink</code> to remove
     */
    public void removeTupleSink(final LeftTupleSink tupleSink) {
        if ( this.sink instanceof EmptyLeftTupleSinkAdapter ) {
            throw new IllegalArgumentException( "Cannot remove a sink, when the list of sinks is null" );
        }

        if ( this.sink instanceof SingleLeftTupleSinkAdapter ) {
            this.sink = EmptyLeftTupleSinkAdapter.getInstance();
        } else {
            final CompositeLeftTupleSinkAdapter sinkAdapter = (CompositeLeftTupleSinkAdapter) this.sink;
            sinkAdapter.removeTupleSink( tupleSink );
            if ( sinkAdapter.size() == 1 ) {
                this.sink = new SingleLeftTupleSinkAdapter( this.getPartitionId(), sinkAdapter.getSinks()[0] );
            }
        }
    }

    public LeftTupleSinkNode getFirstLeftTupleSinkIgnoreRemoving(TerminalNode removingTn) {
        if (removingTn == null) {
            return this.sink.getFirstLeftTupleSink();
        }

        for ( LeftTupleSink sink : this.sink.getSinks()) {
            if ( !BuildtimeSegmentUtilities.sinkNotExclusivelyAssociatedWithTerminal(removingTn, sink)) {
                // skip this node as it's being removed;
                continue;
            }
            return (LeftTupleSinkNode) sink;
        }

        throw new RuntimeException("This should always return a sink");
    }

    public LeftTupleSinkPropagator getSinkPropagator() {
        return this.sink;
    }

    public void setSourcePartitionId(BuildContext context, RuleBasePartitionId partitionId) {
        setSourcePartitionId(leftInput, context, partitionId);
    }

    protected void setSourcePartitionId(BaseNode source, BuildContext context, RuleBasePartitionId partitionId) {
        if (this.partitionId == partitionId) {
            return;
        }
        this.partitionId = partitionId;
        if (source.getPartitionId() == RuleBasePartitionId.MAIN_PARTITION) {
            setPartitionIdWithSinks( partitionId );
        } else {
            source.setPartitionId( context, partitionId );
        }
    }

    public final void setPartitionIdWithSinks( RuleBasePartitionId partitionId ) {
        this.partitionId = partitionId;
        for (LeftTupleSink sink : getSinkPropagator().getSinks()) {
            sink.setPartitionIdWithSinks( partitionId );
        }
    }

    public boolean isInUse() {
        return this.sink.size() > 0;
    }

    protected final void initMasks(BuildContext context) {
        initDeclaredMask( context );
        initInferredMask( );
    }

    protected void initDeclaredMask(BuildContext context) {
        if ( context == null || context.getLastBuiltPatterns() == null) {
            // only happens during unit tests
            declaredMask = AllSetBitMask.get();
            return;
        }

        if ( !NodeTypeEnums.isLeftInputAdapterNode(leftInput)) {
            // BetaNode's not after LIANode are not relevant for left mask property specific, so don't block anything.
            declaredMask = AllSetBitMask.get();
            return;
        }

        Pattern pattern = getLeftInputPattern( context ); // left input pattern

        ObjectType objectType = getObjectTypeForPropertyReactivity( (LeftInputAdapterNode) leftInput, pattern );

        if ( !(objectType instanceof ClassObjectType) ) {
            // Only ClassObjectType can use property specific
            declaredMask = AllSetBitMask.get();
            return;
        }

        if ( pattern != null && isPropertyReactive(context.getRuleBase(), objectType) ) {
            Collection<String> leftListenedProperties = pattern.getListenedProperties();
            List<String> accessibleProperties = getAccessibleProperties( context.getRuleBase(), objectType );
            declaredMask     = calculatePositiveMask(objectType, leftListenedProperties, accessibleProperties);
            declaredMask = setNodeConstraintsPropertyReactiveMask(declaredMask, objectType, accessibleProperties);
            negativeMask = calculateNegativeMask(objectType, leftListenedProperties, accessibleProperties);
            setLeftListenedProperties(leftListenedProperties);
        } else {
            // if property specific is not on, then accept all modification propagations
            declaredMask = AllSetBitMask.get();
        }
    }

    protected BitMask setNodeConstraintsPropertyReactiveMask(BitMask mask, ObjectType objectType, List<String> accessibleProperties) {
        return mask;
    }

    protected Pattern getLeftInputPattern( BuildContext context ) {
        return context.getLastBuiltPatterns()[1];
    }

    protected ObjectType getObjectTypeForPropertyReactivity( LeftInputAdapterNode leftInput, Pattern pattern ) {
        return pattern != null ?
               pattern.getObjectType() :
               leftInput.getParentObjectSource().getObjectTypeNode().getObjectType();
    }

    protected void setLeftListenedProperties(Collection<String> leftListenedProperties) { }

    protected void initInferredMask() {
        LeftTupleSource unwrappedLeft = unwrapLeftInput();
        if ( NodeTypeEnums.isLeftInputAdapterNode(unwrappedLeft) && ((LeftInputAdapterNode)unwrappedLeft).getParentObjectSource().getType() == NodeTypeEnums.AlphaNode ) {
            ObjectSource objectSource = ((LeftInputAdapterNode)unwrappedLeft).getParentObjectSource();
            inferredMask = objectSource.updateMask(declaredMask);
        } else {
            inferredMask = declaredMask;
        }
        inferredMask = inferredMask.resetAll(negativeMask);
    }

    private LeftTupleSource unwrapLeftInput() {
        if (leftInput.getType() == NodeTypeEnums.FromNode || leftInput.getType() == NodeTypeEnums.ReactiveFromNode) {
            return leftInput.getLeftTupleSource();
        }
        return leftInput;
    }

    public BitMask getDeclaredMask() {
        return declaredMask;
    }

    public BitMask getInferredMask() {
        return inferredMask;
    }

    protected void setInferredMask(BitMask inferredMask) {
        this.inferredMask = inferredMask;
    }

    public BitMask getNegativeMask() {
        return negativeMask;
    }

    public ObjectType getObjectType() {
        ObjectTypeNode objectTypeNode = getObjectTypeNode();
        return objectTypeNode != null ? objectTypeNode.getObjectType() : null;
    }

    public abstract boolean isLeftTupleMemoryEnabled();
}
