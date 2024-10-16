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
import java.util.Optional;

import org.drools.base.base.ObjectType;
import org.drools.base.common.NetworkNode;
import org.drools.base.common.RuleBasePartitionId;
import org.drools.base.reteoo.BaseTerminalNode;
import org.drools.base.reteoo.NodeTypeEnums;
import org.drools.base.rule.Pattern;
import org.drools.base.rule.constraint.AlphaNodeFieldConstraint;
import org.drools.core.common.InternalFactHandle;
import org.drools.core.common.InternalWorkingMemory;
import org.drools.core.common.PropagationContext;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.reteoo.builder.BuildContext;
import org.drools.util.bitmask.BitMask;
import org.kie.api.definition.rule.Rule;

/**
 * <code>AlphaNodes</code> are nodes in the <code>Rete</code> network used
 * to apply <code>FieldConstraint<.code>s on asserted fact
 * objects where the <code>FieldConstraint</code>s have no dependencies on any other of the facts in the current <code>Rule</code>.
 *
 * @see AlphaNodeFieldConstraint
 */
public class AlphaNode extends ObjectSource
        implements
        ObjectSinkNode {

    private static final long serialVersionUID = 510l;

    /**
     * The <code>FieldConstraint</code>
     */
    protected AlphaNodeFieldConstraint constraint;

    private ObjectSinkNode previousRightTupleSinkNode;
    private ObjectSinkNode nextRightTupleSinkNode;

    public AlphaNode() {

    }

    /**
     * Construct an <code>AlphaNode</code> with a unique id using the provided
     * <code>FieldConstraint</code> and the given <code>ObjectSource</code>.
     * Set the boolean flag to true if the node is supposed to have local
     * memory, or false otherwise. Memory is optional for <code>AlphaNode</code>s
     * and is only of benefic when adding additional <code>Rule</code>s at runtime.
     *
     * @param id           Node's ID
     * @param constraint   Node's constraints
     * @param objectSource Node's object source
     */
    public AlphaNode(final int id,
                     final AlphaNodeFieldConstraint constraint,
                     final ObjectSource objectSource,
                     final BuildContext context) {
        super(id,
              context.getPartitionId(),
              objectSource,
              context.getRuleBase().getRuleBaseConfiguration().getAlphaNodeHashingThreshold(),
              context.getRuleBase().getRuleBaseConfiguration().getAlphaNodeRangeIndexThreshold());

        this.constraint = constraint.cloneIfInUse();
        this.constraint.registerEvaluationContext(context);

        initDeclaredMask(context);
        hashcode = calculateHashCode();
    }

    /**
     * Retruns the <code>FieldConstraint</code>
     *
     * @return <code>FieldConstraint</code>
     */
    public AlphaNodeFieldConstraint getConstraint() {
        return this.constraint;
    }

    public int getType() {
        return NodeTypeEnums.AlphaNode;
    }

    public void doAttach(BuildContext context) {
        super.doAttach(context);
        this.source.addObjectSink(this);
    }

    @Override
    public void setPartitionId(BuildContext context, RuleBasePartitionId partitionId) {
        if (this.partitionId != null && this.partitionId != partitionId) {
            if (source.getType() == NodeTypeEnums.AlphaNode) {
                source.setPartitionId( context, partitionId );
            }
            source.sink.changeSinkPartition( this, this.partitionId, partitionId, source.alphaNodeHashingThreshold, source.alphaNodeRangeIndexThreshold );
        }
        this.partitionId = partitionId;
    }

    public void assertObject(final InternalFactHandle factHandle,
                             final PropagationContext context,
                             final ReteEvaluator reteEvaluator) {
        if (this.constraint.isAllowed(factHandle, reteEvaluator)) {
            this.sink.propagateAssertObject( factHandle, context, reteEvaluator );
        }
    }

    public void modifyObject(final InternalFactHandle factHandle,
                             final ModifyPreviousTuples modifyPreviousTuples,
                             final PropagationContext context,
                             final ReteEvaluator reteEvaluator) {
        if (context.getModificationMask().intersects(inferredMask)) {

            if (this.constraint.isAllowed(factHandle, reteEvaluator)) {
                this.sink.propagateModifyObject(factHandle,
                        modifyPreviousTuples,
                        context,
                        reteEvaluator);
            }
        } else {
            byPassModifyToBetaNode(factHandle, modifyPreviousTuples, context, reteEvaluator);
        }
    }

    public void byPassModifyToBetaNode(final InternalFactHandle factHandle,
                                       final ModifyPreviousTuples modifyPreviousTuples,
                                       final PropagationContext context,
                                       final ReteEvaluator reteEvaluator) {
        sink.byPassModifyToBetaNode(factHandle, modifyPreviousTuples, context, reteEvaluator);
    }


    public void updateSink(final ObjectSink sink,
                           final PropagationContext context,
                           final InternalWorkingMemory workingMemory) {
        // get the objects from the parent
        ObjectSinkUpdateAdapter adapter = new ObjectSinkUpdateAdapter(sink, this.constraint);
        this.source.updateSink(adapter, context, workingMemory);
    }

    public String toString() {
        return "[AlphaNode(" + this.id + ") constraint=" + this.constraint + "]";
    }

    private int calculateHashCode() {
        return (this.source != null ? this.source.hashCode() : 0) * 37 + (this.constraint != null ? this.constraint.hashCode() : 0) * 31;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        if ( ((NetworkNode)object).getType() != NodeTypeEnums.AlphaNode || this.hashCode() != object.hashCode() ) {
            return false;
        }

        AlphaNode other = (AlphaNode) object;
        return this.source.getId() == other.source.getId() && constraint.equals(other.constraint, getRuleBase());
    }

    /**
     * Returns the next node
     *
     * @return The next ObjectSinkNode
     */
    public ObjectSinkNode getNextObjectSinkNode() {
        return this.nextRightTupleSinkNode;
    }

    /**
     * Sets the next node
     *
     * @param next The next ObjectSinkNode
     */
    public void setNextObjectSinkNode(final ObjectSinkNode next) {
        this.nextRightTupleSinkNode = next;
    }

    /**
     * Returns the previous node
     *
     * @return The previous ObjectSinkNode
     */
    public ObjectSinkNode getPreviousObjectSinkNode() {
        return this.previousRightTupleSinkNode;
    }

    /**
     * Sets the previous node
     *
     * @param previous The previous ObjectSinkNode
     */
    public void setPreviousObjectSinkNode(final ObjectSinkNode previous) {
        this.previousRightTupleSinkNode = previous;
    }

    /**
     * Used with the updateSink method, so that the parent ObjectSource
     * can  update the  TupleSink
     */
    private static class ObjectSinkUpdateAdapter
            implements
            ObjectSink {
        private final ObjectSink sink;
        private final AlphaNodeFieldConstraint constraint;

        public ObjectSinkUpdateAdapter(final ObjectSink sink,
                                       final AlphaNodeFieldConstraint constraint) {
            this.sink = sink;
            this.constraint = constraint;
        }

        public void assertObject(final InternalFactHandle handle,
                                 final PropagationContext propagationContext,
                                 final ReteEvaluator reteEvaluator) {
            try {
                if (this.constraint.isAllowed(handle, reteEvaluator)) {
                    this.sink.assertObject(handle, propagationContext, reteEvaluator);
                }
            } catch (RuntimeException e) {
                // Forcing the jitting of a constraint the eveluation may throw a CCE
                // it is safe to ignore it since this means that the old fact is no longer compatible
                // with the updated constraint and then its propagation should be skipped
                if (!(e.getCause() instanceof ClassCastException)) {
                    throw e;
                }
            }


        }

        public int getId() {
            return 0;
        }

        public RuleBasePartitionId getPartitionId() {
            return this.sink.getPartitionId();
        }

        public void modifyObject(final InternalFactHandle factHandle,
                                 final ModifyPreviousTuples modifyPreviousTuples,
                                 final PropagationContext context,
                                 final ReteEvaluator reteEvaluator) {
            throw new UnsupportedOperationException("This method should NEVER EVER be called");
        }

        public void byPassModifyToBetaNode(InternalFactHandle factHandle,
                                           ModifyPreviousTuples modifyPreviousTuples,
                                           PropagationContext context,
                                           ReteEvaluator reteEvaluator) {
        }

        public int getType() {
            return NodeTypeEnums.AlphaNode;
        }

        @Override public Rule[] getAssociatedRules() {
            return sink.getAssociatedRules();
        }

        public boolean isAssociatedWith(Rule rule) {
            return sink.isAssociatedWith(rule);
        }

        @Override
        public void addAssociatedTerminal(BaseTerminalNode terminalNode) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void removeAssociatedTerminal(BaseTerminalNode terminalNode) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getAssociatedTerminalsSize() {
            return sink.getAssociatedTerminalsSize();
        }

        @Override
        public boolean hasAssociatedTerminal(BaseTerminalNode terminalNode) {
            return sink.hasAssociatedTerminal(terminalNode);
        }

        @Override
        public NetworkNode[] getSinks() {
            return new NetworkNode[0];
        }
    }

    public BitMask calculateDeclaredMask(Pattern pattern, ObjectType objectType, List<String> settableProperties) {
        return constraint.getListenedPropertyMask(Optional.ofNullable(pattern), objectType, settableProperties);
    }

    @Override
    public BitMask getDeclaredMask() {
        return declaredMask;
    }

    public BitMask getInferredMask() {
        return inferredMask;
    }
}
