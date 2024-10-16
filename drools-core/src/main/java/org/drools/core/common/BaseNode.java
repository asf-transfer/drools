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
package org.drools.core.common;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.drools.base.common.NetworkNode;
import org.drools.base.common.RuleBasePartitionId;
import org.drools.base.reteoo.BaseTerminalNode;
import org.drools.core.reteoo.EntryPointNode;
import org.drools.core.reteoo.LeftTupleSource;
import org.drools.core.reteoo.ObjectSource;
import org.drools.core.reteoo.ObjectTypeNode;
import org.drools.core.reteoo.ReteooBuilder;
import org.drools.core.reteoo.RuleRemovalContext;
import org.drools.core.reteoo.Sink;
import org.drools.core.reteoo.TerminalNode;
import org.drools.core.reteoo.builder.BuildContext;
import org.drools.util.bitmask.BitMask;
import org.kie.api.definition.rule.Rule;
import org.drools.base.reteoo.NodeTypeEnums;

/**
 * The base class for all Rete nodes.
 */
public abstract class BaseNode
    implements
        NetworkNode {

    protected int                        id;

    protected int                        memoryId = -1;

    protected RuleBasePartitionId partitionId;
    protected Set<Rule>                  associations;

    private Map<Integer, TerminalNode> associatedTerminals;

    private   boolean                    streamMode;

    protected int                        hashcode;

    public BaseNode() {

    }

    /**
     * All nodes have a unique id, set in the constructor.
     *
     * @param id
     *      The unique id
     */
    public BaseNode(final int id,
                    final RuleBasePartitionId partitionId) {
        super();
        this.id = id;
        this.partitionId = partitionId;
        this.associations = new HashSet<>();
        this.associatedTerminals = new HashMap<>();
    }

    /* (non-Javadoc)
     * @see org.kie.spi.ReteooNode#getId()
     */
    public int getId() {
        return this.id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getMemoryId() {
        if (memoryId < 0) {
            throw new UnsupportedOperationException();
        }
        return memoryId;
    }

    protected void initMemoryId( BuildContext context ) {
        if (context != null && this instanceof MemoryFactory) {
            memoryId = context.getNextMemoryId();
        }
    }

    public boolean isStreamMode() {
        return this.streamMode;
    }

    protected void setStreamMode(boolean streamMode) {
        this.streamMode = streamMode;
    }

    public abstract BaseNode getParent();

    /**
     * Attaches the node into the network. Usually to the parent <code>ObjectSource</code> or <code>TupleSource</code>
     */
    public void attach(BuildContext context) {
        // do common shared code here, so it executes for all nodes
        doAttach(context);
        // do common shared code here, so it executes for all nodes
    }

    public void doAttach(BuildContext context) {

    }


    /**
     * A method that is called for all nodes whose network below them
     * changed, after the change is complete, providing them with an opportunity
     * for state update
     */
    public abstract void networkUpdated(UpdateContext updateContext);

    public boolean remove(RuleRemovalContext context,
                          ReteooBuilder builder) {
        boolean removed = doRemove( context, builder );
        if ( !this.isInUse() && !(NodeTypeEnums.EntryPointNode == getType()) ) {
            builder.releaseId(this);
        }
        return removed;
    }

    /**
     * Removes the node from the network. Usually from the parent <code>ObjectSource</code> or <code>TupleSource</code>
     */
    protected abstract boolean doRemove(RuleRemovalContext context,
                                        ReteooBuilder builder);

    /**
     * Returns true in case the current node is in use (is referenced by any other node)
     */
    public abstract boolean isInUse();

    public abstract ObjectTypeNode getObjectTypeNode();

    public String toString() {
        return "[" + this.getClass().getSimpleName() + "(" + this.id + ")]";
    }

    /**
     * Returns the partition ID for which this node belongs to
     */
    public RuleBasePartitionId getPartitionId() {
        return this.partitionId;
    }

    /**
     * Sets the partition this node belongs to
     */
    public void setPartitionId(BuildContext context, RuleBasePartitionId partitionId) {
        this.partitionId = partitionId;
    }

    /**
     * Associates this node with the give rule
     */
    public void addAssociation(Rule rule, BuildContext context) {
        this.associations.add( rule );
    }

    /**
     * Removes the association to the given rule from the
     * associations map.
     */
    public boolean removeAssociation( Rule rule, RuleRemovalContext context) {
        return this.associations.remove(rule);
    }

    public int getAssociationsSize() {
        return this.associations.size();
    }

    public Rule[] getAssociatedRules() {
        return this.associations.toArray( new Rule[this.associations.size()] );
    }

    public boolean isAssociatedWith( Rule rule ) {
        return this.associations.contains( rule );
    }

    @Override
    public void addAssociatedTerminal(BaseTerminalNode terminalNode) {
        associatedTerminals.put(terminalNode.getId(),(TerminalNode) terminalNode);
    }

    @Override
    public void removeAssociatedTerminal(BaseTerminalNode terminalNode) {
        associatedTerminals.remove(terminalNode.getId());
    }

    public int getAssociatedTerminalsSize() {
        return associatedTerminals.size();
    }

    public boolean hasAssociatedTerminal(BaseTerminalNode terminalNode) {
        return associatedTerminals.containsKey(terminalNode.getId());
    }

    @Override
    public final int hashCode() {
        return hashcode;
    }

    public NetworkNode[] getSinks() {
        Sink[] sinks = null;
        if (NodeTypeEnums.EntryPointNode == getType() ) {
            EntryPointNode source = (EntryPointNode) this;
            Collection<ObjectTypeNode> otns = source.getObjectTypeNodes().values();
            sinks = otns.toArray(new Sink[otns.size()]);
        } else if (NodeTypeEnums.isObjectSource(this)) {
            ObjectSource source = (ObjectSource) this;
            sinks = source.getObjectSinkPropagator().getSinks();
        } else if (NodeTypeEnums.isLeftTupleSource(this)) {
            LeftTupleSource source = (LeftTupleSource) this;
            sinks = source.getSinkPropagator().getSinks();
        }
        return sinks;
    }


    public abstract BitMask getDeclaredMask();
    public abstract BitMask getInferredMask();
}
