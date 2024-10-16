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
import java.util.HashMap;
import java.util.Map;

import org.drools.base.common.NetworkNode;
import org.drools.base.reteoo.NodeTypeEnums;
import org.drools.base.rule.EvalCondition;
import org.drools.base.rule.RuleComponent;
import org.drools.core.RuleBaseConfiguration;
import org.drools.core.common.Memory;
import org.drools.core.common.MemoryFactory;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.common.UpdateContext;
import org.drools.core.reteoo.builder.BuildContext;
import org.drools.core.util.AbstractLinkedListNode;
import org.kie.api.definition.rule.Rule;

public class EvalConditionNode extends LeftTupleSource
    implements
    LeftTupleSinkNode,
    MemoryFactory<EvalConditionNode.EvalMemory> {

    private static final long serialVersionUID = 510l;

    /** The semantic <code>Test</code>. */
    protected EvalCondition     condition;

    protected boolean         tupleMemoryEnabled;

    private LeftTupleSinkNode previousTupleSinkNode;
    private LeftTupleSinkNode nextTupleSinkNode;

    private Map<RuleKey, RuleComponent> componentsMap = new HashMap<>();

    // ------------------------------------------------------------
    // Constructors
    // ------------------------------------------------------------
    public EvalConditionNode() {

    }

    public EvalConditionNode(final int id,
                             final LeftTupleSource tupleSource,
                             final EvalCondition eval,
                             final BuildContext context) {
        super(id, context);
        this.condition = eval;
        setLeftTupleSource(tupleSource);
        this.setObjectCount(leftInput.getObjectCount()); // 'eval' nodes do not increase the count
        this.tupleMemoryEnabled = context.isTupleMemoryEnabled();

        initMasks(context);

        hashcode = calculateHashCode();
    }

    public void doAttach( BuildContext context ) {
        super.doAttach(context);
        this.leftInput.addTupleSink( this, context );
    }

    public void networkUpdated(UpdateContext updateContext) {
        this.leftInput.networkUpdated(updateContext);
    }

    @Override
    protected void initInferredMask() {
        super.initInferredMask( );
        if (NodeTypeEnums.isBetaNode(leftInput)) {
            ((BetaNode)leftInput).disablePropertyReactivity();
        }
    }

    // ------------------------------------------------------------
    // Instance methods
    // ------------------------------------------------------------

    /**
     * Retrieve the <code>Test</code> associated with this node.
     *
     * @return The <code>Test</code>.
     */
    public EvalCondition getCondition() {
        return this.condition;
    }
    
    /**
     * Produce a debug string.
     *
     * @return The debug string.
     */
    public String toString() {
        return "[EvalConditionNode(" + this.id + ")]: cond=" + this.condition + "]";
    }

    private int calculateHashCode() {
        return this.leftInput.hashCode() ^ this.condition.hashCode();
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }

        if (((NetworkNode)object).getType() != NodeTypeEnums.EvalConditionNode || this.hashCode() != object.hashCode()) {
            return false;
        }

        EvalConditionNode other = (EvalConditionNode)object;
        return this.leftInput.getId() == other.leftInput.getId() && this.condition.equals( other.condition );
    }

    public EvalMemory createMemory(final RuleBaseConfiguration config, ReteEvaluator reteEvaluator) {
        return new EvalMemory( this.condition.createContext() );
    }

    public boolean isLeftTupleMemoryEnabled() {
        return tupleMemoryEnabled;
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

    public int getType() {
        return NodeTypeEnums.EvalConditionNode;
    }

    public static class EvalMemory extends AbstractLinkedListNode<Memory>
        implements
        Externalizable,
        Memory {

        private static final long serialVersionUID = 510l;

        public Object             context;
        
        private SegmentMemory     memory;

        public EvalMemory() {

        }

        public EvalMemory(final Object context) {
            this.context = context;
        }

        public void readExternal(ObjectInput in) throws IOException,
                                                ClassNotFoundException {
            context = in.readObject();
        }

        public void writeExternal(ObjectOutput out) throws IOException {
            out.writeObject( context );
        }

        public int getNodeType() {
            return NodeTypeEnums.EvalConditionNode;
        }

        public void setSegmentMemory(SegmentMemory smem) {
            this.memory = smem;
        }
        
        public SegmentMemory getSegmentMemory() {
            return this.memory;
        }

        public void reset() { }
    }

    @Override
    public ObjectTypeNode getObjectTypeNode() {
        return leftInput.getObjectTypeNode();
    }

    protected boolean doRemove(final RuleRemovalContext context,
                               final ReteooBuilder builder) {
        if ( !this.isInUse() ) {
            getLeftTupleSource().removeTupleSink( this );
            return true;
        } else {
            // need to re-wire eval expression to the same one from another rule
            // that is sharing this node
            this.condition = (EvalCondition) componentsMap.values().iterator().next();
            return false;
        }
    }


    public static class RuleKey {
        Rule rule;
        int subRuleindex;

        public RuleKey(Rule rule, int subRuleindex) {
            this.rule = rule;
            this.subRuleindex = subRuleindex;
        }

        @Override
        public boolean equals(Object o) {
            RuleKey ruleKey = (RuleKey) o;
            return subRuleindex == ruleKey.subRuleindex && rule.equals(ruleKey.rule);
        }

        @Override
        public int hashCode() {
            return 31 * (31 + rule.hashCode()) + subRuleindex;
        }
    }

    @Override
    public void addAssociation(Rule rule, BuildContext context) {
        super.addAssociation(rule, context);
        componentsMap.put(new RuleKey(rule, context.getSubRuleIndex()), context.peekRuleComponent());
    }

    @Override
    public boolean removeAssociation( Rule rule, RuleRemovalContext context ) {
        boolean result = super.removeAssociation(rule, context);
        if (!isAssociatedWith( rule )) {
            componentsMap.remove( new RuleKey(rule, context.getSubRuleIndex()) );
        }
        return result;
    }
}
