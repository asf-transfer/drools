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
package org.drools.core.phreak;

import java.util.ArrayList;
import java.util.List;

import org.drools.base.common.NetworkNode;
import org.drools.base.reteoo.NodeTypeEnums;
import org.drools.core.impl.InternalRuleBase;
import org.drools.core.reteoo.AsyncReceiveNode;
import org.drools.core.reteoo.AsyncSendNode;
import org.drools.core.reteoo.BetaNode;
import org.drools.core.reteoo.ConditionalBranchNode;
import org.drools.core.reteoo.EvalConditionNode;
import org.drools.core.reteoo.ExistsNode;
import org.drools.core.reteoo.FromNode;
import org.drools.core.reteoo.LeftInputAdapterNode;
import org.drools.core.reteoo.LeftTupleNode;
import org.drools.core.reteoo.LeftTupleSinkNode;
import org.drools.core.reteoo.LeftTupleSinkPropagator;
import org.drools.core.reteoo.LeftTupleSource;
import org.drools.core.reteoo.NotNode;
import org.drools.core.reteoo.PathEndNode;
import org.drools.core.reteoo.PathEndNode.PathMemSpec;
import org.drools.core.reteoo.QueryElementNode;
import org.drools.core.reteoo.ReactiveFromNode;
import org.drools.core.reteoo.TupleToObjectNode;
import org.drools.core.reteoo.SegmentMemory.AccumulateMemoryPrototype;
import org.drools.core.reteoo.SegmentMemory.AsyncReceiveMemoryPrototype;
import org.drools.core.reteoo.SegmentMemory.AsyncSendMemoryPrototype;
import org.drools.core.reteoo.SegmentMemory.BetaMemoryPrototype;
import org.drools.core.reteoo.SegmentMemory.ConditionalBranchMemoryPrototype;
import org.drools.core.reteoo.SegmentMemory.EvalMemoryPrototype;
import org.drools.core.reteoo.SegmentMemory.FromMemoryPrototype;
import org.drools.core.reteoo.SegmentMemory.LiaMemoryPrototype;
import org.drools.core.reteoo.SegmentMemory.MemoryPrototype;
import org.drools.core.reteoo.SegmentMemory.QueryMemoryPrototype;
import org.drools.core.reteoo.SegmentMemory.ReactiveFromMemoryPrototype;
import org.drools.core.reteoo.SegmentMemory.RightInputAdapterPrototype;
import org.drools.core.reteoo.SegmentMemory.SegmentPrototype;
import org.drools.core.reteoo.SegmentMemory.TerminalPrototype;
import org.drools.core.reteoo.SegmentMemory.TimerMemoryPrototype;
import org.drools.core.reteoo.TerminalNode;
import org.drools.core.reteoo.TimerNode;

public class BuildtimeSegmentUtilities {

    public static void updateSegmentEndNodes(PathEndNode endNode) {
        SegmentPrototype smproto = endNode.getSegmentPrototypes()[endNode.getSegmentPrototypes().length-1];
        if (smproto.getPathEndNodes() != null) {
            // This is a special check, for shared subnetwork paths. It ensure it's initallysed only once,
            // even though the TupleToObjectNode is shared with different TNs.
            return;
        }

        for ( int i = 0; i < endNode.getSegmentPrototypes().length; i++) {
            smproto = endNode.getSegmentPrototypes()[i];

            if ( smproto.getPathEndNodes() != null) {
                PathEndNode[] existingNodes = smproto.getPathEndNodes();
                PathEndNode[] newNodes = new PathEndNode[existingNodes.length+1];
                System.arraycopy(existingNodes, 0, newNodes, 0, existingNodes.length);
                newNodes[newNodes.length-1] = endNode;
                smproto.setPathEndNodes(newNodes);
            } else {
                smproto.setPathEndNodes( new PathEndNode[]{endNode});
            }
        }
    }

    private static void setSegments(PathEndNode endNode, SegmentPrototype[] smems) {
        List<SegmentPrototype> eager = new ArrayList<>();
        for (SegmentPrototype smem : smems) {
            // The segments before the start of a subnetwork, will be null for a TupleToObjectNode path.
            if (smem != null && smem.requiresEager()) {
                eager.add(smem);
            }
        }
        endNode.setEagerSegmentPrototypes(eager.toArray(new SegmentPrototype[eager.size()]));

        long allLinkedMaskTest = getPathAllLinkedMaskTest(smems, endNode);

        endNode.setPathMemSpec(new PathMemSpec(allLinkedMaskTest, smems.length));

        endNode.setSegmentPrototypes(smems);

        updateSegmentEndNodes(endNode);
    }

