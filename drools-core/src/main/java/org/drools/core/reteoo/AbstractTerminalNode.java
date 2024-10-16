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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.drools.base.base.ObjectType;
import org.drools.base.common.NetworkNode;
import org.drools.base.common.RuleBasePartitionId;
import org.drools.base.definitions.rule.impl.RuleImpl;
import org.drools.base.reteoo.NodeTypeEnums;
import org.drools.base.rule.Declaration;
import org.drools.base.rule.GroupElement;
import org.drools.base.rule.Pattern;
import org.drools.core.RuleBaseConfiguration;
import org.drools.core.common.BaseNode;
import org.drools.core.common.ReteEvaluator;
import org.drools.core.common.UpdateContext;
import org.drools.core.reteoo.SegmentMemory.SegmentPrototype;
import org.drools.core.reteoo.builder.BuildContext;
import org.drools.util.bitmask.AllSetBitMask;
import org.drools.util.bitmask.BitMask;
import org.drools.util.bitmask.EmptyBitMask;

import static org.drools.base.reteoo.PropertySpecificUtil.isPropertyReactive;

public abstract class AbstractTerminalNode extends BaseNode implements TerminalNode {
    /** The rule to invoke upon match. */
    private RuleImpl                      rule;

    /**
     * the subrule reference is needed to resolve declarations
     * because declarations may have different offsets in each subrule
     */
    private GroupElement      subrule;
    private int               subruleIndex;
    private Declaration[]     allDeclarations;
    protected Declaration[]   requiredDeclarations;


    private LeftTupleSinkNode previousTupleSinkNode;
    private LeftTupleSinkNode nextTupleSinkNode;

    private LeftTupleSource   tupleSource;

    private LeftTupleSource   startTupleSource;

    private BitMask declaredMask = EmptyBitMask.get();
    private BitMask inferredMask = EmptyBitMask.get();
    private BitMask negativeMask = EmptyBitMask.get();

    private LeftTupleNode[]         pathNodes;

    private transient PathEndNode[] pathEndNodes;

    private SegmentPrototype[]      segmentPrototypes;

    private SegmentPrototype[]      eagerSegmentPrototypes;

    protected PathMemSpec           pathMemSpec;

    private int                     objectCount;

    public AbstractTerminalNode() { }

    public AbstractTerminalNode(int id, RuleBasePartitionId partitionId, LeftTupleSource source,
                                BuildContext context,
                                RuleImpl rule, GroupElement subrule, int subruleIndex) {
        super(id, partitionId);
        this.tupleSource = source;
        this.rule = rule;
        this.subrule = subrule;
        this.subruleIndex = subruleIndex;
        this.setObjectCount(getLeftTupleSource().getObjectCount()); // 'terminal' nodes do not increase the count
        context.addPathEndNode(this);
        initMemoryId( context );
        initDeclaredMask(context);
        initInferredMask();

        Map<String, Declaration> decls = this.subrule.getOuterDeclarations();
        this.allDeclarations = decls.values().toArray( new Declaration[decls.size()] );
        initDeclarations(decls, context);

        LeftTupleSource current = getLeftTupleSource();
        while (current.getLeftTupleSource() != null) {
            current = current.getLeftTupleSource();
        }
        startTupleSource = current;
    }

    abstract void initDeclarations(Map<String, Declaration> decls, BuildContext context);

    @Override
    public PathMemSpec getPathMemSpec() {
        return getPathMemSpec(null);
    }

    @Override
    public BaseNode getParent() {
        return tupleSource;
    }

    @Override
    public void setPathMemSpec(PathMemSpec pathMemSpec) {
        this.pathMemSpec = pathMemSpec;
    }

    @Override
    public PathMemSpec getPathMemSpec(TerminalNode removingTN) {
        if (pathMemSpec == null) {
            pathMemSpec = calculatePathMemSpec( startTupleSource, removingTN );
        }
        return pathMemSpec;
    }

    @Override
    public void resetPathMemSpec(TerminalNode removingTN) {
        // null all PathMemSpecs, for all pathEnds, or the recursion will use a previous value.
        // calling calculatePathMemSpec, will eventually getPathMemSpec on all nested ttons, so previous values must be nulled
        Arrays.stream(pathEndNodes).forEach( n -> {
            n.nullPathMemSpec();
            n.setSegmentPrototypes(null);
            n.setEagerSegmentPrototypes(null);}
        );
        pathMemSpec = removingTN == null ? null : calculatePathMemSpec( startTupleSource, removingTN );
    }

    public RuleImpl getRule() {
        return this.rule;
    }

    public GroupElement getSubRule() {
        return this.subrule;
    }

    @Override
    public int getSubruleIndex() {
        return subruleIndex;
    }

    public Declaration[] getAllDeclarations() {
        return this.allDeclarations;
    }

    public Declaration[] getRequiredDeclarations() {
        return this.requiredDeclarations;
    }

    public LeftTupleSource getStartTupleSource() {
        return startTupleSource;
    }

    public void nullPathMemSpec() {
        pathMemSpec = null;
    }

    @Override
    public void setPathEndNodes(PathEndNode[] pathEndNodes) {
        this.pathEndNodes = pathEndNodes;
    }


    @Override
    public PathEndNode[] getPathEndNodes() {
        return pathEndNodes;
    }

    @Override
    public void setSegmentPrototypes(SegmentPrototype[] smems) {
        this.segmentPrototypes = smems;
    }

