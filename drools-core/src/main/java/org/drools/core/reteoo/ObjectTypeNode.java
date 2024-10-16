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

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.io.Serializable;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.drools.base.InitialFact;
import org.drools.base.base.ClassObjectType;
import org.drools.base.base.ObjectType;
import org.drools.base.common.NetworkNode;
import org.drools.base.common.RuleBasePartitionId;
import org.drools.base.reteoo.NodeTypeEnums;
import org.drools.base.rule.EntryPointId;
import org.drools.base.rule.Pattern;
import org.drools.base.time.JobHandle;
import org.drools.core.common.DefaultFactHandle;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.InternalWorkingMemory;
import org.drools.core.common.PropagationContext;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.common.SuperCacheFixer;
import org.drools.core.common.UpdateContext;
import org.drools.core.impl.InternalRuleBase;
import org.drools.core.impl.WorkingMemoryReteExpireAction;
import org.drools.core.reteoo.builder.BuildContext;
import org.drools.core.time.Job;
import org.drools.core.time.JobContext;
import org.drools.core.time.impl.DefaultJobHandle;
import org.drools.util.bitmask.BitMask;
import org.drools.util.bitmask.EmptyBitMask;

import static org.drools.base.rule.TypeDeclaration.NEVER_EXPIRES;

/**
 * <code>ObjectTypeNodes<code> are responsible for filtering and propagating the matching
 * fact assertions propagated from the <code>Rete</code> node using <code>ObjectType</code> interface.
 * <p/>
 * The assert and retract methods do not attempt to filter as this is the role of the <code>Rete</code>
 * node which builds up a cache of matching <code>ObjectTypdeNodes</code>s for each asserted object, using
 * the <code>matches(Object object)</code> method. Incorrect propagation in these methods is not checked and
 * will result in <code>ClassCastExpcections</code> later on in the network.
 * <p/>
 * Filters <code>Objects</code> coming from the <code>Rete</code> using a
 * <code>ObjectType</code> semantic module.
 *
 * @see Rete
 */
public class ObjectTypeNode extends ObjectSource implements ObjectSink {


    private static final long serialVersionUID = 510l;

    /**
     * The <code>ObjectType</code> semantic module.
     */
    protected ObjectType objectType;

    private long expirationOffset = -1;

    /* always dirty after serialisation */
    private transient volatile boolean dirty;

    /* reset counter when dirty */
    protected transient IdGenerator idGenerator;

    public int getOtnIdCounter() {
        return idGenerator.otnIdCounter;
    }

    public ObjectTypeNode() {

    }

    /**
     * Construct given a semantic <code>ObjectType</code> and the provided
     * unique id. All <code>ObjectTypdeNode</code> have node memory.
     *
     * @param id         The unique id for the node.
     * @param objectType The semantic object-type differentiator.
     */
    public ObjectTypeNode(final int id,
                          final EntryPointNode source,
                          final ObjectType objectType,
                          final BuildContext context) {
        super(id,
              RuleBasePartitionId.MAIN_PARTITION,
              source,
              context.getRuleBase().getRuleBaseConfiguration().getAlphaNodeHashingThreshold(),
              context.getRuleBase().getRuleBaseConfiguration().getAlphaNodeRangeIndexThreshold());
        this.objectType = objectType;
        this.idGenerator = new IdGenerator(id);
        this.dirty = true;
        this.hashcode = calculateHashCode();
        initMemoryId( context );
    }

    public void setupParallelExecution(InternalRuleBase kbase) {
        if (objectType == ClassObjectType.InitialFact_ObjectType) {
            return;
        }
        CompositePartitionAwareObjectSinkAdapter partitionedSink = new CompositePartitionAwareObjectSinkAdapter(kbase.getParallelEvaluationSlotsCount());
        for ( ObjectSink objectSink : this.sink.getSinks()) {
            partitionedSink.addObjectSink(objectSink, alphaNodeHashingThreshold, alphaNodeRangeIndexThreshold);
        }
        this.sink = partitionedSink;
    }

    private static class IdGenerator {
        private final int otnId;
        private int otnIdCounter;

        private IdGenerator(int otnId) {
            this.otnId = otnId;
        }

        private ObjectTypeNodeId nextId() {
            return new ObjectTypeNodeId(otnId, otnIdCounter++);
        }

        private void reset() {
            otnIdCounter = 0;
        }
    }

    public static final ObjectTypeNodeId DEFAULT_ID = new ObjectTypeNodeId(-1, 0);