    public static long getPathAllLinkedMaskTest(SegmentPrototype[] smems, PathEndNode endNode) {
        long allLinkedMaskTest = 0;
        for (int i = smems.length - 1; i >= 0; i--) {
            SegmentPrototype smem = smems[i];

            if (EagerPhreakBuilder.isInsideSubnetwork(endNode, smem) && smem.getAllLinkedMaskTest() > 0) {
                allLinkedMaskTest = allLinkedMaskTest | 1;
            }
            if (i > 0) {
                allLinkedMaskTest = nextNodePosMask(allLinkedMaskTest);
            }
        }
        return allLinkedMaskTest;
    }

    public static SegmentPrototype[] createPathProtoMemories(TerminalNode tn, TerminalNode removingTn, InternalRuleBase rbase) {
        // Will initialise all segments in a path
        SegmentPrototype[] smems = createLeftTupleNodeProtoMemories(tn, removingTn, rbase);

        // smems are empty, if there is no beta network. Which means it has an AlphaTerminalNode
        if  (smems.length > 0) {
            setSegments(tn, smems);
        }

        return smems;
    }

    public static SegmentPrototype[] createLeftTupleNodeProtoMemories(LeftTupleNode lts, TerminalNode removingTn, InternalRuleBase rbase) {
        LeftTupleNode segmentRoot = lts;
        LeftTupleNode segmentTip = lts;
        List<SegmentPrototype> smems = new ArrayList<>();
        int start = 0;

        LeftTupleNode firstConditional = getFirstConditionalBranchNode(lts);
        int recordBefore = firstConditional == null  ? Integer.MAX_VALUE : firstConditional.getPathIndex();  // nodes after a branch CE can notify, but they cannot impact linking

        do {
            // iterate to find the actual segment root
            while (!BuildtimeSegmentUtilities.isRootNode(segmentRoot, removingTn)) {
                segmentRoot = segmentRoot.getLeftTupleSource();
            }

            // Store all nodes for the main path in reverse order (we're starting from the terminal node).
            SegmentPrototype smem = rbase.getSegmentPrototype(segmentRoot);
            if (smem == null) {
                start = segmentRoot.getPathIndex(); // we want counter to start from the new segment proto only
                smem = createSegmentMemory(segmentRoot, segmentTip, recordBefore, removingTn, rbase);
            }
            smems.add(0, smem);

            // this is the new segment so set both to same, and it iterates for the actual segmentRoot next loop.
            segmentRoot = segmentRoot.getLeftTupleSource();
            segmentTip = segmentRoot;
        } while (segmentRoot != null); // it's after lian

        // reset to find the next segments and set their position and their bit mask
        int ruleSegmentPosMask = 1;
        for (int i = 0; i < smems.size(); i++) {
            if ( start >= 0 && smems.get(i) != null) { // The segments before the start of a subnetwork, will be null for a TupleToObjectNode path.
                smems.get(i).setPos(i);
                if (smems.get(i).getAllLinkedMaskTest() > 0) {
                    smems.get(i).setSegmentPosMaskBit(ruleSegmentPosMask);
                } else {
                    smems.get(i).setSegmentPosMaskBit(0);
                }
            }
            ruleSegmentPosMask = ruleSegmentPosMask << 1;
        }

        return smems.toArray(new SegmentPrototype[smems.size()]);
    }

    static LeftTupleNode getFirstConditionalBranchNode(LeftTupleNode tupleSource) {
        LeftTupleNode conditionalBranch = null;
        while (  !NodeTypeEnums.isLeftInputAdapterNode(tupleSource)) {
            if ( tupleSource.getType() == NodeTypeEnums.ConditionalBranchNode ) {
                conditionalBranch = tupleSource;
            }
            tupleSource = tupleSource.getLeftTupleSource();
        }
        return conditionalBranch;
    }