    @Override
    public SegmentPrototype[] getSegmentPrototypes() {
        return segmentPrototypes;
    }

    public SegmentPrototype[] getEagerSegmentPrototypes() {
        return eagerSegmentPrototypes;
    }

    public void setEagerSegmentPrototypes(SegmentPrototype[] eagerSegmentPrototypes) {
        this.eagerSegmentPrototypes = eagerSegmentPrototypes;
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

    protected void initDeclaredMask(BuildContext context) {
        if ( !(NodeTypeEnums.isLeftInputAdapterNode(unwrapTupleSource()))) {
            // RTN's not after LIANode are not relevant for property specific, so don't block anything.
            declaredMask = AllSetBitMask.get();
            return;
        }

        Pattern pattern = context.getLastBuiltPatterns()[0];
        ObjectType objectType = pattern.getObjectType();

        if ( isPropertyReactive(context.getRuleBase(), objectType) ) {
            List<String> accessibleProperties = pattern.getAccessibleProperties( context.getRuleBase() );
            declaredMask = pattern.getPositiveWatchMask(accessibleProperties);
            negativeMask = pattern.getNegativeWatchMask(accessibleProperties);
        } else  {
            // if property specific is not on, then accept all modification propagations
            declaredMask = AllSetBitMask.get();
        }
    }

    public void initInferredMask() {
        LeftTupleSource leftTupleSource = unwrapTupleSource();
        if ( NodeTypeEnums.isLeftInputAdapterNode(leftTupleSource) &&
             ((LeftInputAdapterNode)leftTupleSource).getParentObjectSource().getType() == NodeTypeEnums.AlphaNode ) {
            AlphaNode alphaNode = (AlphaNode) ((LeftInputAdapterNode)leftTupleSource).getParentObjectSource();
            inferredMask = alphaNode.updateMask( getDeclaredMask() );
        } else {
            inferredMask = getDeclaredMask();
        }

        inferredMask = getInferredMask().resetAll( getNegativeMask() );
        if ( getNegativeMask().isAllSet() && !getDeclaredMask().isAllSet() ) {
            inferredMask = getInferredMask().setAll( getDeclaredMask() );
        }
    }

    public LeftTupleSource unwrapTupleSource() {
        return tupleSource.getType() == NodeTypeEnums.FromNode ? tupleSource.getLeftTupleSource() : tupleSource;
    }

    public PathMemory createMemory(RuleBaseConfiguration config, ReteEvaluator reteEvaluator) {
        return initPathMemory( this, new PathMemory(this, reteEvaluator) );
    }

    public static PathMemory initPathMemory( PathEndNode pathEndNode, PathMemory pmem ) {
        PathMemSpec pathMemSpec = pathEndNode.getPathMemSpec();
        pmem.setAllLinkedMaskTest(pathMemSpec.allLinkedTestMask );
        pmem.setSegmentMemories( new SegmentMemory[pathEndNode.getPathMemSpec().smemCount()] );
        return pmem;
    }

    protected boolean doRemove(final RuleRemovalContext context,
                               final ReteooBuilder builder) {
        getLeftTupleSource().removeTupleSink(this);
        this.tupleSource = null;
        return true;
    }

    public LeftTupleSource getLeftTupleSource() {
        return this.tupleSource;
    }

    public BitMask getDeclaredMask() {
        return declaredMask;
    }

    public BitMask getInferredMask() {
        return inferredMask;
    }

    public BitMask getNegativeMask() {
        return negativeMask;
    }

    public void networkUpdated(UpdateContext updateContext) {
        getLeftTupleSource().networkUpdated(updateContext);
    }

    public boolean isInUse() {
        return false;
    }

    public boolean isLeftTupleMemoryEnabled() {
        return false;
    }

    public static LeftTupleNode[] getPathNodes(PathEndNode endNode) {
        LeftTupleNode[] pathNodes = new LeftTupleNode[endNode.getPathIndex() + 1];
        for (LeftTupleNode node = endNode; node != null; node = node.getLeftTupleSource()) {
            pathNodes[node.getPathIndex()] = node;
        }
        return pathNodes;
    }

    public LeftTupleNode[] getPathNodes() {
        if (pathNodes == null) {
            pathNodes = getPathNodes( this );
        }
        return pathNodes;
    }

    public final boolean hasPathNode(LeftTupleNode node) {
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
    public final void setPartitionIdWithSinks( RuleBasePartitionId partitionId ) {
        this.partitionId = partitionId;
    }

    @Override
    public ObjectTypeNode getObjectTypeNode() {
        return getLeftTupleSource().getObjectTypeNode();
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

    public void visitLeftTupleNodes(Consumer<LeftTupleNode> func) {
        for (PathEndNode endNode : getPathEndNodes()) {
            for (int i = endNode.getPathNodes().length-1; i >= 0; i--) {
                LeftTupleNode node = endNode.getPathNodes()[i];
                func.accept(node);
                if (endNode.getStartTupleSource() == node) {
                    break;
                }
            }
        }
    }

    protected int calculateHashCode() {
        return (31 * (31 + this.rule.hashCode() )) + subruleIndex;
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }

        if (!NodeTypeEnums.isTerminalNode((NetworkNode) object)|| this.hashCode() != object.hashCode()) {
            return false;
        }
        final TerminalNode other = (TerminalNode) object;
        return getRule().equals(other.getRule()) && getSubruleIndex() == other.getSubruleIndex();
    }

}