    @Override
    public int getType() {
        return NodeTypeEnums.ObjectTypeNode;
    }

    /**
     * Retrieve the semantic <code>ObjectType</code> differentiator.
     *
     * @return The semantic <code>ObjectType</code> differentiator.
     */
    public ObjectType getObjectType() {
        return this.objectType;
    }

    /**
     * Returns the partition ID for which this node belongs to
     */
    @Override
    public RuleBasePartitionId getPartitionId() {
        return RuleBasePartitionId.MAIN_PARTITION;
    }

    @Override
    public BitMask calculateDeclaredMask(Pattern pattern, ObjectType modifiedType, List<String> settableProperties) {
        return EmptyBitMask.get();
    }

    public boolean isAssignableFrom(final ObjectType objectType) {
        return this.objectType.isAssignableFrom(objectType);
    }

    public void assertInitialFact(final InternalFactHandle factHandle,
                                  final PropagationContext context,
                                  final ReteEvaluator reteEvaluator) {
        checkDirty();
        propagateAssert(factHandle, context, reteEvaluator);
    }

    protected void checkDirty() {
        if (dirty) {
            synchronized (this) {
                if (dirty) {
                    resetIdGenerator();
                    updateTupleSinkId( this, this );
                    dirty = false;
                }
            }
        }
    }

    /**
     * Propagate the <code>FactHandleimpl</code> through the <code>Rete</code> network. All
     * <code>FactHandleImpl</code> should be remembered in the node memory, so that later runtime rule attachmnents
     * can have the matched facts propagated to them.
     *
     * @param factHandle    The fact handle.
     * @param context       The propagation context.
     * @param reteEvaluator The working memory session.
     */
    @Override
    public void assertObject(final InternalFactHandle factHandle,
                             final PropagationContext context,
                             final ReteEvaluator reteEvaluator) {
    }

    public void propagateAssert(InternalFactHandle factHandle, PropagationContext context, ReteEvaluator reteEvaluator) {
        checkDirty();
        this.sink.propagateAssertObject(factHandle, context, reteEvaluator);
    }

    /**
     * Retract the <code>FactHandleimpl</code> from the <code>Rete</code> network. Also remove the
     * <code>FactHandleImpl</code> from the node memory.
     *
     * @param factHandle    The fact handle.
     * @param context       The propagation context.
     * @param reteEvaluator The working memory session.
     */
    public void retractObject(final InternalFactHandle factHandle,
                              final PropagationContext context,
                              final ReteEvaluator reteEvaluator) {
        checkDirty();

        doRetractObject( factHandle, context, reteEvaluator);
    }

    public void retractObject(final InternalFactHandle factHandle,
                              final PropagationContext context,
                              final ReteEvaluator reteEvaluator,
                              int partition) {
        checkDirty();

        retractRightTuples( factHandle, context, reteEvaluator, partition );
        retractLeftTuples( factHandle, context, reteEvaluator, partition );
    }

    public static void doRetractObject(final InternalFactHandle factHandle,
                                       final PropagationContext context,
                                       final ReteEvaluator reteEvaluator) {
        retractRightTuples( factHandle, context, reteEvaluator );
        retractLeftTuples( factHandle, context, reteEvaluator );
    }

    public static void expireLeftTuple(TupleImpl leftTuple) {
        if (!leftTuple.isExpired()) {
            leftTuple.setExpired();
            for ( TupleImpl child = leftTuple.getFirstChild(); child != null; child = child.getHandleNext() ) {
                expireLeftTuple(child);
            }
            for ( TupleImpl peer = leftTuple.getPeer(); peer != null; peer = peer.getPeer() ) {
                expireLeftTuple(peer);
            }
        }
    }

    public static void expireRightTuple(TupleImpl rightTuple) {
        for ( TupleImpl child = rightTuple.getFirstChild(); child != null; child = child.getHandleNext() ) {
            expireLeftTuple(child);
        }
    }

    public static void retractLeftTuples( InternalFactHandle factHandle, PropagationContext context, ReteEvaluator reteEvaluator ) {
        factHandle.forEachLeftTuple( lt -> {
            ((LeftInputAdapterNode) SuperCacheFixer.getLeftTupleSource(lt)).retractLeftTuple(lt, context, reteEvaluator);
        } );
        factHandle.clearLeftTuples();
    }