    /**
     * Initialises the NodeSegment memory for all nodes in the segment.
     */
    public static SegmentPrototype createSegmentMemory(LeftTupleNode segmentRoot, LeftTupleNode segmentTip, int recordBefore, TerminalNode removingTn, InternalRuleBase rbase) {
        LeftTupleNode node = segmentRoot;
        int nodeTypesInSegment = 0;

        SegmentPrototype smem = new SegmentPrototype(segmentRoot, segmentTip);
        List<MemoryPrototype> memories = new ArrayList<>();
        List<LeftTupleNode> nodes = new ArrayList<>();

        // Iterate all nodes on the same segment, assigning their position as a bit mask value
        // allLinkedTestMask is the resulting mask used to test if all nodes are linked in
        long nodePosMask = 1;
        long allLinkedTestMask = 0;
        boolean updateNodeBit = true;

        while (true) {
            nodeTypesInSegment = updateNodeTypesMask(node, nodeTypesInSegment);
            if (NodeTypeEnums.isBetaNode(node)) {
                boolean updateAllLinked = node.getPathIndex() < recordBefore && updateNodeBit;
                allLinkedTestMask = processBetaNode((BetaNode)node, smem, memories, nodes, nodePosMask, allLinkedTestMask, updateAllLinked, removingTn, rbase);
            } else {
                switch (node.getType()) {
                    case NodeTypeEnums.LeftInputAdapterNode:
                    case NodeTypeEnums.AlphaTerminalNode:
                        allLinkedTestMask = processLiaNode((LeftInputAdapterNode) node, memories, nodes, nodePosMask, allLinkedTestMask);
                        break;
                    case NodeTypeEnums.ConditionalBranchNode:
                        processConditionalBranchNode((ConditionalBranchNode) node, memories, nodes);
                        break;
                    case NodeTypeEnums.FromNode:
                        processFromNode((FromNode) node, memories, nodes);
                        break;
                    case NodeTypeEnums.EvalConditionNode:
                        processEvalFromNode((EvalConditionNode) node, memories, nodes);
                        break;
                    case NodeTypeEnums.ReactiveFromNode:
                        processReactiveFromNode((ReactiveFromNode) node, memories, nodes, nodePosMask);
                        break;
                    case NodeTypeEnums.TimerConditionNode:
                        processTimerNode((TimerNode) node, memories, nodes, nodePosMask);
                        break;
                    case NodeTypeEnums.AsyncSendNode:
                        processAsyncSendNode((AsyncSendNode) node, memories, nodes);
                        break;
                    case NodeTypeEnums.AsyncReceiveNode:
                        processAsyncReceiveNode((AsyncReceiveNode) node, memories, nodes, nodePosMask);
                        break;
                    case NodeTypeEnums.QueryElementNode:
                        updateNodeBit = processQueryNode((QueryElementNode) node, memories, nodes, nodePosMask);
                        break;
                    case NodeTypeEnums.TupleToObjectNode:
                        processRightInputAdapterNode((TupleToObjectNode) node, memories, nodes);
                        break;
                    case NodeTypeEnums.RuleTerminalNode:
                    case NodeTypeEnums.QueryTerminalNode:
                        processTerminalNode((TerminalNode) node, memories, nodes);
                        break;
                }
            }

            nodePosMask = nextNodePosMask(nodePosMask);

            if (node == segmentTip || !(NodeTypeEnums.isLeftTupleSource(node))) {
                break;
            }

            node = ((LeftTupleSource)node).getFirstLeftTupleSinkIgnoreRemoving(removingTn);
        }
        smem.setAllLinkedMaskTest(allLinkedTestMask);

        smem.setNodesInSegment(nodes.toArray( new LeftTupleNode[nodes.size()]));
        smem.setMemories(memories.toArray( new MemoryPrototype[memories.size()]));
        smem.setNodeTypesInSegment(nodeTypesInSegment);

        rbase.registerSegmentPrototype(segmentRoot, smem);

        return smem;
    }

    public static boolean requiresAnEagerSegment(int nodeTypesInSegment) {
        // A Not node has to be eagerly initialized unless in its segment there is at least a join node
        return isSet(nodeTypesInSegment, NOT_NODE_BIT) &&
             !isSet(nodeTypesInSegment, JOIN_NODE_BIT) &&
             !isSet(nodeTypesInSegment, REACTIVE_EXISTS_NODE_BIT);
    }

    public static long nextNodePosMask(long posMask) {
        // prevent overflow of segment and path memories masks when a segment has 64 or more nodes or a path has 64 or more segments
        // in this extreme case all the items after the 64th will be all mapped by the same bit and then the linking of one of them
        // will be enough to consider all those item linked
        long nextNodePosMask = posMask << 1;
        return nextNodePosMask > 0 ? nextNodePosMask : posMask;
    }

    private static boolean processQueryNode(QueryElementNode queryNode, List<MemoryPrototype> memories, List<LeftTupleNode> nodes, long nodePosMask) {
        QueryMemoryPrototype queryNodeMem = new QueryMemoryPrototype(nodePosMask, queryNode);
        memories.add(queryNodeMem);
        nodes.add(queryNode);

        return ! queryNode.getQueryElement().isAbductive();
    }

    private static void processAsyncSendNode(AsyncSendNode tupleSource, List<MemoryPrototype> memories, List<LeftTupleNode> nodes) {
        AsyncSendMemoryPrototype mem = new AsyncSendMemoryPrototype();
        memories.add(mem);
        nodes.add(tupleSource);
    }

