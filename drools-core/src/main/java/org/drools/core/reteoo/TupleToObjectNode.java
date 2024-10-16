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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.drools.base.base.ObjectType;
import org.drools.base.common.NetworkNode;
import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.base.reteoo.NodeTypeEnums;
import org.drools.base.rule.Pattern;
import org.drools.core.RuleBaseConfiguration;
import org.drools.core.common.ActivationsManager;
import org.drools.core.common.InternalWorkingMemory;
import org.drools.core.common.Memory;
import org.drools.core.common.PropagationContext;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.common.UpdateContext;
import org.drools.core.reteoo.SegmentMemory.SegmentPrototype;
import org.drools.core.reteoo.builder.BuildContext;
import org.drools.util.bitmask.BitMask;
import org.kie.api.definition.rule.Rule;

/**
 * When joining a subnetwork into the main network again, TupleToObjectNode adapts the
 * subnetwork's tuple into a fact in order right join it with the tuple being propagated in
 * the main network.
 */
public class TupleToObjectNode extends ObjectSource
    implements
    LeftTupleSinkNode,
    PathEndNode {

    private static final long serialVersionUID = 510l;

    private LeftTupleSource   tupleSource;

    /**
     * This is first node inside of the subnetwork. The split, with two outs, would be the parent node.
     */
    private LeftTupleSource   startTupleSource;

    private boolean           tupleMemoryEnabled;

    private LeftTupleSinkNode previousTupleSinkNode;
    private LeftTupleSinkNode nextTupleSinkNode;

    private LeftTupleNode[] pathNodes;

    private PathEndNode[] pathEndNodes;

    private PathMemSpec pathMemSpec;

    private SegmentPrototype[] segmentPrototypes;

    private SegmentPrototype[] eagerSegmentPrototypes;

    private int objectCount;

    public TupleToObjectNode() {
    }

    /**
     * Constructor specifying the unique id of the node in the Rete network, the position of the propagating <code>FactHandleImpl</code> in
     * <code>ReteTuple</code> and the source that propagates the receive <code>ReteTuple<code>s.
     *
     * @param id
     *      Unique id
     * @param source
     *      The <code>TupleSource</code> which propagates the received <code>ReteTuple</code>
     */
    public TupleToObjectNode(final int id,
                             final LeftTupleSource source,
                             final LeftTupleSource startTupleSource,
                             final BuildContext context) {
        super( id, context.getPartitionId() );
        this.tupleSource = source;
        this.tupleMemoryEnabled = context.isTupleMemoryEnabled();
        this.startTupleSource = startTupleSource;

        hashcode = calculateHashCode();
        initMemoryId( context );
    }

    @Override
    public PathMemSpec getPathMemSpec() {
        return getPathMemSpec(null);
    }


    /**
     * used during network build time, potentially during rule removal time.
     * @param removingTN
     * @return
     */
    @Override
    public PathMemSpec getPathMemSpec(TerminalNode removingTN) {
        if (pathMemSpec == null) {
            pathMemSpec = calculatePathMemSpec( startTupleSource, removingTN );
        }
        return pathMemSpec;
    }


    @Override
    public void nullPathMemSpec() {
        pathMemSpec = null;
    }

    @Override
    public void setPathMemSpec(PathMemSpec pathMemSpec) {
        this.pathMemSpec = pathMemSpec;
    }

    @Override
    public void resetPathMemSpec(TerminalNode removingTN) {
        nullPathMemSpec();
        pathMemSpec = getPathMemSpec(removingTN);
    }

    @Override
    public void setSegmentPrototypes(SegmentPrototype[] smems) {
        this.segmentPrototypes = smems;
    }

    @Override
    public SegmentPrototype[] getSegmentPrototypes() {
        return segmentPrototypes;
    }

    @Override
    public SegmentPrototype[] getEagerSegmentPrototypes() {
        return eagerSegmentPrototypes;
    }

    @Override
    public void setEagerSegmentPrototypes(SegmentPrototype[] eagerSegmentPrototypes) {
        this.eagerSegmentPrototypes = eagerSegmentPrototypes;
    }

    @Override
    public void setPathEndNodes(PathEndNode[] pathEndNodes) {
        this.pathEndNodes = pathEndNodes;
    }

    @Override
    public PathEndNode[] getPathEndNodes() {
        return pathEndNodes;
    }

    public LeftTupleSource getStartTupleSource() {
        return startTupleSource;
    }

    public int getPathIndex() {
        return tupleSource.getPathIndex() + 1;
    }

    public int getObjectCount() {
        return objectCount;
    }

    public void setObjectCount(int count) {
        objectCount = count;
    }

    /**
     * Creates and return the node memory
     */    
    public SubnetworkPathMemory createMemory(final RuleBaseConfiguration config, ReteEvaluator reteEvaluator) {
        return (SubnetworkPathMemory) AbstractTerminalNode.initPathMemory(this, new SubnetworkPathMemory(this, reteEvaluator));
    }

    public void doAttach( BuildContext context ) {
        this.tupleSource.addTupleSink( this, context );
    }

    public void networkUpdated(UpdateContext updateContext) {
        this.tupleSource.networkUpdated(updateContext);
    }


    protected boolean doRemove(final RuleRemovalContext context,
                            final ReteooBuilder builder) {
        if ( !isInUse() ) {
            tupleSource.removeTupleSink(this);
            return true;
        }
        return false;
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
        return NodeTypeEnums.TupleToObjectNode;
    }

    private int calculateHashCode() {
        return this.tupleSource.hashCode() * 17 + ((this.tupleMemoryEnabled) ? 1234 : 4321);
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }

        return ((NetworkNode)object).getType() == NodeTypeEnums.TupleToObjectNode && this.hashCode() == object.hashCode() &&
               this.tupleSource.getId() == ((TupleToObjectNode)object).tupleSource.getId() &&
               this.tupleMemoryEnabled == ( (TupleToObjectNode) object ).tupleMemoryEnabled;
    }

    @Override
    public String toString() {
        return "RightInputAdapterNode(" + id + ")[ tupleMemoryEnabled=" + tupleMemoryEnabled + ", tupleSource=" + tupleSource + ", source="
               + source + ", associations=" + associations + ", partitionId=" + partitionId + "]";
    }

    public LeftTupleSource getLeftTupleSource() {
        return this.tupleSource;
    }

    public void setTupleSource( LeftTupleSource tupleSource ) {
        this.tupleSource = tupleSource;
    }

    public ObjectTypeNodeId getInputOtnId() {
        throw new UnsupportedOperationException();
    }

    public void setInputOtnId(ObjectTypeNodeId leftInputOtnId) {
        throw new UnsupportedOperationException();
    }      
    
    @Override
    public BitMask calculateDeclaredMask(Pattern pattern, ObjectType modifiedType, List<String> settableProperties) {
        throw new UnsupportedOperationException();
    }

    public static class SubnetworkPathMemory extends PathMemory implements Memory {
        private List<RuleImpl> rules;

        public SubnetworkPathMemory(PathEndNode pathEndNode, ReteEvaluator reteEvaluator) {
            super(pathEndNode, reteEvaluator);
        }

        @Override
        protected boolean initDataDriven( ReteEvaluator reteEvaluator ) {
            for (PathEndNode pnode : getPathEndNode().getPathEndNodes()) {
                if (NodeTypeEnums.isTerminalNode(pnode)) {
                    RuleImpl rule = ( (TerminalNode) pnode ).getRule();
                    if ( isRuleDataDriven( reteEvaluator, rule ) ) {
                        return true;
                    }
                }
            }
            return false;
        }

        public TupleToObjectNode getTupleToObjectNode() {
            return (TupleToObjectNode) getPathEndNode();
        }

        @Override
        public void doLinkRule(ReteEvaluator reteEvaluator) {
            getTupleToObjectNode().getObjectSinkPropagator().doLinkSubnetwork(reteEvaluator);
        }

        @Override
        public void doLinkRule(ActivationsManager activationsManager) {
            doLinkRule(activationsManager.getReteEvaluator());
        }

        @Override
        public void doUnlinkRule(ReteEvaluator reteEvaluator) {
            getTupleToObjectNode().getObjectSinkPropagator().doUnlinkSubnetwork(reteEvaluator);
        }

        private void updateRuleTerminalNodes() {
            rules = new ArrayList<>();
            for ( ObjectSink osink : getTupleToObjectNode().getObjectSinkPropagator().getSinks() ) {
                for ( LeftTupleSink ltsink : ((BetaNode)osink).getSinkPropagator().getSinks() )  {
                    findAndAddTN(ltsink, rules );
                }
            }
        }

        private void findAndAddTN( LeftTupleSink ltsink, List<RuleImpl> terminalNodes) {
            if ( NodeTypeEnums.isTerminalNode(ltsink)) {
                terminalNodes.add( ((TerminalNode)ltsink).getRule() );
            } else if ( ltsink.getType() == NodeTypeEnums.TupleToObjectNode) {
                for ( NetworkNode childSink : ( ltsink).getSinks() )  {
                    findAndAddTN((LeftTupleSink)childSink, terminalNodes);
                }
            } else {
                for ( LeftTupleSink childLtSink : (ltsink).getSinkPropagator().getSinks() )  {
                    findAndAddTN(childLtSink, terminalNodes);
                }
            }
        }

        public List<RuleImpl> getAssociatedRules() {
            if ( rules == null ) {
                updateRuleTerminalNodes();
            }
            return rules;
        }

        public String getRuleNames() {
            Set<String> ruleNames = new HashSet<>();
            for (RuleImpl rule : getAssociatedRules()) {
                ruleNames.add(rule.getName());
            }
            return ruleNames.toString();
        }

        @Override
        public int getNodeType() {
            return NodeTypeEnums.TupleToObjectNode;
        }

        public String toString() {
            return "TupleToObjectNodeMem(" + getTupleToObjectNode().getId() + ") [" + getRuleNames() + "]";
        }
    }

    public BitMask getInferredMask() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void updateSink(ObjectSink sink, PropagationContext context, InternalWorkingMemory wm) {
        throw new UnsupportedOperationException();
    }

    public LeftTupleNode[] getPathNodes() {
        if (pathNodes == null) {
            pathNodes = AbstractTerminalNode.getPathNodes( this );
        }
        return pathNodes;
    }

    public boolean hasPathNode(LeftTupleNode node) {
        for (LeftTupleNode pathNode : getPathNodes()) {
            if (node.getId() == pathNode.getId()) {
                return true;
            }
        }
        return false;
    }

    public LeftTupleSinkPropagator getSinkPropagator() {
        return EmptyLeftTupleSinkAdapter.getInstance();
    }

    @Override
    public void addAssociation(Rule rule, BuildContext context) {
        super.addAssociation(rule, context);
        context.addPathEndNode( this );
    }

    @Override
    public boolean removeAssociation( Rule rule, RuleRemovalContext context ) {
        boolean result = super.associations.remove(rule);
        if (getAssociationsSize() == 0) {
            // avoid to recalculate the pathEndNodes if this node is going to be removed
            return result;
        }

        List<PathEndNode> remainingPathNodes = new ArrayList<>();
        for (PathEndNode pathEndNode : pathEndNodes) {
            if (pathEndNode.getAssociatedTerminalsSize() > 0) {
                remainingPathNodes.add(pathEndNode);
            }
        }
        pathEndNodes = remainingPathNodes.toArray( new PathEndNode[remainingPathNodes.size()] );
        return result;
    }

}