    public static void retractLeftTuples( InternalFactHandle factHandle, PropagationContext context, ReteEvaluator reteEvaluator, int partition ) {
        DefaultFactHandle.CompositeLinkedTuples linkedTuples = ( (DefaultFactHandle.CompositeLinkedTuples) factHandle.getLinkedTuples() );
        linkedTuples.forEachLeftTuple( partition, lt -> {
            ((LeftInputAdapterNode) SuperCacheFixer.getLeftTupleSource(lt)).retractLeftTuple(lt, context, reteEvaluator);
        } );
        linkedTuples.clearLeftTuples(partition);
    }

    public static void retractRightTuples( InternalFactHandle factHandle, PropagationContext context, ReteEvaluator reteEvaluator ) {
        factHandle.forEachRightTuple( rt -> ((RightTuple)rt).retractTuple(context, reteEvaluator));
        factHandle.clearRightTuples();
    }

    public static void retractRightTuples( InternalFactHandle factHandle, PropagationContext context, ReteEvaluator reteEvaluator, int partition ) {
        DefaultFactHandle.CompositeLinkedTuples linkedTuples = ( (DefaultFactHandle.CompositeLinkedTuples) factHandle.getLinkedTuples() );
        linkedTuples.forEachRightTuple( partition, rt -> ((RightTuple)rt).retractTuple(context, reteEvaluator));
        linkedTuples.clearRightTuples(partition);
    }

    protected void resetIdGenerator() {
        idGenerator.reset();
    }

    @Override
    public void modifyObject(InternalFactHandle factHandle, ModifyPreviousTuples modifyPreviousTuples, PropagationContext context, ReteEvaluator reteEvaluator) {
        checkDirty();
        this.sink.propagateModifyObject(factHandle,
                                        modifyPreviousTuples,
                                        context.adaptModificationMaskForObjectType(objectType, reteEvaluator),
                                        reteEvaluator);
    }

    @Override
    public void updateSink(ObjectSink sink, PropagationContext context, InternalWorkingMemory workingMemory) {
        checkDirty();
        Class<?> classType = ((ClassObjectType) getObjectType()).getClassType();
        if (InitialFact.class.isAssignableFrom(classType)) {
            sink.assertObject(workingMemory.getInitialFactHandle(), context, workingMemory);
        } else {
            Iterator<InternalFactHandle> it = getFactHandlesIterator(workingMemory);
            while (it.hasNext()) {
                sink.assertObject(it.next(), context, workingMemory);
            }
        }
    }

    public Iterator<InternalFactHandle> getFactHandlesIterator(InternalWorkingMemory workingMemory) {
        Class<?> classType = ((ClassObjectType) getObjectType()).getClassType();
        return InitialFact.class.isAssignableFrom(classType) ?
                Collections.singleton(workingMemory.getInitialFactHandle()).iterator() :
                workingMemory.getEntryPoint(((EntryPointNode)source).getEntryPoint().getEntryPointId()).getObjectStore().getStoreForClass(classType).iterator();
    }

    /**
     * Rete needs to know that this ObjectTypeNode has been added
     */
    @Override
    public void doAttach(BuildContext context) {
        super.doAttach(context);
        this.source.addObjectSink(this);

        EntryPointNode epn = context.getRuleBase().getRete().getEntryPointNode( ((EntryPointNode) source).getEntryPoint() );
        if (epn == null) {
            return;
        }

        ObjectTypeConf objectTypeConf = epn.getTypeConfReg().getConfForObjectType( objectType );
        if ( objectTypeConf != null ) {
            objectTypeConf.resetCache();
        }
    }

    @Override
    public void networkUpdated(UpdateContext updateContext) {
        this.dirty = true;
    }

    protected static void updateTupleSinkId(ObjectTypeNode otn,
                                            ObjectSource source) {
        for (ObjectSink sink : source.sink.getSinks()) {
            if (NodeTypeEnums.isBetaRightNode(sink)) {
                ((RightInputAdapterNode)sink).setInputOtnId(otn.nextOtnId());
            } else if (NodeTypeEnums.isLeftInputAdapterNode(sink)) {
                for (LeftTupleSink liaChildSink : ((LeftInputAdapterNode) sink).getSinkPropagator().getSinks()) {
                    liaChildSink.setInputOtnId(otn.nextOtnId());
                }
            } else if (sink.getType() == NodeTypeEnums.WindowNode) {
                ((WindowNode)sink).setRightInputOtnId(otn.nextOtnId());
                updateTupleSinkId(otn, (WindowNode) sink);
            } else if (sink.getType() == NodeTypeEnums.AlphaNode) {
                updateTupleSinkId(otn, (AlphaNode) sink);
            }
        }
    }