    private static void processAsyncReceiveNode(AsyncReceiveNode tupleSource, List<MemoryPrototype> memories, List<LeftTupleNode> nodes, long nodePosMask) {
        AsyncReceiveMemoryPrototype mem = new AsyncReceiveMemoryPrototype(nodePosMask);
        memories.add(mem);
        nodes.add(tupleSource);
    }

    private static void processConditionalBranchNode(ConditionalBranchNode tupleSource, List<MemoryPrototype> memories, List<LeftTupleNode> nodes) {
        ConditionalBranchMemoryPrototype mem = new ConditionalBranchMemoryPrototype();
        memories.add(mem);
        nodes.add(tupleSource);
    }

    private static void processRightInputAdapterNode(TupleToObjectNode tupleSource, List<MemoryPrototype> memories, List<LeftTupleNode> nodes) {
        RightInputAdapterPrototype mem = new RightInputAdapterPrototype();
        memories.add(mem);
        nodes.add(tupleSource);
    }

    private static void processTerminalNode(TerminalNode tupleSource, List<MemoryPrototype> memories, List<LeftTupleNode> nodes) {
        TerminalPrototype mem = new TerminalPrototype();
        memories.add(mem);
        nodes.add(tupleSource);
    }

    private static void processFromNode(FromNode tupleSource, List<MemoryPrototype> memories, List<LeftTupleNode> nodes) {
        FromMemoryPrototype mem = new FromMemoryPrototype();
        memories.add(mem);
        nodes.add(tupleSource);
    }

    private static void processEvalFromNode(EvalConditionNode tupleSource, List<MemoryPrototype> memories, List<LeftTupleNode> nodes) {
        EvalMemoryPrototype mem = new EvalMemoryPrototype();
        memories.add(mem);
        nodes.add(tupleSource);
    }

    private static void processReactiveFromNode(ReactiveFromNode tupleSource, List<MemoryPrototype> memories, List<LeftTupleNode> nodes, long nodePosMask) {
        ReactiveFromMemoryPrototype mem = new ReactiveFromMemoryPrototype(nodePosMask);
        memories.add(mem);
        nodes.add(tupleSource);
    }

    private static void processTimerNode(TimerNode tupleSource, List<MemoryPrototype> memories, List<LeftTupleNode> nodes, long nodePosMask) {
        TimerMemoryPrototype tnMem = new TimerMemoryPrototype(nodePosMask);
        memories.add(tnMem);
        nodes.add(tupleSource);
    }

    private static long processLiaNode(LeftInputAdapterNode tupleSource, List<MemoryPrototype> memories, List<LeftTupleNode> nodes,
                                       long nodePosMask, long allLinkedTestMask) {
        LiaMemoryPrototype liaMemory = new LiaMemoryPrototype(nodePosMask);
        memories.add(liaMemory);
        nodes.add(tupleSource);
        allLinkedTestMask = allLinkedTestMask | nodePosMask;
        return allLinkedTestMask;
    }

    private static long processBetaNode(BetaNode betaNode, SegmentPrototype smem, List<MemoryPrototype> memories, List<LeftTupleNode> nodes,
                                        long nodePosMask, long allLinkedTestMask, boolean updateNodeBit, TerminalNode removingTn, InternalRuleBase rbase) {
        TupleToObjectNode tton = null;
        if (betaNode.getRightInput().inputIsTupleToObjectNode()) {
            // there is a subnetwork, so create all it's segment memory prototypes
            tton = (TupleToObjectNode) betaNode.getRightInput().getParent();

            SegmentPrototype[] smems = createLeftTupleNodeProtoMemories(tton, removingTn, rbase);
            setSegments(tton, smems);

            if (updateNodeBit && canBeDisabled(betaNode) && tton.getPathMemSpec().allLinkedTestMask() > 0) {
                // only TupleToObjectNode's with reactive subnetworks can be disabled and thus need checking
                allLinkedTestMask = allLinkedTestMask | nodePosMask;
            }
        } else if (updateNodeBit && canBeDisabled(betaNode)) {
            allLinkedTestMask = allLinkedTestMask | nodePosMask;

        }
        if (NodeTypeEnums.NotNode == betaNode.getType()) {
            // not nodes start up linked in
            smem.linkNode(nodePosMask);
        }

        BetaMemoryPrototype bm = new BetaMemoryPrototype(nodePosMask, tton);

        if (NodeTypeEnums.AccumulateNode == betaNode.getType())  {
            AccumulateMemoryPrototype am = new AccumulateMemoryPrototype(bm);
            memories.add(am);
        } else {
            memories.add(bm);
        }

        nodes.add(betaNode);
        return allLinkedTestMask;
    }

