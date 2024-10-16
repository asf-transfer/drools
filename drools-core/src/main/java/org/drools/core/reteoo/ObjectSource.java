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

import java.util.List;

import org.drools.base.base.ObjectType;
import org.drools.base.common.RuleBasePartitionId;
import org.drools.base.reteoo.NodeTypeEnums;
import org.drools.base.rule.Pattern;
import org.drools.core.common.BaseNode;
import org.drools.core.common.DefaultFactHandle;
import org.drools.core.common.InternalWorkingMemory;
import org.drools.core.common.PropagationContext;
import org.drools.core.common.UpdateContext;
import org.drools.core.impl.InternalRuleBase;
import org.drools.core.reteoo.builder.BuildContext;
import org.drools.util.bitmask.AllSetBitMask;
import org.drools.util.bitmask.BitMask;
import org.drools.util.bitmask.EmptyBitMask;

import static org.drools.base.reteoo.PropertySpecificUtil.getAccessibleProperties;
import static org.drools.base.reteoo.PropertySpecificUtil.isPropertyReactive;

/**
 * A source of <code>FactHandle</code>s for an <code>ObjectSink</code>.
 *
 * <p>
 * Nodes that propagate <code>FactHandleImpl</code> extend this class.
 * </p>
 *
 * @see ObjectSource
 * @see DefaultFactHandle
 */
public abstract class ObjectSource extends BaseNode {



    /** The destination for <code>FactHandleImpl</code>. */
    protected ObjectSinkPropagator sink;

    protected ObjectSource         source;

    protected int                  alphaNodeHashingThreshold;
    protected int                  alphaNodeRangeIndexThreshold;


    protected BitMask declaredMask = EmptyBitMask.get();
    protected BitMask inferredMask = EmptyBitMask.get();
    
    // ------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------
    public ObjectSource() {

    }

    /**
     * Single parameter constructor that specifies the unique id of the node.
     */
    protected ObjectSource(int id, RuleBasePartitionId partitionId) {
        this( id, partitionId, null, 3, 3);
    }

    protected ObjectSource(int id, final ObjectSource objectSource, RuleBasePartitionId partitionId) {
        this( id, partitionId, objectSource, 3, 3);
    }

    /**
     * Single parameter constructor that specifies the unique id of the node.
     */
    ObjectSource(final int id,
                 final RuleBasePartitionId partitionId,
                 final ObjectSource objectSource,
                 final int alphaNodeHashingThreshold,
                 final int alphaNodeRangeIndexThreshold) {
        super(id, partitionId);
        this.source = objectSource;
        this.alphaNodeHashingThreshold = alphaNodeHashingThreshold;
        this.alphaNodeRangeIndexThreshold = alphaNodeRangeIndexThreshold;
        this.sink = EmptyObjectSinkAdapter.getInstance();
    }

    public InternalRuleBase getRuleBase() {
        return source.getRuleBase();
    }

    public BaseNode getParent() {
        return source;
    }

    public void initDeclaredMask(BuildContext context) {
        if ( context == null || context.getLastBuiltPatterns() == null ) {
            // only happens during unit tests
            declaredMask = AllSetBitMask.get();
            return;
        }
        
        Pattern pattern = context.getLastBuiltPatterns()[0];
        ObjectType objectType = pattern.getObjectType();
        
        if ( isPropertyReactive(context.getRuleBase(), objectType) ) {
            List<String> settableProperties = getAccessibleProperties( context.getRuleBase(), objectType );
            declaredMask = calculateDeclaredMask(pattern, objectType, settableProperties);
        } else {
            // if property specific is not on, then accept all modification propagations
            declaredMask = AllSetBitMask.get();
        }
    }
    
    public abstract BitMask calculateDeclaredMask(Pattern pattern, ObjectType modifiedType, List<String> settableProperties);
    
    public void resetInferredMask() {
        this.inferredMask = EmptyBitMask.get();
    }
    
    public BitMask updateMask(BitMask mask) {
        BitMask returnMask;
        if ( source.getType() != NodeTypeEnums.ObjectTypeNode ) {
            returnMask = source.updateMask( declaredMask.clone().setAll( mask ) );
        } else { // else ObjectTypeNode
            returnMask = declaredMask.clone().setAll( mask );
        }
        inferredMask = inferredMask.setAll( returnMask );
        return returnMask;
    }

    @Override
    public void setPartitionId(BuildContext context, RuleBasePartitionId partitionId) {
        if (this.partitionId != null && this.partitionId != partitionId) {
            source.sink.changeSinkPartition( (ObjectSink)this, this.partitionId, partitionId, source.alphaNodeHashingThreshold, source.alphaNodeRangeIndexThreshold );
        }
        this.partitionId = partitionId;
    }

    public final RuleBasePartitionId setSourcePartitionId(RuleBasePartitionId partitionId) {
        if (this.partitionId == null || this.partitionId == partitionId) {
            this.partitionId = partitionId;
            return partitionId;
        }
        this.partitionId = partitionId;
        if (source.getPartitionId() == RuleBasePartitionId.MAIN_PARTITION) {
            setPartitionIdWithSinks( partitionId );
        } else {
            source.setSourcePartitionId( partitionId );
        }
        return partitionId;
    }

    public final void setPartitionIdWithSinks( RuleBasePartitionId partitionId ) {
        this.partitionId = partitionId;
        for (ObjectSink sink : getObjectSinkPropagator().getSinks()) {
            ( (ObjectSinkNode) sink ).setPartitionIdWithSinks( partitionId );
        }
    }

    /**
     * Adds the <code>ObjectSink</code> so that it may receive
     * <code>FactHandleImpl</code> propagated from this
     * <code>ObjectSource</code>.
     *
     * @param objectSink
     *            The <code>ObjectSink</code> to receive propagated
     *            <code>FactHandleImpl</code>.
     */
    public void addObjectSink(final ObjectSink objectSink) {
        this.sink = this.sink.addObjectSink( objectSink, this.alphaNodeHashingThreshold, this.alphaNodeRangeIndexThreshold );
    }

    /**
     * Removes the <code>ObjectSink</code>
     *
     * @param objectSink
     *            The <code>ObjectSink</code> to remove
     */
    public void removeObjectSink(final ObjectSink objectSink) {
        this.sink = this.sink.removeObjectSink( objectSink );
    }

    public abstract void updateSink(ObjectSink sink, PropagationContext context, InternalWorkingMemory workingMemory);

    public void networkUpdated(UpdateContext updateContext) {
        this.source.networkUpdated(updateContext);
    }

    public ObjectSinkPropagator getObjectSinkPropagator() {
        return this.sink;
    }

    public void setObjectSinkPropagator(ObjectSinkPropagator sink) {
        this.sink = sink;
    }

    public boolean isInUse() {
        return this.sink.size() > 0;
    }
    
    protected boolean doRemove(final RuleRemovalContext context,
                            final ReteooBuilder builder) {
        if ( !isInUse() && NodeTypeEnums.isObjectSink(this)) {
            this.source.removeObjectSink((ObjectSink) this);
            return true;
        }
        return false;
    }

    @Override
    public ObjectTypeNode getObjectTypeNode() {
        ObjectSource source = this;
        while (source != null) {
            if (source.getType() ==  NodeTypeEnums.ObjectTypeNode) {
                return (ObjectTypeNode)source;
            }
            source = source.source;
        }
        return null;
    }

    public BitMask getDeclaredMask() {
        return declaredMask;
    }

    public BitMask getInferredMask() {
        return inferredMask;
    }
}