    public ObjectTypeNodeId nextOtnId() {
        return idGenerator.nextId();
    }

    /**
    * OTN needs to override remove to avoid releasing the node ID, since OTN are
    * never removed from the rulebase in the current implementation
    */
    @Override
    public boolean remove(RuleRemovalContext context, ReteooBuilder builder) {
            return doRemove(context, builder);
    }

    /**
     * OTN needs to override remove to avoid releasing the node ID, since OTN are
     * never removed from the rulebase in the current implementation
     */
    @Override
    protected boolean doRemove(final RuleRemovalContext context,
                               final ReteooBuilder builder) {
        return false;
    }

    @Override
    public String toString() {
        return "[ObjectTypeNode(" + this.id + ")::" + ((EntryPointNode) this.source).getEntryPoint() + " objectType=" + this.objectType + " expiration=" + this.getExpirationOffset() + "ms ]";
    }

    private int calculateHashCode() {
        return (this.objectType != null ? this.objectType.hashCode() : 0) * 37 + (this.source != null ? this.source.hashCode() : 0) * 31;
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }

        if (((NetworkNode)object).getType() != NodeTypeEnums.ObjectTypeNode || this.hashCode() != object.hashCode() ) {
            return false;
        }

        ObjectTypeNode other = (ObjectTypeNode)object;
        return this.source.getId() == other.source.getId() && this.objectType.equals( other.objectType );
    }

    /**
     * @return the entryPoint
     */
    public EntryPointId getEntryPoint() {
        return ((EntryPointNode) this.source).getEntryPoint();
    }

    public long getExpirationOffset() {
        return expirationOffset;
    }

    public void setExpirationOffset(long expirationOffset) {
        this.expirationOffset = expirationOffset;
    }

    public void mergeExpirationOffset(ObjectTypeNode other) {
        setExpirationOffset( expirationOffset == NEVER_EXPIRES || other.expirationOffset == NEVER_EXPIRES ?
                             NEVER_EXPIRES :
                             Math.max(expirationOffset, other.expirationOffset) );
    }

    public static class ExpireJob
            implements
            Job, Serializable {

        @Override
        public void execute(JobContext ctx) {
            ExpireJobContext context = (ExpireJobContext) ctx;

            context.reteEvaluator.addPropagation(context.expireAction);
            context.getExpireAction().getFactHandle().removeJob( (DefaultJobHandle) context.getJobHandle());
        }
    }

    public static class ExpireJobContext
            implements
            JobContext,
            Externalizable {
        public WorkingMemoryReteExpireAction expireAction;
        public transient ReteEvaluator         reteEvaluator;
        public JobHandle                     handle;

        public ExpireJobContext() {
        }

        public ExpireJobContext(WorkingMemoryReteExpireAction expireAction,
                                ReteEvaluator reteEvaluator) {
            super();
            this.expireAction = expireAction;
            this.reteEvaluator = reteEvaluator;
        }

        @Override
        public JobHandle getJobHandle() {
            return this.handle;
        }

        @Override
        public void setJobHandle(JobHandle jobHandle) {
            this.handle = jobHandle;
        }

        public WorkingMemoryReteExpireAction getExpireAction() {
            return expireAction;
        }

        public ReteEvaluator getReteEvaluator() {
            return reteEvaluator;
        }

        public void setReteEvaluator(ReteEvaluator reteEvaluator) {
            this.reteEvaluator = reteEvaluator;
        }

        public JobHandle getHandle() {
            return handle;
        }

        public void setHandle(JobHandle handle) {
            this.handle = handle;
        }

        @Override
        public void readExternal(ObjectInput in) throws IOException,
                                                        ClassNotFoundException {
            this.expireAction = (WorkingMemoryReteExpireAction) in.readObject();
            this.handle = (JobHandle) in.readObject();
        }

        @Override
        public void writeExternal(ObjectOutput out) throws IOException {
            out.writeObject(expireAction);
            out.writeObject(handle);
        }
    }

    @Override
    public void byPassModifyToBetaNode(InternalFactHandle factHandle,
                                       ModifyPreviousTuples modifyPreviousTuples,
                                       PropagationContext context,
                                       ReteEvaluator reteEvaluator) {
        throw new UnsupportedOperationException("This should never get called, as the PropertyReactive first happens at the AlphaNode");
    }
}