    public static boolean canBeDisabled(BetaNode betaNode) {
        // non empty not nodes and accumulates can never be disabled and thus don't need checking
        return (!(NodeTypeEnums.NotNode == betaNode.getType() && !((NotNode) betaNode).isEmptyBetaConstraints()) &&
                NodeTypeEnums.AccumulateNode != betaNode.getType() && !betaNode.getRightInput().isRightInputPassive());
    }

    /**
     * Returns whether the node is the root of a segment.
     * Lians are always the root of a segment.
     *
     * node cannot be null.
     *
     * The result should discount any removingRule. That means it gives you the result as
     * if the rule had already been removed from the network.
     */
    public static boolean isRootNode(LeftTupleNode node, TerminalNode ignoreTn) {
        return NodeTypeEnums.isLeftInputAdapterNode(node) || isTipNode( node.getLeftTupleSource(), ignoreTn );
    }

    /**
     * Returns whether the node is the tip of a segment.
     * EndNodes (rtn and TupleToObjectNode) are always the tip of a segment.
     *
     * node cannot be null.
     *
     * The result should discount any removingRule. That means it gives you the result as
     * if the rule had already been removed from the network.
     */
    public static boolean isTipNode( LeftTupleNode node, TerminalNode removingTN ) {
        return NodeTypeEnums.isEndNode(node) || isNonTerminalTipNode( node, removingTN );
    }

    public static boolean isNonTerminalTipNode( LeftTupleNode node, TerminalNode removingTN ) {
        LeftTupleSinkPropagator sinkPropagator = node.getSinkPropagator();

        if (removingTN == null) {
            return sinkPropagator.size() > 1;
        }

        if (sinkPropagator.size() == 1) {
            return false;
        }

        // we know the sink size is greater than 1 and that there is a removingRule that needs to be ignored.
        int count = 0;
        for ( LeftTupleSinkNode sink = sinkPropagator.getFirstLeftTupleSink(); sink != null; sink = sink.getNextLeftTupleSinkNode() ) {
            if ( sinkNotExclusivelyAssociatedWithTerminal( removingTN, sink ) ) {
                count++;
                if ( count > 1 ) {
                    // There is more than one sink that is not for the removing rule
                    return true;
                }
            }
        }

        return false;
    }

    public static boolean sinkNotExclusivelyAssociatedWithTerminal( TerminalNode removingTN, LeftTupleNode sink ) {
        return sink.getAssociatedTerminalsSize() > 1 || !sink.hasAssociatedTerminal(removingTN);
    }

    public static final int NOT_NODE_BIT               = 1;
    public static final int JOIN_NODE_BIT              = 1 << 1;
    public static final int REACTIVE_EXISTS_NODE_BIT   = 1 << 2;
    public static final int PASSIVE_EXISTS_NODE_BIT    = 1 << 3;

    public static final int CONDITIONAL_BRANCH_BIT = 1 << 4;

    public static int updateNodeTypesMask(NetworkNode node, int mask) {
        if (node != null) {
            switch ( node.getType() ) {
                case NodeTypeEnums.JoinNode:
                    mask |= JOIN_NODE_BIT;
                    break;
                case NodeTypeEnums.ExistsNode:
                    if ( ( (ExistsNode) node ).getRightInput().isRightInputPassive() ) {
                        mask |= PASSIVE_EXISTS_NODE_BIT;
                    } else {
                        mask |= REACTIVE_EXISTS_NODE_BIT;
                    }
                    break;
                case NodeTypeEnums.NotNode:
                    mask |= NOT_NODE_BIT;
                    break;
                case NodeTypeEnums.ConditionalBranchNode:
                    mask |= CONDITIONAL_BRANCH_BIT;
                    break;
            }
        }
        return mask;
    }

    public static boolean isSet(int mask, int bit) {
        return (mask & bit) == bit;
    }

    public static LeftTupleNode findSegmentRoot(LeftTupleNode tupleSource) {
        return findSegmentRoot(tupleSource, null);
    }

    public static LeftTupleNode findSegmentRoot(LeftTupleNode tupleSource, TerminalNode ignoreTn) {
        while (!BuildtimeSegmentUtilities.isRootNode(tupleSource, ignoreTn)) {
            tupleSource = tupleSource.getLeftTupleSource();
        }
        return tupleSource;
    }

    public static boolean isAssociatedWith(NetworkNode node, TerminalNode tn) {
        return node.hasAssociatedTerminal(tn);
    }
}
